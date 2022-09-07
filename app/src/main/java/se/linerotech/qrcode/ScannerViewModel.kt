package se.linerotech.qrcode

import android.app.Application
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import se.linerotech.qrcode.camera.DetectedObject

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    // State set of the application workflow.
    enum class WorkflowState {
        NOT_STARTED,
        DETECTING,
        CONFIRMING,
        CONFIRMED,
        SEARCHING,
        SEARCHED
    }

    var workflowState = MutableLiveData<WorkflowState>()
    private var confirmedObject: DetectedObject? = null
    var isCameraLive = false
        private set

    @MainThread
    fun setWorkflowState(workflowState: WorkflowState) {
        if (workflowState != WorkflowState.CONFIRMED &&
            workflowState != WorkflowState.SEARCHING &&
            workflowState != WorkflowState.SEARCHED
        ) {
            confirmedObject = null
        }
        this.workflowState.value = workflowState
    }

    fun markCameraLive() {
        isCameraLive = true
    }

    fun markCameraFrozen() {
        isCameraLive = false
    }
}
