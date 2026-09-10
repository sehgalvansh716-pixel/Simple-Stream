package com.lagradost.cloudstream3.ui.utils

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError

/**
 * Lightweight, hardware-accelerated ambient video loop manager powered by Media3 ExoPlayer.
 *
 * Replaces legacy MediaPlayer to provide:
 * 1. True 0ms gapless looping via [Player.REPEAT_MODE_ALL] without seek pauses.
 * 2. Zero audio focus contention (muted volume, no audio focus requests).
 * 3. Automatic center-crop Matrix transformation to fill 1080p TV screens seamlessly.
 */
class TvAmbientVideoHelper(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var targetTextureView: TextureView? = null
    private var themeBackgroundHelper: TvThemeBackgroundHelper? = null
    private var videoWidth: Int = 1920
    private var videoHeight: Int = 1080
    private var isPlayingRequested: Boolean = false
    var onFirstFrameRendered: (() -> Unit)? = null

    fun attach(
        textureView: TextureView,
        @RawRes rawResId: Int = R.raw.tv_search_bg,
        autoPlay: Boolean = true,
        onReady: (() -> Unit)? = null
    ) {
        targetTextureView = textureView
        isPlayingRequested = autoPlay
        if (onReady != null) {
            this.onFirstFrameRendered = onReady
        }

        if (rawResId == R.raw.tv_search_bg || rawResId == R.raw.mobile_search_bg) {
            themeBackgroundHelper?.release()
            themeBackgroundHelper = TvThemeBackgroundHelper(context).apply {
                attach(textureView, onReady)
            }
            return
        }

        themeBackgroundHelper?.release()
        themeBackgroundHelper = null

        initPlayer(rawResId)

        textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTextureMatrix()
        }

        if (textureView.isAvailable) {
            val surfaceTexture = textureView.surfaceTexture
            if (surfaceTexture != null) {
                exoPlayer?.setVideoTextureView(textureView)
                updateTextureMatrix()
                if (autoPlay) {
                    exoPlayer?.playWhenReady = true
                }
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                exoPlayer?.setVideoTextureView(textureView)
                updateTextureMatrix()
                if (isPlayingRequested) {
                    exoPlayer?.playWhenReady = true
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                updateTextureMatrix()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                exoPlayer?.clearVideoTextureView(textureView)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    fun attachUri(
        textureView: TextureView,
        uri: Uri,
        headers: Map<String, String>? = null,
        autoPlay: Boolean = true,
        onReady: (() -> Unit)? = null
    ) {
        themeBackgroundHelper?.release()
        themeBackgroundHelper = null
        targetTextureView = textureView
        isPlayingRequested = autoPlay
        if (onReady != null) {
            this.onFirstFrameRendered = onReady
        }

        initPlayerWithUri(uri, headers)

        if (textureView.isAvailable) {
            val surfaceTexture = textureView.surfaceTexture
            if (surfaceTexture != null) {
                exoPlayer?.setVideoTextureView(textureView)
                updateTextureMatrix()
                if (autoPlay) {
                    exoPlayer?.playWhenReady = true
                }
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                exoPlayer?.setVideoTextureView(textureView)
                updateTextureMatrix()
                if (isPlayingRequested) {
                    exoPlayer?.playWhenReady = true
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                updateTextureMatrix()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                exoPlayer?.clearVideoTextureView(textureView)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun initPlayer(@RawRes rawResId: Int) {
        val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
        initPlayerWithUri(uri)
    }

    private fun initPlayerWithUri(uri: Uri, headers: Map<String, String>? = null) {
        try {
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context.applicationContext)
                    .build()
                    .apply {
                        volume = 0f
                        repeatMode = Player.REPEAT_MODE_ALL
                        videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                        addListener(object : Player.Listener {
                            override fun onVideoSizeChanged(videoSize: VideoSize) {
                                if (videoSize.width > 0 && videoSize.height > 0) {
                                    videoWidth = videoSize.width
                                    videoHeight = videoSize.height
                                    updateTextureMatrix()
                                }
                            }

                            override fun onRenderedFirstFrame() {
                                onFirstFrameRendered?.invoke()
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY) {
                                    updateTextureMatrix()
                                }
                            }
                        })
                    }
            }
            exoPlayer?.apply {
                val mediaItem = MediaItem.fromUri(uri)
                if (!headers.isNullOrEmpty()) {
                    val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(headers)
                    val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                    setMediaSource(mediaSource)
                } else {
                    setMediaItem(mediaItem)
                }
                prepare()
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    fun play() {
        isPlayingRequested = true
        themeBackgroundHelper?.resume()
        exoPlayer?.playWhenReady = true
    }

    fun pause() {
        isPlayingRequested = false
        themeBackgroundHelper?.pause()
        exoPlayer?.playWhenReady = false
    }

    fun release() {
        isPlayingRequested = false
        try {
            themeBackgroundHelper?.release()
            themeBackgroundHelper = null
            targetTextureView?.surfaceTextureListener = null
            exoPlayer?.clearVideoTextureView(targetTextureView)
            exoPlayer?.release()
            exoPlayer = null
            targetTextureView = null
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun updateTextureMatrix() {
        val tv = targetTextureView ?: return
        val viewWidth = tv.width.toFloat()
        val viewHeight = tv.height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val scaleX: Float
        val scaleY: Float
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val viewAspect = viewWidth / viewHeight

        if (viewAspect > videoAspect) {
            scaleX = 1f
            scaleY = (viewWidth / videoWidth) / (viewHeight / videoHeight)
        } else {
            scaleX = (viewHeight / videoHeight) / (viewWidth / videoWidth)
            scaleY = 1f
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        tv.setTransform(matrix)
    }
}
