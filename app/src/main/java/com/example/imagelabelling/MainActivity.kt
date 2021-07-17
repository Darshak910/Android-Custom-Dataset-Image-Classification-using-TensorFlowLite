package com.example.imagelabelling

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.example.imagelabelling.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions

private val permissionsList = arrayOf(
    Manifest.permission.CAMERA
)

// Request code for permission
private const val PERMISSION_REQUEST_CODE = 1001

class MainActivity : AppCompatActivity() {

//    Kotlin binding with the resource file
    private lateinit var binding: ActivityMainBinding

    private lateinit var labeler: ImageLabeler
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        requestPermissions()

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

//            Binding the camera provider
            bindPreview(cameraProvider = cameraProvider)

        }, ContextCompat.getMainExecutor(this))

//        Setting the path to the model file
        val localModel = LocalModel.Builder()
            .setAssetFilePath("modelFASD_2.tflite")
            .build()

//        Setting the custom image labeler with confidence threshold
        val customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(0.5f)
            .setMaxResultCount(1)
            .build()

        labeler = ImageLabeling.getClient(customImageLabelerOptions)
    }

//    Binding all the previews with the camera
    @SuppressLint("UnsafeExperimentalUsageError", "SetTextI18n")
    private fun bindPreview(cameraProvider: ProcessCameraProvider){
        val preview = Preview.Builder().build()

//    Selecting which camera lens will be activated
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

//    Providing the selected camera to the surface
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

//    Below code helps analyze the images feeding to the camera
//    Keep only latest strategy will skip some frames if the camera lags and doesn't get stuck
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

//        Setting the ime analyzer
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), { imageProxy ->
//            Getting the rotation degrees of the device camera
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

//            Getting the image
            val image = imageProxy.image

//            Checks if the image received is an image or null value
            if (image != null) {
                val processImage = InputImage.fromMediaImage(image, rotationDegrees)

//                Processing the image
                labeler.process(processImage)
                    .addOnFailureListener {
//                        If the process fails then throw a error
                        Log.v("MainActivity", "Error - ${it.message}")
                        imageProxy.close()
                    }
                    .addOnSuccessListener { labels ->
//                        If the process is successful then do the following tasks
//                        Getting the list of label(s) detected by the detector
                        for (label in labels) {
//                            Getting the label name
                            val text = label.text
//                            Getting the confidence score
                            val confidence = label.confidence

//                            Setting the text color depending on the received label
                            if (text == "1 normal") {
                                binding.textView.setTextColor(Color.WHITE)
                            }
                            else {
                                binding.textView.setTextColor(Color.RED)
                            }
//                            Setting the label and confidence scroe to the textview
                            binding.textView.text = "$text\n$confidence"
                        }
                        imageProxy.close()
                    }.addOnCompleteListener() {
                        imageProxy.close()
                    }
            }
        })
//    Binding the camera lifecycle with image preview and camera
        cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis, preview)
    }

//    If the user has given the permissions
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkIfPermissionsGranted()
    }

//    Requesting all the permissions required
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissionsList, PERMISSION_REQUEST_CODE)
    }

//    Checking if all the permissions are granted or not
    private fun checkIfPermissionsGranted() {
        var permissionsGranted = true
        permissionsList.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false
            }
        }

        if (!permissionsGranted) permissionDialog()
    }

//    Shows the permission dialogue to the user
    private fun permissionDialog() {
        AlertDialog.Builder(this)
            .setMessage("The App needs camera permissions to run")
            .setPositiveButton("Okay") {dialog, _ ->
                dialog.dismiss()
                requestPermissions()
            }.show()
    }
}