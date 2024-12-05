package com.example.faceandposedetection.common

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.faceandposedetection.common.graphic.FaceGraphic
import com.example.faceandposedetection.common.graphic.PoseGraphic
import com.example.faceandposedetection.common.poseclassification.PoseClassifierProcessor
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class UnifiedDetectorProcessor(
    private val context: Context,
    private val faceDetectorOptions: FaceDetectorOptions? = null,
    private val poseDetectorOptions: PoseDetectorOptionsBase? = null,
    private val showInFrameLikelihood: Boolean = false,
    private val visualizeZ: Boolean = false,
    private val rescaleZForVisualization: Boolean = false,
    private val runPoseClassification: Boolean = false,
    private val isStreamMode: Boolean = false
) : VisionProcessorBase<UnifiedDetectorProcessor.DetectionResult>(context) {

    private val faceDetector: FaceDetector
    private val poseDetector: PoseDetector
    private val classificationExecutor: Executor = Executors.newSingleThreadExecutor()
    private var poseClassifierProcessor: PoseClassifierProcessor? = null

    init {
        faceDetector = FaceDetection.getClient(
            faceDetectorOptions ?: FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()
        )
        poseDetector = PoseDetection.getClient(
            poseDetectorOptions ?: PoseDetectorOptions.Builder().build()
        )
    }

    data class DetectionResult(
        val faces: List<Face>?,
        val poseWithClassification: PoseWithClassification?
    )

    data class PoseWithClassification(
        val pose: Pose,
        val classificationResult: List<String>
    )

    override fun stop() {
        super.stop()
        faceDetector.close()
        poseDetector.close()
    }

    override fun detectInImage(image: InputImage): Task<DetectionResult> {
        val faceTask = faceDetector.process(image)
        val poseTask = poseDetector.process(image).continueWith(classificationExecutor) { task ->
            val pose = task.getResult()
            val classificationResult = if (runPoseClassification) {
                if (poseClassifierProcessor == null) {
                    poseClassifierProcessor =
                        PoseClassifierProcessor(
                            context,
                            isStreamMode
                        )
                }
                poseClassifierProcessor!!.getPoseResult(pose)
            } else {
                emptyList()
            }
            PoseWithClassification(pose, classificationResult)
        }

        return Tasks.whenAllComplete(faceTask, poseTask).continueWith { tasks ->
            val faces = (tasks.result[0].result as? List<Face>)
            val poseWithClassification = (tasks.result[1].result as? PoseWithClassification)
            DetectionResult(faces, poseWithClassification)
        }
    }

    override fun onSuccess(
        result: DetectionResult,
        graphicOverlay: GraphicOverlay
    ) {

        result.poseWithClassification?.let { poseResult ->
            graphicOverlay.add(
                PoseGraphic(
                    graphicOverlay,
                    poseResult.pose,
                    showInFrameLikelihood,
                    visualizeZ,
                    rescaleZForVisualization,
                    poseResult.classificationResult
                )
            )
        }
        result.faces?.forEach { face ->
            graphicOverlay.add(FaceGraphic(graphicOverlay, face))
            logFaceDetails(face)
        }
    }

    @SuppressLint("LongLogTag")
    override fun onFailure(e: Exception) {
        Log.e(TAG, "Unified detection failed!", e)
    }

    @SuppressLint("LongLogTag")
    private fun logFaceDetails(face: Face) {

        val smileProb = face.smilingProbability
        if (smileProb != null) {
            if (smileProb > 0.5) {
                println("The person is smiling! Probability: $smileProb")
                Log.e("FaceExpression", "FaceExpression : $smileProb")
            } else {
                println("The person is not smiling. Probability: $smileProb")
                Log.e("FaceExpression", "FaceExpression : $smileProb")
            }
        }

        Log.v(TAG, "Face bounding box: ${face.boundingBox.flattenToString()}")
        Log.v(TAG, "Euler angles: X=${face.headEulerAngleX}, Y=${face.headEulerAngleY}, Z=${face.headEulerAngleZ}")
        Log.v(TAG, "Smiling probability: ${face.smilingProbability}")
        Log.v(TAG, "Left eye open probability: ${face.leftEyeOpenProbability}")
        Log.v(TAG, "Right eye open probability: ${face.rightEyeOpenProbability}")
        Log.v(TAG, "Tracking ID: ${face.trackingId}")


    }



    companion object {
        private const val TAG = "UnifiedDetectorProcessor"
    }
}
