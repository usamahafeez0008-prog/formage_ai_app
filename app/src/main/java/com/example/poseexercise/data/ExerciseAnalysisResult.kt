package com.example.poseexercise.data

/**
 * Detailed analysis result for a single frame of an exercise.
 */
data class ExerciseAnalysisResult(
    val exercise: String,
    val repCount: Int,
    val currentState: String,
    val formScore: Int,
    val feedback: String?,
    val angles: Map<String, Double>
)
