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
    private var exerciseLogic: ExerciseLogic? = null
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
            
            // Run new analysis logic
            val landmarks = pose.getAllPoseLandmarks()
            if (landmarks.isNotEmpty()) {
                // Count high-confidence landmarks to prevent ghost detection
                val highConfidenceCount = landmarks.count { it.inFrameLikelihood > 0.5f }
                
                if (highConfidenceCount >= 6) {
                    val validation = cameraValidator.validateSetup(pose)
                    analysis = exerciseLogic?.analyze(pose)
                    
                    val combinedFeedback = mutableListOf<String>()
                    if (analysis != null) {
                        combinedFeedback.addAll(analysis!!.feedback)
                    }
                    if (!validation.first && validation.second != null) {
                        combinedFeedback.add(validation.second!!)
                    }
                    
                    val filteredFeedback = feedbackEngine.getFilteredFeedback(combinedFeedback)
                    val finalResult = ExerciseAnalysisResult(
                        analysis?.exercise ?: "",
                        analysis?.repCount ?: 0,
                        analysis?.currentState?.name ?: "",
                        analysis?.formScore ?: 0,
                        filteredFeedback,
                        analysis?.angles ?: emptyMap()
                    )
                    cameraXViewModel?.analysisLiveData?.postValue(finalResult)
                } else {
                    // No person detected with enough confidence
                    val emptyResult = ExerciseAnalysisResult(
                        "", 0, "", 0, null, emptyMap()
                    )
                    cameraXViewModel?.analysisLiveData?.postValue(emptyResult)
                }
            }
        }
    }

    init {
        detector = PoseDetection.getClient(options)
        classificationExecutor = Executors.newSingleThreadExecutor()
        if (notCompletedExercise.isNotEmpty()) {
            exercisesToDetect = notCompletedExercise.map { plan -> plan.exercise }
            // Initialize the first exercise logic
            val firstExercise = exercisesToDetect?.getOrNull(0)
            if (firstExercise != null) {
                exerciseLogic = ExerciseLogicFactory.getLogic(firstExercise)
                Log.d("PoseDetectorProcessor", "Initialized exercise logic for: $firstExercise")
            }
        }
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
