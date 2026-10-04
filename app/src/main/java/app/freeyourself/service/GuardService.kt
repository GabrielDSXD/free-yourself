package app.freeyourself.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.mutableStateOf
import app.freeyourself.FreeYourself
import app.freeyourself.R
import app.freeyourself.core.Decision
import app.freeyourself.detect.ImageDetector
import app.freeyourself.detect.ScreenFrame
import app.freeyourself.detect.TextDetector
import app.freeyourself.ui.BlockOverlay
import app.freeyourself.ui.MainActivity
import app.freeyourself.ui.WarningOverlay
import java.util.concurrent.Executors

/**
 * Único ponto de contato com o sistema: lê a tela, decide com o Guard e mostra os overlays.
 * Toda a lógica de estado roda na main thread; leitura da árvore, screenshot e inferência vão para [worker].
 */
class GuardService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var overlay: Overlay
    private lateinit var text: TextDetector
    @Volatile private var image: ImageDetector? = null

    /** Janelas de outros pacotes que aparecem por cima sem trocar o app em primeiro plano. */
    private var passthrough = emptySet<String>()
    /** Apps que nunca analisamos (nem bloqueamos): o próprio, launcher, telefone. */
    private var skipped = emptySet<String>()
    private var foreground: String? = null
    private var shownBlock: String? = null
    private var lastShot = 0L
    /** Foco de áudio permanente durante o bloqueio: a maioria dos players pausa e não retoma sozinha. */
    private val silence by lazy { AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener {}.build() }

    private val heartbeat = object : Runnable {
        override fun run() {
            FreeYourself.guard.tick().forEach(::onBlockEnded)
            foreground?.let { if (canScan(it)) takeShot(it) }      // vídeo não gera eventos
            main.postDelayed(this, 5_000)
        }
    }

    override fun onServiceConnected() {
        FreeYourself.init(this)
        overlay = Overlay(this)
        text = TextDetector(assets.open("blocklist.txt").bufferedReader().use { it.readText() })
        val imes = getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        // Barra de notificações, teclados e diálogos do sistema aparecem por cima sem trocar o app.
        passthrough = setOf(
            "com.android.systemui", "android", "com.android.intentresolver",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        ) + imes
        val homes = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        val phone = listOfNotNull(getSystemService(TelecomManager::class.java).defaultDialerPackage) +
            listOf("com.android.incallui", "com.samsung.android.incallui", "com.android.server.telecom")
        skipped = passthrough + packageName + homes + phone
        imageStatus.value = ImageStatus.LOADING
        worker.execute {
            val loaded = runCatching { ImageDetector(this) { FreeYourself.store.sensitivity } }.getOrNull()
            image = loaded                                     // se falhar, segue só com texto
            main.post { imageStatus.value = if (loaded != null) ImageStatus.READY else ImageStatus.FAILED }
        }
        main.post(heartbeat)
        seedForeground()
    }

    /**
     * Confere o app da janela ativa. Necessário porque nem toda volta de app gera STATE_CHANGED
     * (o Chrome retomado não gera) e porque após (re)conexão nenhum evento de troca chega.
     * Com overlay visível a janela ativa pode ser o próprio overlay, então não confere.
     */
    private fun seedForeground() {
        if (overlay.visible) return
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        // Sem overlay visível, uma janela ativa do próprio pacote só pode ser a Activity.
        foregroundAfter(pkg, MainActivity::class.java.name, packageName, MainActivity::class.java.name, passthrough)
            ?.let(::onForeground)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val stateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (stateChanged) {
            foregroundAfter(pkg, event.className?.toString(), packageName, MainActivity::class.java.name, passthrough)
                ?.let(::onForeground)
        }
        if (pkg != foreground && (stateChanged || pkg !in passthrough)) seedForeground()
        if (pkg == foreground && canScan(pkg)) scanText(pkg)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        worker.execute { image?.close(); image = null }
        worker.shutdown()
        super.onDestroy()
    }

    private fun canScan(pkg: String) =
        pkg !in skipped && !overlay.visible && !FreeYourself.guard.isQuiet() &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun onForeground(pkg: String) {
        if (pkg == foreground) return
        foreground = pkg
        if (FreeYourself.guard.remainingMs(pkg) > 0) {
            showBlock(pkg)
        } else if (overlay.visible) {
            if (shownBlock == null) FreeYourself.guard.onLeave()           // saiu do app com um aviso aberto
            hideOverlay()
        }
    }

    /** Percorrer a árvore são várias chamadas IPC: fica fora da main thread; a decisão volta para ela. */
    private fun scanText(pkg: String) {
        worker.execute {
            val root = rootInActiveWindow ?: return@execute
            if (root.packageName?.toString() != pkg) return@execute
            val adult = text.isAdult(ScreenFrame(pkg, text = collectText(root)))
            main.post { if (adult) onDetected(pkg) else takeShot(pkg) }
        }
    }

    private fun collectText(root: AccessibilityNodeInfo): String {
        val out = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 300 && out.length < 5_000) {
            val node = stack.removeLast()
            node.text?.let { out.append(it).append(' ') }
            node.contentDescription?.let { out.append(it).append(' ') }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
        }
        return out.toString()
    }

    private fun takeShot(pkg: String) {
        val detector = image ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShot < 2_000 || !getSystemService(PowerManager::class.java).isInteractive) return
        lastShot = now
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer = result.hardwareBuffer
                val adult = runCatching {
                    Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let { bitmap ->
                        try { detector.isAdult(ScreenFrame(pkg, bitmap = bitmap)) } finally { bitmap.recycle() }
                    } ?: false
                }.getOrDefault(false)
                buffer.close()
                if (adult) main.post { onDetected(pkg) }
            }

            /** Janela segura (FLAG_SECURE), intervalo curto etc.: o detector de texto continua valendo. */
            override fun onFailure(errorCode: Int) {}
        })
    }

    private fun onDetected(pkg: String) {
        if (pkg != foreground || overlay.visible) return
        when (val decision = FreeYourself.guard.onDetection(pkg)) {
            Decision.Ignore -> {}
            is Decision.Warn -> showWarning(decision.level)
            Decision.Block -> showBlock(pkg)
        }
    }

    private fun showWarning(level: Int) {
        shownBlock = null
        overlay.show {
            WarningOverlay(
                level = level,
                onBack = {
                    FreeYourself.guard.onLeave()
                    hideOverlay()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                },
                onContinue = {
                    FreeYourself.guard.onContinue()
                    hideOverlay()
                },
            )
        }
    }

    private fun showBlock(pkg: String) {
        if (shownBlock == pkg && overlay.visible) return
        shownBlock = pkg
        getSystemService(AudioManager::class.java).requestAudioFocus(silence)
        val attempts = FreeYourself.guard.today.attempts
        overlay.show {
            BlockOverlay(
                attempts = attempts,
                remaining = { FreeYourself.guard.remainingMs(pkg) },
                onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
                onClose = {
                    FreeYourself.guard.onLeave()
                    hideOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                },
            )
        }
    }

    private fun hideOverlay() {
        if (shownBlock != null) getSystemService(AudioManager::class.java).abandonAudioFocusRequest(silence)
        overlay.hide()
        shownBlock = null
    }

    /** Se o usuário está vendo a tela de bloqueio, ela mesma mostra "Bloqueio encerrado". */
    private fun onBlockEnded(pkg: String) {
        if (overlay.visible && shownBlock == pkg) return
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Status da proteção", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(
            1,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Free Yourself")
                .setContentText("O bloqueio terminou. Continue usando seu celular com intenção.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL = "status"
        /** Lido pelos Ajustes; estado do Compose para a tela atualizar sozinha. */
        val imageStatus = mutableStateOf(ImageStatus.LOADING)
    }
}

enum class ImageStatus { LOADING, READY, FAILED }
