package com.haloxtraffic.feature.detection.runtime

import android.graphics.Bitmap
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.InferenceDelegate

/** Result of one detector inference. Boxes are normalised to the square model input. */
data class DetectionResult(
    val boxes: List<BoundingBox>,
    val activeDelegate: InferenceDelegate,
    val inferenceMs: Long,
)

/** On-device object detector contract (Stage 1). */
interface Detector {
    val activeDelegate: InferenceDelegate?

    /** Detection classes this loaded model can actually emit — used to gate which violations run. */
    val supportedClasses: Set<DetectionClass>

    fun isReady(): Boolean

    /** Load the model for [config]. Returns the delegate/backend that initialised. */
    fun init(config: DetectionConfig): InferenceDelegate

    /** Run inference on a preprocessed square [input] bitmap → boxes (model-input-normalised). */
    fun detect(input: Bitmap, scoreThreshold: Float = 0.4f): DetectionResult

    fun close()
}
