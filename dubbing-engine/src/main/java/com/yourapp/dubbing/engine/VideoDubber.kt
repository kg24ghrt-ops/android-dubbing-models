package com.yourapp.dubbing.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
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
            .build()
        
        val mediaItem = MediaItem.fromUri(videoUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setAudioTrackReplacementFile(newAudioFile)
            .build()
        
        transformer.start(editedMediaItem, outputFile.absolutePath)
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(mediaItem: MediaItem, result: ExportResult) {
                cont.resume(outputFile)
            }
            override fun onError(mediaItem: MediaItem, exception: ExportException) {
                cont.resumeWithException(exception)
            }
        })
        cont.invokeOnCancellation { transformer.cancel() }
    }
}