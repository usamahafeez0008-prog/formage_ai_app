package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Calculator for joint angles based on 2D coordinates of body landmarks.
 * Implements the cosine rule via dot product for accurate angle measurement.
 */
object JointAngleCalculator {

    /**
     * Calculates the angle (in degrees) at joint B given three points A, B, and C.
     * Formula: angle = arccos((BA dot BC) / (|BA| * |BC|))
     * 
     * @param firstPoint Point A
     * @param midPoint Point B (the vertex/joint)
     * @param lastPoint Point C
     * @return Angle in degrees [0, 180]
     */
    fun calculateAngle(
        firstPoint: PoseLandmark,
        midPoint: PoseLandmark,
        lastPoint: PoseLandmark
    ): Double {
        val p1 = firstPoint.position3D
        val p2 = midPoint.position3D
        val p3 = lastPoint.position3D

        // BA = A - B
        val baX = p1.x - p2.x
        val baY = p1.y - p2.y
        val baZ = p1.z - p2.z

        // BC = C - B
        val bcX = p3.x - p2.x
        val bcY = p3.y - p2.y
        val bcZ = p3.z - p2.z

        // Dot product: BA dot BC
        val dotProduct = (baX * bcX) + (baY * bcY) + (baZ * bcZ)

        // Magnitudes: |BA| and |BC|
        val magnitudeBA = sqrt(baX * baX + baY * baY + baZ * baZ)
        val magnitudeBC = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)

        // Cosine of the angle: (BA dot BC) / (|BA| * |BC|)
        var cosAngle = dotProduct / (magnitudeBA * magnitudeBC)

        // Handle floating point precision errors
        if (cosAngle > 1.0f) cosAngle = 1.0f
        if (cosAngle < -1.0f) cosAngle = -1.0f

        // Calculate angle in radians and convert to degrees
        val radians = acos(cosAngle)
        return Math.toDegrees(radians.toDouble())
    }

    /**
     * Alternative method using atan2 which provides a more robust range if needed (e.g., orientation).
     * For internal consistency with standard exercise logic, calculateAngle is preferred.
     */
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
