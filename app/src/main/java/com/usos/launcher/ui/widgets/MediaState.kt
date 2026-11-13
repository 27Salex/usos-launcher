package com.usos.launcher.ui.widgets

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.usos.launcher.service.NotificationState
import com.usos.launcher.service.NotificationWatcher

/** Lo que sabe la pieza de música de la sesión de medios activa. */
sealed interface MediaUi {
    /** Sin acceso a notificaciones: no se pueden leer las sesiones. */
    data object NoAccess : MediaUi
    /** Acceso concedido pero sin sesión activa. */
    data object Idle : MediaUi
    data class Session(
        val title: String, val artist: String, val playing: Boolean, val packageName: String,
        val controller: MediaController,
    ) : MediaUi
}

/**
 * Sesión de medios activa: MediaSessionManager.getActiveSessions con el componente del NotificationListener
 * que ya existe (`NotificationWatcher`). Se vuelve a evaluar al volver a primer plano (por si se concede el acceso).
 */
@Composable
fun rememberMediaUi(): State<MediaUi> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { mutableStateOf<MediaUi>(MediaUi.NoAccess) }
    DisposableEffect(context, lifecycle) {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(context, NotificationWatcher::class.java)
        val handler = Handler(Looper.getMainLooper())
        var controllers: List<MediaController> = emptyList()
        var attached = false

        fun publish() {
            val pick = controllers.firstOrNull {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
            } ?: controllers.firstOrNull()
            state.value = if (pick == null) MediaUi.Idle else {
                val md = pick.metadata
                val ps = pick.playbackState?.state
                MediaUi.Session(
                    title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
                    playing = ps == PlaybackState.STATE_PLAYING || ps == PlaybackState.STATE_BUFFERING,
                    packageName = pick.packageName, controller = pick,
                )
            }
        }

        val cb = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(s: PlaybackState?) = publish()
            override fun onMetadataChanged(m: MediaMetadata?) = publish()
            override fun onSessionDestroyed() = publish()
        }
        fun detach() {
            controllers.forEach { runCatching { it.unregisterCallback(cb) } }
            controllers = emptyList()
        }
        fun reload(list: List<MediaController>?) {
            detach()
            controllers = list.orEmpty()
            controllers.forEach { it.registerCallback(cb, handler) }
            publish()
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { reload(it) }
        fun detachAll() {
            if (attached) runCatching { msm.removeOnActiveSessionsChangedListener(listener) }
            attached = false
            detach()
        }
        fun attach() {
            detachAll()
            if (!NotificationState.isGranted(context)) { state.value = MediaUi.NoAccess; return }
            runCatching {
                msm.addOnActiveSessionsChangedListener(listener, component, handler)
                attached = true
                reload(msm.getActiveSessions(component))
            }.onFailure { state.value = MediaUi.NoAccess }
        }
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) attach() else if (e == Lifecycle.Event.ON_PAUSE) detachAll()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); detachAll() }
    }
    return state
}

/** Abre la app que reproduce (actividad de la sesión o su intent de lanzamiento). */
fun openMediaApp(context: Context, s: MediaUi.Session) {
    runCatching {
        val pi = s.controller.sessionActivity
        if (pi != null) pi.send()
        else context.packageManager.getLaunchIntentForPackage(s.packageName)?.let { context.startActivity(it) }
    }
}
