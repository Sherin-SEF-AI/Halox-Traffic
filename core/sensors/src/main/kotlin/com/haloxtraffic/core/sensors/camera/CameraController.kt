package com.haloxtraffic.core.sensors.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.haloxtraffic.core.model.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Receives analysis frames on the analysis executor. Plug detection in here in Phase 2. */
fun interface FrameAnalyzer {
    fun analyze(image: ImageProxy)
}

/** Live camera metrics for the HUD. */
data class CameraMetrics(
    val fps: Float = 0f,
    val analysisWidth: Int = 0,
    val analysisHeight: Int = 0,
    val bound: Boolean = false,
)

/**
 * Wraps CameraX (§10): `Preview` for the HUD, `ImageAnalysis` (YUV_420_888, KEEP_ONLY_LATEST) for the
 * inference path, and on-demand full-res `ImageCapture` for the sharpest evidence frame. Computes a
 * rolling FPS and recovers gracefully from a camera disconnect. Lifecycle-bound — never leaks the
 * provider.
 */
@Singleton
class CameraController @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    private val _metrics = MutableStateFlow(CameraMetrics())
    val metrics: StateFlow<CameraMetrics> = _metrics.asStateFlow()

    private var lastFrameNs = 0L
    private var emaFps = 0f

    /**
     * Binds the camera use cases to [lifecycleOwner]. [analyzer] receives each analysis frame after
     * FPS bookkeeping; pass a no-op in Phase 1. Safe to call again to rebind after a config change.
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        analyzer: FrameAnalyzer = FrameAnalyzer { it.close() },
    ) = withContext(ioDispatcher) {
        try {
            val provider = ProcessCameraProvider.getInstance(context).await()
            cameraProvider = provider

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { ia ->
                    ia.setAnalyzer(analysisExecutor) { image ->
                        recordFrame(image)
                        try {
                            analyzer.analyze(image)
                        } catch (t: Throwable) {
                            Timber.e(t, "Frame analyzer threw")
                            image.close()
                        }
                    }
                }

            // Bias toward a fast shutter to fight motion blur where the device allows it (§10).
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(resolutionSelector)
                .build()
            imageCapture = capture

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                capture,
            )
            _metrics.value = _metrics.value.copy(bound = true)
            Timber.i("CameraX bound")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to bind camera")
            _metrics.value = CameraMetrics(bound = false)
        }
    }

    private fun recordFrame(image: ImageProxy) {
        val now = System.nanoTime()
        if (lastFrameNs != 0L) {
            val dt = (now - lastFrameNs).coerceAtLeast(1)
            val inst = 1_000_000_000f / dt
            emaFps = if (emaFps == 0f) inst else emaFps * 0.8f + inst * 0.2f
        }
        lastFrameNs = now
        _metrics.value = _metrics.value.copy(
            fps = emaFps,
            analysisWidth = image.width,
            analysisHeight = image.height,
        )
    }

    /** On-demand full-res capture for evidence. Returns the written [file] or an error. */
    suspend fun captureStill(file: File): Result<File> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("Camera not bound"))
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        return runCatching {
            suspendCancellableCoroutine { cont ->
                capture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            cont.resume(file)
                        }

                        override fun onError(exc: ImageCaptureException) {
                            cont.resumeWithException(exc)
                        }
                    },
                )
            }
        }.onFailure { Timber.e(it, "captureStill failed") }
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        _metrics.value = _metrics.value.copy(bound = false)
        lastFrameNs = 0L
        emaFps = 0f
    }
}
