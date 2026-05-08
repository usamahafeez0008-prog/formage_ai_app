package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Squat analysis — requirements §6 squat states, §8 Exercise 1, §10 scoring.
 */
class SquatLogic : ExerciseLogic {
    private var repCount = 0
    private var currentState = ExerciseState.STANDING
    private var minKneeAngle = 180.0
    private val feedbackList = mutableListOf<String>()
    private val highlights = mutableSetOf<PoseHighlight>()
    private var smoothedKneeAngle = -1.0
    private var smoothedBackAngle = -1.0
    private val smoothingFactor = 0.15
    private var initialized = false
    private var stateFrameCount = 0
    private val minStateFrames = 3

    companion object {
        /** Slightly relaxed vs old 0.55 so seated / partial frames still get form feedback. */
        private const val LIKELIHOOD = 0.48f

        /** §8.A depth — good depth knee angle &lt; 95°, bad &gt; 110°. */
        private const val DEPTH_GOOD_MAX = 95.0
        private const val DEPTH_BAD_MIN = 110.0

        /** §8.B back — bad &lt; 140° (good posture stays above ~150°). */
        private const val BACK_BAD_MAX = 140.0

        /** One foot toe-out vs the other — knee–ankle–toe angle delta (degrees). */
        private const val FOOT_ASYMMETRY_THRESHOLD_DEG = 15.0
        private const val FOOT_OUTWARD_THRESHOLD_DEG = 20.0
        private const val SHOULDER_TILT_THRESHOLD = 0.11
    }

