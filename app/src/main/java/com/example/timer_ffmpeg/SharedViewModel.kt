package com.example.timer_ffmpeg

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SharedViewModel : ViewModel() {

    var currentVideoPath: String = ""
    val thresholdValue: MutableLiveData<Int> = MutableLiveData()
    val delayInput: MutableLiveData<Int> = MutableLiveData()
    val recordLengthInput: MutableLiveData<Int> = MutableLiveData()

    @Throws(IOException::class)
    fun createVideoFile(): File {
        // Create a video file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File.createTempFile(
            "MP4_${timeStamp}_", /* prefix */
            ".mp4", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentVideoPath = absolutePath
        }
    }
    fun calculateAverageDifference(previousFrame: Bitmap, currentFrame: Bitmap, startX: Int, startY: Int, width: Int, height: Int): Int {
        var totalDiff = 0
        for (x in startX until startX + width) {
            for (y in startY until startY + height) {
                val prevPixel = previousFrame.getPixel(x, y)
                val currentPixel = currentFrame.getPixel(x, y)
                val diff = Math.abs(Color.red(prevPixel) - Color.red(currentPixel))
                totalDiff += diff
            }
        }
        return totalDiff / (width * height)
    }
}
