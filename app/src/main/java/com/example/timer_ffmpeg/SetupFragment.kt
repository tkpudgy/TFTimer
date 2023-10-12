package com.example.timer_ffmpeg

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.ArrayList

interface RecordingFinishedCallback {
    fun onRecordingFinished()
}

class SetupFragment : Fragment(), RecordingFinishedCallback {

    private lateinit var viewModel: SharedViewModel
    private lateinit var currentVideoPath: String
    private lateinit var averageDifferenceTextView: TextView
    private lateinit var thresholdInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var recordLengthInput: EditText
    private lateinit var calibrationProgressBar: ProgressBar
    private lateinit var statusTextView: TextView
    private lateinit var calibrateButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_setup, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)

        calibrateButton = view.findViewById<Button>(R.id.calibrate_button)
        averageDifferenceTextView = view.findViewById<TextView>(R.id.average_difference_text_view)
        thresholdInput = view.findViewById<EditText>(R.id.thresholdInput)
        delayInput = view.findViewById<EditText>(R.id.delayInput)
        recordLengthInput = view.findViewById<EditText>(R.id.recordLengthInput)

        thresholdInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    viewModel.thresholdValue.value = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // you can leave this method empty if you don't need to use it
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // you can leave this method empty if you don't need to use it
            }
        })
        delayInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    viewModel.thresholdValue.value = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // you can leave this method empty if you don't need to use it
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // you can leave this method empty if you don't need to use it
            }
        })
        recordLengthInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    viewModel.thresholdValue.value = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // you can leave this method empty if you don't need to use it
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // you can leave this method empty if you don't need to use it
            }
        })

        calibrationProgressBar = view.findViewById(R.id.calibrationProgressBar)
        statusTextView = view.findViewById(R.id.statusTextView)

        calibrateButton.setOnClickListener {
            startCalibration()
            calibrateCamera(this)
        }

        return view
    }

    private fun startCalibration() {
        calibrateButton.isEnabled = false
        calibrationProgressBar.visibility = View.VISIBLE
        statusTextView.text = "Calibrating..."
    }

    private fun endCalibration() {
        calibrateButton.isEnabled = true
        calibrationProgressBar.visibility = View.GONE
        statusTextView.text = ""
    }

    private fun calibrateCamera(callback: RecordingFinishedCallback) {

        val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) {
            Log.e("Calibration", "No cameras available")
            return
        }
        val cameraId = cameraIdList[0]  // Use the first available camera
        val mediaRecorder = MediaRecorder()

        try {
            mediaRecorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
                //setVideoSize(videoSize.width, videoSize.height)
                val videoFile = viewModel.createVideoFile()
                currentVideoPath = videoFile.absolutePath
                setOutputFile(currentVideoPath)
                prepare()
            }
            val surfaces = ArrayList<Surface>().apply {
                add(mediaRecorder.surface)
            }
            // Check for camera permission
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // If not granted, request the permission
                requestPermissions(arrayOf(Manifest.permission.CAMERA),
                    MainFragment.REQUEST_CODE_PERMISSIONS
                )
                return
            }
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(mediaRecorder.surface)
                    }
                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(captureRequest.build(), null, null)

                            mediaRecorder.start()
                            Log.d("Calibration", "Recording started")

                            // Stop recording after 10 seconds
                            Handler(Looper.getMainLooper()).postDelayed({
                                mediaRecorder.stop()
                                mediaRecorder.reset()
                                camera.close()
                                Log.d("Calibration", "Recording stopped")
                                callback.onRecordingFinished()
                                endCalibration() // call this after the recording has finished
                            }, 2000)
                            Handler(Looper.getMainLooper()).postDelayed({
                                //Wait until file write is finished
                            }, 1000)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("Calibration", "Capture session configuration failed")
                        }
                    }, null)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.e("Calibration", "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("Calibration", "Camera error: $error")
                }
            }, null)
        } catch (e: Exception) {
            Log.e("Calibration", "Error starting recording", e)
        }
    }

    override fun onRecordingFinished() {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        if (File(currentVideoPath).exists()) {
            mediaMetadataRetriever.setDataSource(currentVideoPath)
            //Toast.makeText(requireContext(), "Video path: $currentVideoPath", Toast.LENGTH_LONG).show()
            val videoLengthInMs = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L

            val frameRes = 50 //in ms
            val numFrames = videoLengthInMs / frameRes // One frame per second
            var previousFrame: Bitmap? = null

            var totalDiffSum = 0
            var totalSquaredDiffSum = 0.0
            var diffCount = 0

            for (i in 0 until numFrames) {
                val frameBitmap = mediaMetadataRetriever.getFrameAtTime(i * frameRes * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frameBitmap != null) {
                    if (previousFrame != null) {
                        // Process the frame
                        val width = frameBitmap.width
                        val height = frameBitmap.height

                        // only look at the center 50x50 pixels, adjust as necessary
                        val diff = viewModel.calculateAverageDifference(previousFrame, frameBitmap, width / 2 - 50, height / 2 - 50, 100, 100)

                        totalDiffSum += diff
                        totalSquaredDiffSum += (diff * diff)
                        diffCount++

                        // Log the difference
                        Log.i("FrameDifference", "Difference for frame at ${i}s: $diff")
                    }
                    previousFrame = frameBitmap.copy(frameBitmap.config, true)
                } else {
                    Log.w("FrameExtractor", "Frame at $i ms is null")
                }
            }

            val averageDiff = if (diffCount > 0) totalDiffSum / diffCount else 0
            val variance = totalSquaredDiffSum / diffCount - (averageDiff * averageDiff)
            val standardDeviation = Math.sqrt(variance)
            val formattedStandardDeviation = String.format("%.2f", standardDeviation)
            val thresholdValue = averageDiff + 5 * standardDeviation

            averageDifferenceTextView.text = "Average Frame Diff: $averageDiff\nStandard Deviation: $formattedStandardDeviation"
            // Inside onRecordingFinished() in SetupFragment
            viewModel.thresholdValue.value = thresholdValue.toInt()
            thresholdInput.setText(thresholdValue.toInt().toString())
        } else {
            Toast.makeText(requireContext(), "Video file not ready", Toast.LENGTH_SHORT).show()
        }
    }
}


