package com.haloxtraffic.feature.detection.runtime

import android.graphics.Bitmap
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.InferenceDelegate
import com.haloxtraffic.feature.detection.model.ModelSpec
import java.io.File

/** Result of one detector inference. Boxes are normalised to the square model input. */
data class DetectionResult(
    val boxes: List<BoundingBox>,
    val activeDelegate: InferenceDelegate,
    val inferenceMs: Long,
)

/** On-device object detector contract (Stage 1). */
interface Detector {
    val activeDelegate: InferenceDelegate?
    fun isReady(): Boolean

    /** Load the model with delegate fallback. Returns the delegate that initialised. */
    fun init(modelFile: File, spec: ModelSpec, config: DetectionConfig): InferenceDelegate

    /** Run inference on a preprocessed square [input] bitmap → boxes via [Yolo26Decoder]. */
    fun detect(input: Bitmap, scoreThreshold: Float = 0.25f): DetectionResult

    fun close()
}
