package com.yourapp.dubbing.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class AudioExtractor {
    suspend fun extractAudio(context: Context, videoUri: Uri): File {
        val outputFile = File(context.cacheDir, "extracted_audio.pcm")
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoUri, null)
        
        var audioTrackIndex = -1
        var sampleRate = 0
        var channelCount = 0
        
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                break
            }
        }
        if (audioTrackIndex == -1) throw Exception("No audio track found")
        extractor.selectTrack(audioTrackIndex)
        
        FileOutputStream(outputFile).use { fos ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size <= 0) break
                fos.write(buffer, 0, size)
                extractor.advance()
            }
        }
        extractor.release()
        return outputFile
    }
}