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
import java.nio.ByteBuffer

class VideoDubber(private val context: Context) {

    suspend fun replaceAudioTrack(videoUri: Uri, ttsAudioFile: File): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "dubbed_${System.currentTimeMillis()}.mp4")
        
        // Step 1: Ensure TTS audio is in AAC format
        val aacAudioFile = convertToAAC(ttsAudioFile)
        
        // Step 2: Mux video with the AAC audio
        muxVideoWithAudio(videoUri, aacAudioFile, outputFile)
        
        outputFile
    }
    
    private fun convertToAAC(inputFile: File): File {
        val outputFile = File(context.cacheDir, "tts_audio.aac")
        var extractor: MediaExtractor? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            val inputFormat = extractor.getTrackFormat(0)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            extractor.selectTrack(0)

            // If already AAC, just return the original file
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                return inputFile
            }

            // Create AAC encoder
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

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
                        // Codec config – ignore
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    } else {
                        // Add track on first valid frame
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

            // If no data was written, fall back to original file
            if (!muxerStarted) {
                return inputFile
            }

        } catch (e: Exception) {
            Log.e("VideoDubber", "AAC conversion failed", e)
            return inputFile   // fallback to original
        } finally {
            try {
                encoder?.stop()
            } catch (e: IllegalStateException) {
                // Already stopped or never started
            }
            try {
                encoder?.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (e: IllegalStateException) {
                // Already stopped
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                extractor?.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return outputFile
    }

    private fun muxVideoWithAudio(videoUri: Uri, aacAudioFile: File, outputFile: File) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            videoExtractor = MediaExtractor().apply {
                setDataSource(context, videoUri, null)
            }
            audioExtractor = MediaExtractor().apply {
                setDataSource(aacAudioFile.absolutePath)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Find and add video track
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

            // Find and add audio track
            val audioTrackIdx = 0
            audioExtractor.selectTrack(audioTrackIdx)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIdx)
            val muxerAudioTrack = muxer.addTrack(audioFormat)

            // Start muxer only if we have at least one track (we always have audio here)
            if (muxerVideoTrack != -1 || muxerAudioTrack != -1) {
                muxer.start()
                muxerStarted = true
            } else {
                throw Exception("No tracks to mux")
            }

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
                        muxer.writeSampleData(
                            muxerVideoTrack, buffer,
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
                    muxer.writeSampleData(
                        muxerAudioTrack, buffer,
                        MediaCodec.BufferInfo().apply {
                            offset = 0
                            this.size = size
                            presentationTimeUs = audioExtractor.sampleTime
                            flags = audioExtractor.sampleFlags
                        })
                    audioExtractor.advance()
                }
            }

        } catch (e: Exception) {
            Log.e("VideoDubber", "Muxing failed", e)
            throw e
        } finally {
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (e: IllegalStateException) {
                // Already stopped
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                videoExtractor?.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                audioExtractor?.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}