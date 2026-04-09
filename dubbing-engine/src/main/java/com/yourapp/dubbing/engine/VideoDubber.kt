// VideoDubber.kt (Self-contained with AAC transcoding)
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

    suspend fun replaceAudioTrack(videoUri: Uri, ttsAudioFile: File): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "dubbed_${System.currentTimeMillis()}.mp4")
        
        // Step 1: Convert TTS audio to AAC if necessary
        val aacAudioFile = convertToAAC(ttsAudioFile)
        
        // Step 2: Mux video with new AAC audio
        muxVideoWithAudio(videoUri, aacAudioFile, outputFile)
        
        outputFile
    }
    
    private fun convertToAAC(inputFile: File): File {
        val outputFile = File(context.cacheDir, "tts_audio.aac")
        
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)
        
        val inputFormat = extractor.getTrackFormat(0)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
        extractor.selectTrack(0)
        
        // If already AAC, just return original
        if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
            extractor.release()
            return inputFile
        }
        
        // Create AAC encoder
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val outputFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        
        val inputBuffer = ByteBuffer.allocate(1024 * 1024)
        var inputEos = false
        
        while (!inputEos) {
            // Feed raw audio to encoder
            val inputBufferIndex = encoder.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val buffer = encoder.getInputBuffer(inputBufferIndex)!!
                buffer.clear()
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputEos = true
                } else {
                    inputBuffer.flip()
                    buffer.put(inputBuffer)
                    encoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
            
            // Get encoded AAC data
            val info = MediaCodec.BufferInfo()
            var outputBufferIndex = encoder.dequeueOutputBuffer(info, 10000)
            while (outputBufferIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferIndex)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // Codec config data - ignore for muxer
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                } else {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    encodedData.position(info.offset)
                    encodedData.limit(info.offset + info.size)
                    muxer.writeSampleData(trackIndex, encodedData, info)
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                }
                outputBufferIndex = encoder.dequeueOutputBuffer(info, 10000)
            }
        }
        
        encoder.stop()
        encoder.release()
        extractor.release()
        muxer.stop()
        muxer.release()
        
        return outputFile
    }
    
    private fun muxVideoWithAudio(videoUri: Uri, aacAudioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor().apply {
            setDataSource(context, videoUri, null)
        }
        val audioExtractor = MediaExtractor().apply {
            setDataSource(aacAudioFile.absolutePath)
        }
        
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        var videoTrackIdx = -1
        var muxerVideoTrack = -1
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                videoExtractor.selectTrack(i)
                videoTrackIdx = i
                muxerVideoTrack = muxer.addTrack(format)
                break
            }
        }
        
        val audioTrackIdx = 0
        audioExtractor.selectTrack(audioTrackIdx)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIdx)
        val muxerAudioTrack = muxer.addTrack(audioFormat)
        
        muxer.start()
        
        // Copy video
        val buffer = ByteBuffer.allocate(1024 * 1024)
        var eos = false
        while (!eos) {
            buffer.clear()
            val size = videoExtractor.readSampleData(buffer, 0)
            if (size < 0) {
                eos = true
            } else {
                muxer.writeSampleData(muxerVideoTrack, buffer, 
                    MediaCodec.BufferInfo().apply {
                        offset = 0
                        this.size = size
                        presentationTimeUs = videoExtractor.sampleTime
                        flags = videoExtractor.sampleFlags
                    })
                videoExtractor.advance()
            }
        }
        
        // Copy audio
        eos = false
        while (!eos) {
            buffer.clear()
            val size = audioExtractor.readSampleData(buffer, 0)
            if (size < 0) {
                eos = true
            } else {
                muxer.writeSampleData(muxerAudioTrack, buffer,
                    MediaCodec.BufferInfo().apply {
                        offset = 0
                        this.size = size
                        presentationTimeUs = audioExtractor.sampleTime
                        flags = audioExtractor.sampleFlags
                    })
                audioExtractor.advance()
            }
        }
        
        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
    }
}