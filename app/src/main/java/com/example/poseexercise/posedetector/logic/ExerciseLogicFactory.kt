package com.example.poseexercise.posedetector.logic

/**
 * Factory to create the appropriate exercise logic based on the exercise name.
 */
object ExerciseLogicFactory {
    fun getLogic(exerciseName: String): ExerciseLogic? {
        val normalized = exerciseName.lowercase().trim().replace("_", "")
        return when {
            normalized.contains("squat") -> SquatLogic()
            normalized.contains("bicep curl") || normalized.contains("bicepcurl") ->
                BicepCurlLogic()
            normalized.contains("pushup") ||
                normalized.contains("push up") ||
                normalized.contains("pushupsdown") -> PushupLogic()
            else -> null
        }
    }
}
