// dubbing-engine/src/main/java/com/yourapp/dubbing/engine/VideoDubber.kt
package com.yourapp.dubbing.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodecInfo
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VideoDubber(private val context: Context) {

    suspend fun replaceAudioTrack(videoUri: Uri, ttsAudioFile: File): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "dubbed_${System.currentTimeMillis()}.mp4")
        
        // Debug: inspect TTS format
        inspectAudioFormat(ttsAudioFile)
        
        // Step 1: Convert TTS audio to proper AAC in an MP4 container
        val aacMp4File = convertToAacMp4(ttsAudioFile)
        
        // Step 2: Mux video with the new audio
        muxVideoWithAudio(videoUri, aacMp4File, outputFile)
        
        outputFile
    }
    
    private fun inspectAudioFormat(file: File) {
        try {
            MediaExtractor().use { extractor ->
                extractor.setDataSource(file.absolutePath)
                val format = extractor.getTrackFormat(0)
                Log.d("VideoDubber", "--- TTS Audio Format ---")
                Log.d("VideoDubber", "MIME: ${format.getString(MediaFormat.KEY_MIME)}")
                Log.d("VideoDubber", "Sample Rate: ${format.getInteger(MediaFormat.KEY_SAMPLE_RATE)}")
                Log.d("VideoDubber", "Channels: ${format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}")
            }
        } catch (e: Exception) {
            Log.e("VideoDubber", "Could not inspect audio format", e)
        }
    }
    
    /**
     * Converts any audio file to AAC inside an MP4 container.
     * Returns a file that MediaMuxer can easily consume.
     */
    private fun convertToAacMp4(inputFile: File): File {
        val outputFile = File(context.cacheDir, "tts_aac.mp4")
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var audioTrackIndex = -1
        
        try {
            // 1. Setup extractor for input file
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)
            
            val inputFormat = extractor.getTrackFormat(0)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            extractor.selectTrack(0)
            
            // 2. Create decoder for the input format
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            
            // 3. Create AAC encoder
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            
            // 4. Create muxer for output (MP4)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            // 5. Feed input -> decoder -> encoder -> muxer
            val inputBuffer = ByteBuffer.allocate(1024 * 1024)
            var inputEos = false
            var decoderOutputEos = false
            
            while (!decoderOutputEos) {
                // Feed extractor to decoder
                if (!inputEos) {
                    val inIdx = decoder.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        buf.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            inputBuffer.flip()
                            buf.put(inputBuffer)
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                
                // Get decoded PCM from decoder
                val decInfo = MediaCodec.BufferInfo()
                val decOutIdx = decoder.dequeueOutputBuffer(decInfo, 10000)
                if (decOutIdx >= 0) {
                    val decodedData = decoder.getOutputBuffer(decOutIdx)!!
                    
                    if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        // Signal encoder EOS
                        val encInIdx = encoder.dequeueInputBuffer(10000)
                        if (encInIdx >= 0) {
                            encoder.queueInputBuffer(encInIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                        decoderOutputEos = true
                    } else if (decInfo.size > 0) {
                        // Feed PCM to encoder
                        val encInIdx = encoder.dequeueInputBuffer(10000)
                        if (encInIdx >= 0) {
                            val encInBuf = encoder.getInputBuffer(encInIdx)!!
                            encInBuf.clear()
                            decodedData.position(decInfo.offset)
                            decodedData.limit(decInfo.offset + decInfo.size)
                            encInBuf.put(decodedData)
                            encoder.queueInputBuffer(encInIdx, 0, decInfo.size, decInfo.presentationTimeUs, 0)
                        }
                    }
                    decoder.releaseOutputBuffer(decOutIdx, false)
                }
                
                // Get encoded AAC from encoder
                val encInfo = MediaCodec.BufferInfo()
                val encOutIdx = encoder.dequeueOutputBuffer(encInfo, 10000)
                while (encOutIdx >= 0) {
                    val encData = encoder.getOutputBuffer(encOutIdx)!!
                    if (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // Ignore codec config
                        encoder.releaseOutputBuffer(encOutIdx, false)
                    } else {
                        if (!muxerStarted) {
                            audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        encData.position(encInfo.offset)
                        encData.limit(encInfo.offset + encInfo.size)
                        muxer.writeSampleData(audioTrackIndex, encData, encInfo)
                        encoder.releaseOutputBuffer(encOutIdx, false)
                    }
                    encOutIdx = encoder.dequeueOutputBuffer(encInfo, 0)
                }
            }
            
            // Wait for encoder to finish
            encoder.signalEndOfInputStream()
            var encEos = false
            while (!encEos) {
                val info = MediaCodec.BufferInfo()
                val outIdx = encoder.dequeueOutputBuffer(info, 10000)
                if (outIdx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encEos = true
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                }
            }
            
            Log.d("VideoDubber", "AAC conversion successful, track added: $audioTrackIndex")
            
        } catch (e: Exception) {
            Log.e("VideoDubber", "AAC conversion failed, using original file", e)
            // Fallback: return original file (will likely fail muxing later, but we tried)
            return inputFile
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
        
        return outputFile
    }
    
    private fun muxVideoWithAudio(videoUri: Uri, aacAudioFile: File, outputFile: File) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        
        try {
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(context, videoUri, null)
            
            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(aacAudioFile.absolutePath)
            
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            // Find video track
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
            
            // Find audio track from AAC file (it's an MP4 with one audio track)
            audioExtractor.selectTrack(0)
            val audioFormat = audioExtractor.getTrackFormat(0)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            
            muxer.start()
            muxerStarted = true
            
            val buffer = ByteBuffer.allocate(1024 * 1024)
            
            // Write video samples
            if (videoTrackIdx != -1) {
                var videoEos = false
                while (!videoEos) {
                    buffer.clear()
                    val size = videoExtractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        videoEos = true
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
            }
            
            // Write audio samples
            var audioEos = false
            while (!audioEos) {
                buffer.clear()
                val size = audioExtractor.readSampleData(buffer, 0)
                if (size < 0) {
                    audioEos = true
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
            
            Log.d("VideoDubber", "Muxing completed: $outputFile")
            
        } catch (e: Exception) {
            Log.e("VideoDubber", "Muxing failed", e)
            throw e
        } finally {
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }
}