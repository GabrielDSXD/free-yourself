package app.freeyourself

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.mutableIntStateOf
import app.freeyourself.core.Clock
import app.freeyourself.core.Guard
import app.freeyourself.data.Store
import java.time.ZoneId

/** Instâncias compartilhadas pela UI e pelo serviço (mesmo processo, sempre na main thread). */
object FreeYourself {
    lateinit var store: Store
        private set
    lateinit var guard: Guard
        private set

    /** Incrementa quando o Guard salva; a UI lê para recompor. */
    val version = mutableIntStateOf(0)

    fun init(context: Context) {
        if (::guard.isInitialized) return
        val app = context.applicationContext
        store = Store(app)
        guard = Guard(AndroidClock(app), store::policy, store.loadState()) {
            store.saveState(it)
            version.intValue++
        }
    }
}

private class AndroidClock(private val context: Context) : Clock {
    override fun wallMs() = System.currentTimeMillis()
    override fun elapsedMs() = SystemClock.elapsedRealtime()
    override fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Serviço ligado E vinculado pelo sistema (não só a chave nos ajustes). */
fun isServiceEnabled(context: Context): Boolean =
    context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
