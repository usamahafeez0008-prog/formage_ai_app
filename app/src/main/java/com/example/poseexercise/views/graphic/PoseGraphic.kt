/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.poseexercise.views.graphic

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.poseexercise.views.graphic.GraphicOverlay.Graphic
import com.example.poseexercise.posedetector.logic.AnalysisResult
import com.example.poseexercise.posedetector.logic.PoseHighlight
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.Locale

/** Draw the detected pose in preview. */
class PoseGraphic
internal constructor(
    overlay: GraphicOverlay,
    private val pose: Pose,
    private val showInFrameLikelihood: Boolean,
    private val visualizeZ: Boolean,
    private val rescaleZForVisualization: Boolean,
    private val analysisResult: AnalysisResult? = null
) : Graphic(overlay) {
    private var zMin = java.lang.Float.MAX_VALUE
    private var zMax = java.lang.Float.MIN_VALUE
    private val whitePaint: Paint = Paint()
    private val correctPaint: Paint
    private val incorrectPaint: Paint
    private val textPaint: Paint = Paint()

    init {
        whitePaint.strokeWidth = STROKE_WIDTH
        whitePaint.color = Color.WHITE
        whitePaint.textSize = IN_FRAME_LIKELIHOOD_TEXT_SIZE

        textPaint.color = Color.WHITE
        textPaint.textSize = 40f
        textPaint.style = Paint.Style.FILL

        correctPaint = Paint()
        correctPaint.strokeWidth = STROKE_WIDTH
        correctPaint.color = Color.GREEN

        incorrectPaint = Paint()
        incorrectPaint.strokeWidth = STROKE_WIDTH
        incorrectPaint.color = Color.RED
    }

    private fun linePaint(vararg tags: PoseHighlight): Paint {
        val bad = analysisResult?.incorrectSegments ?: return correctPaint
        return if (tags.any { it in bad }) incorrectPaint else correctPaint
    }

    private fun hasIncorrect(vararg tags: PoseHighlight): Boolean {
        val bad = analysisResult?.incorrectSegments ?: return false
        return tags.any { it in bad }
    }

    override fun draw(canvas: Canvas) {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) {
            return
        }

        for (landmark in landmarks) {
            drawPoint(canvas, landmark, whitePaint)
            if (visualizeZ && rescaleZForVisualization) {
                zMin = kotlin.math.min(zMin, landmark.position3D.z)
                zMax = kotlin.math.max(zMax, landmark.position3D.z)
            }
        }

        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftEyeInner = pose.getPoseLandmark(PoseLandmark.LEFT_EYE_INNER)
        val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
        val leftEyeOuter = pose.getPoseLandmark(PoseLandmark.LEFT_EYE_OUTER)
        val rightEyeInner = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE_INNER)
        val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE)
        val rightEyeOuter = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE_OUTER)
        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
        val leftMouth = pose.getPoseLandmark(PoseLandmark.LEFT_MOUTH)
        val rightMouth = pose.getPoseLandmark(PoseLandmark.RIGHT_MOUTH)

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val leftPinky = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY)
        val rightPinky = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY)
        val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
        val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
        val leftThumb = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)
        val rightThumb = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)
        val leftHeel = pose.getPoseLandmark(PoseLandmark.LEFT_HEEL)
        val rightHeel = pose.getPoseLandmark(PoseLandmark.RIGHT_HEEL)
        val leftFootIndex = pose.getPoseLandmark(PoseLandmark.LEFT_FOOT_INDEX)
        val rightFootIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_FOOT_INDEX)

        val facePaint = linePaint(PoseHighlight.NECK)

        drawLine(canvas, nose, leftEyeInner, facePaint)
        drawLine(canvas, leftEyeInner, leftEye, facePaint)
        drawLine(canvas, leftEye, leftEyeOuter, facePaint)
        drawLine(canvas, leftEyeOuter, leftEar, facePaint)
        drawLine(canvas, nose, rightEyeInner, facePaint)
        drawLine(canvas, rightEyeInner, rightEye, facePaint)
        drawLine(canvas, rightEye, rightEyeOuter, facePaint)
        drawLine(canvas, rightEyeOuter, rightEar, facePaint)
        drawLine(canvas, leftMouth, rightMouth, facePaint)

        drawLine(canvas, leftShoulder, rightShoulder, linePaint(PoseHighlight.MID_SHOULDERS))
        drawLine(canvas, leftHip, rightHip, linePaint(PoseHighlight.MID_HIPS))

        drawLine(canvas, leftShoulder, leftElbow, linePaint(PoseHighlight.LEFT_UPPER_ARM))
        drawLine(canvas, leftElbow, leftWrist, linePaint(PoseHighlight.LEFT_FOREARM))
        drawLine(canvas, leftShoulder, leftHip, linePaint(PoseHighlight.LEFT_TRUNK))
        drawLine(canvas, leftHip, leftKnee, linePaint(PoseHighlight.LEFT_THIGH))
        drawLine(canvas, leftKnee, leftAnkle, linePaint(PoseHighlight.LEFT_SHIN))
        drawLine(canvas, leftWrist, leftThumb, linePaint(PoseHighlight.LEFT_FOREARM))
        drawLine(canvas, leftWrist, leftPinky, linePaint(PoseHighlight.LEFT_FOREARM))
        drawLine(canvas, leftWrist, leftIndex, linePaint(PoseHighlight.LEFT_FOREARM))
        drawLine(canvas, leftIndex, leftPinky, linePaint(PoseHighlight.LEFT_FOREARM))
        drawLine(canvas, leftAnkle, leftHeel, linePaint(PoseHighlight.LEFT_FOOT))
        drawLine(canvas, leftHeel, leftFootIndex, linePaint(PoseHighlight.LEFT_FOOT))

        drawLine(canvas, rightShoulder, rightElbow, linePaint(PoseHighlight.RIGHT_UPPER_ARM))
        drawLine(canvas, rightElbow, rightWrist, linePaint(PoseHighlight.RIGHT_FOREARM))
        drawLine(canvas, rightShoulder, rightHip, linePaint(PoseHighlight.RIGHT_TRUNK))
        drawLine(canvas, rightHip, rightKnee, linePaint(PoseHighlight.RIGHT_THIGH))
        drawLine(canvas, rightKnee, rightAnkle, linePaint(PoseHighlight.RIGHT_SHIN))
        drawLine(canvas, rightWrist, rightThumb, linePaint(PoseHighlight.RIGHT_FOREARM))
        drawLine(canvas, rightWrist, rightPinky, linePaint(PoseHighlight.RIGHT_FOREARM))
        drawLine(canvas, rightWrist, rightIndex, linePaint(PoseHighlight.RIGHT_FOREARM))
        drawLine(canvas, rightIndex, rightPinky, linePaint(PoseHighlight.RIGHT_FOREARM))
        drawLine(canvas, rightAnkle, rightHeel, linePaint(PoseHighlight.RIGHT_FOOT))
        drawLine(canvas, rightHeel, rightFootIndex, linePaint(PoseHighlight.RIGHT_FOOT))

        analysisResult?.let { result ->
            when (result.exercise.lowercase()) {
                "squat" -> {
                    result.angles["knee_smooth"]?.let { angle ->
                        val knee = leftKnee ?: rightKnee
                        knee?.let {
                            val depthBad = hasIncorrect(
                                PoseHighlight.LEFT_THIGH,
                                PoseHighlight.RIGHT_THIGH,
                                PoseHighlight.LEFT_SHIN,
                                PoseHighlight.RIGHT_SHIN,
                                PoseHighlight.LEFT_FOOT,
                                PoseHighlight.RIGHT_FOOT,
                            )
                            val color = if (depthBad) Color.RED else Color.GREEN
                            drawText(canvas, "${angle.toInt()}°", it, color)
                        }
                    }
                    result.angles["back_angle"]?.let { angle ->
                        val hip = leftHip ?: rightHip
                        hip?.let {
                            val backBad = hasIncorrect(
                                PoseHighlight.LEFT_TRUNK,
                                PoseHighlight.RIGHT_TRUNK,
                                PoseHighlight.MID_SHOULDERS,
                                PoseHighlight.MID_HIPS,
                            )
                            val color = if (backBad) Color.RED else Color.GREEN
                            drawText(canvas, "${angle.toInt()}°", it, color)
                        }
                    }
                }
                "pushup", "push-up" -> {
                    result.angles["elbow_smooth"]?.let { angle ->
                        val elbow = leftElbow ?: rightElbow
                        elbow?.let {
                            val armsBad = hasIncorrect(
                                PoseHighlight.LEFT_UPPER_ARM,
                                PoseHighlight.RIGHT_UPPER_ARM,
                                PoseHighlight.LEFT_FOREARM,
                                PoseHighlight.RIGHT_FOREARM,
                            )
                            val color = if (armsBad) Color.RED else Color.GREEN
                            drawText(canvas, "${angle.toInt()}°", it, color)
                        }
                    }
                    result.angles["body_alignment"]?.let { angle ->
                        val hip = leftHip ?: rightHip
                        hip?.let {
                            val bodyBad = hasIncorrect(
                                PoseHighlight.LEFT_TRUNK,
                                PoseHighlight.RIGHT_TRUNK,
                                PoseHighlight.MID_SHOULDERS,
                                PoseHighlight.MID_HIPS,
                                PoseHighlight.LEFT_THIGH,
                                PoseHighlight.RIGHT_THIGH,
                                PoseHighlight.LEFT_SHIN,
                                PoseHighlight.RIGHT_SHIN,
                                PoseHighlight.LEFT_FOOT,
                                PoseHighlight.RIGHT_FOOT,
                            )
                            val color = if (bodyBad) Color.RED else Color.GREEN
                            drawText(canvas, "${angle.toInt()}°", it, color)
                        }
                    }
                }
                else -> {}
            }
        }

        if (showInFrameLikelihood) {
            for (landmark in landmarks) {
                canvas.drawText(
                    String.format(Locale.US, "%.2f", landmark.inFrameLikelihood),
                    translateX(landmark.position.x),
                    translateY(landmark.position.y),
                    whitePaint
                )
            }
        }
    }

    private fun drawText(canvas: Canvas, text: String, landmark: PoseLandmark, color: Int) {
        textPaint.color = color
        canvas.drawText(
            text,
            translateX(landmark.position.x) + 30f,
            translateY(landmark.position.y),
            textPaint
        )
    }

    private fun drawPoint(canvas: Canvas, landmark: PoseLandmark, paint: Paint) {
        val point = landmark.position3D
        updatePaintColorByZValue(
            paint,
            canvas,
            visualizeZ && analysisResult == null,
            rescaleZForVisualization,
            point.z,
            zMin,
            zMax
        )
        canvas.drawCircle(translateX(point.x), translateY(point.y), DOT_RADIUS, paint)
    }

    private fun drawLine(
        canvas: Canvas,
        startLandmark: PoseLandmark?,
        endLandmark: PoseLandmark?,
        paint: Paint
    ) {
        val start = startLandmark!!.position3D
        val end = endLandmark!!.position3D

        val avgZInImagePixel = (start.z + end.z) / 2
        updatePaintColorByZValue(
            paint,
            canvas,
            visualizeZ && analysisResult == null,
            rescaleZForVisualization,
            avgZInImagePixel,
            zMin,
            zMax
        )

        canvas.drawLine(
            translateX(start.x),
            translateY(start.y),
            translateX(end.x),
            translateY(end.y),
            paint
        )
    }

    companion object {
        private const val DOT_RADIUS = 6.0f
        private const val IN_FRAME_LIKELIHOOD_TEXT_SIZE = 0.0f
        private const val STROKE_WIDTH = 5.0f
    }
}
