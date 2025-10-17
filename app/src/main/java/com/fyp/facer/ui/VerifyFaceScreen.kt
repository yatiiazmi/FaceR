package com.fyp.facer.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fyp.facer.ml.FaceEmbedder
import com.fyp.facer.utils.cropFace
import com.fyp.facer.data.AttendanceRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun VerifyFaceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Try to create once, show message if it fails
    val embedder = remember {
        try { FaceEmbedder(ctx) } catch (e: Exception) {
            status = "Model load failed: ${e.message}"
            null
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Take Attendance • Face Verify", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // Camera with Capture
        CameraFaceScreen(
            onBack = onBack,
            onCapture = { bmp, face ->
                if (bmp == null || face == null) {
                    status = "No face detected."
                    return@CameraFaceScreen
                }
                preview = cropFace(bmp, face.boundingBox, padRatio = 0.25f, size = 160)
                status = "Captured. Tap Verify."
            }
        )

        Spacer(Modifier.height(8.dp))
        preview?.let { Image(it.asImageBitmap(), null, Modifier.size(160.dp)) }

        Spacer(Modifier.height(8.dp))
        Button(
            enabled = preview != null && !loading && embedder != null,
            onClick = {
                android.util.Log.d("FaceR", "Starting verification with preview=${preview != null}")
                val uid = Firebase.auth.currentUser?.uid
                if (uid == null) { status = "Not logged in."; return@Button }
                val faceBmp = preview ?: return@Button

                scope.launch {
                    try {
                        loading = true

                        if (embedder == null) {
                            status = "Model not ready. Check model file path."
                            loading = false
                            return@launch
                        }

                        // A) live embedding from the captured bitmap
                        val live = embedder.embed(faceBmp)

                        // B) download stored embedding
                        val ref = Firebase.storage.reference.child("embeddings/$uid/embedding.bin")
                        val bytes = ref.getBytes(8192).await()
                        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        val stored = FloatArray(bytes.size / 4) { bb.getFloat() }

                        // C) compare
                        val cos = FaceEmbedder.cosineSimilarity(live, stored)

                        // ✅ Debug: print similarity to Logcat
                        android.util.Log.d("FaceR", "cos=$cos")

                        val pass = cos >= 0.5f
                        status = "Similarity: %.3f  ->  %s".format(cos, if (pass) "MATCH ✅" else "NO MATCH ❌")

                        if (pass) {
                            AttendanceRepository.markPresentFirstSession(method = "face")
                            status = "Attendance recorded ✅"
                            Toast.makeText(ctx, "Attendance recorded ✅", Toast.LENGTH_SHORT).show()

                            // ✅ Delay for 1 second before going back to home
                            kotlinx.coroutines.delay(1000)
                            onBack()
                        } else {
                            status = "Face not recognized ❌"
                            Toast.makeText(ctx, "Face not recognized ❌", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        status = if (e.message?.contains("object does not exist", ignoreCase = true) == true) {
                            "No enrolled face found. Please enroll first."
                        } else {
                            "Verify failed: ${e.message}"
                        }
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Verifying..." else "Verify & Mark Present")
        }

        Spacer(Modifier.height(8.dp))
        status?.let { Text(it) }
    }
}
