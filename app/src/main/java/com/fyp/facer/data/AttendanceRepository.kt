package com.fyp.facer.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object AttendanceRepository {
    private val db = Firebase.firestore

    /** Writes a present record under the first course/session (same as earlier helper). */
    suspend fun markPresentFirstSession(method: String = "face") {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val courseDocs = db.collection("courses").get().await()
        val course = courseDocs.documents.firstOrNull() ?: return
        val sessionDocs = course.reference.collection("sessions").get().await()
        val session = sessionDocs.documents.firstOrNull() ?: return

        val attRef = session.reference.collection("attendance").document(uid)
        attRef.set(
            mapOf(
                "uid" to uid,
                "status" to "present",
                "method" to method,
                "timestamp" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }
}
