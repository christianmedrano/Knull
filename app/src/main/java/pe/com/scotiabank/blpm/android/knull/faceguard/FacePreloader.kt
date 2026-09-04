package pe.com.scotiabank.blpm.android.knull.faceguard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

object FacePreloader {

    fun preloadBlockedFaceFromUri(context: Context, imageUri: Uri, onComplete: (Boolean) -> Unit) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val fullBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (fullBitmap == null) {
                onComplete(false)
                return
            }

            val image = InputImage.fromBitmap(fullBitmap, 0)

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()

            val detector = FaceDetection.getClient(options)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces.first()

                        // Recortamos el rostro si FaceNet está activo
                        val croppedBitmap = if (FaceMatcher.USE_FACENET) {
                            cropFace(fullBitmap, face.boundingBox)
                        } else null

                        // Pasamos el croppedBitmap a FaceMatcher
                        FaceMatcher.saveBlockedFace(context, face, croppedBitmap)
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                }
                .addOnFailureListener {
                    onComplete(false)
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false)
        }
    }

    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val left = boundingBox.left.coerceAtLeast(0)
        val top = boundingBox.top.coerceAtLeast(0)
        val width = boundingBox.width().coerceAtMost(bitmap.width - left)
        val height = boundingBox.height().coerceAtMost(bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }
}