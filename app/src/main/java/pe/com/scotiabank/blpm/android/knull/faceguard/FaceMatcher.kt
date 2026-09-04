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
    private const val KEY_EMBEDDINGS_SET = "facenet_embeddings_set"

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

    @Synchronized
    private fun getInterpreter(context: Context): Interpreter {
        if (tfliteInterpreter == null) {
            val assetFileDescriptor = context.assets.openFd("facenet.tflite")
            val inputStream = assetFileDescriptor.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply { setNumThreads(4) }
            tfliteInterpreter = Interpreter(modelBuffer, options)
        }
        return tfliteInterpreter!!
    }

    fun extractFaceNetEmbedding(context: Context, faceBitmap: Bitmap): FloatArray {
        val interpreter = getInterpreter(context)

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

        val embeddingOutput = Array(1) { FloatArray(512) }
        interpreter.run(inputBuffer, embeddingOutput)
        return embeddingOutput[0]
    }

    // =========================================================================
    // MÉTODOS PÚBLICOS UNIFICADOS
    // =========================================================================

    fun saveBlockedFace(context: Context, face: Face, faceBitmap: Bitmap? = null) {
        if (USE_FACENET && faceBitmap != null) {
            val newEmbedding = extractFaceNetEmbedding(context, faceBitmap)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val currentSet = prefs.getStringSet(KEY_EMBEDDINGS_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
            val newSerializedVector = newEmbedding.joinToString(",")
            currentSet.add(newSerializedVector)

            prefs.edit().putStringSet(KEY_EMBEDDINGS_SET, currentSet).apply()
        } else {
            // Guardado por Landmarks
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
            val savedEmbeddingsList = getSavedEmbeddings(context)
            if (savedEmbeddingsList.isEmpty()) return false

            val currentEmbedding = extractFaceNetEmbedding(context, faceBitmap)

            val THRESHOLD = 0.75f // Umbral para vectores de 512 dimensiones
            return savedEmbeddingsList.any { savedVector ->
                calculateEuclideanDistance(currentEmbedding, savedVector) < THRESHOLD
            }
        } else {
            // Verificación por Landmarks
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

    private fun getSavedEmbeddings(context: Context): List<FloatArray> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_EMBEDDINGS_SET, emptySet()) ?: return emptyList()

        return savedSet.mapNotNull { rawEmbedding ->
            try {
                val parts = rawEmbedding.split(",")
                if (parts.size == 512) {
                    FloatArray(512) { index -> parts[index].toFloat() }
                } else null
            } catch (e: Exception) {
                null
            }
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
        return getSavedEmbeddings(context).size
    }
}