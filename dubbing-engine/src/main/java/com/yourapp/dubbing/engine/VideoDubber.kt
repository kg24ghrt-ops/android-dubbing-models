package com.yourapp.dubbing.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioSource
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoDubber(private val context: Context) {
    suspend fun replaceAudioTrack(videoUri: Uri, newAudioFile: File): File = suspendCancellableCoroutine { cont ->
        val outputFile = File(context.cacheDir, "dubbed_${System.currentTimeMillis()}.mp4")
        
        val transformer = Transformer.Builder(context)
            .setAudioMimeType("audio/mp4a-latm")
            .setVideoMimeType("video/avc")
            .build()

        val mediaItem = MediaItem.fromUri(videoUri)
        
        // Create an AudioSource from the new audio file
        val audioSource = AudioSource.fromFile(newAudioFile)
        
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setAudioGraph { graph ->
                // Add the new audio source to the graph
                graph.add(audioSource)
            }
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)
        
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.common.Composition, result: ExportResult) {
                cont.resume(outputFile)
            }
            override fun onError(composition: androidx.media3.common.Composition, exception: ExportException) {
                cont.resumeWithException(exception)
            }
        })
        
        cont.invokeOnCancellation { transformer.cancel() }
    }
}