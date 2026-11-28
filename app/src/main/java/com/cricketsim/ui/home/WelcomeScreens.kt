package com.cricketsim.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.Difficulty

/**
 * The welcome screens, shown the first time the game is opened (patch 160), before the home page:
 *
 *   Welcome (what the game is) > View credits (the Credits page, Back returns here) or Continue
 *   > Choose your difficulty (saved at once, the same setting as Settings > Difficulty) > Continue
 *   > Tutorial: "Continue to tutorial" goes straight to net practice, "Skip tutorial" goes to the home page.
 *
 * [onFinished] is told which of the two was chosen. The caller records that the welcome has been seen, so it is shown only once.
 * ACCESSIBILITY as on the About page: one calm column, a real heading on every page, Back FIRST in the reading order on every page but the first,
 * the difficulty choices are radio rows that read their meaning, and the system back button does what the on-screen Back does.
 */
private enum class WelcomeStep { Intro, Credits, Difficulty, Tutorial }

@Composable
fun WelcomeFlow(onFinished: (startTutorial: Boolean) -> Unit) {
    var step by remember { mutableStateOf(WelcomeStep.Intro) }

    when (step) {
        WelcomeStep.Intro -> WelcomePage(label = "Welcome", title = "Welcome to Accessible Cricket Simulator", onBack = null) {
            INTRO.forEach { paragraph ->
                Text(text = paragraph, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { step = WelcomeStep.Credits },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("View credits") }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { step = WelcomeStep.Difficulty },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Continue") }
        }

        WelcomeStep.Credits -> {
            val back = { step = WelcomeStep.Intro }
            BackHandler(onBack = back)
            CreditsScreen(onBack = back)
        }

        WelcomeStep.Difficulty -> {
            val back = { step = WelcomeStep.Intro }
            BackHandler(onBack = back)
            val services = LocalGameServices.current
            val current = services?.settings?.difficulty ?: Difficulty.MEDIUM
            WelcomePage(label = "Step 1 of 2", title = "Choose your difficulty", onBack = back) {
                Text(
                    text = "This sets how hard the computer plays. You can change it any time in Settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    for (level in Difficulty.values()) {
                        DifficultyRow(
                            title = level.name.lowercase().replaceFirstChar { it.uppercase() },
                            description = difficultyText(level),
                            selected = current == level,
                            onSelect = { services?.update { it.copy(difficulty = level) } }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { step = WelcomeStep.Tutorial },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Continue") }
            }
        }

        WelcomeStep.Tutorial -> {
            val back = { step = WelcomeStep.Difficulty }
            BackHandler(onBack = back)
            WelcomePage(label = "Step 2 of 2", title = "Would you like the tutorial?", onBack = back) {
                Text(
                    text = "The tutorial is net practice: a coach takes you through batting and bowling, with no match to lose. " +
                        "You can come back to net practice any time from the home page.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { onFinished(true) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Continue to tutorial") }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onFinished(false) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Skip tutorial") }
            }
        }
    }
}

private val INTRO = listOf(
    "This is a cricket game made for blind and low-vision players. You play it by touch and by ear, with TalkBack and simple gestures, and everything that happens is spoken or announced.",
    "You can bat, bowl and field in quick matches and series, play tournaments such as the CPL, the IPL with its auction and the T20 World Cup, build a career with My Player, practise in the nets, and design your own teams, players, stadiums and tournaments.",
    "Voice commentary and match sounds follow every ball, and your matches are saved automatically so you can pick one up again later.",
    "First, choose how hard you want the game to be. Then you can take the tutorial, or go straight to the home page."
)

private fun difficultyText(level: Difficulty): String = when (level) {
    Difficulty.EASY -> "A gentle start. The computer's batters get out more often and its bowlers are easier to score off."
    Difficulty.MEDIUM -> "A fair match. This is the usual setting."
    Difficulty.HARD -> "The computer's bowlers are tough to score off and its batters hold their wickets longer."
    Difficulty.HARDEST -> "The toughest setting: runs are hard to come by and wickets have to be earned."
}

/** One calm column like the About page: the label and title are headings, Back (when there is one) comes first in the reading order. */
@Composable
private fun WelcomePage(label: String, title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Back", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun DifficultyRow(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
