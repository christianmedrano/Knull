package pe.com.scotiabank.blpm.android.knull.faceguard

import android.content.Context
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.sqrt

object FaceMatcher {

    private const val PREFS_NAME = "face_guard_prefs"
    private const val KEY_VECTOR = "blocked_face_vector"

    // Extrae un vector de proporciones faciales inmune a la escala/resolución
    private fun extractFacialVector(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return null

        // Distancia base de referencia: Distancia entre ojos
        val interPupillaryDist = distance(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
        if (interPupillaryDist == 0f) return null

        // Calculamos distancias clave relativas (normalizadas dividiendo entre la distancia entre ojos)
        val d1 = distance(nose.x, nose.y, leftEye.x, leftEye.y) / interPupillaryDist
        val d2 = distance(nose.x, nose.y, rightEye.x, rightEye.y) / interPupillaryDist
        val d3 = distance(mouthLeft.x, mouthLeft.y, leftEye.x, leftEye.y) / interPupillaryDist
        val d4 = distance(mouthRight.x, mouthRight.y, rightEye.x, rightEye.y) / interPupillaryDist
        val d5 = distance(mouthBottom.x, mouthBottom.y, nose.x, nose.y) / interPupillaryDist
        val d6 = distance(mouthLeft.x, mouthLeft.y, mouthRight.x, mouthRight.y) / interPupillaryDist

        return floatArrayOf(d1, d2, d3, d4, d5, d6)
    }

    fun saveBlockedFace(context: Context, face: Face) {
        val vector = extractFacialVector(face) ?: return
        val vectorString = vector.joinToString(",")

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VECTOR, vectorString)
            .apply()
    }

    fun isBlockedUser(context: Context, currentFace: Face): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedData = prefs.getString(KEY_VECTOR, null) ?: return false

        val savedVector = savedData.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        val currentVector = extractFacialVector(currentFace) ?: return false

        if (savedVector.size != currentVector.size) return false

        // Calculamos el error absoluto promedio entre las proporciones del rostro
        var totalError = 0f
        for (i in savedVector.indices) {
            totalError += abs(savedVector[i] - currentVector[i])
        }

        val averageError = totalError / savedVector.size

        // UMBRAL DE IDENTIDAD:
        // Un error de proporciones < 0.08 indica que es la misma persona (tu esposa).
        // Si eres tú o tu papá, el cambio de facciones dará un valor mayor (> 0.12).
        return averageError < 0.08f
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}