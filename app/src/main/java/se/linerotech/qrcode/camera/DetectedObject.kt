package se.linerotech.qrcode.camera

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Holds the detected object and its related image info.
 */
class DetectedObject(
    private val visionObject: DetectedObject,
    val objectIndex: Int,
    private val image: InputImage
) {

    private var bitmap: Bitmap? = null
    private var jpegBytes: ByteArray? = null

    val objectId: Int? = visionObject.trackingId
    val boundingBox: Rect = visionObject.boundingBox

    val imageData: ByteArray?
        @Synchronized get() {
            if (jpegBytes == null) {
                try {
                    ByteArrayOutputStream().use { stream ->
                        getBitmap().compress(CompressFormat.JPEG, /* quality= */ 100, stream)
                        jpegBytes = stream.toByteArray()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error getting object image data!")
                }
            }
            return jpegBytes
        }

    @Synchronized
    fun getBitmap(): Bitmap {
        return bitmap ?: let {
            val boundingBox = visionObject.boundingBox
            val createdBitmap = Bitmap.createBitmap(
                image.bitmapInternal!!,
                boundingBox.left,
                boundingBox.top,
                boundingBox.width(),
                boundingBox.height()
            )
            if (createdBitmap.width > MAX_IMAGE_WIDTH) {
                val dstHeight = (MAX_IMAGE_WIDTH.toFloat() / createdBitmap.width * createdBitmap.height).toInt()
                bitmap = Bitmap.createScaledBitmap(createdBitmap, MAX_IMAGE_WIDTH, dstHeight, /* filter= */ false)
            }
            createdBitmap
        }
    }

    companion object {
        private const val TAG = "DetectedObject"
        private const val MAX_IMAGE_WIDTH = 640
    }
}
