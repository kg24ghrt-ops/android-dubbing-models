// dubbing-engine/src/main/java/com/yourapp/dubbing/engine/VideoDubber.kt
package com.yourapp.dubbing.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class VideoDubber(private val context: Context) {

    suspend fun replaceAudioTrack(videoUri: Uri, newAudioFile: File): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "dubbed_${System.currentTimeMillis()}.mp4")
        
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(context, videoUri, null)
        
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(newAudioFile.absolutePath)
        
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerVideoTrack = -1
        var muxerAudioTrack = -1
        
        // Find and add video track
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i)
                videoTrackIndex = i
                muxerVideoTrack = muxer.addTrack(format)
                break
            }
        }
        
        // Find and add new audio track
        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioExtractor.selectTrack(i)
                audioTrackIndex = i
                muxerAudioTrack = muxer.addTrack(format)
                break
            }
        }
        
        if (videoTrackIndex == -1) throw Exception("No video track found")
        if (audioTrackIndex == -1) throw Exception("No audio track in replacement file")
        
        muxer.start()
        
        // Copy video samples
        val videoBuffer = ByteBuffer.allocate(1024 * 1024)
        var videoEos = false
        while (!videoEos) {
            videoBuffer.clear()
            val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
            if (sampleSize < 0) {
                videoEos = true
            } else {
                val sampleTime = videoExtractor.sampleTime
                val sampleFlags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, videoBuffer, MediaCodec.BufferInfo().apply {
                    offset = 0
                    size = sampleSize
                    presentationTimeUs = sampleTime
                    flags = sampleFlags
                })
                videoExtractor.advance()
            }
        }
        
        // Copy audio samples
        val audioBuffer = ByteBuffer.allocate(1024 * 1024)
        var audioEos = false
        while (!audioEos) {
            audioBuffer.clear()
            val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
            if (sampleSize < 0) {
                audioEos = true
            } else {
                val sampleTime = audioExtractor.sampleTime
                val sampleFlags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, audioBuffer, MediaCodec.BufferInfo().apply {
                    offset = 0
                    size = sampleSize
                    presentationTimeUs = sampleTime
                    flags = sampleFlags
                })
                audioExtractor.advance()
            }
        }
        
        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
        
        outputFile
    }
}