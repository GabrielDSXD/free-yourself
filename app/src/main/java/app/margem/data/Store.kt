package app.margem.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import app.margem.core.Policy
import app.margem.core.Sensitivity
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Tudo que o app persiste: contadores do Guard e preferências. Nenhum conteúdo da tela. */
class Store(context: Context) {
    private val guardPrefs = context.getSharedPreferences("state", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var onboarded by pref("onboarded", false, String::toBoolean)
    var vibrate by pref("vibrate", true, String::toBoolean)
    var initialBlockSec by pref("initialBlockSec", 30, String::toInt)
    var maxBlockMin by pref("maxBlockMin", 120, String::toInt)
    var sensitivity by pref("sensitivity", Sensitivity.MEDIUM, Sensitivity::valueOf)
    var theme by pref("theme", ThemeMode.SYSTEM, ThemeMode::valueOf)

    fun policy() = Policy(initialBlock = initialBlockSec.seconds, maxBlock = maxBlockMin.minutes)

    fun loadState(): Map<String, String> = guardPrefs.all.mapValues { it.value.toString() }

    /** commit() síncrono: um bloqueio não pode se perder se o processo morrer logo depois. */
    fun saveState(map: Map<String, String>) {
        guardPrefs.edit().clear().apply { map.forEach(::putString) }.commit()
    }

    /** Preferência salva como texto e exposta como estado do Compose. */
    private fun <T> pref(key: String, default: T, read: (String) -> T): ReadWriteProperty<Any?, T> {
        val state = mutableStateOf(prefs.getString(key, null)?.let { runCatching { read(it) }.getOrNull() } ?: default)
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                state.value = value
                prefs.edit().putString(key, value.toString()).apply()
            }
        }
    }
}
