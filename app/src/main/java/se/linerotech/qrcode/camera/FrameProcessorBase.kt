package se.linerotech.qrcode.camera

import android.graphics.ImageFormat
import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer

/** Abstract base class of [FrameProcessor].  */
abstract class FrameProcessorBase<T> : FrameProcessor {

    // To keep the latest frame and its metadata.
    @GuardedBy("this")
    private var latestFrame: ByteBuffer? = null

    @GuardedBy("this")
    private var latestFrameMetaData: FrameMetadata? = null

    // To keep the frame and metadata in process.
    @GuardedBy("this")
    private var processingFrame: ByteBuffer? = null

    @GuardedBy("this")
    private var processingFrameMetaData: FrameMetadata? = null

    @Synchronized
    override fun process(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay,
    ) {
        latestFrame = data
        latestFrameMetaData = frameMetadata
        if (processingFrame == null && processingFrameMetaData == null) {
            processLatestFrame(graphicOverlay)
        }
    }

    @Synchronized
    private fun processLatestFrame(graphicOverlay: GraphicOverlay) {
        processingFrame = latestFrame
        processingFrameMetaData = latestFrameMetaData
        latestFrame = null
        latestFrameMetaData = null
        val frame = processingFrame ?: return
        val frameMetaData = processingFrameMetaData ?: return
        val image = InputImage.fromByteBuffer(frame,
            frameMetaData.width,
            frameMetaData.height,
            90,
            ImageFormat.NV21)
        val startMs = SystemClock.elapsedRealtime()
        detectInImage(image)
            .addOnSuccessListener { results ->
                Log.d(TAG, "Latency is: ${SystemClock.elapsedRealtime() - startMs}")
                this@FrameProcessorBase.onSuccess(image, results, graphicOverlay)
                processLatestFrame(graphicOverlay)
            }
            .addOnFailureListener { this@FrameProcessorBase.onFailure(it) }
    }

    protected abstract fun detectInImage(image: InputImage): Task<T>

    /** Be called when the detection succeeds.  */
    protected abstract fun onSuccess(
        image: InputImage,
        results: T,
        graphicOverlay: GraphicOverlay,
    )

    protected abstract fun onFailure(e: Exception)

    companion object {
        private const val TAG = "FrameProcessorBase"
    }
}
