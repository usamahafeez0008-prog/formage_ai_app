package com.example.poseexercise.posedetector.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Push-up analysis — requirements §6 push-up states, §8 Exercise 2, §10 scoring.
 */
class PushupLogic : ExerciseLogic {
    private var repCount = 0
    private var currentState = ExerciseState.STANDING // mapped to plank_position in wire format
    private var minElbowAngle = 180.0
    private val feedbackList = mutableListOf<String>()
    private val highlights = mutableSetOf<PoseHighlight>()
    private var smoothedElbowAngle = -1.0
    private val smoothingFactor = 0.15
    private var initialized = false
    private var stateFrameCount = 0
    private val minStateFrames = 3

    companion object {
        private const val LIKELIHOOD = 0.55f

        /** §8.2.A bottom — elbow angle &lt; 90°. */
        private const val ELBOW_BOTTOM_MAX = 90.0

        /** §8.2.B body line — shoulder→hip→ankle should stay nearly straight. */
        private const val BODY_LINE_BAD_MAX = 155.0
    }

    override fun analyze(pose: Pose): AnalysisResult {
        val landmarks = pose.getAllPoseLandmarks()
        if (landmarks.isEmpty()) {
            return AnalysisResult(
                "pushup", repCount, currentState, 0,
                emptyList(), emptyMap(), emptySet()
            )
        }

        feedbackList.clear()
        highlights.clear()

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)

        val angles = mutableMapOf<String, Double>()

        val armsOk =
            leftShoulder != null && leftElbow != null && leftWrist != null &&
                rightShoulder != null && rightElbow != null && rightWrist != null &&
                leftShoulder.inFrameLikelihood > LIKELIHOOD &&
                leftElbow.inFrameLikelihood > LIKELIHOOD &&
                leftWrist.inFrameLikelihood > LIKELIHOOD &&
                rightShoulder.inFrameLikelihood > LIKELIHOOD &&
                rightElbow.inFrameLikelihood > LIKELIHOOD &&
                rightWrist.inFrameLikelihood > LIKELIHOOD

        if (!armsOk) {
            return AnalysisResult(
                "pushup", repCount, currentState, 0,
                feedbackList.toList(), angles, highlights.toSet()
            )
        }

        val ls = leftShoulder!!
        val le = leftElbow!!
        val lw = leftWrist!!
        val rs = rightShoulder!!
        val re = rightElbow!!
        val rw = rightWrist!!

        val leftElbowAngle =
            JointAngleCalculator.elbowAngle(ls, le, lw)
        val rightElbowAngle =
            JointAngleCalculator.elbowAngle(rs, re, rw)
        val rawElbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0

        if (!initialized) {
            smoothedElbowAngle = rawElbowAngle
            initialized = true
        } else {
            smoothedElbowAngle =
                rawElbowAngle * smoothingFactor + smoothedElbowAngle * (1 - smoothingFactor)
        }

        angles["left_elbow"] = leftElbowAngle
        angles["right_elbow"] = rightElbowAngle
        angles["elbow_smooth"] = smoothedElbowAngle

        if (leftHip != null && rightHip != null) {
            val lhip = leftHip!!
            val rhip = rightHip!!
            angles["left_shoulder"] =
                JointAngleCalculator.shoulderAngle(le, ls, lhip)
            angles["right_shoulder"] =
                JointAngleCalculator.shoulderAngle(re, rs, rhip)
        }

        if (leftHip != null && leftAnkle != null && rightHip != null && rightAnkle != null) {
            val lhip = leftHip!!
            val rhip = rightHip!!
            val lank = leftAnkle!!
            val rank = rightAnkle!!
            val leftBodyAngle =
                JointAngleCalculator.backAngle(ls, lhip, lank)
            val rightBodyAngle =
                JointAngleCalculator.backAngle(rs, rhip, rank)
            val avgBodyAngle = (leftBodyAngle + rightBodyAngle) / 2.0
            angles["body_alignment"] = avgBodyAngle

            if (avgBodyAngle < BODY_LINE_BAD_MAX) {
                feedbackList.add("Keep body straight")
                highlights.addAll(
                    listOf(
                        PoseHighlight.LEFT_TRUNK,
                        PoseHighlight.RIGHT_TRUNK,
                        PoseHighlight.MID_HIPS,
                        PoseHighlight.MID_SHOULDERS,
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

        if (nose != null && leftHip != null && rightHip != null) {
            val n = nose!!
            val lhip = leftHip!!
            val rhip = rightHip!!
            val shoulderMidY = (ls.position.y + rs.position.y) / 2f
            val neckLeft =
                JointAngleCalculator.neckAngleAtShoulder(lhip, ls, n)
            val neckRight =
                JointAngleCalculator.neckAngleAtShoulder(rhip, rs, n)
            angles["neck_left"] = neckLeft
            angles["neck_right"] = neckRight

            val droopByY = n.position.y > shoulderMidY + 0.045f
            val tuckForward = neckLeft < 52.0 || neckRight < 52.0
            if (droopByY || tuckForward) {
                feedbackList.add("Keep neck neutral")
                highlights.add(PoseHighlight.NECK)
            }
        }

        processState(smoothedElbowAngle)

        return AnalysisResult(
            exercise = "pushup",
            repCount = repCount,
            currentState = currentState,
            formScore = calculateScore(),
            feedback = feedbackList.distinct(),
            angles = angles,
            incorrectSegments = highlights.toSet()
        )
    }

    private fun processState(elbowAngle: Double) {
        if (elbowAngle < minElbowAngle) {
            minElbowAngle = elbowAngle
        }

        when (currentState) {
            ExerciseState.STANDING -> {
                if (elbowAngle < 140.0) {
                    currentState = ExerciseState.DESCENDING
                }
            }
            ExerciseState.DESCENDING -> {
                when {
                    elbowAngle < ELBOW_BOTTOM_MAX -> currentState = ExerciseState.BOTTOM
                    elbowAngle > 165.0 -> {
                        currentState = ExerciseState.STANDING
                        minElbowAngle = 180.0
                    }
                }
            }
            ExerciseState.BOTTOM -> {
                if (elbowAngle > 100.0) {
                    currentState = ExerciseState.ASCENDING
                }
            }
            ExerciseState.ASCENDING -> {
                if (elbowAngle > 160.0) {
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
                minElbowAngle = 180.0
                stateFrameCount = 0
            }
        }
    }

    private fun calculateScore(): Int {
        var score = 100
        if (feedbackList.contains("Keep body straight")) score -= 25
        if (feedbackList.contains("Keep neck neutral")) score -= 15
        return kotlin.math.max(0, score)
    }
}
