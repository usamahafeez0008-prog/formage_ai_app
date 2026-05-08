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

package com.example.poseexercise.posedetector

import android.content.Context
import android.util.Log
import com.example.poseexercise.data.PostureResult
import com.example.poseexercise.data.ExerciseAnalysisResult
import com.example.poseexercise.data.plan.Plan
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor
import com.example.poseexercise.posedetector.logic.AnalysisResult
import com.example.poseexercise.posedetector.logic.ExerciseLogic
import com.example.poseexercise.posedetector.logic.ExerciseLogicFactory
import com.example.poseexercise.posedetector.logic.FeedbackEngine
import com.example.poseexercise.posedetector.logic.CameraValidator
import com.example.poseexercise.posedetector.logic.toWireName
import com.example.poseexercise.util.VisionProcessorBase
import com.example.poseexercise.viewmodels.CameraXViewModel
import com.example.poseexercise.views.graphic.GraphicOverlay
import com.example.poseexercise.views.graphic.PoseGraphic
import com.google.android.gms.tasks.Task
import com.google.android.odml.image.MlImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** A processor to run pose detector. */
class PoseDetectorProcessor(
    private val context: Context,
    options: PoseDetectorOptionsBase,
    private val showInFrameLikelihood: Boolean,
    private val visualizeZ: Boolean,
    private val rescaleZForVisualization: Boolean,
    private val runClassification: Boolean,
    private val isStreamMode: Boolean,
    private var cameraXViewModel: CameraXViewModel? = null,
    notCompletedExercise: List<Plan>
) : VisionProcessorBase<PoseDetectorProcessor.PoseWithClassification>(context) {

    private val detector: PoseDetector
    private val classificationExecutor: Executor

    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private var exercisesToDetect: List<String>? = null
    private val plannedExerciseNames = notCompletedExercise.map { it.exercise }
    private val plannedExerciseSet = plannedExerciseNames.map { it.lowercase().trim() }.toSet()
    private var exerciseLogic: ExerciseLogic? = null
    private var activeExerciseName: String? = null
    private var manualExerciseName: String? = null
    private val feedbackEngine = FeedbackEngine()
    private val cameraValidator = CameraValidator()

    /** Internal class to hold Pose and classification results. */
    inner class PoseWithClassification(
        val pose: Pose,
        classificationResult: Map<String, PostureResult>
    ) {
        var analysis: AnalysisResult? = null

        init {
            // update live data value
            if (classificationResult.isNotEmpty()) {
                cameraXViewModel?.postureLiveData?.postValue(classificationResult)
            }

            // Keep analysis logic aligned with what classifier currently sees.
            maybeSwitchExerciseLogic(classificationResult)
            
            // Run new analysis logic
            val landmarks = pose.getAllPoseLandmarks()
            if (landmarks.isNotEmpty()) {
                val highConfidenceCount = landmarks.count { it.inFrameLikelihood > 0.5f }
                val trackingReady = highConfidenceCount >= 5

                // Always compute analysis when form logic is active so PoseGraphic receives
                // AnalysisResult. If this stays null, visualize-Z paints the skeleton red/white/blue
                // (ML Kit depth) instead of green/red form feedback.
                analysis = exerciseLogic?.analyze(pose)

                when {
                    exerciseLogic != null && analysis != null -> {
                        val validation = cameraValidator.validateSetup(pose)
                        val combinedFeedback = mutableListOf<String>()
                        combinedFeedback.addAll(analysis!!.feedback)
                        if (!validation.first && validation.second != null) {
                            combinedFeedback.add(validation.second!!)
                        }

                        val feedbackOutput = feedbackEngine.process(combinedFeedback)
                        val wireState =
                            analysis!!.currentState.toWireName(
                                analysis!!.exercise.lowercase().contains("push"),
                            )
                        val score = analysis!!.formScore
                        val finalResult =
                            ExerciseAnalysisResult(
                                exercise = analysis!!.exercise,
                                repCount = analysis!!.repCount,
                                currentState = wireState,
                                formScore = score,
                                repScore = score,
                                feedback = feedbackOutput.messages,
                                primaryFeedback = feedbackOutput.primaryMessage,
                                angles = analysis!!.angles,
                                incorrectSegments =
                                    analysis!!.incorrectSegments.map { seg -> seg.name },
                            )
                        cameraXViewModel?.analysisLiveData?.postValue(finalResult)
                    }
                    trackingReady -> {
                        cameraXViewModel?.analysisLiveData?.postValue(
                            ExerciseAnalysisResult(
                                exercise = "",
                                repCount = 0,
                                currentState = "",
                                formScore = 0,
                                repScore = 0,
                                feedback = emptyList(),
                                primaryFeedback = null,
                                angles = emptyMap(),
                                incorrectSegments = emptyList(),
                            ),
                        )
                    }
                    else -> {
                        cameraXViewModel?.analysisLiveData?.postValue(
                            ExerciseAnalysisResult(
                                exercise = "",
                                repCount = 0,
                                currentState = "",
                                formScore = 0,
                                repScore = 0,
                                feedback = emptyList(),
                                primaryFeedback = null,
                                angles = emptyMap(),
                                incorrectSegments = emptyList(),
                            ),
                        )
                    }
                }
            }
        }
    }

    init {
        detector = PoseDetection.getClient(options)
        classificationExecutor = Executors.newSingleThreadExecutor()
        if (plannedExerciseNames.isNotEmpty()) {
            exercisesToDetect = plannedExerciseNames
            // Initialize the first exercise logic
            val firstExercise = exercisesToDetect?.getOrNull(0)
            if (firstExercise != null) {
                exerciseLogic = ExerciseLogicFactory.getLogic(firstExercise)
                activeExerciseName = firstExercise
                Log.d("PoseDetectorProcessor", "Initialized exercise logic for: $firstExercise")
            }
        }
    }

    private fun maybeSwitchExerciseLogic(classificationResult: Map<String, PostureResult>) {
        if (!manualExerciseName.isNullOrBlank()) return
        if (classificationResult.isEmpty() || exercisesToDetect.isNullOrEmpty()) return

        val top = classificationResult.maxByOrNull { (_, value) -> value.confidence } ?: return
        if (top.value.confidence < 0.45f) return

        val nextExercise = when (top.key) {
            PoseClassifierProcessor.SQUATS_CLASS -> plannedExerciseNames.firstOrNull {
                it.lowercase().contains("squat")
            } ?: "Squat"
            PoseClassifierProcessor.PUSHUPS_CLASS -> plannedExerciseNames.firstOrNull {
                val n = it.lowercase()
                n.contains("push up") || n.contains("pushup")
            } ?: "Push up"
            PoseClassifierProcessor.BICEP_CURL_CLASS -> plannedExerciseNames.firstOrNull {
                val n = it.lowercase()
                n.contains("bicep curl") || n.contains("biceps curl") || n.contains("bicepcurl")
            } ?: "Bicep curl"
            else -> null
        } ?: return

        if (nextExercise.equals(activeExerciseName, ignoreCase = true)) return
        if (!plannedExerciseSet.contains(nextExercise.lowercase().trim())) return

        val newLogic = ExerciseLogicFactory.getLogic(nextExercise) ?: return
        exerciseLogic = newLogic
        activeExerciseName = nextExercise
        Log.d(
            "PoseDetectorProcessor",
            "Switched exercise logic to: $nextExercise via class=${top.key} conf=${top.value.confidence}"
        )
    }

    fun setManualExercise(exerciseName: String?) {
        if (exerciseName.isNullOrBlank()) {
            manualExerciseName = null
            return
        }
        val normalized = exerciseName.lowercase().trim()
        if (!plannedExerciseSet.contains(normalized)) return
        val logic = ExerciseLogicFactory.getLogic(exerciseName) ?: return
        manualExerciseName = exerciseName
        activeExerciseName = exerciseName
        exerciseLogic = logic
        Log.d("PoseDetectorProcessor", "Manual exercise selected: $exerciseName")
    }


    override fun stop() {
        super.stop()
        detector.close()
        cameraXViewModel = null
    }

    override fun detectInImage(image: InputImage): Task<PoseWithClassification> {
        return detector
            .process(image)
            .continueWith(
                classificationExecutor
            ) { task ->
                val pose = task.result
                var classificationResult: Map<String, PostureResult> = HashMap()
                if (runClassification) {
                    if (poseClassifierProcessor == null) {
                        poseClassifierProcessor =
                            PoseClassifierProcessor(
                                context,
                                isStreamMode,
                                exercisesToDetect
                            )
                    }
                    classificationResult = poseClassifierProcessor!!.getPoseResult(pose)

                }
                PoseWithClassification(pose, classificationResult)
            }
    }

    override fun detectInImage(image: MlImage): Task<PoseWithClassification> {
        return detector
            .process(image)
            .continueWith(
                classificationExecutor
            ) { task ->
                val pose = task.result
                var classificationResult: Map<String, PostureResult> = HashMap()
                if (runClassification) {
                    if (poseClassifierProcessor == null) {
                        poseClassifierProcessor =
                            PoseClassifierProcessor(
                                context,
                                isStreamMode,
                                exercisesToDetect
                            )
                    }
                    classificationResult = poseClassifierProcessor!!.getPoseResult(pose)
                }
                PoseWithClassification(pose, classificationResult)
            }
    }

    override fun onSuccess(
        poseWithClassification: PoseWithClassification,
        graphicOverlay: GraphicOverlay
    ) {
        graphicOverlay.add(
            PoseGraphic(
                graphicOverlay,
                poseWithClassification.pose,
                showInFrameLikelihood,
                visualizeZ,
                rescaleZForVisualization,
                poseWithClassification.analysis
            )
        )
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Pose detection failed!", e)
    }

    override fun isMlImageEnabled(context: Context?): Boolean {
        // Use MlImage in Pose Detection by default, change it to OFF to switch to InputImage.
        return true
    }

    companion object {
        private const val TAG = "PoseDetectorProcessor"
    }
}