    override fun analyze(pose: Pose): AnalysisResult {
        val landmarks = pose.getAllPoseLandmarks()
        if (landmarks.isEmpty()) {
            return AnalysisResult(
                "squat", repCount, currentState, 0,
                emptyList(), emptyMap(), emptySet()
            )
        }

        feedbackList.clear()
        highlights.clear()

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)

        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val leftFootIndex = pose.getPoseLandmark(PoseLandmark.LEFT_FOOT_INDEX)
        val rightFootIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_FOOT_INDEX)

        val angles = mutableMapOf<String, Double>()

        val legsOk =
            leftHip != null && leftKnee != null && leftAnkle != null &&
                rightHip != null && rightKnee != null && rightAnkle != null &&
                leftHip.inFrameLikelihood > LIKELIHOOD &&
                leftKnee.inFrameLikelihood > LIKELIHOOD &&
                leftAnkle.inFrameLikelihood > LIKELIHOOD &&
                rightHip.inFrameLikelihood > LIKELIHOOD &&
                rightKnee.inFrameLikelihood > LIKELIHOOD &&
                rightAnkle.inFrameLikelihood > LIKELIHOOD

        if (!legsOk) {
            return AnalysisResult(
                "squat", repCount, currentState, 0,
                feedbackList.toList(), angles, highlights.toSet()
            )
        }

        val lh = leftHip!!
        val lk = leftKnee!!
        val la = leftAnkle!!
        val rh = rightHip!!
        val rk = rightKnee!!
        val ra = rightAnkle!!

        val leftKneeAngle = JointAngleCalculator.kneeAngle(lh, lk, la)
        val rightKneeAngle = JointAngleCalculator.kneeAngle(rh, rk, ra)
        val rawKneeAngle = (leftKneeAngle + rightKneeAngle) / 2.0

        if (!initialized) {
            smoothedKneeAngle = rawKneeAngle
            initialized = true
        } else {
            smoothedKneeAngle =
                rawKneeAngle * smoothingFactor + smoothedKneeAngle * (1 - smoothingFactor)
        }

        angles["left_knee"] = leftKneeAngle
        angles["right_knee"] = rightKneeAngle
        angles["knee_avg"] = rawKneeAngle
        angles["knee_smooth"] = smoothedKneeAngle

        if (leftShoulder != null && rightShoulder != null) {
            val ls = leftShoulder!!
            val rs = rightShoulder!!
            val leftHipAngle = JointAngleCalculator.hipAngle(ls, lh, lk)
            val rightHipAngle = JointAngleCalculator.hipAngle(rs, rh, rk)
            angles["left_hip_angle"] = leftHipAngle
            angles["right_hip_angle"] = rightHipAngle
            angles["hip_avg"] = (leftHipAngle + rightHipAngle) / 2.0
        }

        if (leftFootIndex != null && rightFootIndex != null) {
            val lfi = leftFootIndex!!
            val rfi = rightFootIndex!!
            val leftAnkleAngle = JointAngleCalculator.ankleAngle(lk, la, lfi)
            val rightAnkleAngle = JointAngleCalculator.ankleAngle(rk, ra, rfi)
            angles["left_ankle"] = leftAnkleAngle
            angles["right_ankle"] = rightAnkleAngle

            val asymmetry = kotlin.math.abs(leftAnkleAngle - rightAnkleAngle)
            angles["foot_asymmetry_deg"] = asymmetry
            if (asymmetry > FOOT_ASYMMETRY_THRESHOLD_DEG) {
                feedbackList.add("Feet not aligned")
                val avg = (leftAnkleAngle + rightAnkleAngle) / 2.0
                if (kotlin.math.abs(leftAnkleAngle - avg) >= kotlin.math.abs(rightAnkleAngle - avg)) {
                    highlights.add(PoseHighlight.LEFT_FOOT)
                } else {
                    highlights.add(PoseHighlight.RIGHT_FOOT)
                }
            }

            val leftToeOut = footOutwardAngleDeg(la, lfi)
            val rightToeOut = footOutwardAngleDeg(ra, rfi)
            angles["left_foot_outward"] = leftToeOut
            angles["right_foot_outward"] = rightToeOut
            val leftTooOutward = leftToeOut > FOOT_OUTWARD_THRESHOLD_DEG
            val rightTooOutward = rightToeOut > FOOT_OUTWARD_THRESHOLD_DEG
            when {
                leftTooOutward && rightTooOutward -> {
                    feedbackList.add("Keep both feet straighter")
                    highlights.addAll(listOf(PoseHighlight.LEFT_FOOT, PoseHighlight.RIGHT_FOOT))
                }
                leftTooOutward -> {
                    feedbackList.add("Keep left foot straighter")
                    highlights.add(PoseHighlight.LEFT_FOOT)
                }
                rightTooOutward -> {
                    feedbackList.add("Keep right foot straighter")
                    highlights.add(PoseHighlight.RIGHT_FOOT)
                }
            }
        }

        // Arm position safety cue: when wrists go behind torso while full body is visible.
        if (leftShoulder != null && rightShoulder != null && leftWrist != null && rightWrist != null) {
            val ls = leftShoulder!!
            val rs = rightShoulder!!
            val lw = leftWrist!!
            val rw = rightWrist!!
            val leftBehind = isWristBehindTorso(lw, ls, lh, rs, rh)
            val rightBehind = isWristBehindTorso(rw, rs, rh, ls, lh)
            if (leftBehind || rightBehind) {
                feedbackList.add("Don't move arms behind")
                highlights.addAll(
                    listOf(
                        PoseHighlight.LEFT_UPPER_ARM,
                        PoseHighlight.LEFT_FOREARM,
                        PoseHighlight.RIGHT_UPPER_ARM,
                        PoseHighlight.RIGHT_FOREARM
                    )
                )
            }
        }

        if (leftShoulder != null && rightShoulder != null) {
            val ls = leftShoulder!!
            val rs = rightShoulder!!
            // Back angle is derived from trunk lean in frontal view:
            // angle = 180 - leanFromVertical(midShoulders -> midHips)
            // Straight torso ~= 180, more lean/sway -> lower.
            val shoulderMidX = (ls.position.x + rs.position.x) / 2f
            val shoulderMidY = (ls.position.y + rs.position.y) / 2f
            val hipMidX = (lh.position.x + rh.position.x) / 2f
            val hipMidY = (lh.position.y + rh.position.y) / 2f
            val trunkLean = trunkLeanAngleFromVertical(shoulderMidX, shoulderMidY, hipMidX, hipMidY)
            val rawBack = 180.0 - trunkLean
            if (smoothedBackAngle < 0) {
                smoothedBackAngle = rawBack
            } else {
                smoothedBackAngle =
                    rawBack * smoothingFactor + smoothedBackAngle * (1 - smoothingFactor)
            }
            angles["back_angle"] = smoothedBackAngle

            if (smoothedBackAngle < BACK_BAD_MAX) {
                feedbackList.add("Keep back straight")
                highlights.addAll(
                    listOf(
                        PoseHighlight.LEFT_TRUNK,
                        PoseHighlight.RIGHT_TRUNK,
                        PoseHighlight.MID_SHOULDERS,
                        PoseHighlight.MID_HIPS
                    )
                )
            }

            val shoulderSpan = abs(ls.position.x - rs.position.x).coerceAtLeast(0.05f)
            val shoulderTiltNormalized = abs(ls.position.y - rs.position.y) / shoulderSpan
            angles["shoulder_tilt"] = shoulderTiltNormalized.toDouble()
            if (shoulderTiltNormalized > SHOULDER_TILT_THRESHOLD) {
                feedbackList.add("Keep shoulders level")
                highlights.addAll(
                    listOf(
                        PoseHighlight.MID_SHOULDERS,
                        PoseHighlight.LEFT_TRUNK,
                        PoseHighlight.RIGHT_TRUNK
                    )
                )
            }
        }

        processState(smoothedKneeAngle)

        if (currentState == ExerciseState.BOTTOM || currentState == ExerciseState.ASCENDING) {
            if (minKneeAngle > DEPTH_BAD_MIN) {
                feedbackList.add("Go lower")
                highlights.addAll(
                    listOf(
                        PoseHighlight.LEFT_THIGH,
                        PoseHighlight.RIGHT_THIGH,
                        PoseHighlight.LEFT_SHIN,
                        PoseHighlight.RIGHT_SHIN,
                        PoseHighlight.LEFT_FOOT,
                        PoseHighlight.RIGHT_FOOT
                    )
                )
            }
        }

        if (detectKneeValgus(lh, lk, la, rh, rk, ra)) {
            feedbackList.add("Knees collapsing inward")
            highlights.addAll(
                listOf(
                    PoseHighlight.LEFT_THIGH,
                    PoseHighlight.LEFT_SHIN,
                    PoseHighlight.RIGHT_THIGH,
                    PoseHighlight.RIGHT_SHIN
                )
            )
        }

        val hipWidth = abs(lh.position.x - rh.position.x)
        val ankleWidth = abs(la.position.x - ra.position.x)
        if (ankleWidth < hipWidth * 1.0f) {
            feedbackList.add("Stance too narrow")
            highlights.addAll(listOf(PoseHighlight.LEFT_FOOT, PoseHighlight.RIGHT_FOOT))
        } else if (ankleWidth > hipWidth * 1.6f) {
            feedbackList.add("Stance too wide")
            highlights.addAll(listOf(PoseHighlight.LEFT_FOOT, PoseHighlight.RIGHT_FOOT))
        }

        return AnalysisResult(
            exercise = "squat",
            repCount = repCount,
            currentState = currentState,
            formScore = calculateScore(),
            feedback = feedbackList.distinct(),
            angles = angles,
            incorrectSegments = highlights.toSet()
        )
    }

    /** §8.C — knees drifting inward relative to ankles (frontal-plane heuristic). */
    private fun detectKneeValgus(
        leftHip: PoseLandmark,
        leftKnee: PoseLandmark,
        leftAnkle: PoseLandmark,
        rightHip: PoseLandmark,
        rightKnee: PoseLandmark,
        rightAnkle: PoseLandmark
    ): Boolean {
        // Mirror-safe valgus heuristic:
        // inward collapse means knees become closer to each other than ankles.
        val hipWidth = abs(rightHip.position.x - leftHip.position.x).coerceAtLeast(1e-4f)
        val kneeWidth = abs(rightKnee.position.x - leftKnee.position.x)
        val ankleWidth = abs(rightAnkle.position.x - leftAnkle.position.x).coerceAtLeast(1e-4f)
        val kneesInsideFeet = kneeWidth < ankleWidth * 0.86f
        val kneesTooNarrowForHips = kneeWidth < hipWidth * 0.82f
        return kneesInsideFeet && kneesTooNarrowForHips
    }

    private fun footOutwardAngleDeg(ankle: PoseLandmark, footIndex: PoseLandmark): Double {
        val dx = abs(footIndex.position.x - ankle.position.x).toDouble()
        val dy = abs(footIndex.position.y - ankle.position.y).toDouble()
        if (dx < 1e-6 && dy < 1e-6) return 0.0
        // Angle from vertical axis; larger = more outward in camera plane.
        return Math.toDegrees(atan2(dx, dy))
    }

    private fun trunkLeanAngleFromVertical(
        shoulderX: Float,
        shoulderY: Float,
        hipX: Float,
        hipY: Float
    ): Double {
        val dx = abs(shoulderX - hipX).toDouble()
        val dy = abs(shoulderY - hipY).toDouble()
        if (dx < 1e-6 && dy < 1e-6) return 0.0
        return Math.toDegrees(atan2(dx, dy))
    }

    private fun isWristBehindTorso(
        wrist: PoseLandmark,
        shoulder: PoseLandmark,
        hip: PoseLandmark,
        otherShoulder: PoseLandmark,
        otherHip: PoseLandmark
    ): Boolean {
        if (wrist.inFrameLikelihood < 0.35f) return false
        val torsoLeft = minOf(shoulder.position.x, hip.position.x, otherShoulder.position.x, otherHip.position.x) - 0.04f
        val torsoRight = maxOf(shoulder.position.x, hip.position.x, otherShoulder.position.x, otherHip.position.x) + 0.04f
        val torsoTop = minOf(shoulder.position.y, otherShoulder.position.y) + 0.08f
        val torsoBottom = maxOf(hip.position.y, otherHip.position.y) + 0.12f
        val withinTorsoBand =
            wrist.position.x in torsoLeft..torsoRight &&
                wrist.position.y in torsoTop..torsoBottom

        // Z-based "behind" is unstable on many front-camera devices; keep this primarily
        // geometry-driven so users can reliably trigger the warning while testing.
        return withinTorsoBand
    }

    private fun processState(kneeAngle: Double) {
        if (kneeAngle < minKneeAngle) {
            minKneeAngle = kneeAngle
        }

        when (currentState) {
            ExerciseState.STANDING -> {
                if (kneeAngle < 155.0) {
                    currentState = ExerciseState.DESCENDING
                }
            }
            ExerciseState.DESCENDING -> {
                when {
                    kneeAngle < DEPTH_GOOD_MAX -> currentState = ExerciseState.BOTTOM
                    kneeAngle > 170.0 -> {
                        currentState = ExerciseState.STANDING
                        minKneeAngle = 180.0
                    }
                }
            }
            ExerciseState.BOTTOM -> {
                if (kneeAngle > 105.0) {
                    currentState = ExerciseState.ASCENDING
                }
            }
            ExerciseState.ASCENDING -> {
                if (kneeAngle > 165.0) {
                    stateFrameCount++
                    if (stateFrameCount >= minStateFrames) {
                        repCount++
                        currentState = ExerciseState.COMPLETED
                        stateFrameCount = 0
                    }
                } else {
                    stateFrameCount = 0
                }
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.STANDING
                minKneeAngle = 180.0
                stateFrameCount = 0
            }
        }
    }

    private fun calculateScore(): Int {
        var score = 100
        if (feedbackList.contains("Go lower")) score -= 20
        if (feedbackList.contains("Keep back straight")) score -= 30
        if (feedbackList.contains("Knees collapsing inward")) score -= 25
        if (feedbackList.contains("Feet not aligned")) score -= 15
        if (feedbackList.contains("Keep shoulders level")) score -= 20
        if (feedbackList.contains("Keep both feet straighter")) score -= 20
        if (feedbackList.contains("Keep left foot straighter")) score -= 10
        if (feedbackList.contains("Keep right foot straighter")) score -= 10
        if (feedbackList.contains("Don't move arms behind")) score -= 15
        return kotlin.math.max(0, score)
    }
}
