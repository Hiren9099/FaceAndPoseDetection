package com.example.faceandposedetection.presentation.view.fragment

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import com.example.faceandposedetection.R
import com.example.faceandposedetection.common.CameraSource
import com.example.faceandposedetection.common.CameraSourcePreview
import com.example.faceandposedetection.common.GraphicOverlay
import com.example.faceandposedetection.common.PreferenceUtils
import com.example.faceandposedetection.common.UnifiedDetectorProcessor
import com.example.faceandposedetection.databinding.FragmentHomeBinding
import com.google.mlkit.common.model.LocalModel
import java.io.IOException


class HomeFragment : Fragment() ,CompoundButton.OnCheckedChangeListener{

    private var cameraSource: CameraSource? = null
    private lateinit var binding: FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    fun init() {
        binding.facingSwitch.setOnCheckedChangeListener(this)

        createCameraSource()
        startCameraSource()
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(requireActivity(), binding.graphicOverlay)
        }
        try {
            val poseDetectorOptions = PreferenceUtils.getPoseDetectorOptionsForLivePreview(requireActivity())
            Log.i(TAG, "Using Pose Detector with options $poseDetectorOptions")
            val shouldShowInFrameLikelihood = PreferenceUtils.shouldShowPoseDetectionInFrameLikelihoodLivePreview(requireActivity())
            val visualizeZ = PreferenceUtils.shouldPoseDetectionVisualizeZ(requireActivity())
            val rescaleZ = PreferenceUtils.shouldPoseDetectionRescaleZForVisualization(requireActivity())
            val runClassification = PreferenceUtils.shouldPoseDetectionRunClassification(requireActivity())
            val faceDetectorOptions = PreferenceUtils.getFaceDetectorOptions(requireActivity())
            cameraSource!!.setMachineLearningFrameProcessor(
                UnifiedDetectorProcessor(
                    requireActivity(),
                    faceDetectorOptions,
                    poseDetectorOptions,
                    shouldShowInFrameLikelihood,
                    visualizeZ,
                    rescaleZ,
                    runClassification,
                    isStreamMode =  true
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                requireActivity(),
                "Can not create image processor: " + e.message,
                Toast.LENGTH_LONG
            )
                .show()
        }
    }



    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        createCameraSource()
        startCameraSource()
    }

    /** Stops the camera. */
    override fun onPause() {
        super.onPause()
        binding.previewView?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cameraSource != null) {
            cameraSource?.release()
        }
    }

    private fun startCameraSource() {
        if (cameraSource != null) {
            try {
                if (binding.previewView == null) {
                    Log.d(TAG, "resume: Preview is null")
                }
                if (binding.graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null")
                }
                binding.previewView!!.start(cameraSource, binding.graphicOverlay)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                cameraSource!!.release()
                cameraSource = null
            }
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        Log.d(TAG, "Set facing")
        if (cameraSource != null) {
            if (isChecked) {
                cameraSource?.setFacing(CameraSource.CAMERA_FACING_FRONT)
            } else {
                cameraSource?.setFacing(CameraSource.CAMERA_FACING_BACK)
            }
        }
        binding.previewView?.stop()
        startCameraSource()
    }
}