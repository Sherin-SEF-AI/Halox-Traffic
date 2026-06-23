package com.haloxtraffic.core.sensors.camera

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes a short list of frames into an H.264 MP4 evidence clip (§8) via MediaCodec + MediaMuxer
 * (buffer mode — no OpenGL). Used off the hot path on COMMIT for the pre/post context clip. Fail-soft:
 * any encoder/muxer error returns null and the case still seals (the clip is simply absent).
 *
 * The android.media APIs are stable (low compile risk); runtime correctness across encoders (colour
 * format, timestamps) is the part to device-test — hence the broad catch + null fallback.
 */
@Singleton
class ClipEncoder @Inject constructor() {

    /** @return the written MP4, or null on failure. Bitmaps must share even dimensions. */
    fun encode(frames: List<Bitmap>, fps: Int, out: File): File? {
        if (frames.isEmpty()) return null
        val w = frames[0].width and 1.inv() // force even
        val h = frames[0].height and 1.inv()
        if (w < 16 || h < 16) return null

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        return try {
            codec = MediaCodec.createEncoderByType(MIME)
            val colorFormat = selectColorFormat(codec)
            val format = MediaFormat.createVideoFormat(MIME, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 4).coerceAtLeast(800_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            drain(codec, muxer, frames, w, h, fps, colorFormat)
            out
        } catch (t: Throwable) {
            Timber.e(t, "Clip encode failed (device codec issue?)")
            runCatching { out.delete() }
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun drain(
        codec: MediaCodec, muxer: MediaMuxer, frames: List<Bitmap>, w: Int, h: Int, fps: Int, colorFormat: Int,
    ) {
        val info = MediaCodec.BufferInfo()
        val semiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        val frameSize = w * h * 3 / 2
        var inputDone = false
        var frameIdx = 0
        var trackIndex = -1
        var muxerStarted = false

        while (true) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    if (frameIdx < frames.size) {
                        val pixels = IntArray(w * h)
                        Bitmap.createScaledBitmap(frames[frameIdx], w, h, false).let { scaled ->
                            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                            if (scaled != frames[frameIdx]) scaled.recycle()
                        }
                        val yuv = if (semiPlanar) argbToNv12(pixels, w, h) else argbToI420(pixels, w, h)
                        codec.getInputBuffer(inIdx)?.apply { clear(); put(yuv) }
                        codec.queueInputBuffer(inIdx, 0, frameSize, frameIdx * 1_000_000L / fps, 0)
                        frameIdx++
                    } else {
                        codec.queueInputBuffer(inIdx, 0, 0, frameIdx * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
            }
            when (val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) { /* keep polling for EOS */ }
                else -> if (outIdx >= 0) {
                    val encoded: ByteBuffer? = codec.getOutputBuffer(outIdx)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted && encoded != null) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, encoded, info)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun selectColorFormat(codec: MediaCodec): Int {
        val supported = codec.codecInfo.getCapabilitiesForType(MIME).colorFormats.toSet()
        return PREFERRED.firstOrNull { it in supported } ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
    }

    companion object {
        private const val MIME = "video/avc"
        private const val TIMEOUT_US = 10_000L
        private val PREFERRED = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
        )

        /** ARGB pixels → I420 (planar Y, U, V). Pure + unit-testable. */
        fun argbToI420(argb: IntArray, w: Int, h: Int): ByteArray {
            val out = ByteArray(w * h * 3 / 2)
            val uStart = w * h
            val vStart = uStart + w * h / 4
            var uvIndex = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = argb[y * w + x]
                    val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                    out[y * w + x] = clampToByte((66 * r + 129 * g + 25 * b + 128 shr 8) + 16)
                    if (y % 2 == 0 && x % 2 == 0) {
                        out[uStart + uvIndex] = clampToByte((-38 * r - 74 * g + 112 * b + 128 shr 8) + 128)
                        out[vStart + uvIndex] = clampToByte((112 * r - 94 * g - 18 * b + 128 shr 8) + 128)
                        uvIndex++
                    }
                }
            }
            return out
        }

        /** ARGB pixels → NV12 (planar Y, interleaved UV). Pure + unit-testable. */
        fun argbToNv12(argb: IntArray, w: Int, h: Int): ByteArray {
            val out = ByteArray(w * h * 3 / 2)
            val uvStart = w * h
            var uvIndex = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = argb[y * w + x]
                    val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                    out[y * w + x] = clampToByte((66 * r + 129 * g + 25 * b + 128 shr 8) + 16)
                    if (y % 2 == 0 && x % 2 == 0) {
                        out[uvStart + uvIndex] = clampToByte((-38 * r - 74 * g + 112 * b + 128 shr 8) + 128)
                        out[uvStart + uvIndex + 1] = clampToByte((112 * r - 94 * g - 18 * b + 128 shr 8) + 128)
                        uvIndex += 2
                    }
                }
            }
            return out
        }

        private fun clampToByte(v: Int): Byte = v.coerceIn(0, 255).toByte()
    }
}
