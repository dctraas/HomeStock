package com.dtraas.boodschapbeheer.ui.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * ML Kit-backed [androidx.camera.core.ImageAnalysis.Analyzer]. Reports the
 * raw value of the first detected barcode via [onBarcodeDetected] and lets
 * the caller decide when to stop analyzing (e.g. after the first hit).
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
) : androidx.camera.core.ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes: List<Barcode> ->
                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onBarcodeDetected)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
