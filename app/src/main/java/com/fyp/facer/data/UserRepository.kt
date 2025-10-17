package com.fyp.facer.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

data class AppUser(
    val role: String = "student",
    val matric: String = "",
    val face_template: Boolean = false,
    val device_id_hash: String? = null
)

object UserRepository {
    private val db = Firebase.firestore

    suspend fun ensureUserDoc() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = db.collection("users").document(uid)
        val snap = ref.get().await()
        if (!snap.exists()) ref.set(AppUser()).await()
    }
}
