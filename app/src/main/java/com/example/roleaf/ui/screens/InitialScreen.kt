package com.example.roleaf.ui.screens

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.roleaf.ui.components.OrganButton
import com.example.roleaf.ui.viewmodel.MainViewModel
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@Composable
fun InitialScreen(viewModel: MainViewModel) {
    val uiState by viewModel.ui.collectAsState()
    val context = LocalContext.current

    // Helper must be defined before launchers to avoid unresolved reference problems
    fun createImageFileUri(ctx: android.content.Context): Uri? {
        return try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "JPEG_${timeStamp}_"
            val storageDir: File = ctx.cacheDir // safe temporary location
            val imageFile = File.createTempFile(fileName, ".jpg", storageDir)
            // authority must match provider in AndroidManifest (you use "${applicationId}.provider")
            val authority = "${ctx.packageName}.provider"
            FileProvider.getUriForFile(ctx, authority, imageFile)
        } catch (e: Exception) {
            Log.e("InitialScreen", "createImageFileUri error: ${e.message}", e)
            null
        }
    }

    // remember which organ the chooser refers to
    var selectedOrgan by remember { mutableStateOf<String?>(null) }

    // state to show/hide the chooser dialog
    var showSourceChooser by remember { mutableStateOf(false) }

    // --- Gallery launcher (unchanged) ---
    val pickImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null && selectedOrgan != null) {
                viewModel.setOrganUri(selectedOrgan!!, uri)
            }
        }

    // --- Camera launcher (TakePicture) requires a Uri to write into ---
    // Use delegated state so `pendingCameraUri` is a Uri? (not MutableState)
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCameraUri != null && selectedOrgan != null) {
            // photo taken and saved to pendingCameraUri — hand it to the viewmodel
            viewModel.setOrganUri(selectedOrgan!!, pendingCameraUri!!)
        } else {
            Log.d("InitialScreen", "Camera capture failed or was cancelled")
        }
        // clear pending uri either way
        pendingCameraUri = null
    }

    // permission launcher for CAMERA
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            // Permission granted — create uri and launch camera
            val uri = createImageFileUri(context)
            if (uri != null) {
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
            } else {
                Log.e("InitialScreen", "Could not create camera file uri after permission granted")
                Toast.makeText(context, "Unable to open camera (file error)", Toast.LENGTH_SHORT).show()
                selectedOrgan = null
            }
        } else {
            // Denied
            Log.w("InitialScreen", "Camera permission denied")
            Toast.makeText(context, "Camera permission denied — cannot take photo", Toast.LENGTH_SHORT).show()
            selectedOrgan = null
        }
    }

    // Attempt to launch camera, asking for permission if needed
    fun attemptLaunchCamera() {
        // If permission already granted, create uri and launch immediately
        val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            val uri = createImageFileUri(context)
            if (uri != null) {
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
            } else {
                Log.e("InitialScreen", "Could not create camera file uri")
                Toast.makeText(context, "Unable to open camera (file error)", Toast.LENGTH_SHORT).show()
                selectedOrgan = null
            }
        } else {
            // Request permission — the callback will create the uri and launch
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val organs = listOf("leaf", "flower", "fruit", "bark")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title centered both in layout and text alignment
        Text(
            text = "Start by adding photos of your plant",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            for (row in organs.chunked(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    row.forEach { organ ->
                        OrganButton(
                            label = organ,
                            uri = uiState.organUris[organ],
                            onTap = {
                                // show chooser to pick camera or gallery
                                selectedOrgan = organ
                                showSourceChooser = true
                            },
                            onLongPress = { viewModel.clearOrgan(organ) },      // keeps your existing long-press behavior
                            onDelete = { viewModel.clearOrgan(organ) }          // explicit delete button
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                Log.d("RoLeaf", "Identify button clicked; organUris=${viewModel.ui.value.organUris}")
                viewModel.identify()
            },
            enabled = uiState.organUris.values.any { it != null },
            modifier = Modifier
                .width(200.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Identify",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    // --- Source chooser dialog ---
    if (showSourceChooser) {
        AlertDialog(
            onDismissRequest = {
                showSourceChooser = false
                selectedOrgan = null
            },
            title = { Text(text = "Add photo") },
            text = { Text(text = "Choose source for ${selectedOrgan ?: "photo"}") },
            confirmButton = {
                TextButton(onClick = {
                    // Launch camera (permission-aware)
                    showSourceChooser = false
                    attemptLaunchCamera()
                }) {
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Launch gallery
                    showSourceChooser = false
                    pickImageLauncher.launch("image/*")
                }) {
                    Text("Gallery")
                }
            }
        )
    }
}
