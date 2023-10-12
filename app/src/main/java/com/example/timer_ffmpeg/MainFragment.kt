package com.example.timer_ffmpeg

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import android.media.MediaPlayer


interface RunRecordingFinishedCallback {
    fun processRunData()
}

class MainFragment : Fragment(), RunRecordingFinishedCallback  {

    private var startTime = 0L
    private var timeInMilliseconds = 0L
    private var timeSwapBuff = 0L
    private var updateTime = 0L

    private lateinit var currentVideoPath: String
    private lateinit var timerHandler: Handler
    companion object {
        internal const val REQUEST_CODE_PERMISSIONS = 10
    }
    private val MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1
    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
    private lateinit var result: TextView

    private lateinit var startButton: Button
    private lateinit var recordsTable: TableLayout
    private lateinit var resetButton: Button
    private var intervalCounter: Int = 1

    private lateinit var viewModel: SharedViewModel


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_main, container, false)
        timerHandler = Handler()

        startButton = view?.findViewById<Button>(R.id.start_stop_button) ?: return view

        result = view?.findViewById<TextView>(R.id.timer_text) ?: TextView(requireContext())
        viewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)

        startButton?.setOnClickListener {
            if (startButton.text == "Start") {
                startButton.isEnabled = false // Disable the start button
                result.text = "Go to the starting line"

                // Get user delay
                var userDelay = viewModel.delayInput.value ?: 0
                if (userDelay > 60) userDelay = 60

                // After user delay
                Handler(Looper.getMainLooper()).postDelayed({
                    val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
                    result.text = "On your marks"

                    // After 4 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
                        result.text = "Set"

                        // After random delay between 1 and 4 seconds
                        val randomDelay = (1000..4000).random()
                        Handler(Looper.getMainLooper()).postDelayed({
                            //toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
                            val mediaPlayer = MediaPlayer.create(requireContext(), R.raw.sound_starter_gun)
                            mediaPlayer.start()
                            startRecording(this)  // <-- Start recording her
                            result.text = "Go!"
                            startTime = SystemClock.uptimeMillis()
                            timerHandler.postDelayed(updateTimerThread, 0)
                            mediaPlayer.setOnCompletionListener {
                                it.release()
                            }

                        }, randomDelay.toLong())

                    }, 4000)
                }, userDelay.toLong() * 1000)
            }
        }

        recordsTable = view?.findViewById<TableLayout>(R.id.recordsTable) ?: TableLayout(requireContext())
        resetButton = view?.findViewById<Button>(R.id.reset_button) ?: Button(requireContext())
        resetButton.setOnClickListener {
            resetTable()
        }
        return view
    }

    fun saveBitmapToFile(bitmap: Bitmap, filename: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename)
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
    }

    private fun startRecording(callback: RunRecordingFinishedCallback) {
        var recordLen = viewModel.recordLengthInput.value ?: 5
        val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) {
            Log.e("MainActivity", "No cameras available")
            return
        }
        val cameraId = cameraIdList[0]  // Use the first available camera
        val mediaRecorder = MediaRecorder()

        try {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

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
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
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
                            Log.d("MainActivity", "Recording started")

                            // Stop recording after 10 seconds
                            Handler(Looper.getMainLooper()).postDelayed({
                                mediaRecorder.stop()
                                mediaRecorder.reset()
                                camera.close()
                                Log.d("MainActivity", "Recording stopped")
                                startButton.isEnabled = true  // Re-enable the start button
                                callback.processRunData()
                            }, recordLen.toLong() * 1000)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("MainActivity", "Capture session configuration failed")
                        }
                    }, null)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.e("MainActivity", "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("MainActivity", "Camera error: $error")
                }
            }, null)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting recording", e)
        }
    }
    override fun processRunData() {
        val ImageView = view?.findViewById<ImageView>(R.id.frameImageView)
        val mediaMetadataRetriever = MediaMetadataRetriever()
        var frameTimeMs: Long? = null
        if (File(currentVideoPath).exists()) {
            result.text = "Processing..."
            //timeSwapBuff += timeInMilliseconds
            timerHandler.removeCallbacks(updateTimerThread)
            /*
            // Replay the recorded video
            val videoView = findViewById<VideoView>(R.id.videoView)
            val mediaController = MediaController(this)
            videoView.apply {
                setVideoURI(Uri.parse(currentVideoPath))
                setMediaController(mediaController)
                start()
            }*/
            mediaMetadataRetriever.setDataSource(currentVideoPath)

            val videoLengthInMs = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L

            val frameRes = 50 //in
            val numFrames = videoLengthInMs / frameRes // One frame per second
            var previousFrame: Bitmap? = null
            for (i in 0 until numFrames) {
                val frameBitmap = mediaMetadataRetriever.getFrameAtTime(i * frameRes * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frameBitmap != null) {
                    if (previousFrame != null) {
                        if (previousFrame.sameAs(frameBitmap)) {
                            Log.w("FrameExtractor", "Frame at $i ms is the same as the previous frame")
                        } else {
                            //saveBitmapToFile(frameBitmap, "frame$i.png")
                        }
                    } else {
                        Log.w("FrameExtractor", "Frame at $i ms is null")
                    }
                    // Process the frame
                    val width = frameBitmap.width
                    val height = frameBitmap.height
                    // convert the bitmap to grayscale
                    //val grayScaleBitmap = toGrayscale(frameBitmap)

                    if (previousFrame != null) {
                        // only look at the center 50x50 pixels, adjust as necessary
                        val diff = viewModel.calculateAverageDifference(previousFrame, frameBitmap, width / 2 - 50, height / 2 - 50, 100, 100)
                        val threshold = viewModel.thresholdValue.value ?: 70  // Using a default value of 0 if null
                        if (diff > threshold) {
                            frameTimeMs = i * frameRes
                            val frameDrawable = BitmapDrawable(resources, frameBitmap)
                            ImageView?.setImageDrawable(frameDrawable)
                            break
                        }
                        // Log the difference
                        Log.i("FrameDifference", "Difference for frame at ${i}s: $diff")
                    }
                    previousFrame = frameBitmap.copy(frameBitmap.config, true)
                } else {
                    Log.w("FrameExtractor", "Frame at $i ms is null")
                }
            }

            if (frameTimeMs != null) {
                Toast.makeText(
                    requireContext(),
                    "Significant frame change at: ${frameTimeMs / 1000.0} seconds",
                    Toast.LENGTH_SHORT
                ).show()
                result.text = (frameTimeMs / 1000.0).toString()
            } else {
                Toast.makeText(
                    requireContext(),
                    "No significant frame change detected.",
                    Toast.LENGTH_SHORT
                ).show()
                result.text = "N/A"
            }
        }else {
            Toast.makeText(requireContext(), "Video file not ready", Toast.LENGTH_SHORT).show()
        }

        // After processing the run data, update the table
        val newRecord = (frameTimeMs?.div(1000.0) ?: "N/A").toString()
        updateTable(intervalCounter++, newRecord)
    }

    private fun updateTable(interval: Int, record: String) {
        // If there are more than 8 intervals, rewrite the last entry
        val rowIndex = if (interval > 8) 7 else interval - 1

        // Find the TableRow based on rowIndex
        val row = recordsTable.getChildAt(rowIndex) as TableRow

        // Assuming the second TextView in each TableRow is for showing the record
        val recordTextView = row.getChildAt(1) as TextView
        recordTextView.text = record

        // If the interval counter exceeds 8, reset it back to 8
        if (intervalCounter > 8) intervalCounter = 8
    }

    private fun resetTable() {
        for (i in 0 until recordsTable.childCount) {
            val row = recordsTable.getChildAt(i) as TableRow
            val recordTextView = row.getChildAt(1) as TextView
            recordTextView.text = ""
        }
        intervalCounter = 1
    }

    private val updateTimerThread = object : Runnable {
        override fun run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime
            updateTime = timeSwapBuff + timeInMilliseconds

            val totalSeconds = updateTime / 1000.0
            val timerText = String.format(Locale.US, "%.2f", totalSeconds)

            view?.findViewById<TextView>(R.id.timer_text)?.text = timerText

            timerHandler.postDelayed(this, 0)
        }
    }


    private fun checkPermissions(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.size == REQUIRED_PERMISSIONS.size) {
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                // All permissions granted. Start recording.
                startRecording(this)
            } else {
                Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

}

