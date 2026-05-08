package com.example.poseexercise.posedetector.logic

/**
 * Common states for repetition-based exercises.
 */
enum class ExerciseState {
    STANDING,      // Initial position
    DESCENDING,    // Moving downwards
    BOTTOM,        // Reached target depth/position
    ASCENDING,     // Moving upwards
    COMPLETED      // Cycle finished
}

/**
 * Result of a single frame analysis.
 */
data class AnalysisResult(
    val exercise: String,
    val repCount: Int,
    val currentState: ExerciseState,
    val formScore: Int,
    val feedback: List<String>,
    val angles: Map<String, Double>,
    /** Skeleton segments that should render red this frame. */
    val incorrectSegments: Set<PoseHighlight> = emptySet(),
)
