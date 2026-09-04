package pe.com.scotiabank.blpm.android.knull.faceguard

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.sqrt

object FaceMatcher {

    private const val PREFS_NAME = "face_guard_prefs"
    private const val KEY_VECTOR = "blocked_face_vector"
    private const val KEY_FACENET_EMBEDDINGS = "facenet_embeddings_list"

    // =========================================================================
    // FLAG DE CONFIGURACIÓN: Cambia a false para regresar a tus landmarks
    // =========================================================================
    var USE_FACENET = true

    // =========================================================================
    // MOTOR 1: LANDMARKS (TU CÓDIGO ORIGINAL PRESERVADO INTEGRALMENTE)
    // =========================================================================
    private fun extractFacialVector(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return null

        val interPupillaryDist = distance(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
        if (interPupillaryDist == 0f) return null

        val d1 = distance(nose.x, nose.y, leftEye.x, leftEye.y) / interPupillaryDist
        val d2 = distance(nose.x, nose.y, rightEye.x, rightEye.y) / interPupillaryDist
        val d3 = distance(mouthLeft.x, mouthLeft.y, leftEye.x, leftEye.y) / interPupillaryDist
        val d4 = distance(mouthRight.x, mouthRight.y, rightEye.x, rightEye.y) / interPupillaryDist
        val d5 = distance(mouthBottom.x, mouthBottom.y, nose.x, nose.y) / interPupillaryDist
        val d6 = distance(mouthLeft.x, mouthLeft.y, mouthRight.x, mouthRight.y) / interPupillaryDist

        return floatArrayOf(d1, d2, d3, d4, d5, d6)
    }

    // =========================================================================
    // MOTOR 2: FACENET (TENSORFLOW LITE)
    // =========================================================================
    private var tfliteInterpreter: Interpreter? = null

    private fun getInterpreter(context: Context): Interpreter {
        if (tfliteInterpreter == null) {
            val assetFileDescriptor = context.assets.openFd("facenet.tflite")
            val inputStream = assetFileDescriptor.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            tfliteInterpreter = Interpreter(modelBuffer)
        }
        return tfliteInterpreter!!
    }

    fun extractFaceNetEmbedding(context: Context, faceBitmap: Bitmap): FloatArray {
        val interpreter = getInterpreter(context)

        // Redimensionar rostro a 160x160 (entrada estándar de FaceNet)
        val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, 160, 160, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(160 * 160)
        resizedBitmap.getPixels(intValues, 0, 160, 0, 0, 160, 160)

        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 128.0f)
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 128.0f)
            inputBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 128.0f)
        }

        val embeddingOutput = Array(1) { FloatArray(128) }
        interpreter.run(inputBuffer, embeddingOutput)
        return embeddingOutput[0]
    }

    // =========================================================================
    // MÉTODOS PÚBLICOS UNIFICADOS (TRANSPARENTES PARA EL RESTO DE TU APP)
    // =========================================================================

    fun saveBlockedFace(context: Context, face: Face, faceBitmap: Bitmap? = null) {
        if (USE_FACENET && faceBitmap != null) {
            val newEmbedding = extractFaceNetEmbedding(context, faceBitmap)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Guardar múltiples embeddings acumulados (soporta fotos de frente, perfil, etc.)
            val existingData = prefs.getString(KEY_FACENET_EMBEDDINGS, "") ?: ""
            val newSerializedVector = newEmbedding.joinToString(",")
            val updatedData = if (existingData.isEmpty()) newSerializedVector else "$existingData;$newSerializedVector"

            prefs.edit().putString(KEY_FACENET_EMBEDDINGS, updatedData).apply()
        } else {
            // Guardado por Landmarks (Original)
            val vector = extractFacialVector(face) ?: return
            val vectorString = vector.joinToString(",")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_VECTOR, vectorString)
                .apply()
        }
    }

    fun isBlockedUser(context: Context, currentFace: Face, faceBitmap: Bitmap? = null): Boolean {
        if (USE_FACENET && faceBitmap != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedData = prefs.getString(KEY_FACENET_EMBEDDINGS, null) ?: return false

            val currentEmbedding = extractFaceNetEmbedding(context, faceBitmap)
            val savedEmbeddingsList = savedData.split(";").map { vectorStr ->
                vectorStr.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
            }

            // Comparar contra todas las fotos registradas
            val THRESHOLD = 0.40f // Umbral de distancia euclidiana de FaceNet
            return savedEmbeddingsList.any { savedVector ->
                calculateEuclideanDistance(currentEmbedding, savedVector) < THRESHOLD
            }
        } else {
            // Verificación por Landmarks (Original)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedData = prefs.getString(KEY_VECTOR, null) ?: return false

            val savedVector = savedData.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
            val currentVector = extractFacialVector(currentFace) ?: return false

            if (savedVector.size != currentVector.size) return false

            var totalError = 0f
            for (i in savedVector.indices) {
                totalError += abs(savedVector[i] - currentVector[i])
            }

            val averageError = totalError / savedVector.size
            return averageError < 0.08f
        }
    }

    private fun calculateEuclideanDistance(v1: FloatArray, v2: FloatArray): Float {
        var sum = 0f
        for (i in v1.indices) {
            val diff = v1[i] - v2[i]
            sum += diff * diff
        }
        return sqrt(sum.toDouble()).toFloat()
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    fun getSavedEmbeddingsCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedData = prefs.getString(KEY_FACENET_EMBEDDINGS, null) ?: return 0
        if (savedData.isEmpty()) return 0
        return savedData.split(";").size
    }
}