package com.example.poseexercise.data

/**
 * Continuous structured output (requirements §11) for UI / future JSON bridge.
 */
data class ExerciseAnalysisResult(
    val exercise: String,
    val repCount: Int,
    /** Wire-format state name (e.g. bottom_position, plank_position). */
    val currentState: String,
    /** Instantaneous form score 0–100 (§10). */
    val formScore: Int,
    /** Same as [formScore] for per-rep summaries; kept separate for future rep-finalized scoring. */
    val repScore: Int,
    /** All active feedback strings after persistence filtering (ordered by priority). */
    val feedback: List<String>,
    /** Highest-priority message with cooldown applied — convenient for a single TextView. */
    val primaryFeedback: String?,
    val angles: Map<String, Double>,
    /** Names of [com.example.poseexercise.posedetector.logic.PoseHighlight] for overlays / APIs. */
    val incorrectSegments: List<String>,
)
