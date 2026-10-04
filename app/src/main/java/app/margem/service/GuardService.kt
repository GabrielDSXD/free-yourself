package app.margem.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import app.margem.Margem
import app.margem.R
import app.margem.core.Decision
import app.margem.detect.ImageDetector
import app.margem.detect.ScreenFrame
import app.margem.detect.TextDetector
import app.margem.ui.BlockOverlay
import app.margem.ui.MainActivity
import app.margem.ui.WarningOverlay
import java.util.concurrent.Executors

/**
 * Único ponto de contato com o sistema: lê a tela, decide com o Guard e mostra os overlays.
 * Toda a lógica de estado roda na main thread; só screenshot + inferência vão para [worker].
 */
class GuardService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var overlay: Overlay
    private lateinit var text: TextDetector
    @Volatile private var image: ImageDetector? = null

    /** Janelas que aparecem por cima sem trocar o app em primeiro plano. */
    private var passthrough = emptySet<String>()
    /** Apps que nunca analisamos. */
    private var skipped = emptySet<String>()
    private var foreground: String? = null
    private var shownBlock: String? = null
    private var lastShot = 0L

    private val heartbeat = object : Runnable {
        override fun run() {
            Margem.guard.tick().forEach(::onBlockEnded)
            foreground?.let { if (canScan(it)) takeShot(it) }      // vídeo não gera eventos
            main.postDelayed(this, 5_000)
        }
    }

    override fun onServiceConnected() {
        Margem.init(this)
        overlay = Overlay(this)
        text = TextDetector(assets.open("blocklist.txt").bufferedReader().use { it.readText() })
        val imes = getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        passthrough = setOf(packageName, "com.android.systemui") + imes
        val homes = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        skipped = passthrough + "android" + homes
        worker.execute {
            runCatching { ImageDetector(this) { Margem.store.sensitivity } }
                .onSuccess { image = it; imageReady = true }      // se falhar, segue só com texto
        }
        main.post(heartbeat)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg !in passthrough) onForeground(pkg)
        if (pkg == foreground && canScan(pkg)) scanText(pkg)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        imageReady = false
        main.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        worker.execute { image?.close(); image = null }
        worker.shutdown()
        super.onDestroy()
    }

    private fun canScan(pkg: String) =
        pkg !in skipped && !overlay.visible && !Margem.guard.isQuiet() &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun onForeground(pkg: String) {
        if (pkg == foreground) return
        foreground = pkg
        if (Margem.guard.remainingMs(pkg) > 0) {
            showBlock(pkg)
        } else if (overlay.visible) {
            if (shownBlock == null) Margem.guard.onLeave()           // saiu do app com um aviso aberto
            hideOverlay()
        }
    }

    private fun scanText(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        if (text.isAdult(ScreenFrame(pkg, text = collectText(root)))) onDetected(pkg) else takeShot(pkg)
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
        when (val decision = Margem.guard.onDetection(pkg)) {
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
                    Margem.guard.onLeave()
                    hideOverlay()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                },
                onContinue = {
                    Margem.guard.onContinue()
                    hideOverlay()
                },
            )
        }
    }

    private fun showBlock(pkg: String) {
        if (shownBlock == pkg && overlay.visible) return
        shownBlock = pkg
        val attempts = Margem.guard.today.attempts
        overlay.show {
            BlockOverlay(
                attempts = attempts,
                remaining = { Margem.guard.remainingMs(pkg) },
                onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
                onClose = {
                    Margem.guard.onLeave()
                    hideOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                },
            )
        }
    }

    private fun hideOverlay() {
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
                .setContentTitle("Margem")
                .setContentText("O bloqueio terminou. Continue usando seu celular com intenção.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL = "status"
        @Volatile var imageReady = false
            private set
    }
}
