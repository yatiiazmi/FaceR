package com.fyp.facer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.fyp.facer.data.SessionRepository
import com.fyp.facer.data.UserRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.fyp.facer.data.Seed
import com.google.android.gms.location.LocationServices
import com.fyp.facer.utils.LatLng
import com.fyp.facer.utils.insideGeofence
import com.fyp.facer.ui.EnrollFaceScreen
import com.fyp.facer.ui.CameraFaceScreen
import com.fyp.facer.ui.VerifyFaceScreen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase sanity check
        val auth = Firebase.auth
        val firestore = Firebase.firestore
        val storage = Firebase.storage
        Log.d("FaceR", "Firebase init ok: ${auth.app.name}")

        setContent { FaceRApp(
            onEnsureUser = { lifecycleScope.launch { UserRepository.ensureUserDoc() } },
            onSeed = { lifecycleScope.launch { SessionRepository.seedSample() } }
        ) }
    }
}

@Composable
fun FaceRApp(
    onEnsureUser: () -> Unit,
    onSeed: () -> Unit

) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()   // for coroutine launch
    var current by remember { mutableStateOf("login") }

    when (current) {
        "login" -> LoginScreen(onLoginSuccess = {
            onEnsureUser()

            // Seed Firestore quietly after login
            scope.launch {
                try {
                    Seed.ensureSampleData()
                } catch (e: Exception) {
                    android.util.Log.e("FaceR", "Seeding failed: ${e.message}")
                }
            }

            current = "home"
        })

        "home" -> HomeScreen(
            onSeed = onSeed,
            onOpenCamera = { current = "camera" },
            onEnroll = { current = "enroll" },
            onVerify = { current = "verify" }
        )

        "camera" -> CameraFaceScreen(onBack = { current = "home" })
        "enroll" -> EnrollFaceScreen(onBack = { current = "home" })
        "verify" -> VerifyFaceScreen(onBack = { current = "home" })
    }
}


@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth = Firebase.auth
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("FaceR Login", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        message = "Enter email & password"
                    } else {
                        loading = true
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { message = "Login failed: ${it.message}" }
                            .addOnCompleteListener { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) { Text(if (loading) "Signing in..." else "Login") }

            OutlinedButton(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        message = "Enter email & password"
                    } else {
                        loading = true
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { message = "Registered successfully. Now login." }
                            .addOnFailureListener { message = "Register failed: ${it.message}" }
                            .addOnCompleteListener { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                enabled = !loading
            ) { Text("Register") }

            if (message.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    onSeed: () -> Unit,
    onOpenCamera: () -> Unit,
    onEnroll: () -> Unit,
    onVerify: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(ctx) }

    var session by remember { mutableStateOf<com.fyp.facer.data.Session?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Welcome to FaceR", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))

            OutlinedButton(onClick = {
                scope.launch {
                    onSeed()
                    status = "Seeded sample course & session ✅"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Seed sample course + session") }

            Button(onClick = {
                scope.launch {
                    val s = com.fyp.facer.data.SessionRepository.getFirstSession()
                    session = s
                    status = if (s == null) "No session found" else "Loaded session ${s.id}"
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Load first session")
            }

            // 🔹 NEW: Check Geofence
            Button(onClick = {
                // Request permission if not yet granted
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        (ctx as ComponentActivity),
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        1001
                    )
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                        loc?.let {
                            val current = LatLng(it.latitude, it.longitude)
                            val center = LatLng(
                                session?.center?.lat ?: 4.3852,
                                session?.center?.lng ?: 100.9675
                            )
                            val inside = insideGeofence(current, center, session?.radius_m?.toDouble() ?: 150.0)
                            status = if (inside) "Inside geofence ✅" else "Outside geofence ❌"
                        } ?: run { status = "Could not get current location" }
                    }
                }
            },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("Check Geofence")
            }
            Button(
                onClick = onOpenCamera,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Open Camera (Face Detect)")
            }
            Button(
                onClick = onEnroll, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("Enroll Face")
            }
            Button(onClick = onVerify, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Take Attendance (Verify)")
            }
            Spacer(Modifier.height(16.dp))
            status?.let { Text(it) }
        }
    }
}
