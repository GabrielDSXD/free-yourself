package app.freeyourself.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.freeyourself.core.CHALLENGE_PHRASES
import app.freeyourself.core.phraseMatches

/** Fricção antes de afrouxar a proteção pelo app: digitar as frases, uma de cada vez. */
@Composable
fun ChallengeScreen(onPassed: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    var index by rememberSaveable { mutableIntStateOf(0) }
    var typed by rememberSaveable { mutableStateOf("") }
    val expected = CHALLENGE_PHRASES[index]
    val last = index == CHALLENGE_PHRASES.lastIndex
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val matches = phraseMatches(typed, expected)
    val advance = { if (last) onPassed() else { index++; typed = "" } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("Antes de afrouxar a proteção", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Digite as frases abaixo, uma de cada vez. Se mudar de ideia, toque em Desistir e nada muda.",
            style = MaterialTheme.typography.bodyMedium, color = muted,
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(progress = { index / CHALLENGE_PHRASES.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Frase ${index + 1} de ${CHALLENGE_PHRASES.size}", style = MaterialTheme.typography.labelLarge, color = muted)
        Spacer(Modifier.height(16.dp))
        Text("\"$expected\"", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text("Digite a frase") },
            singleLine = true,
            // O ✓ do teclado também avança: não é preciso fechar o teclado para achar o botão.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onAny = { if (matches) advance() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = advance,
            enabled = matches,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (last) "Concluir" else "Próxima") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Desistir") }
    }
}
