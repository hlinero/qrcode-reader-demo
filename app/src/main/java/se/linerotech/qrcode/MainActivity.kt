package se.linerotech.qrcode

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.common.internal.Objects.equal
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import se.linerotech.qrcode.camera.CameraSource
import se.linerotech.qrcode.camera.CameraSourcePreview
import se.linerotech.qrcode.camera.GraphicOverlay
import se.linerotech.qrcode.scanner.BarcodeLoadingGraphic
import se.linerotech.qrcode.scanner.BarcodeProcessor
import java.io.IOException
import java.lang.Exception

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var flashButton: View? = null
    private var textViewPrompt: TextView? = null
    private var scannerViewModel: ScannerViewModel? = null
    private var currentScannerViewState: ScannerViewModel.WorkflowState? = null
    private lateinit var loadingAnimator: ValueAnimator
    private var actionBarLiveCamera: RelativeLayout? = null
    private var cameraNotGrantedLayout: View? = null

    private val compositeDisposable = CompositeDisposable()
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_main)

        loadingAnimator = createLoadingAnimator()
        permissionManager = PermissionManager(this)

        cameraNotGrantedLayout = findViewById(R.id.activity_scanner_layout_camera_not_granted)
        cameraNotGrantedLayout
            ?.findViewById<Button>(R.id.layout_camera_permission_buttonGetPermission)
            ?.setOnClickListener {
                try {
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (exception: Exception) {
                    Toast
                        .makeText(this, R.string.please_open_setting_page, Toast.LENGTH_LONG)
                        .show()
                }
            }

        textViewPrompt = findViewById(R.id.action_bar_textViewPrompt)
        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@MainActivity)
            cameraSource = CameraSource(this)
        }

        findViewById<View>(R.id.close_button)?.setOnClickListener(this)
        findViewById<View>(R.id.buttonQuestionMark)?.setOnClickListener(this)
        flashButton = findViewById<View>(R.id.flash_button).apply {
            setOnClickListener(this@MainActivity)
        }

        actionBarLiveCamera = findViewById(R.id.activity_scanner_top_bar_relative_layout)

        setupViewModels()
    }

    override fun onResume() {
        super.onResume()
        scannerViewModel?.markCameraFrozen()
        currentScannerViewState = ScannerViewModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(
            BarcodeProcessor(
                graphicOverlay!!,
                scannerViewModel!!,
            )
        )
    }

    override fun onPostResume() {
        super.onPostResume()
        scannerViewModel?.setWorkflowState(ScannerViewModel.WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentScannerViewState = ScannerViewModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.close_button -> finish()
            R.id.flash_button -> {
                flashButton?.let {
                    if (it.isSelected) {
                        it.isSelected = false
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                    } else {
                        it.isSelected = true
                        cameraSource!!.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                    }
                }
            }
            R.id.buttonQuestionMark -> {
                Toast.makeText(this, "Open onboarding activity", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                // If request is cancelled, grantResults is empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    cameraNotGrantedLayout?.visibility = View.GONE
                    scannerViewModel?.setWorkflowState(ScannerViewModel.WorkflowState.DETECTING)
                } else {
                    showCameraPermissionNotGrantedView()
                }
            }
        }
    }

    private fun startCameraPreview() {
        val workflowModel = this.scannerViewModel ?: return
        val cameraSource = this.cameraSource ?: return
        actionBarLiveCamera?.visibility = View.VISIBLE
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        val workflowModel = this.scannerViewModel ?: return
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setupViewModels() {
        // Scanner ViewModel Setup
        scannerViewModel = ViewModelProviders.of(this)[ScannerViewModel::class.java]
        scannerViewModel!!.workflowState.observe(this, Observer { workflowState ->
                if (workflowState == null || equal(currentScannerViewState, workflowState)) {
                    return@Observer
                }

                currentScannerViewState = workflowState
                when (workflowState) {
                    ScannerViewModel.WorkflowState.DETECTING -> {
                        val disposable = permissionManager
                            .isPermissionGranted(Manifest.permission.CAMERA)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe { result ->
                                if (result == false) {
                                    if (!permissionManager.hasPermissionBeenAsked()) {
                                        permissionManager.requestPermissionFor(
                                            Manifest.permission.CAMERA,
                                            CAMERA_REQUEST_CODE
                                        )
                                        permissionManager.permissionHasBeenAsked()
                                    } else {
                                        showCameraPermissionNotGrantedView()
                                    }
                                } else {
                                    if (cameraNotGrantedLayout?.visibility == View.VISIBLE) {
                                        cameraNotGrantedLayout?.visibility = View.GONE
                                    }

                                    textViewPrompt?.apply {
                                        setText(R.string.prompt_point_at_a_qrcode)
                                        visibility = View.VISIBLE
                                    }
                                    startCameraPreview()
                                }
                            }

                        compositeDisposable.add(disposable)
                    }
                    ScannerViewModel.WorkflowState.CONFIRMING -> {
                        textViewPrompt?.apply {
                            setText(R.string.prompt_move_camera_closer)
                            visibility = View.VISIBLE
                        }
                        startCameraPreview()
                    }
                    ScannerViewModel.WorkflowState.SEARCHING -> {
                        graphicOverlay?.let {
                            loadingAnimator.start()
                            it.add(BarcodeLoadingGraphic(it, loadingAnimator))
                        }

                        textViewPrompt?.apply {
                            setText(R.string.prompt_searching)
                            visibility = View.VISIBLE
                        }
                        stopCameraPreview()
                    }
                    else -> {
                        textViewPrompt?.apply {
                            visibility = View.GONE
                        }
                    }
                }
            }
        )
    }

    private fun createLoadingAnimator(): ValueAnimator {
        val endProgress = 2.0f
        return ValueAnimator.ofFloat(0f, endProgress).apply {
            duration = 2000
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if ((animatedValue as Float).compareTo(endProgress) >= 0) {
                    graphicOverlay?.clear()
                } else {
                    graphicOverlay?.invalidate()
                }
            }
        }
    }

    private fun showCameraPermissionNotGrantedView() {
        cameraNotGrantedLayout?.visibility = View.VISIBLE
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 1000
        private const val TAG = "LiveBarcodeActivity"
    }
}
