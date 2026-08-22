package com.dtraas.homestock.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Translucent chrome color reused across the full-bleed camera overlay — top bar, reticle
 *  scrim, and bottom action buttons all sit on the same rgba(0,0,0,0.55) per the design spec. */
private val ScrimColor = Color.Black.copy(alpha = 0.55f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    isActive: Boolean,
    onBack: () -> Unit,
    onNeedsConfirmation: (String) -> Unit,
    onSearchClick: () -> Unit,
    onAiRecognizeClick: () -> Unit,
    onReceiptScanClick: () -> Unit,
    onNavigateToPremium: () -> Unit,
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
    // AI-productherkenning is premium-only — the photo actually leaves the device (to the
    // recognizeProduct Cloud Function), unlike the on-device barcode scanner this screen
    // otherwise is, so it carries a real per-scan cost the free tier shouldn't run up.
    val isPremium by application.container.householdMembersRepository
        .observeHouseholdIsPremium()
        .collectAsState(initial = false)
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

    val sessionScanCount by viewModel.sessionScanCount.collectAsState()
    val lastScanResult by viewModel.lastScanResult.collectAsState()

    // No app bar at all — full-bleed camera per the design review, reached via Voorraad's "+"
    // menu (see InventoryScreen) as a normal pushed screen. Insets are handled by hand below
    // (windowInsetsPadding on the overlay chrome) since there's no Scaffold to do it for us.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreview(
                isActive = isActive,
                onBarcodeDetected = { barcode ->
                    when (viewModel.handleScannedBarcode(barcode)) {
                        ScanOutcome.QuickAdded -> true
                        ScanOutcome.NeedsConfirmation -> {
                            onNeedsConfirmation(barcode)
                            false
                        }
                    }
                },
                sessionScanCount = sessionScanCount,
                lastScanResult = lastScanResult,
                onUndo = viewModel::undoLastScan,
                onClose = onBack,
                onSearchClick = onSearchClick,
                onAiRecognizeClick = { if (isPremium) onAiRecognizeClick() else onNavigateToPremium() },
                onReceiptScanClick = onReceiptScanClick,
            )
        } else {
            PermissionRationale(
                permanentlyDenied = permanentlyDenied,
                onClose = onBack,
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
    isActive: Boolean,
    onBarcodeDetected: suspend (String) -> Boolean,
    sessionScanCount: Int,
    lastScanResult: ScanResultCard?,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    onSearchClick: () -> Unit,
    onAiRecognizeClick: () -> Unit,
    onReceiptScanClick: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scannedCode by remember { mutableStateOf<String?>(null) }
    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    val coroutineScope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    // Navigation Compose's restoreState/saveState bottom-nav pattern doesn't guarantee this
    // composable is disposed and recreated on every tab switch, and CameraX doesn't reliably
    // resume delivering frames on its own either way. Rebinding from scratch every time this
    // tab becomes active — and resetting the "already scanned" guard with it — is what
    // actually makes scanning work again.
    DisposableEffect(isActive) {
        // Tracks only the use cases *this* effect run bound, so cleanup below can unbind
        // exactly those — never the process-wide unbindAll(). ProcessCameraProvider is a
        // single shared instance across the whole app; a global unbindAll() here would also
        // tear down another screen's camera (e.g. AiRecognizeScreen's) if its bind happened
        // to land first, which is exactly what caused the frozen-preview bug: navigating to
        // AI-productherkenning let it bind its own camera, then this effect's disposal
        // (isActive turning false) called unbindAll() and ripped that fresh binding out from
        // under it a moment later — and the same race in reverse froze the scanner again on
        // the way back.
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null

        if (isActive) {
            scannedCode = null
            torchOn = false
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

                boundPreview = preview
                boundAnalysis = analysis
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            camera = null
            runCatching {
                val useCases = listOfNotNull(boundPreview, boundAnalysis).toTypedArray()
                if (useCases.isNotEmpty()) {
                    ProcessCameraProvider.getInstance(context).get().unbind(*useCases)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        ScanOverlay(modifier = Modifier.fillMaxSize())

        ScanTopBar(
            sessionScanCount = sessionScanCount,
            torchOn = torchOn,
            onClose = onClose,
            onToggleTorch = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (lastScanResult != null) {
                ScanResultCardView(result = lastScanResult, onUndo = onUndo)
            } else {
                ScanHintBar()
            }
            ScanActionRow(
                onSearchClick = onSearchClick,
                onAiRecognizeClick = onAiRecognizeClick,
                onReceiptScanClick = onReceiptScanClick,
            )
        }
    }
}

/** Top overlay row: close (left), a pill showing how many barcodes were scanned so far this
 *  session (center), and a flashlight toggle (right) — all on the shared translucent scrim. */
@Composable
private fun ScanTopBar(
    sessionScanCount: Int,
    torchOn: Boolean,
    onClose: () -> Unit,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrimIconButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.common_close),
            onClick = onClose,
        )
        Surface(shape = RoundedCornerShape(50), color = ScrimColor) {
            Text(
                text = stringResource(R.string.scan_session_count_format, sessionScanCount),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        ScrimIconButton(
            icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            contentDescription = stringResource(R.string.scan_flash_cd),
            onClick = onToggleTorch,
        )
    }
}

@Composable
private fun ScrimIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(44.dp), shape = CircleShape, color = ScrimColor) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White)
        }
    }
}

