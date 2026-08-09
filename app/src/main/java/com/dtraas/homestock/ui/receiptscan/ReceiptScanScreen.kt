package com.dtraas.homestock.ui.receiptscan

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.io.File
import java.util.concurrent.Executors

/**
 * Photographs a whole receipt and asks the `recognizeReceipt` Cloud Function (Claude Haiku 4.5
 * server-side, see functions/src/index.ts) to read off every purchased product line, rather
 * than the on-device ML Kit OCR + hand-rolled row/price parser this used to run locally — that
 * approach was fragile against the wide variety of real supermarket receipt layouts. This is a
 * premium feature; see [ReceiptFailReason.PREMIUM_REQUIRED].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanScreen(onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: ReceiptScanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ReceiptScanViewModel(
                    productRepository = application.container.productRepository,
                    inventoryRepository = application.container.inventoryRepository,
                    receiptRecognitionRepository = application.container.receiptRecognitionRepository,
                )
            }
        },
    )
    val step by viewModel.step.collectAsState()

    LaunchedEffect(step) {
        if (step is ReceiptScanStep.Done) onBack()
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.receipt_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val current = step) {
            is ReceiptScanStep.Capturing -> ReceiptCamera(
                padding = padding,
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onCaptureFailed = viewModel::onCaptureFailed,
            )
            is ReceiptScanStep.Analyzing -> ReceiptCenteredMessage(
                padding = padding,
                loading = true,
                text = stringResource(R.string.receipt_scan_processing),
            )
            is ReceiptScanStep.Saving -> ReceiptCenteredMessage(
                padding = padding,
                loading = true,
                text = stringResource(R.string.receipt_scan_saving),
            )
            is ReceiptScanStep.Done -> ReceiptCenteredMessage(
                padding = padding,
                loading = false,
                text = stringResource(R.string.receipt_scan_saved),
            )
            is ReceiptScanStep.Failed -> ReceiptFailedView(
                padding = padding,
                reason = current.reason,
                onRetry = viewModel::retake,
            )
            is ReceiptScanStep.Confirming -> ReceiptConfirmList(
                padding = padding,
                items = current.items,
                onToggle = viewModel::toggleItem,
                onNameChange = viewModel::updateItemName,
                onRetake = viewModel::retake,
                onConfirm = viewModel::confirmAndSave,
            )
        }
    }
}

@Composable
private fun ReceiptCamera(
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
        // Tracks only the use cases bound here so cleanup can unbind exactly those instead of
        // the process-wide unbindAll() — ProcessCameraProvider is a single shared instance
        // app-wide, and a global unbindAll() on disposal can race with another camera screen's
        // own bind/unbind (e.g. navigating here from the barcode scanner), tearing down
        // whichever screen bound second. See AiRecognizeScreen/ScanScreen for the same fix.
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
                text = stringResource(R.string.receipt_scan_hint),
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
                // Write straight to a JPEG file — this photo goes to the recognizeReceipt Cloud
                // Function, unlike the old on-device ML Kit pass, which consumed the camera's
                // raw frame directly instead of needing encoded bytes to upload.
                val outputFile = File.createTempFile("receipt_scan_", ".jpg", context.cacheDir)
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
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.receipt_scan_capture_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptCenteredMessage(padding: PaddingValues, loading: Boolean, text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator()
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ReceiptFailedView(padding: PaddingValues, reason: ReceiptFailReason, onRetry: () -> Unit) {
    val (icon, messageRes) = when (reason) {
        ReceiptFailReason.PREMIUM_REQUIRED -> Icons.Filled.WorkspacePremium to R.string.receipt_scan_failed_premium
        ReceiptFailReason.NO_CONNECTION -> Icons.Filled.CloudOff to R.string.receipt_scan_failed_no_connection
        ReceiptFailReason.CAPTURE, ReceiptFailReason.UNKNOWN -> Icons.Filled.Error to R.string.receipt_scan_failed
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
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(R.string.receipt_scan_retry))
        }
    }
}

@Composable
private fun ReceiptConfirmList(
    padding: PaddingValues,
    items: List<ReceiptConfirmItem>,
    onToggle: (String) -> Unit,
    onNameChange: (String, String) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.receipt_scan_no_items),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetake, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.receipt_scan_retry))
                }
            }
            return
        }

        Text(
            text = stringResource(R.string.receipt_scan_confirm_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = SoftCardShapeCompact,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.id) })
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { onNameChange(item.id, it) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        // AI-read quantity — same dense stepper as everywhere else in the app,
                        // shown read-only here (no +/- wiring) since it's just a confirmation
                        // aid; a genuinely wrong quantity is rare enough that editing it isn't
                        // worth another interactive control on an already busy row.
                        Text(
                            text = "×${item.quantity}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onConfirm,
            enabled = items.any { it.checked && it.name.isNotBlank() },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(stringResource(R.string.receipt_scan_confirm_action))
        }
    }
}
