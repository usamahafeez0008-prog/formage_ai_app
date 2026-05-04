package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Validates camera setup before a workout starts.
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

        // Essential landmarks for most exercises
        val essentialLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )

        val missingOrLowVisibility = essentialLandmarks.filter { type ->
            val landmark = pose.getPoseLandmark(type)
            landmark == null || landmark.inFrameLikelihood < 0.5f
        }

        if (missingOrLowVisibility.isNotEmpty()) {
            return Pair(false, "Move farther from camera")
        }

        // Check for "Too Close" (shoulders too high in frame)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        if (leftShoulder != null && leftShoulder.position.y < 0.1f) {
            return Pair(false, "Move down a bit")
        }

        return Pair(true, null)
    }
}
