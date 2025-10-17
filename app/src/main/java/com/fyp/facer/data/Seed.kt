package com.fyp.facer.data
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object Seed {
    suspend fun ensureSampleData() {
        val db = Firebase.firestore
        val courseRef = db.collection("courses").document("FYP101")
        val course = courseRef.get().await()
        if (!course.exists()) courseRef.set(mapOf("name" to "FYP 101")).await()
        val sessionRef = courseRef.collection("sessions").document("S1")
        val session = sessionRef.get().await()
        if (!session.exists()) sessionRef.set(mapOf("title" to "Week 1")).await()
    }
}
