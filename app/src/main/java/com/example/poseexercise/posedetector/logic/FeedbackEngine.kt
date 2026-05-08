package com.example.poseexercise.posedetector.logic

import android.os.SystemClock

/**
 * Requirements §9 — priority, persistence (&gt;500ms), cooldown (anti-spam).
 */
data class FeedbackProcessResult(
    /** Messages that have satisfied persistence; ordered by priority (high first). */
    val messages: List<String>,
    /** Single line suitable for compact UI / TTS; respects cooldown on churn. */
    val primaryMessage: String?,
)

class FeedbackEngine {
    private val feedbackStartTime = mutableMapOf<String, Long>()
    private var lastPrimaryChangeTime = 0L
    private var lastPrimaryMessage: String? = null

    private val persistenceMs = 500L
    private val cooldownMs = 500L

    private val priorityMap = mapOf(
        "dangerous posture" to 100,
        "No body detected" to 99,
        "Knees collapsing inward" to 95,
        "Keep body straight" to 88,
        "Keep back straight" to 87,
        "Keep shoulders level" to 86,
        "Don't move arms behind" to 86,
        "Curl higher" to 86,
        "Lower fully" to 85,
        "Keep elbows close to torso" to 85,
        "Avoid shoulder swing" to 84,
        "Go lower" to 85,
        "Keep neck neutral" to 82,
        "Keep both feet straighter" to 81,
        "Keep left foot straighter" to 81,
        "Keep right foot straighter" to 81,
        "Feet not aligned" to 79,
        "Stance too narrow" to 78,
        "Stance too wide" to 78,
        "Move farther from camera" to 72,
        "Full body not visible" to 70,
        "Move down a bit" to 65,
    )

    fun process(rawFeedback: List<String>): FeedbackProcessResult {
        val now = SystemClock.elapsedRealtime()

        rawFeedback.forEach { msg ->
            if (!feedbackStartTime.containsKey(msg)) {
                feedbackStartTime[msg] = now
            }
        }
        feedbackStartTime.keys.filter { it !in rawFeedback }.forEach { feedbackStartTime.remove(it) }

        val persisted = rawFeedback
            .filter { msg ->
                val start = feedbackStartTime[msg] ?: return@filter false
                now - start >= persistenceMs
            }
            .distinct()
            .sortedByDescending { priorityMap[it] ?: 0 }

        if (persisted.isEmpty()) {
            lastPrimaryMessage = null
            return FeedbackProcessResult(emptyList(), null)
        }

        val candidate = persisted.first()

        if (lastPrimaryMessage != null && lastPrimaryMessage !in persisted) {
            lastPrimaryMessage = null
        }

        val primary = when {
            lastPrimaryMessage == null -> {
                lastPrimaryMessage = candidate
                lastPrimaryChangeTime = now
                candidate
            }
            candidate == lastPrimaryMessage -> candidate
            now - lastPrimaryChangeTime >= cooldownMs -> {
                lastPrimaryMessage = candidate
                lastPrimaryChangeTime = now
                candidate
            }
            else -> lastPrimaryMessage
        }

        return FeedbackProcessResult(persisted, primary)
    }

    /** @deprecated legacy API — use [process]. */
    fun getFilteredFeedback(rawFeedback: List<String>): String? =
        process(rawFeedback).primaryMessage
}
