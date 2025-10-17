package com.fyp.facer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fyp.facer.utils.imageProxyToBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Suspend helper to get the ProcessCameraProvider without guava await */
private suspend fun getCameraProvider(context: android.content.Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cont.resume(future.get()) },
            ContextCompat.getMainExecutor(context)
        )
    }

@Composable
fun CameraFaceScreen(
    onBack: (() -> Unit)? = null,
    onFaceFound: ((Face) -> Unit)? = null,
    onCapture: ((bitmap: Bitmap?, face: Face?) -> Unit)? = null
) {
    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var latestFace by remember { mutableStateOf<Face?>(null) }

    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    var faceBoxes by remember { mutableStateOf<List<Box>>(emptyList()) }

    // ML Kit detector
    val detector = remember {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .enableTracking()
            .build()
        FaceDetection.getClient(opts)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera • Face Detection", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (!hasPermission) {
            Text("Camera permission needed.")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant") }
            onBack?.let {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            return@Column
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            var previewView: PreviewView? by remember { mutableStateOf(null) }

            // Camera Preview View
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PreviewView(context).also { pv -> previewView = pv }
                }
            )

            // Bind the camera once the view exists & permission granted
            LaunchedEffect(previewView, hasPermission) {
                val pv = previewView ?: return@LaunchedEffect
                if (!hasPermission) return@LaunchedEffect

                val cameraProvider = getCameraProvider(ctx)
                cameraProvider.unbindAll()

                val preview = Preview.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .build()
                    .also { it.setSurfaceProvider(pv.surfaceProvider) }

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val media = imageProxy.image
                    if (media != null) {
                        val img = InputImage.fromMediaImage(
                            media, imageProxy.imageInfo.rotationDegrees
                        )
                        detector.process(img)
                            .addOnSuccessListener { faces ->
                                val bmp = imageProxyToBitmap(imageProxy)
                                latestBitmap = bmp
                                latestFace = faces.firstOrNull()

                                if (faces.isNotEmpty()) onFaceFound?.invoke(faces[0])
                                // Very simple scale mapping for the overlay (good enough to verify)
                                faceBoxes = faces.map { f ->
                                    val b = f.boundingBox
                                    val w = pv.width.toFloat().coerceAtLeast(1f)
                                    val h = pv.height.toFloat().coerceAtLeast(1f)
                                    val scaleX = w / 480f
                                    val scaleY = h / 640f
                                    Box(
                                        left = b.left * scaleX,
                                        top = b.top * scaleY,
                                        right = b.right * scaleX,
                                        bottom = b.bottom * scaleY
                                    )
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                cameraProvider.bindToLifecycle(owner, selector, preview, analysis)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(20f, 12f), 0f)
                faceBoxes.forEach { b ->
                    drawRect(
                        color = Color(0xFF2ECC71),
                        topLeft = androidx.compose.ui.geometry.Offset(b.left, b.top),
                        size = androidx.compose.ui.geometry.Size(b.right - b.left, b.bottom - b.top),
                        style = Stroke(width = 6f, pathEffect = dash)
                    )
                }
            }
        }
        // Add toast messages so we can see clicks actually fire
        OutlinedButton(
            onClick = {
                if (latestBitmap == null) {
                    android.util.Log.w("FaceR", "No bitmap available at capture time")
                } else {
                    android.util.Log.d("FaceR", "Captured bitmap and face=$latestFace")
                }
                onCapture?.invoke(latestBitmap, latestFace)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Capture")
        }

        Spacer(Modifier.height(12.dp))

        onBack?.let {
            OutlinedButton(
                onClick = {
                    Toast.makeText(ctx, "Back clicked", Toast.LENGTH_SHORT).show()
                    it()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }

    }
}
