package com.fyp.facer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AttendanceResultScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Loading…") }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val uid = Firebase.auth.currentUser?.uid ?: return@launch
                val db = Firebase.firestore
                val doc = db.collection("courses").document("FYP101")
                    .collection("sessions").document("S1")
                    .collection("attendance").document(uid)
                    .get().await()
                text = if (doc.exists()) "Status: ${doc.getString("status")} ✅"
                else "No record found."
            } catch (e: Exception) {
                text = "Error: ${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Attendance Result", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(text)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}