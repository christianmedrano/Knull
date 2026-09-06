package pe.com.scotiabank.blpm.android.knull.faceguard

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

object FaceMatcher {

    private const val PREFS_NAME = "face_guard_prefs"
    private const val KEY_VECTOR = "blocked_face_vector"
    private const val KEY_EMBEDDINGS_SET = "facenet_embeddings_set"

    var USE_FACENET = true

    // =========================================================================
    // MOTOR 1: LANDMARKS (PROPORCIONES ROBUSTAS Y INVARIANTES A ESCALA)
    // =========================================================================
    private fun extractFacialVector(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position

        val interPupillaryDist = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
        if (interPupillaryDist == 0f) return null

        val eyeCenterX = (leftEye.x + rightEye.x) / 2f
        val eyeCenterY = (leftEye.y + rightEye.y) / 2f

        // Proporciones invariantemente escaladas respecto a la distancia de los ojos
        val d1 = hypot(nose.x - eyeCenterX, nose.y - eyeCenterY) / interPupillaryDist

        val d2 = if (mouthLeft != null && mouthRight != null) {
            val mouthCenterX = (mouthLeft.x + mouthRight.x) / 2f
            val mouthCenterY = (mouthLeft.y + mouthRight.y) / 2f
            hypot(mouthCenterX - eyeCenterX, mouthCenterY - eyeCenterY) / interPupillaryDist
        } else if (mouthBottom != null) {
            hypot(mouthBottom.x - eyeCenterX, mouthBottom.y - eyeCenterY) / interPupillaryDist
        } else {
            return null
        }

        val d3 = if (mouthLeft != null && mouthRight != null) {
            hypot(mouthRight.x - mouthLeft.x, mouthRight.y - mouthLeft.y) / interPupillaryDist
        } else 0.5f

        return floatArrayOf(d1, d2, d3)
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

        // Asegurar que el bitmap sea ARGB_8888 para evitar errores de formato de pixel
        val configBitmap = faceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val resizedBitmap = Bitmap.createScaledBitmap(configBitmap, 160, 160, true)

        val inputBuffer = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(160 * 160)
        resizedBitmap.getPixels(intValues, 0, 160, 0, 0, 160, 160)

        for (pixelValue in intValues) {
            // FaceNet espera RGB. Aquí extraemos R, G y B correctamente.
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 128.0f)
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 128.0f)
            inputBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 128.0f)
        }

        val embeddingOutput = Array(1) { FloatArray(512) }
        interpreter.run(inputBuffer, embeddingOutput)
        return l2Normalize(embeddingOutput[0])
    }

    private fun l2Normalize(embeddings: FloatArray): FloatArray {
        var sum = 0f
        for (v in embeddings) {
            sum += v * v
        }
        val norm = sqrt(sum)
        if (norm == 0f) return embeddings
        for (i in embeddings.indices) {
            embeddings[i] = embeddings[i] / norm
        }
        return embeddings
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
            val vector = extractFacialVector(face) ?: return
            val vectorString = vector.joinToString(",")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_VECTOR, vectorString)
                .apply()
        }
    }

    private const val THRESHOLD = 0.70f

    fun isBlockedUser(context: Context, currentFace: Face, faceBitmap: Bitmap? = null): Boolean {
        if (USE_FACENET && faceBitmap != null) {
            val savedEmbeddingsList = getSavedEmbeddings(context)
            if (savedEmbeddingsList.isEmpty()) return false

            val currentEmbedding = extractFaceNetEmbedding(context, faceBitmap)

            return savedEmbeddingsList.any { savedVector ->
                val dist = calculateEuclideanDistance(currentEmbedding, savedVector)
                android.util.Log.d("FaceGuard", "DISTANCIA CALCULADA: $dist")
                dist < THRESHOLD
            }
        } else {
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
            Log.d("FaceGuard", "Error promedio de Landmarks: $averageError")

            // Umbral ajustado a 0.18f (18% de tolerancia en proporciones faciales)
            return averageError < 0.18f
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

    fun getSavedEmbeddingsCount(context: Context): Int {
        return getSavedEmbeddings(context).size
    }
}