package com.home.library.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX 프레임을 ML Kit(번들, 오프라인)으로 분석해 EAN-13 바코드를 인식한다.
 * 최초 1건 인식 시 [onIsbn]에 raw 문자열을 넘기고 이후 프레임은 무시한다.
 */
class BarcodeAnalyzer(
    private val onIsbn: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .build(),
    )

    @Volatile
    private var handled = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || handled) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstOrNull { it.format == Barcode.FORMAT_EAN_13 }?.rawValue
                if (!raw.isNullOrBlank() && !handled) {
                    handled = true
                    onIsbn(raw)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.w("HomeLib", "BarcodeAnalyzer: 인식 실패", e)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
