package com.idk.anypay.ui.screens

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.idk.anypay.data.model.UpiPaymentInfo
import com.idk.anypay.ui.theme.*
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onScanResult: (UpiPaymentInfo) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasFlash by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scannedOnce by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasFlash) {
                        IconButton(onClick = {
                            flashEnabled = !flashEnabled
                            cameraControl?.enableTorch(flashEnabled)
                        }) {
                            Icon(
                                if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Flash"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (!scannedOnce) {
                                            processImage(imageProxy) { result ->
                                                if (result != null && !scannedOnce) {
                                                    scannedOnce = true
                                                    onScanResult(result)
                                                }
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }
                            
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            
                            hasFlash = camera.cameraInfo.hasFlashUnit()
                            cameraControl = camera.cameraControl
                            
                        } catch (e: Exception) {
                            Log.e("QrScanner", "Camera initialization failed", e)
                            errorMessage = "Failed to initialize camera: ${e.message}"
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Overlay with scanning frame
            ScannerOverlay()
            
            // Instructions
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ErrorRed.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Position the UPI QR code within the frame",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Supports all UPI payment QR codes",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Frame size
        val frameSize = minOf(canvasWidth, canvasHeight) * 0.7f
        val frameLeft = (canvasWidth - frameSize) / 2
        val frameTop = (canvasHeight - frameSize) / 2
        
        // Semi-transparent background
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = size
        )
        
        // Clear the scanning area
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameSize, frameSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            blendMode = BlendMode.Clear
        )
        
        // Draw corner brackets
        val cornerLength = 40.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val cornerRadius = 8.dp.toPx()
        
        val bracketColor = Color(0xFF4CAF50)
        
        // Top-left corner
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft, frameTop + cornerLength),
            end = Offset(frameLeft, frameTop + cornerRadius),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft, frameTop),
            end = Offset(frameLeft + cornerLength, frameTop),
            strokeWidth = strokeWidth
        )
        
        // Top-right corner
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft + frameSize - cornerLength, frameTop),
            end = Offset(frameLeft + frameSize, frameTop),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft + frameSize, frameTop),
            end = Offset(frameLeft + frameSize, frameTop + cornerLength),
            strokeWidth = strokeWidth
        )
        
        // Bottom-left corner
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft, frameTop + frameSize - cornerLength),
            end = Offset(frameLeft, frameTop + frameSize),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft, frameTop + frameSize),
            end = Offset(frameLeft + cornerLength, frameTop + frameSize),
            strokeWidth = strokeWidth
        )
        
        // Bottom-right corner
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft + frameSize - cornerLength, frameTop + frameSize),
            end = Offset(frameLeft + frameSize, frameTop + frameSize),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = bracketColor,
            start = Offset(frameLeft + frameSize, frameTop + frameSize - cornerLength),
            end = Offset(frameLeft + frameSize, frameTop + frameSize),
            strokeWidth = strokeWidth
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    onResult: (UpiPaymentInfo?) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.valueType == Barcode.TYPE_TEXT) {
                        val rawValue = barcode.rawValue ?: continue
                        if (rawValue.startsWith("upi://pay")) {
                            val paymentInfo = UpiPaymentInfo.parse(rawValue)
                            if (paymentInfo != null) {
                                onResult(paymentInfo)
                                imageProxy.close()
                                return@addOnSuccessListener
                            }
                        }
                    }
                }
                onResult(null)
            }
            .addOnFailureListener {
                Log.e("QrScanner", "Barcode scanning failed", it)
                onResult(null)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
        onResult(null)
    }
}
