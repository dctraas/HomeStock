package com.dtraas.homestock.ui.productdetail

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.RecognizeExpirationDateResult
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

private sealed interface ExpirationScanStep {
    data object Capturing : ExpirationScanStep
    data object Analyzing : ExpirationScanStep
    data class Found(val epochMillis: Long, val confidencePercent: Int) : ExpirationScanStep
    data class Failed(val reason: ExpirationScanFailReason) : ExpirationScanStep
}

private enum class ExpirationScanFailReason { NOT_FOUND, PREMIUM_REQUIRED, NO_CONNECTION, CAPTURE, UNKNOWN }

/**
 * Full-screen camera dialog for "THT-datum scannen" on ProductDetailScreen (premium — the caller
 * checks isPremium before ever showing the entry point, same convention as
 * AiRecognizeScreen/ReceiptScanScreen). Takes one photo of a product's packaging, sends it to the
 * `recognizeExpirationDate` Cloud Function (see AiRecognitionRepository), and — unlike the
 * AI-product-recognition flow, which always lands on an editable confirm form — shows the found
 * date for a quick "Toepassen"/"Opnieuw" confirmation rather than applying it silently: a
 * misread date is easy to miss otherwise, and [onDateRecognized] writes straight into
 * ProductDetailViewModel's already-live expirationDate state.
 *
 * Implemented as a [Dialog] (`usePlatformDefaultWidth = false`, its own Scaffold/top bar) rather
 * than a new navigation destination — this is a same-screen, same-ViewModel action (the result
 * only ever needs to flow back into the ExpirationRow that opened it), and Compose Navigation has
 * no clean way to return a value to the screen that pushed a route, unlike a dialog's own
 * callback closures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpirationDateScanDialog(onDismiss: () -> Unit, onDateRecognized: (Long) -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableStateOf<ExpirationScanStep>(ExpirationScanStep.Capturing) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                HomeStockTopAppBar(
                    title = { Text(stringResource(R.string.expiration_scan_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                )
            },
        ) { padding ->
            when (val current = step) {
                ExpirationScanStep.Capturing -> ExpirationScanCamera(
                    padding = padding,
                    onPhotoCaptured = { jpegBytes ->
                        step = ExpirationScanStep.Analyzing
                        coroutineScope.launch {
                            step = when (val result = application.container.aiRecognitionRepository.recognizeExpirationDate(jpegBytes)) {
                                is RecognizeExpirationDateResult.Success ->
                                    ExpirationScanStep.Found(result.epochMillis, result.confidencePercent)
                                RecognizeExpirationDateResult.NotFound ->
                                    ExpirationScanStep.Failed(ExpirationScanFailReason.NOT_FOUND)
                                RecognizeExpirationDateResult.PremiumRequired ->
                                    ExpirationScanStep.Failed(ExpirationScanFailReason.PREMIUM_REQUIRED)
                                RecognizeExpirationDateResult.NoConnection ->
                                    ExpirationScanStep.Failed(ExpirationScanFailReason.NO_CONNECTION)
                                RecognizeExpirationDateResult.Failed ->
                                    ExpirationScanStep.Failed(ExpirationScanFailReason.UNKNOWN)
                            }
                        }
                    },
                    onCaptureFailed = { step = ExpirationScanStep.Failed(ExpirationScanFailReason.CAPTURE) },
                )
                ExpirationScanStep.Analyzing -> Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.expiration_scan_analyzing),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                is ExpirationScanStep.Found -> ExpirationScanFound(
                    padding = padding,
                    epochMillis = current.epochMillis,
                    confidencePercent = current.confidencePercent,
                    onApply = {
                        onDateRecognized(current.epochMillis)
                        onDismiss()
                    },
                    onRetake = { step = ExpirationScanStep.Capturing },
                )
                is ExpirationScanStep.Failed -> ExpirationScanFailed(
                    padding = padding,
                    reason = current.reason,
                    onRetake = { step = ExpirationScanStep.Capturing },
                )
            }
        }
    }
}

@Composable
private fun ExpirationScanFound(
    padding: PaddingValues,
    epochMillis: Long,
    confidencePercent: Int,
    onApply: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.expiration_scan_found_format, formatExpirationScanDate(epochMillis)),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.expiration_scan_confidence_format, confidencePercent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(onClick = onApply, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.expiration_scan_apply))
        }
        TextButton(onClick = onRetake, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.expiration_scan_retry))
        }
    }
}

@Composable
private fun ExpirationScanFailed(padding: PaddingValues, reason: ExpirationScanFailReason, onRetake: () -> Unit) {
    val (icon, messageRes) = when (reason) {
        ExpirationScanFailReason.NOT_FOUND -> Icons.Filled.CalendarMonth to R.string.expiration_scan_not_found
        ExpirationScanFailReason.PREMIUM_REQUIRED -> Icons.Filled.WorkspacePremium to R.string.ai_recognize_failed_premium
        ExpirationScanFailReason.NO_CONNECTION -> Icons.Filled.CloudOff to R.string.ai_recognize_failed_no_connection
        ExpirationScanFailReason.CAPTURE, ExpirationScanFailReason.UNKNOWN -> Icons.Filled.Error to R.string.expiration_scan_failed
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
            Text(stringResource(R.string.expiration_scan_retry))
        }
    }
}

/** Single-shot CameraX capture — a compact variant of AiRecognizeScreen's AiCamera, scoped to
 *  just "take one photo" since this dialog has no ongoing preview/candidate-selection step of
 *  its own after that. */
@Composable
private fun ExpirationScanCamera(
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

    DisposableEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose {}
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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        // Tracks only the use cases bound here so cleanup unbinds exactly those, never the
        // process-wide unbindAll() — see AiRecognizeScreen's AiCamera for why a global unbind
        // is unsafe with ProcessCameraProvider's single shared instance.
        var boundPreview: Preview? = null
        var boundCapture: ImageCapture? = null

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val capture = ImageCapture.Builder().build()
            boundPreview = preview
            boundCapture = capture
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            imageCapture = capture
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            camera = null
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
                text = stringResource(R.string.expiration_scan_hint),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // Many THT-datums are printed in embossed or low-contrast ink that's hard for the
        // model to read in dim kitchen/pantry lighting — a torch toggle here (same pattern as
        // ScanScreen's barcode camera) turns "walk to better light" into one tap.
        Surface(
            onClick = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = stringResource(R.string.scan_flash_cd),
                    tint = Color.White,
                )
            }
        }

        Surface(
            onClick = {
                val capture = imageCapture
                if (capture == null || isCapturing) return@Surface
                isCapturing = true
                val outputFile = File.createTempFile("expiration_scan_", ".jpg", context.cacheDir)
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
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.expiration_scan_capture_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

private fun formatExpirationScanDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(date)
}