/**
 * Dims everything outside a centered scan frame and draws corner brackets
 * around it, like a typical barcode-scanner viewfinder.
 */
@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val frameColor = OnTopAppBarContainerAccent

    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.74f
        val frameHeight = frameWidth / 1.5f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val right = left + frameWidth
        val bottom = top + frameHeight

        drawRect(color = ScrimColor, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(color = ScrimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(color = ScrimColor, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(color = ScrimColor, topLeft = Offset(right, top), size = Size(size.width - right, frameHeight))

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
    // Rounded stroke caps stand in for the design's 8dp corner radius — a true rounded-rect
    // bracket needs an arc per corner, which isn't worth the added complexity here.
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
private fun ScanHintBar() {
    Surface(shape = RoundedCornerShape(24.dp), color = ScrimColor) {
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

/** The persistent result card that replaces the old auto-dismissing snackbar — stays on screen
 *  until the next scan replaces it (or [onUndo] is tapped), so a fast-scanning user can always
 *  see and correct what the previous scan just did. */
@Composable
private fun ScanResultCardView(result: ScanResultCard, onUndo: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = SageGreenPrimary) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (result.imageUrl != null) {
                    AsyncImage(
                        model = result.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(result.productName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.scan_result_added_format, result.newQuantity),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.common_undo),
                color = OnTopAppBarContainerAccent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUndo).padding(8.dp),
            )
        }
    }
}

/** "Op naam" / "Foto" / "Bon" — three labelled entry points for when a barcode isn't the way
 *  in, replacing the old pair of unlabeled floating circles. */
@Composable
private fun ScanActionRow(onSearchClick: () -> Unit, onAiRecognizeClick: () -> Unit, onReceiptScanClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ScanActionButton(icon = Icons.Filled.Search, label = stringResource(R.string.scan_action_by_name), onClick = onSearchClick)
        ScanActionButton(icon = Icons.Filled.PhotoCamera, label = stringResource(R.string.scan_action_photo), onClick = onAiRecognizeClick)
        ScanActionButton(icon = Icons.Filled.ReceiptLong, label = stringResource(R.string.scan_action_receipt), onClick = onReceiptScanClick)
    }
}

@Composable
private fun ScanActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.size(52.dp), shape = CircleShape, color = ScrimColor) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White)
            }
        }
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PermissionRationale(
    permanentlyDenied: Boolean,
    onClose: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
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
}
