package com.usos.launcher.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import com.usos.launcher.BuildConfig
import com.usos.launcher.R
import com.usos.launcher.UsosApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, NEEDS_PERMISSION, DOWNLOADING, INSTALLING, ERROR }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: ReleaseInfo? = null,
    val done: Long = 0,
    val total: Long = 0,
    @StringRes val error: Int? = null,
)

/** Busca, descarga, verifica e instala nuevas versiones desde las releases de GitHub. Vive en AppContainer. */
class UpdateManager(
    private val context: Context,
    val store: UpdateStore,
    http: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val checkClient = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val downloadClient = http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val current = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    /** Versión recién descubierta por una comprobación automática y aún no avisada (aviso de la barra de búsqueda). */
    private val _announce = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val announce: SharedFlow<String> = _announce

    private var restored = false

    private val busy
        get() = _state.value.phase.let {
            it == UpdatePhase.CHECKING || it == UpdatePhase.DOWNLOADING || it == UpdatePhase.INSTALLING
        }

    private suspend fun restore() {
        if (restored) return
        restored = true
        val cached = store.release() ?: return
        if (UpdateVersion.isNewer(cached.version, current)) {
            if (_state.value.phase == UpdatePhase.IDLE) _state.value = UpdateState(UpdatePhase.AVAILABLE, cached)
        } else {
            store.setRelease(null)
        }
    }

    /** Al abrir el launcher: solo si el ajuste está activo y han pasado 24 h. */
    fun autoCheck() {
        scope.launch {
            restore()
            if (!store.auto.first()) return@launch
            if (!UpdateSchedule.due(store.lastCheck(), now())) return@launch
            check(auto = true)
        }
    }

    fun checkNow() {
        scope.launch { restore(); check(auto = false) }
    }

    /** Carga el estado guardado para que Ajustes lo muestre. */
    fun load() { scope.launch { restore() } }

    private suspend fun check(auto: Boolean) {
        if (busy) return
        val before = _state.value
        _state.value = before.copy(phase = UpdatePhase.CHECKING, error = null)
        when (val result = withContext(Dispatchers.IO) { fetchLatest() }) {
            is Fetch.Failed ->
                _state.value = if (auto) before else UpdateState(UpdatePhase.ERROR, before.release, error = R.string.update_error_network)
            is Fetch.Ok -> {
                store.setLastCheck(now())
                val r = result.release
                if (r != null && UpdateVersion.isNewer(r.version, current)) {
                    store.setRelease(r)
                    _state.value = UpdateState(UpdatePhase.AVAILABLE, r)
                    if (auto && store.notified() != r.version) {
                        store.setNotified(r.version)
                        _announce.tryEmit(r.version)
                    }
                } else {
                    store.setRelease(null)
                    _state.value = UpdateState(UpdatePhase.UP_TO_DATE)
                }
            }
        }
    }

    private sealed interface Fetch {
        data class Ok(val release: ReleaseInfo?) : Fetch
        data object Failed : Fetch
    }

    private fun fetchLatest(): Fetch = try {
        val req = Request.Builder().url(LATEST_URL)
            .header("Accept", "application/vnd.github+json").header("User-Agent", "Usos-Launcher").build()
        checkClient.newCall(req).execute().use { resp ->
            when {
                resp.code == 404 -> Fetch.Ok(null)
                !resp.isSuccessful -> Fetch.Failed
                else -> Fetch.Ok(ReleaseParser.parse(resp.body.string()))
            }
        }
    } catch (_: Exception) {
        Fetch.Failed
    }

    /** «DESCARGAR E INSTALAR». */
    fun downloadAndInstall() {
        val r = _state.value.release ?: return
        if (busy) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState(UpdatePhase.NEEDS_PERMISSION, r)
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        scope.launch { run(r) }
    }

    /** Al volver de los ajustes de fuentes desconocidas: si ya hay permiso, continúa. */
    fun onResume() {
        val s = _state.value
        val r = s.release
        if (s.phase == UpdatePhase.NEEDS_PERMISSION && r != null) {
            if (context.packageManager.canRequestPackageInstalls()) scope.launch { run(r) }
            else _state.value = UpdateState(UpdatePhase.AVAILABLE, r)
        }
    }

    private suspend fun run(r: ReleaseInfo) {
        try {
            _state.value = UpdateState(UpdatePhase.DOWNLOADING, r, 0, r.apkSize)
            val file = withContext(Dispatchers.IO) { download(r) }
            _state.value = UpdateState(UpdatePhase.INSTALLING, r)
            val check = withContext(Dispatchers.IO) { verify(file) }
            if (check != ApkCheck.Result.OK) {
                file.delete()
                _state.value = UpdateState(
                    UpdatePhase.ERROR, r,
                    error = when (check) {
                        ApkCheck.Result.WRONG_SIGNATURE -> R.string.update_error_signature
                        ApkCheck.Result.WRONG_PACKAGE -> R.string.update_error_package
                        ApkCheck.Result.NOT_NEWER -> R.string.update_error_not_newer
                        else -> R.string.update_error_unreadable
                    },
                )
                return
            }
            withContext(Dispatchers.IO) { install(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState(
                UpdatePhase.ERROR, r,
                error = if (e is DownloadSizeException) R.string.update_error_size else R.string.update_error_download,
            )
        }
    }

    private class DownloadSizeException : Exception()

    private fun download(r: ReleaseInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "usos-${r.version}.apk")
        val part = File(dir, "usos-${r.version}.apk.part")
        val req = Request.Builder().url(r.apkUrl).header("User-Agent", "Usos-Launcher").build()
        downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val total = resp.body.contentLength().takeIf { it > 0 } ?: r.apkSize
            var done = 0L
            var lastEmit = 0L
            resp.body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val t = System.nanoTime()
                        if (t - lastEmit > 100_000_000L) {
                            lastEmit = t
                            _state.value = UpdateState(UpdatePhase.DOWNLOADING, r, done, total)
                        }
                    }
                }
            }
            val expected = if (r.apkSize > 0) r.apkSize else total
            if (done <= 0 || (expected > 0 && done != expected)) {
                part.delete()
                throw DownloadSizeException()
            }
        }
        if (!part.renameTo(target)) throw IOException("rename")
        return target
    }

    private fun verify(file: File): ApkCheck.Result {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
        val installed = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull()
            ?: return ApkCheck.Result.UNREADABLE
        fun signers(p: PackageInfo?) = p?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        return ApkCheck.verify(
            context.packageName, archive?.packageName, installed.longVersionCode, archive?.longVersionCode ?: 0,
            signers(installed), signers(archive),
        )
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("usos.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT)
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallStatus(status: Int) {
        val r = _state.value.release
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {}
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = UpdateState(UpdatePhase.AVAILABLE, r)
            else -> _state.value = UpdateState(UpdatePhase.ERROR, r, error = R.string.update_error_install)
        }
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/27Salex/usos-launcher/releases/latest"
        const val ACTION_RESULT = "com.usos.launcher.UPDATE_INSTALL_RESULT"
    }
}

/** Resultado de la sesión de PackageInstaller: lanza la confirmación del usuario o actualiza el estado. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            if (confirm != null) runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            (context.applicationContext as? UsosApp)?.container?.updateManager?.onInstallStatus(status)
        }
    }
}
