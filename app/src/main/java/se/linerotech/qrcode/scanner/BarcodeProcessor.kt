package se.linerotech.qrcode.scanner

import android.util.Log
import androidx.annotation.MainThread
import se.linerotech.qrcode.camera.CameraReticleAnimator
import se.linerotech.qrcode.camera.FrameProcessorBase
import se.linerotech.qrcode.camera.GraphicOverlay
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import se.linerotech.qrcode.ScannerViewModel
import java.io.IOException

/** A processor to run the barcode detector.  */
class BarcodeProcessor(
    graphicOverlay: GraphicOverlay,
    private val scannerViewModel: ScannerViewModel,
) :
    FrameProcessorBase<List<Barcode>>() {

    private val detector: BarcodeScanner = BarcodeScanning.getClient()
    private val cameraReticleAnimator: CameraReticleAnimator = CameraReticleAnimator(graphicOverlay)

    override fun detectInImage(image: InputImage): Task<List<Barcode>> =
        detector.process(image)

    @MainThread
    override fun onSuccess(
        image: InputImage,
        results: List<Barcode>,
        graphicOverlay: GraphicOverlay
    ) {

        if (!scannerViewModel.isCameraLive) return

        Log.d(TAG, "Barcode result size: ${results.size}")

        // Picks the barcode, if exists, that covers the center of graphic overlay.
        val barcodeInCenter = results.firstOrNull { barcode ->
            val boundingBox = barcode.boundingBox ?: return@firstOrNull false
            val box = graphicOverlay.translateRect(boundingBox)
            box.contains(graphicOverlay.width / 2f, graphicOverlay.height / 2f)
        }

        graphicOverlay.clear()
        if (barcodeInCenter == null) {
            cameraReticleAnimator.start()
            graphicOverlay.add(BarcodeReticleGraphic(graphicOverlay, cameraReticleAnimator))
            scannerViewModel.setWorkflowState(ScannerViewModel.WorkflowState.DETECTING)
        } else {
            cameraReticleAnimator.cancel()
            val sizeProgress = BarcodeUtils.getProgressToMeetBarcodeSizeRequirement(
                graphicOverlay,
                barcodeInCenter
            )
            if (sizeProgress < 1) {
                // Barcode in the camera view is too small, so prompt user to move camera closer.
                graphicOverlay.add(BarcodeConfirmingGraphic(graphicOverlay, barcodeInCenter))
                scannerViewModel.setWorkflowState(ScannerViewModel.WorkflowState.CONFIRMING)
            } else {
                // Barcode size in the camera view is sufficient.
                scannerViewModel.setWorkflowState(ScannerViewModel.WorkflowState.SEARCHING)
                val scannedResult = barcodeInCenter.rawValue
                Log.i("RESULT", "$scannedResult")
            }
        }
        graphicOverlay.invalidate()
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Barcode detection failed!", e)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close barcode detector!", e)
        }
    }

    companion object {
        private const val TAG = "BarcodeProcessor"
    }
}
