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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun EnrollFaceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var lastFace by remember { mutableStateOf<Face?>(null) }
    var lastBmp by remember { mutableStateOf<Bitmap?>(null) }
    var cropped112 by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enroll Face", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // Reuse camera with face callback
        CameraFaceScreen(
            onBack = onBack,
            onCapture = { bmp, face ->
                if (bmp == null || face == null) {
                    status = "No face detected."
                    Toast.makeText(ctx, "No face detected. Try again.", Toast.LENGTH_SHORT).show()
                    return@CameraFaceScreen
                }

                preview = cropFace(bmp, face.boundingBox, padRatio = 0.25f)
                status = "Captured. Tap Upload to enroll."
                Toast.makeText(ctx, "Captured successfully.", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = {
                status = null
                if (lastFace == null) {
                    status = "No face detected yet."
                    return@OutlinedButton
                }
                // We need the last ImageProxy bitmap; simplest is to ask user to press "Capture" in camera.
                status = "Tap Capture on camera not implemented yet."
            }) { Text("Capture (todo)") }

            // For now: small helper button expects you changed CameraFaceScreen to give you the ImageProxy/Bitmap.
        }

        cropped112?.let { img ->
            Spacer(Modifier.height(8.dp))
            Image(img.asImageBitmap(), contentDescription = null, modifier = Modifier.size(160.dp))
        }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = cropped112 != null && !loading,
            onClick = {
                val uid = Firebase.auth.currentUser?.uid
                if (uid == null) {
                    status = "Not logged in"
                    return@Button
                }
                val faceBmp = cropped112 ?: return@Button

                try {
                    loading = true
                    val embedder = FaceEmbedder(ctx)
                    val emb = embedder.embed(faceBmp)   // FloatArray (128/512)

                    // pack floats to bytes
                    val bb = ByteBuffer.allocate(emb.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                    emb.forEach { bb.putFloat(it) }
                    val bytes = bb.array()

                    // ✅ upload to Storage under embeddings/<uid>/embedding.bin
                    val ref = Firebase.storage.reference.child("embeddings/$uid/embedding.bin")
                    ref.putBytes(bytes)
                        .addOnSuccessListener {
                            status = "Enrolled & uploaded ✅"
                            Toast.makeText(ctx, "Enrolled ✅", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            status = "Upload failed: ${it.message}"
                        }
                        .addOnCompleteListener { loading = false }

                } catch (e: Exception) {
                    loading = false
                    status = "Model missing or error: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Uploading..." else "Save embedding") }

        Spacer(Modifier.height(8.dp))
        status?.let { Text(it) }
    }
}
