package pe.com.scotiabank.blpm.android.knull.faceguard

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val onResult: (faces: List<Face>, faceBitmap: Bitmap?) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val croppedBitmap = if (FaceMatcher.USE_FACENET) {
                            // 1. Obtenemos el bitmap original (que viene rotado)
                            val rawBitmap = imageProxy.toBitmap()

                            // 2. Enderezamos el bitmap completo ANTES de recortar
                            val rotatedBitmap = rotateBitmap(rawBitmap, rotationDegrees)

                            // 3. Recortamos el rostro usando el cuadro que nos da ML Kit
                            cropFace(rotatedBitmap, faces.first().boundingBox)
                        } else null

                        onResult(faces, croppedBitmap)
                    } else {
                        onResult(faces, null)
                    }
                }
                .addOnFailureListener {
                    onResult(emptyList(), null)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    // Esta función endereza la imagen según la posición del celular
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // Esta función recorta el rostro de la imagen ya enderezada
    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap? {
        return try {
            // Aseguramos que el cuadro de recorte no se salga de la foto
            val left = boundingBox.left.coerceIn(0, bitmap.width - 1)
            val top = boundingBox.top.coerceIn(0, bitmap.height - 1)
            val width = boundingBox.width().coerceAtMost(bitmap.width - left)
            val height = boundingBox.height().coerceAtMost(bitmap.height - top)

            if (width <= 0 || height <= 0) return null

            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            null
        }
    }
}