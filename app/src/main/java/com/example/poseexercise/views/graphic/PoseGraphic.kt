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
    private val leftPaint: Paint
    private val rightPaint: Paint
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
        
        leftPaint = Paint()
        leftPaint.strokeWidth = STROKE_WIDTH
        leftPaint.color = Color.GREEN
        rightPaint = Paint()
        rightPaint.strokeWidth = STROKE_WIDTH
        rightPaint.color = Color.YELLOW

        correctPaint = Paint()
        correctPaint.strokeWidth = STROKE_WIDTH
        correctPaint.color = Color.GREEN

        incorrectPaint = Paint()
        incorrectPaint.strokeWidth = STROKE_WIDTH
        incorrectPaint.color = Color.RED
    }

    override fun draw(canvas: Canvas) {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) {
            return
        }

        // Draw all the points
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

        // Determine paints based on feedback
        var leftLegPaint = correctPaint
        var rightLegPaint = rightPaint // Keep right side yellow for contrast
        var leftArmPaint = correctPaint
        var rightArmPaint = rightPaint
        var trunkPaint = correctPaint

        analysisResult?.let { result ->
            when (result.exercise.lowercase()) {
                "squat" -> {
                    if (result.feedback.contains("Go lower")) {
                        leftLegPaint = incorrectPaint
                        rightLegPaint = incorrectPaint
                    }
                    if (result.feedback.contains("Keep back straight")) {
                        trunkPaint = incorrectPaint
                    }
                }
                "pushup", "push-up" -> {
                    if (result.feedback.contains("Keep body straight")) {
                        trunkPaint = incorrectPaint
                        leftLegPaint = incorrectPaint
                        rightLegPaint = incorrectPaint
                    }
                }
                else -> {}
            }
        }

        // Face
        drawLine(canvas, nose, leftEyeInner, whitePaint)
        drawLine(canvas, leftEyeInner, leftEye, whitePaint)
        drawLine(canvas, leftEye, leftEyeOuter, whitePaint)
        drawLine(canvas, leftEyeOuter, leftEar, whitePaint)
        drawLine(canvas, nose, rightEyeInner, whitePaint)
        drawLine(canvas, rightEyeInner, rightEye, whitePaint)
        drawLine(canvas, rightEye, rightEyeOuter, whitePaint)
        drawLine(canvas, rightEyeOuter, rightEar, whitePaint)
        drawLine(canvas, leftMouth, rightMouth, whitePaint)

        drawLine(canvas, leftShoulder, rightShoulder, trunkPaint)
        drawLine(canvas, leftHip, rightHip, trunkPaint)

        // Left body
        drawLine(canvas, leftShoulder, leftElbow, leftArmPaint)
        drawLine(canvas, leftElbow, leftWrist, leftArmPaint)
        drawLine(canvas, leftShoulder, leftHip, trunkPaint)
        drawLine(canvas, leftHip, leftKnee, leftLegPaint)
        drawLine(canvas, leftKnee, leftAnkle, leftLegPaint)
        drawLine(canvas, leftWrist, leftThumb, leftArmPaint)
        drawLine(canvas, leftWrist, leftPinky, leftArmPaint)
        drawLine(canvas, leftWrist, leftIndex, leftArmPaint)
        drawLine(canvas, leftIndex, leftPinky, leftArmPaint)
        drawLine(canvas, leftAnkle, leftHeel, leftLegPaint)
        drawLine(canvas, leftHeel, leftFootIndex, leftLegPaint)

        // Right body
        drawLine(canvas, rightShoulder, rightElbow, rightArmPaint)
        drawLine(canvas, rightElbow, rightWrist, rightArmPaint)
        drawLine(canvas, rightShoulder, rightHip, trunkPaint)
        drawLine(canvas, rightHip, rightKnee, rightLegPaint)
        drawLine(canvas, rightKnee, rightAnkle, rightLegPaint)
        drawLine(canvas, rightWrist, rightThumb, rightArmPaint)
        drawLine(canvas, rightWrist, rightPinky, rightArmPaint)
        drawLine(canvas, rightWrist, rightIndex, rightArmPaint)
        drawLine(canvas, rightIndex, rightPinky, rightArmPaint)
        drawLine(canvas, rightAnkle, rightHeel, rightLegPaint)
        drawLine(canvas, rightHeel, rightFootIndex, rightLegPaint)

        // Draw angles if available
        analysisResult?.let { result ->
            when (result.exercise.lowercase()) {
                "squat" -> {
                    result.angles["knee_smooth"]?.let { angle ->
                        val knee = leftKnee ?: rightKnee
                        knee?.let {
                            val color = if (result.feedback.contains("Go lower")) Color.RED else Color.GREEN
                            drawText(canvas, "${angle.toInt()}°", it, color)
                        }
                    }
                    result.angles["back_angle"]?.let { angle ->
                        val hip = leftHip ?: rightHip
                        hip?.let {
                            val color = if (result.feedback.contains("Keep back straight")) Color.RED else Color.GREEN
                            drawText(canvas, "${angle.toInt()}°", it, color)
                        }
                    }
                }
                "pushup", "push-up" -> {
                    result.angles["elbow_smooth"]?.let { angle ->
                        val elbow = leftElbow ?: rightElbow
                        elbow?.let { drawText(canvas, "${angle.toInt()}°", it, Color.GREEN) }
                    }
                    result.angles["body_alignment"]?.let { angle ->
                        val hip = leftHip ?: rightHip
                        hip?.let {
                            val color = if (result.feedback.contains("Keep body straight")) Color.RED else Color.GREEN
                            drawText(canvas, "${angle.toInt()}°", it, color)
                        }
                    }
                }
                else -> {}
            }
        }

        // Draw inFrameLikelihood for all points
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

        // Gets average z for the current body line
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
