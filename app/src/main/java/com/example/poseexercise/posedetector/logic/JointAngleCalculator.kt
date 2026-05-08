package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Reusable joint angle engine (requirements §5).
 * angle(A,B,C) with B the vertex; BA = A − B, BC = C − B;
 * θ = arccos((BA · BC) / (|BA| × |BC|)).
 */
object JointAngleCalculator {

    fun calculateAngle(
        firstPoint: PoseLandmark,
        midPoint: PoseLandmark,
        lastPoint: PoseLandmark
    ): Double {
        val p1 = firstPoint.position3D
        val p2 = midPoint.position3D
        val p3 = lastPoint.position3D
        return calculateAngleDegrees(
            p1.x, p1.y, p1.z,
            p2.x, p2.y, p2.z,
            p3.x, p3.y, p3.z
        )
    }

    /** Pure math variant for tests and tooling. */
    fun calculateAngleDegrees(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ): Double {
        val baX = (ax - bx).toDouble()
        val baY = (ay - by).toDouble()
        val baZ = (az - bz).toDouble()

        val bcX = (cx - bx).toDouble()
        val bcY = (cy - by).toDouble()
        val bcZ = (cz - bz).toDouble()

        val dotProduct = (baX * bcX) + (baY * bcY) + (baZ * bcZ)

        val magnitudeBA = sqrt(baX * baX + baY * baY + baZ * baZ)
        val magnitudeBC = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)

        if (magnitudeBA < 1e-10 || magnitudeBC < 1e-10) return 0.0

        var cosAngle = dotProduct / (magnitudeBA * magnitudeBC)
        cosAngle = cosAngle.coerceIn(-1.0, 1.0)

        val radians = acos(cosAngle)
        return Math.toDegrees(radians)
    }

    /** Lower body — knee flexion: hip–knee–ankle. */
    fun kneeAngle(hip: PoseLandmark, knee: PoseLandmark, ankle: PoseLandmark): Double =
        calculateAngle(hip, knee, ankle)

    /** Lower body — hip: shoulder–hip–knee. */
    fun hipAngle(shoulder: PoseLandmark, hip: PoseLandmark, knee: PoseLandmark): Double =
        calculateAngle(shoulder, hip, knee)

    /** Lower body — ankle: knee–ankle–foot index. */
    fun ankleAngle(knee: PoseLandmark, ankle: PoseLandmark, footIndex: PoseLandmark): Double =
        calculateAngle(knee, ankle, footIndex)

    /** Upper body — elbow: shoulder–elbow–wrist. */
    fun elbowAngle(shoulder: PoseLandmark, elbow: PoseLandmark, wrist: PoseLandmark): Double =
        calculateAngle(shoulder, elbow, wrist)

    /** Upper body — shoulder: elbow–shoulder–hip. */
    fun shoulderAngle(elbow: PoseLandmark, shoulder: PoseLandmark, hip: PoseLandmark): Double =
        calculateAngle(elbow, shoulder, hip)

    /** Spine / torso — back line: shoulder–hip–ankle (common squat diagnostic). */
    fun backAngle(shoulder: PoseLandmark, hip: PoseLandmark, ankle: PoseLandmark): Double =
        calculateAngle(shoulder, hip, ankle)

    /**
     * Neck / upper-torso posture: angle at shoulder between hip→shoulder and nose→shoulder.
     * Lower values often correlate with excessive forward head / rounding while pushing.
     */
    fun neckAngleAtShoulder(hip: PoseLandmark, shoulder: PoseLandmark, nose: PoseLandmark): Double =
        calculateAngle(hip, shoulder, nose)

    fun calculateAngleAtan2(
        firstPoint: PoseLandmark,
        midPoint: PoseLandmark,
        lastPoint: PoseLandmark
    ): Double {
        val result = Math.toDegrees(
            (atan2(lastPoint.position.y - midPoint.position.y, lastPoint.position.x - midPoint.position.x)
                - atan2(firstPoint.position.y - midPoint.position.y, firstPoint.position.x - midPoint.position.x)).toDouble()
        )
        var angle = Math.abs(result)
        if (angle > 180) {
            angle = 360 - angle
        }
        return angle
    }
}
