package com.example.poseexercise.posedetector.logic

/**
 * Factory to create the appropriate exercise logic based on the exercise name.
 */
object ExerciseLogicFactory {
    fun getLogic(exerciseName: String): ExerciseLogic? {
        val normalized = exerciseName.lowercase().trim()
        return when {
            normalized.contains("squat") -> SquatLogic()
            normalized.contains("pushup") || normalized.contains("push up") || normalized.contains("pushups_down") -> PushupLogic()
            else -> null
        }
    }
}
