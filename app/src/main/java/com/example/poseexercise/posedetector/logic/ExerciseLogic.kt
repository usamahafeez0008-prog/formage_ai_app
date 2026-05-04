package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose

/**
 * Interface for exercise-specific logic (e.g., Squat, Push-up).
 * Each implementation handles its own state machine, form validation, and scoring.
 */
interface ExerciseLogic {
    /**
     * Updates the analysis with a new frame.
     * @param pose The detected pose landmarks.
     * @return The result of the analysis for this frame.
     */
    fun analyze(pose: Pose): AnalysisResult
}
