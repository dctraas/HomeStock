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
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
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
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.ui.components.CategoryDropdown
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.Executors

/**
 * A camera-based alternative to barcode scanning for products that don't have (or don't need)
 * one: take a photo, ML Kit's on-device Image Labeling model suggests what it looks like, and
 * that suggestion — fully editable — becomes the starting point for the normal "add to
 * inventory" confirm screen (see [AiRecognizeViewModel.confirm]). The model runs entirely on
 * the device (no network call, no API key), but its vocabulary is generic visual categories
 * ("Food", "Produce", "Bread", "Dairy product") rather than specific product names or brands —
 * it's a head start for typing the real name in, not a barcode-accurate lookup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecognizeScreen(onBack: () -> Unit, onNeedsConfirmation: (String) -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: AiRecognizeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AiRecognizeViewModel(application.container.productRepository) }
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
                onLabelRecognized = viewModel::onLabelRecognized,
                onCaptureFailed = viewModel::onCaptureFailed,
            )
            AiRecognizeStep.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.ai_recognize_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(onClick = viewModel::retake, modifier = Modifier.padding(top = 20.dp)) {
                    Text(stringResource(R.string.ai_recognize_retry))
                }
            }
            is AiRecognizeStep.Recognized -> RecognizedResult(
                padding = padding,
                result = current,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onRetake = viewModel::retake,
                onConfirm = viewModel::confirm,
            )
        }
    }
}

@Composable
private fun AiCamera(
    padding: PaddingValues,
    onLabelRecognized: (label: String, confidencePercent: Int) -> Unit,
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
    val labeler = remember { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val capture = ImageCapture.Builder().build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            imageCapture = capture
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            cameraExecutor.shutdown()
            labeler.close()
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
                capture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val mediaImage = image.image
                            if (mediaImage == null) {
                                image.close()
                                isCapturing = false
                                onCaptureFailed()
                                return
                            }
                            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                            val mainExecutor = ContextCompat.getMainExecutor(context)
                            labeler.process(inputImage)
                                .addOnSuccessListener(mainExecutor) { labels ->
                                    image.close()
                                    isCapturing = false
                                    val top = labels.maxByOrNull { it.confidence }
                                    if (top == null) {
                                        onCaptureFailed()
                                    } else {
                                        onLabelRecognized(top.text, (top.confidence * 100).toInt())
                                    }
                                }
                                .addOnFailureListener(mainExecutor) {
                                    image.close()
                                    isCapturing = false
                                    onCaptureFailed()
                                }
                        }

                        override fun onError(exception: ImageCaptureException) {
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
            text = stringResource(R.string.ai_recognize_confidence_format, result.suggestedName, result.confidencePercent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.ai_recognize_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
