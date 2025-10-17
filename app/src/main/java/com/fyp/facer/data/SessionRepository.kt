package com.fyp.facer.data

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object SessionRepository {
    private val db = Firebase.firestore

    /** One-time seeding for quick testing */
    suspend fun seedSample() {
        val courseId = "CSE301"
        val courseRef = db.collection("courses").document(courseId)
        courseRef.set(mapOf(
            "code" to "CSE301",
            "name" to "Mobile Development",
            "lecturer_uid" to "demo-lecturer-uid"
        )).await()

        val sessionRef = courseRef.collection("sessions").document("S1")
        sessionRef.set(mapOf(
            "courseId" to courseId,
            "start_time" to com.google.firebase.Timestamp.now(),
            "end_time" to com.google.firebase.Timestamp.now(),
            "method" to "face",
            "radius_m" to 150,
            "center" to mapOf("lat" to 4.3852, "lng" to 100.9675) // sample coords
        )).await()
    }

    /** Returns the first session it finds under the first course */
    suspend fun getFirstSession(): Session? {
        val courses = db.collection("courses").get().await()
        val courseDoc = courses.documents.firstOrNull() ?: return null
        val sessions = courseDoc.reference.collection("sessions").get().await()
        val doc = sessions.documents.firstOrNull() ?: return null
        val s = doc.toObject(Session::class.java) ?: return null
        return s.copy(id = doc.id)
    }
}
