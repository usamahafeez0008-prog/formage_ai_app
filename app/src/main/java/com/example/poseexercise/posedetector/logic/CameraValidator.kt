package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Validates camera setup before a workout starts (requirements §13).
 *
 * Lighting quality and ideal camera tilt need image statistics / calibration data;
 * extend here when [com.google.mlkit.vision.pose.Pose] is augmented with frame metadata.
 */
class CameraValidator {
    /**
     * Checks if the user is fully visible in the frame.
     * @return Pair of (isValid, warningMessage)
     */
    fun validateSetup(pose: Pose): Pair<Boolean, String?> {
        val landmarks = pose.getAllPoseLandmarks()
        if (landmarks.isEmpty()) {
            return Pair(false, "No body detected")
        }

        val essentialLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP
        )

        val missingEssential = essentialLandmarks.filter { type ->
            val landmark = pose.getPoseLandmark(type)
            landmark == null || landmark.inFrameLikelihood < 0.4f
        }

        if (missingEssential.isNotEmpty()) {
            return Pair(false, "Move farther from camera")
        }

        // Check for legs visibility (needed for squats)
        val legLandmarks = listOf(
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )
        val missingLegs = legLandmarks.count { type ->
            val landmark = pose.getPoseLandmark(type)
            landmark == null || landmark.inFrameLikelihood < 0.4f
        }
        val missingAnkles = listOf(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE).count { type ->
            val landmark = pose.getPoseLandmark(type)
            landmark == null || landmark.inFrameLikelihood < 0.45f
        }

        if (missingAnkles > 0 || missingLegs > 1) {
            return Pair(false, "Full body not visible")
        }

        // Check for "Too Close" (shoulders too high in frame)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        if (leftShoulder != null && leftShoulder.position.y < 0.05f) {
            return Pair(false, "Move down a bit")
        }

        return Pair(true, null)
    }
}
