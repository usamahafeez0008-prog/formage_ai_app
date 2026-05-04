package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Logic for analyzing Squats.
 * Tracks depth, back posture, and manages the squat state machine.
 */
class SquatLogic : ExerciseLogic {
    private var repCount = 0
    private var currentState = ExerciseState.STANDING
    private var minKneeAngle = 180.0
    private var feedbackList = mutableListOf<String>()
    private var smoothedKneeAngle = -1.0
    private var smoothedBackAngle = -1.0
    private val SMOOTHING_FACTOR = 0.2
    private var isInitialized = false

    override fun analyze(pose: Pose): AnalysisResult {
        val landmarks = pose.getAllPoseLandmarks()
        if (landmarks.isEmpty()) {
            return AnalysisResult("Squat", repCount, currentState, 0, emptyList(), emptyMap())
        }

        // Get required landmarks
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)

        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        val angles = mutableMapOf<String, Double>()
        feedbackList.clear()

        if (leftHip != null && leftKnee != null && leftAnkle != null &&
            rightHip != null && rightKnee != null && rightAnkle != null &&
            leftHip.inFrameLikelihood > 0.6f && leftKnee.inFrameLikelihood > 0.6f && leftAnkle.inFrameLikelihood > 0.6f &&
            rightHip.inFrameLikelihood > 0.6f && rightKnee.inFrameLikelihood > 0.6f && rightAnkle.inFrameLikelihood > 0.6f) {

            // Calculate knee angles
            val leftKneeAngle = JointAngleCalculator.calculateAngle(leftHip, leftKnee, leftAnkle)
            val rightKneeAngle = JointAngleCalculator.calculateAngle(rightHip, rightKnee, rightAnkle)
            val rawKneeAngle = (leftKneeAngle + rightKneeAngle) / 2
            
            // Initialize smoothing on first valid frame
            if (!isInitialized) {
                smoothedKneeAngle = rawKneeAngle
                isInitialized = true
            } else {
                smoothedKneeAngle = (rawKneeAngle * SMOOTHING_FACTOR) + (smoothedKneeAngle * (1 - SMOOTHING_FACTOR))
            }
            
            angles["knee_raw"] = rawKneeAngle
            angles["knee_smooth"] = smoothedKneeAngle

            // ... back angle logic ...
            // State Machine & Rep Counting
            processState(smoothedKneeAngle)

            // Calculate back angle (Shoulder - Hip - Ankle) (Requirement 8.B)
            if (leftShoulder != null && leftAnkle != null && rightShoulder != null && rightAnkle != null) {
                val leftBackAngle = JointAngleCalculator.calculateAngle(leftShoulder, leftHip, leftAnkle)
                val rightBackAngle = JointAngleCalculator.calculateAngle(rightShoulder, rightHip, rightAnkle)
                val rawBackAngle = (leftBackAngle + rightBackAngle) / 2
                
                // Apply EMA smoothing
                if (smoothedBackAngle < 0) {
                    smoothedBackAngle = rawBackAngle
                } else {
                    smoothedBackAngle = (rawBackAngle * SMOOTHING_FACTOR) + (smoothedBackAngle * (1 - SMOOTHING_FACTOR))
                }
                angles["back_angle"] = smoothedBackAngle
                
                if (smoothedBackAngle < 140.0) {
                    feedbackList.add("Keep back straight")
                }
            }

            
            // Validation: Depth Check (Requirement 8.A)
            if (currentState == ExerciseState.BOTTOM || currentState == ExerciseState.ASCENDING) {
                if (minKneeAngle > 110.0) {
                    feedbackList.add("Go lower")
                }
            }
        }

        return AnalysisResult(
            exercise = "squat",
            repCount = repCount,
            currentState = currentState,
            formScore = calculateScore(),
            feedback = feedbackList.toList(),
            angles = angles
        )
    }

    private fun processState(kneeAngle: Double) {
        // Track the lowest point reached during the descent
        if (kneeAngle < minKneeAngle) {
            minKneeAngle = kneeAngle
        }

        when (currentState) {
            ExerciseState.STANDING -> {
                if (kneeAngle < 160.0) {
                    currentState = ExerciseState.DESCENDING
                }
            }
            ExerciseState.DESCENDING -> {
                if (kneeAngle < 95.0) {
                    currentState = ExerciseState.BOTTOM
                } else if (kneeAngle > 165.0) {
                    // Reset if they stood back up without completing
                    currentState = ExerciseState.STANDING
                    minKneeAngle = 180.0
                }
            }
            ExerciseState.BOTTOM -> {
                if (kneeAngle > 100.0) {
                    currentState = ExerciseState.ASCENDING
                }
            }
            ExerciseState.ASCENDING -> {
                if (kneeAngle > 165.0) {
                    // Rep Completed (Requirement 7)
                    repCount++
                    currentState = ExerciseState.COMPLETED
                }
            }
            ExerciseState.COMPLETED -> {
                // Reset for next rep
                currentState = ExerciseState.STANDING
                minKneeAngle = 180.0
            }
        }
    }

    private fun calculateScore(): Int {
        var score = 100
        // Penalties (Requirement 10)
        if (feedbackList.contains("Go lower")) score -= 20
        if (feedbackList.contains("Keep back straight")) score -= 30
        return Math.max(0, score)
    }
}
