package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

/**
 * Lightweight logic to support bicep curl analysis and manual switching.
 * Reps are still also tracked by classifier counters.
 */
class BicepCurlLogic : ExerciseLogic {
    private var repCount = 0
    private var currentState = ExerciseState.STANDING
    private var stateFrameCount = 0
    private val minStateFrames = 2
    private var smoothedElbowAngle = -1.0
    private val smoothingFactor = 0.2
    private var minElbowThisRep = 180.0
    private var maxElbowThisRep = 0.0
    private var topHoldFrames = 0
    private var baselineShoulderY = -1f
    private val feedback = mutableListOf<String>()
    private val highlights = mutableSetOf<PoseHighlight>()

    companion object {
        // Practical test thresholds for dumbbell curls:
        // bottom should approach extension (~155-175), top should approach deep flexion (~45-70).
        private const val BOTTOM_GOOD_MIN = 155.0
        private const val TOP_GOOD_MAX = 70.0
        private const val START_DESCENDING_MAX = 130.0
        private const val TOP_ENTRY_MAX = 75.0
        private const val START_ASCENDING_MIN = 95.0
        private const val REP_COMPLETE_MIN = 150.0
    }

    override fun analyze(pose: Pose): AnalysisResult {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val hasArms = leftShoulder != null && leftElbow != null && leftWrist != null &&
            rightShoulder != null && rightElbow != null && rightWrist != null &&
            leftShoulder.inFrameLikelihood > 0.45f && leftElbow.inFrameLikelihood > 0.45f &&
            leftWrist.inFrameLikelihood > 0.45f && rightShoulder.inFrameLikelihood > 0.45f &&
            rightElbow.inFrameLikelihood > 0.45f && rightWrist.inFrameLikelihood > 0.45f

        if (!hasArms) {
            return AnalysisResult(
                exercise = "bicep_curl",
                repCount = repCount,
                currentState = currentState,
                formScore = 100,
                feedback = emptyList(),
                angles = emptyMap(),
                incorrectSegments = emptySet()
            )
        }

        feedback.clear()
        highlights.clear()

        val ls = leftShoulder!!
        val le = leftElbow!!
        val lw = leftWrist!!
        val rs = rightShoulder!!
        val re = rightElbow!!
        val rw = rightWrist!!

        val left = JointAngleCalculator.elbowAngle(ls, le, lw)
        val right = JointAngleCalculator.elbowAngle(rs, re, rw)
        val avg = (left + right) / 2.0
        smoothedElbowAngle = if (smoothedElbowAngle < 0) avg
        else avg * smoothingFactor + smoothedElbowAngle * (1 - smoothingFactor)
        minElbowThisRep = minOf(minElbowThisRep, smoothedElbowAngle)
        maxElbowThisRep = maxOf(maxElbowThisRep, smoothedElbowAngle)

        // Range-of-motion feedback:
        // "Curl higher" only when user reaches/holds top but elbow still not flexed enough.
        if (currentState == ExerciseState.BOTTOM) {
            topHoldFrames++
            if (topHoldFrames >= 2 && smoothedElbowAngle > TOP_GOOD_MAX + 8.0) {
                feedback.add("Curl higher")
                highlights.addAll(
                    listOf(
                        PoseHighlight.LEFT_FOREARM,
                        PoseHighlight.RIGHT_FOREARM,
                        PoseHighlight.LEFT_UPPER_ARM,
                        PoseHighlight.RIGHT_UPPER_ARM
                    )
                )
            }
        } else {
            topHoldFrames = 0
        }

        // "Lower fully" should be checked at rep end / standing (not while moving).
        if (currentState == ExerciseState.STANDING && maxElbowThisRep > 0.0) {
            if (maxElbowThisRep < BOTTOM_GOOD_MIN) {
                feedback.add("Lower fully")
                highlights.addAll(
                    listOf(PoseHighlight.LEFT_FOREARM, PoseHighlight.RIGHT_FOREARM)
                )
            }
        }

        // Keep elbows close to torso: detect large lateral elbow drift.
        val torsoWidth = abs(ls.position.x - rs.position.x).coerceAtLeast(0.08f)
        val leftElbowDrift = abs(le.position.x - ls.position.x) / torsoWidth
        val rightElbowDrift = abs(re.position.x - rs.position.x) / torsoWidth
        if (leftElbowDrift > 0.95f || rightElbowDrift > 0.95f) {
            feedback.add("Keep elbows close to torso")
            highlights.addAll(
                listOf(
                    PoseHighlight.LEFT_UPPER_ARM,
                    PoseHighlight.RIGHT_UPPER_ARM,
                    PoseHighlight.LEFT_TRUNK,
                    PoseHighlight.RIGHT_TRUNK
                )
            )
        }

        // Shoulder swing compensation: compare shoulder lift against standing baseline.
        val shoulderY = (ls.position.y + rs.position.y) / 2f
        if (currentState == ExerciseState.STANDING || baselineShoulderY < 0f) {
            baselineShoulderY = shoulderY
        }
        val shoulderLift = baselineShoulderY > 0f && shoulderY < baselineShoulderY - 0.05f
        if (shoulderLift && currentState != ExerciseState.STANDING) {
            feedback.add("Avoid shoulder swing")
            highlights.addAll(
                listOf(
                    PoseHighlight.MID_SHOULDERS,
                    PoseHighlight.LEFT_TRUNK,
                    PoseHighlight.RIGHT_TRUNK
                )
            )
        }

        processState(smoothedElbowAngle)

        val formScore = calculateScore(feedback)
        return AnalysisResult(
            exercise = "bicep_curl",
            repCount = repCount,
            currentState = currentState,
            formScore = formScore,
            feedback = feedback.distinct(),
            angles = mapOf(
                "left_elbow" to left,
                "right_elbow" to right,
                "elbow_smooth" to smoothedElbowAngle,
                "left_elbow_drift" to leftElbowDrift.toDouble(),
                "right_elbow_drift" to rightElbowDrift.toDouble()
            ),
            incorrectSegments = highlights.toSet()
        )
    }

    private fun processState(elbow: Double) {
        when (currentState) {
            ExerciseState.STANDING -> {
                if (elbow < START_DESCENDING_MAX) {
                    currentState = ExerciseState.DESCENDING
                    minElbowThisRep = elbow
                    maxElbowThisRep = elbow
                } else {
                    // keep reset while idle
                    minElbowThisRep = 180.0
                    maxElbowThisRep = elbow
                }
            }
            ExerciseState.DESCENDING -> if (elbow < TOP_ENTRY_MAX) currentState = ExerciseState.BOTTOM
            ExerciseState.BOTTOM -> if (elbow > START_ASCENDING_MIN) currentState = ExerciseState.ASCENDING
            ExerciseState.ASCENDING -> {
                if (elbow > REP_COMPLETE_MIN) {
                    stateFrameCount++
                    if (stateFrameCount >= minStateFrames) {
                        repCount++
                        currentState = ExerciseState.COMPLETED
                        stateFrameCount = 0
                    }
                } else stateFrameCount = 0
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.STANDING
                // prepare for next rep
                minElbowThisRep = 180.0
                maxElbowThisRep = 0.0
            }
        }
    }

    private fun calculateScore(messages: List<String>): Int {
        var score = 100
        if (messages.contains("Curl higher")) score -= 25
        if (messages.contains("Lower fully")) score -= 20
        if (messages.contains("Keep elbows close to torso")) score -= 20
        if (messages.contains("Avoid shoulder swing")) score -= 20
        return score.coerceAtLeast(0)
    }
}
