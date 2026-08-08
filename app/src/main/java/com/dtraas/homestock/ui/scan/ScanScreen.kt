package com.dtraas.homestock.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ScanScreen(
    isActive: Boolean,
    onNeedsConfirmation: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: ScanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ScanViewModel(
                    productRepository = application.container.productRepository,
                    inventoryRepository = application.container.inventoryRepository,
                )
            }
        },
    )
    val context = LocalContext.current
    val activity = context as? Activity
    var hasCameraPermission by remember {
        mutableStateOf(
            checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    // shouldShowRequestPermissionRationale is false both before the first-ever request and
    // after the user picks "don't ask again" — the two are only distinguishable by tracking
    // that we've actually asked once. When it's false *after* a real denial, asking again is
    // pointless; the only way forward is the app's system settings page.
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
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val quickAddedFormat = stringResource(R.string.scan_quick_added_format)
    val restockedFormat = stringResource(R.string.inventory_restocked_snackbar_format)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (hasCameraPermission) {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreview(
                    padding = padding,
                    isActive = isActive,
                    onBarcodeDetected = { barcode ->
                        when (val outcome = viewModel.handleScannedBarcode(barcode)) {
                            is ScanOutcome.QuickAdded -> {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(quickAddedFormat.format(outcome.productName))
                                    outcome.restockedProductName?.let { name ->
                                        snackbarHostState.showSnackbar(restockedFormat.format(name))
                                    }
                                }
                                true
                            }
                            ScanOutcome.NeedsConfirmation -> {
                                onNeedsConfirmation(barcode)
                                false
                            }
                        }
                    },
                )
                Surface(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .size(48.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.scan_search_by_name_cd),
                            tint = Color.White,
                        )
                    }
                }
            }
        } else {
            PermissionRationale(
                padding = padding,
                permanentlyDenied = permanentlyDenied,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
        }
    }
}

@Composable
private fun CameraPreview(
    padding: PaddingValues,
    isActive: Boolean,
    onBarcodeDetected: suspend (String) -> Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scannedCode by remember { mutableStateOf<String?>(null) }
    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    // Navigation Compose's restoreState/saveState bottom-nav pattern doesn't guarantee this
    // composable is disposed and recreated on every tab switch, and CameraX doesn't reliably
    // resume delivering frames on its own either way. Rebinding from scratch every time this
    // tab becomes active — and resetting the "already scanned" guard with it — is what
    // actually makes scanning work again.
    DisposableEffect(isActive) {
        if (isActive) {
            scannedCode = null
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                            if (scannedCode == null) {
                                scannedCode = barcode
                                coroutineScope.launch {
                                    val keepScanning = currentOnBarcodeDetected(barcode)
                                    if (keepScanning) {
                                        // Brief pause so the same barcode isn't immediately
                                        // re-detected while still in view, then re-arm so
                                        // the next item can be scanned right away.
                                        delay(1200)
                                        scannedCode = null
                                    }
                                    // Otherwise we're navigating to the confirmation screen;
                                    // the isActive-driven rebind resets the guard on return.
                                }
                            }
                        })
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        ScanOverlay(modifier = Modifier.fillMaxSize())

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.scan_overlay_hint),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * Dims everything outside a centered scan frame and draws corner brackets
 * around it, like a typical barcode-scanner viewfinder. The bracket color is
 * the app's secondary (coral) accent — this is the one screen where that
 * accent gets to be the whole point, marking "aim here" rather than a small
 * badge among mostly-sage chrome.
 */
@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val scrimColor = Color.Black.copy(alpha = 0.55f)
    val frameColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.78f
        val frameHeight = frameWidth / 1.6f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val right = left + frameWidth
        val bottom = top + frameHeight

        drawRect(color = scrimColor, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(color = scrimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(color = scrimColor, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(color = scrimColor, topLeft = Offset(right, top), size = Size(size.width - right, frameHeight))

        drawCornerBrackets(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            bracketLength = 28.dp.toPx(),
            strokeWidth = 5.dp.toPx(),
            color = frameColor,
        )
    }
}

private fun DrawScope.drawCornerBrackets(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    bracketLength: Float,
    strokeWidth: Float,
    color: Color,
) {
    val cap = StrokeCap.Round

    // top-left
    drawLine(color, Offset(left, top + bracketLength), Offset(left, top), strokeWidth, cap)
    drawLine(color, Offset(left, top), Offset(left + bracketLength, top), strokeWidth, cap)
    // top-right
    drawLine(color, Offset(right - bracketLength, top), Offset(right, top), strokeWidth, cap)
    drawLine(color, Offset(right, top), Offset(right, top + bracketLength), strokeWidth, cap)
    // bottom-left
    drawLine(color, Offset(left, bottom - bracketLength), Offset(left, bottom), strokeWidth, cap)
    drawLine(color, Offset(left, bottom), Offset(left + bracketLength, bottom), strokeWidth, cap)
    // bottom-right
    drawLine(color, Offset(right - bracketLength, bottom), Offset(right, bottom), strokeWidth, cap)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - bracketLength), strokeWidth, cap)
}

@Composable
private fun PermissionRationale(
    padding: PaddingValues,
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
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
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.scan_permission_open_settings))
            }
        } else {
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.scan_permission_button))
            }
        }
    }
}
