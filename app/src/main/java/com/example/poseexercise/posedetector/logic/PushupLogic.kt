package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Logic for analyzing Push-ups.
 * Tracks elbow depth, body alignment, and manages the push-up state machine.
 */
class PushupLogic : ExerciseLogic {
    private var repCount = 0
    private var currentState = ExerciseState.STANDING // Using STANDING as PLANK_POSITION
    private var minElbowAngle = 180.0
    private var feedbackList = mutableListOf<String>()
    private var smoothedElbowAngle = -1.0
    private val SMOOTHING_FACTOR = 0.2
    private var isInitialized = false

    override fun analyze(pose: Pose): AnalysisResult {
        val landmarks = pose.getAllPoseLandmarks()
        if (landmarks.isEmpty()) {
            return AnalysisResult("Push-up", repCount, currentState, 0, emptyList(), emptyMap())
        }

        // Get required landmarks
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)

        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val angles = mutableMapOf<String, Double>()
        feedbackList.clear()

        if (leftShoulder != null && leftElbow != null && leftWrist != null &&
            rightShoulder != null && rightElbow != null && rightWrist != null &&
            leftShoulder.inFrameLikelihood > 0.6f && leftElbow.inFrameLikelihood > 0.6f && leftWrist.inFrameLikelihood > 0.6f &&
            rightShoulder.inFrameLikelihood > 0.6f && rightElbow.inFrameLikelihood > 0.6f && rightWrist.inFrameLikelihood > 0.6f) {

            // Calculate elbow angles
            val leftElbowAngle = JointAngleCalculator.calculateAngle(leftShoulder, leftElbow, leftWrist)
            val rightElbowAngle = JointAngleCalculator.calculateAngle(rightShoulder, rightElbow, rightWrist)
            val rawElbowAngle = (leftElbowAngle + rightElbowAngle) / 2
            
            // Initialize smoothing on first valid frame
            if (!isInitialized) {
                smoothedElbowAngle = rawElbowAngle
                isInitialized = true
            } else {
                smoothedElbowAngle = (rawElbowAngle * SMOOTHING_FACTOR) + (smoothedElbowAngle * (1 - SMOOTHING_FACTOR))
            }
            
            angles["elbow_raw"] = rawElbowAngle
            angles["elbow_smooth"] = smoothedElbowAngle
            
            angles["left_elbow"] = leftElbowAngle
            angles["right_elbow"] = rightElbowAngle

            // Body Alignment: Shoulder -> Hip -> Ankle (Requirement 8.E)
            if (leftHip != null && leftAnkle != null && rightHip != null && rightAnkle != null) {
                val leftBodyAngle = JointAngleCalculator.calculateAngle(leftShoulder, leftHip, leftAnkle)
                val rightBodyAngle = JointAngleCalculator.calculateAngle(rightShoulder, rightHip, rightAnkle)
                val avgBodyAngle = (leftBodyAngle + rightBodyAngle) / 2
                angles["body_alignment"] = avgBodyAngle
                angles["left_hip"] = leftBodyAngle
                angles["right_hip"] = rightBodyAngle

                if (avgBodyAngle < 155.0) {
                    feedbackList.add("Keep body straight")
                }
            }

            // Neck Position: Nose vs Shoulder level (Requirement 8.F)
            if (nose != null) {
                val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
                if (nose.position.y > shoulderY + 0.05) { // Threshold for drooping
                    feedbackList.add("Keep neck neutral")
                }
            }

            // State Machine (Requirement 6)
            processState(smoothedElbowAngle)
        }

        return AnalysisResult(
            exercise = "pushup",
            repCount = repCount,
            currentState = currentState,
            formScore = calculateScore(),
            feedback = feedbackList.toList(),
            angles = angles
        )
    }

    private fun processState(elbowAngle: Double) {
        if (elbowAngle < minElbowAngle) {
            minElbowAngle = elbowAngle
        }

        when (currentState) {
            ExerciseState.STANDING -> { // This is Plank Position
                if (elbowAngle < 140.0) {
                    currentState = ExerciseState.DESCENDING
                }
            }
            ExerciseState.DESCENDING -> {
                if (elbowAngle < 90.0) { // Requirement 8.D
                    currentState = ExerciseState.BOTTOM
                } else if (elbowAngle > 160.0) {
                    currentState = ExerciseState.STANDING
                    minElbowAngle = 180.0
                }
            }
            ExerciseState.BOTTOM -> {
                if (elbowAngle > 100.0) {
                    currentState = ExerciseState.ASCENDING
                }
            }
            ExerciseState.ASCENDING -> {
                if (elbowAngle > 160.0) {
                    repCount++
                    currentState = ExerciseState.COMPLETED
                }
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.STANDING
                minElbowAngle = 180.0
            }
        }
    }

    private fun calculateScore(): Int {
        var score = 100
        if (feedbackList.contains("Keep body straight")) score -= 20
        if (feedbackList.contains("Keep neck neutral")) score -= 15
        return Math.max(0, score)
    }
}
