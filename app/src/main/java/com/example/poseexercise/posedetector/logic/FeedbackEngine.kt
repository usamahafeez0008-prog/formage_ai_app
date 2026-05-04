package com.example.poseexercise.posedetector.logic

import android.os.SystemClock

/**
 * Manages exercise feedback, ensuring priority and preventing spam.
 */
class FeedbackEngine {
    private val feedbackStartTime = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 500L
    private val PERSISTENCE_THRESHOLD_MS = 100L

    // Priority map: higher value = higher priority
    private val priorityMap = mapOf(
        "dangerous posture" to 100,
        "Knees collapsing inward" to 90,
        "Keep body straight" to 80,
        "Keep back straight" to 70,
        "Feets too narrow" to 65,
        "Feets too wide" to 65,
        "Go lower" to 60,
        "Keep neck neutral" to 50,
        "Move farther from camera" to 20,
        "Full body not visible" to 15,
        "Move down a bit" to 10
    )

    /**
     * Filters and prioritizes feedback.
     * Only returns feedback if it has persisted for long enough and is the highest priority.
     */
    fun getFilteredFeedback(rawFeedback: List<String>): String? {
        val currentTime = SystemClock.elapsedRealtime()

        // Update persistence timers
        rawFeedback.forEach { feedback ->
            if (!feedbackStartTime.containsKey(feedback)) {
                feedbackStartTime[feedback] = currentTime
            }
        }

        // Remove feedback that is no longer present
        val toRemove = feedbackStartTime.keys.filter { it !in rawFeedback }
        toRemove.forEach { feedbackStartTime.remove(it) }

        // Filter by persistence
        val persistentFeedback = rawFeedback.filter { feedback ->
            val startTime = feedbackStartTime[feedback] ?: return@filter false
            (currentTime - startTime) >= PERSISTENCE_THRESHOLD_MS
        }

        // Sort by priority and return the most important one
        return persistentFeedback
            .sortedByDescending { priorityMap[it] ?: 0 }
            .firstOrNull()
    }
}
