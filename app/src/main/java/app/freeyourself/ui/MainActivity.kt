package app.freeyourself.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import app.freeyourself.FreeYourself
import app.freeyourself.R

private enum class Tab(val label: String, val icon: Int) {
    Today("Hoje", R.drawable.ic_tab_today),
    Week("Semana", R.drawable.ic_tab_week),
    Settings("Ajustes", R.drawable.ic_tab_settings),
}

class MainActivity : ComponentActivity() {
    /** Muda a cada onResume: telas reavaliam status do serviço e permissões ao voltar dos ajustes. */
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreeYourself.init(this)
        enableEdgeToEdge()
        setContent {
            FreeYourselfTheme {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(style, style)
                }
                Surface(color = MaterialTheme.colorScheme.background) { App(resumes.intValue) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FreeYourself.guard.tick()
        resumes.intValue++
    }
}

@Composable
private fun App(resumes: Int) {
    val store = FreeYourself.store
    if (!store.onboarded) {
        Onboarding(resumes) { store.onboarded = true }
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.Today -> TodayScreen(resumes)
                Tab.Week -> WeekScreen()
                Tab.Settings -> SettingsScreen(resumes)
            }
        }
    }
}
