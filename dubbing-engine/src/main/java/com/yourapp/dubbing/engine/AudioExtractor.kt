// dubbing-engine/src/main/java/com/yourapp/dubbing/engine/AudioExtractor.kt
package com.yourapp.dubbing.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class AudioExtractor {
    suspend fun extractAudio(context: Context, videoUri: Uri): File {
        val outputFile = File(context.cacheDir, "extracted_audio.pcm")
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoUri, null)
        
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex == -1) throw Exception("No audio track found")
        extractor.selectTrack(audioTrackIndex)
        
        val buffer = ByteBuffer.allocate(1024 * 1024)
        FileOutputStream(outputFile).use { fos ->
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size <= 0) break
                fos.write(buffer.array(), 0, size)
                extractor.advance()
            }
        }
        extractor.release()
        return outputFile
    }
}