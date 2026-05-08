package com.example.poseexercise.posedetector.logic

/**
 * Maps internal state machine values to API / product wire names (requirements §6).
 */
fun ExerciseState.toWireName(isPushUp: Boolean): String =
    when (this) {
        ExerciseState.STANDING ->
            if (isPushUp) "plank_position" else "standing"
        ExerciseState.DESCENDING -> "descending"
        ExerciseState.BOTTOM -> "bottom_position"
        ExerciseState.ASCENDING -> "ascending"
        ExerciseState.COMPLETED -> "completed_rep"
    }
