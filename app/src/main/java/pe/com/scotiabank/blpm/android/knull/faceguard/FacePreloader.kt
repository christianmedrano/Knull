package pe.com.scotiabank.blpm.android.knull.faceguard

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

object FacePreloader {

    fun preloadBlockedFaceFromUri(context: Context, imageUri: Uri, onComplete: (Boolean) -> Unit) {
        try {
            // Cargar InputImage directamente desde la Uri elegida en la galería
            val image = InputImage.fromFilePath(context, imageUri)

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()

            val detector = FaceDetection.getClient(options)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        FaceMatcher.saveBlockedFace(context, faces.first())
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
}