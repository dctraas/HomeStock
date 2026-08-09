package com.dtraas.homestock.ui.airecognize

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.ui.components.CategoryDropdown
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.io.File
import java.util.concurrent.Executors

/**
 * A camera-based alternative to barcode scanning for products that don't have (or don't need)
 * one: take a photo, an AI model (Claude, via the `recognizeProduct` Cloud Function — see
 * `functions/src/index.ts`) suggests the actual product name and category, and that
 * suggestion — fully editable — becomes the starting point for the normal "add to inventory"
 * confirm screen (see [AiRecognizeViewModel.confirm]). This is a premium feature: the photo
 * leaves the device (unlike the on-device barcode scanner), and the Cloud Function itself
 * re-checks premium status server-side before spending anything on the Claude call, so this
 * screen should only ever be reached from an already-premium-gated entry point (see
 * MoreScreen/ScanScreen) — [FailReason.PREMIUM_REQUIRED] is the fallback for the rare case a
 * subscription lapses mid-session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecognizeScreen(onBack: () -> Unit, onNeedsConfirmation: (String) -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: AiRecognizeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AiRecognizeViewModel(
                    productRepository = application.container.productRepository,
                    aiRecognitionRepository = application.container.aiRecognitionRepository,
                )
            }
        },
    )
    val step by viewModel.step.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.confirmed.collect { barcode -> onNeedsConfirmation(barcode) }
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.ai_recognize_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val current = step) {
            AiRecognizeStep.Capturing -> AiCamera(
                padding = padding,
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onCaptureFailed = viewModel::onCaptureFailed,
            )
            AiRecognizeStep.Analyzing -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.ai_recognize_analyzing),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            is AiRecognizeStep.Failed -> AiRecognizeFailed(padding = padding, reason = current.reason, onRetake = viewModel::retake)
            is AiRecognizeStep.Recognized -> RecognizedResult(
                padding = padding,
                result = current,
                onSelectCandidate = viewModel::selectCandidate,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onRetake = viewModel::retake,
                onConfirm = viewModel::confirm,
            )
        }
    }
}

@Composable
private fun AiRecognizeFailed(padding: PaddingValues, reason: FailReason, onRetake: () -> Unit) {
    val (icon, messageRes) = when (reason) {
        FailReason.PREMIUM_REQUIRED -> Icons.Filled.WorkspacePremium to R.string.ai_recognize_failed_premium
        FailReason.NO_CONNECTION -> Icons.Filled.CloudOff to R.string.ai_recognize_failed_no_connection
        FailReason.CAPTURE, FailReason.UNKNOWN -> Icons.Filled.Error to R.string.ai_recognize_failed
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(onClick = onRetake, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(R.string.ai_recognize_retry))
        }
    }
}

@Composable
private fun AiCamera(
    padding: PaddingValues,
    onPhotoCaptured: (jpegBytes: ByteArray) -> Unit,
    onCaptureFailed: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasCameraPermission by remember {
        mutableStateOf(checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    if (permanentlyDenied) R.string.scan_permission_denied_rationale else R.string.scan_permission_rationale
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            if (permanentlyDenied) {
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text(stringResource(R.string.scan_permission_open_settings)) }
            } else {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.scan_permission_button))
                }
            }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        // Tracks only the use cases bound here, so cleanup can unbind exactly those instead
        // of the process-wide unbindAll() — ProcessCameraProvider is a single shared instance
        // app-wide, and a global unbindAll() on disposal can race with ScanScreen's own
        // camera bind/unbind (e.g. when navigating back), tearing down whichever screen bound
        // second. That race was the cause of the preview freezing shortly after opening this
        // screen, and again after returning to the barcode scanner.
        var boundPreview: Preview? = null
        var boundCapture: ImageCapture? = null

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val capture = ImageCapture.Builder().build()
            boundPreview = preview
            boundCapture = capture
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            imageCapture = capture
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching {
                val useCases = listOfNotNull(boundPreview, boundCapture).toTypedArray()
                if (useCases.isNotEmpty()) {
                    ProcessCameraProvider.getInstance(context).get().unbind(*useCases)
                }
            }
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            shape = SoftCardShapeCompact,
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Text(
                text = stringResource(R.string.ai_recognize_hint),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Surface(
            onClick = {
                val capture = imageCapture
                if (capture == null || isCapturing) return@Surface
                isCapturing = true
                // Write straight to a JPEG file — CameraX handles the sensor-format-to-JPEG
                // encoding internally, so this avoids hand-rolling a YUV/ImageProxy conversion
                // just to get bytes suitable for uploading (this photo goes to the
                // recognizeProduct Cloud Function, unlike the old on-device ML Kit pass, which
                // could consume the ImageProxy's raw frame directly).
                val outputFile = File.createTempFile("ai_recognize_", ".jpg", context.cacheDir)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                capture.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val bytes = runCatching { outputFile.readBytes() }.getOrNull()
                            outputFile.delete()
                            isCapturing = false
                            if (bytes == null) onCaptureFailed() else onPhotoCaptured(bytes)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            outputFile.delete()
                            isCapturing = false
                            onCaptureFailed()
                        }
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isCapturing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = stringResource(R.string.ai_recognize_capture_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognizedResult(
    padding: PaddingValues,
    result: AiRecognizeStep.Recognized,
    onSelectCandidate: (label: String, confidencePercent: Int) -> Unit,
    onNameChange: (String) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.ai_recognize_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // More than one candidate is the common case — shown as tappable chips rather than
        // silently picking #1, since the top-confidence guess isn't always the best match.
        // Selecting one fills both the name field and the category suggestion below with it;
        // the name field stays freely editable regardless.
        if (result.candidates.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                result.candidates.forEach { (label, confidencePercent) ->
                    FilterChip(
                        selected = label == result.suggestedName,
                        onClick = { onSelectCandidate(label, confidencePercent) },
                        label = { Text(stringResource(R.string.ai_recognize_candidate_format, label, confidencePercent)) },
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.ai_recognize_confidence_format, result.suggestedName, result.confidencePercent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = result.suggestedName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.common_name)) },
            modifier = Modifier.fillMaxWidth(),
        )

        CategoryDropdown(
            selected = result.category,
            onSelected = onCategoryChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onConfirm,
            enabled = result.suggestedName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.ai_recognize_confirm))
        }
        TextButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ai_recognize_retry))
        }
    }
}
