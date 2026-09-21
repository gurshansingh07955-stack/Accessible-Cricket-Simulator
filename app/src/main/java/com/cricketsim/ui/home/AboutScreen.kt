package com.cricketsim.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The About page: what this game is, why it exists, and how it is played,
 * written to be read (and heard) slowly.
 *
 * WRITING. Every claim in it is something the game actually does, and it
 * says nothing about testing or awards that haven't happened — it is
 * written to still be true after the first real-device test, not to
 * impress before it. (Facts used: three formats; 101 grounds; eighteen
 * nations; the four-pulse bowling release and five-pulse batting swing with
 * the accented last tick; the captain's per-ball field changes being
 * announced; Normal/Defensive/Attacking field presets; rain and revised
 * targets; commentary that reacts to the match; autosave.) The commentary
 * voices are disclosed as AI-generated.
 *
 * LAYOUT. One quiet column at a comfortable reading width, generous space
 * between sections, serif headings, larger body text. Each section title is
 * a real heading, so TalkBack's heading navigation can jump from section to
 * section. Back comes FIRST in the reading order (it is a long page, so Back
 * is not a dozen swipes away), then the page heading.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Back", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Cricket, made to be heard.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = LEAD,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ABOUT_SECTIONS.forEach { section ->
                Spacer(modifier = Modifier.height(36.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = section.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(44.dp))
            // A hairline, purely decorative (no semantics, so never a stop).
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "For everyone who loves the game, however they follow it.",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = buildString {
                    append("The commentary voices are AI-generated.")
                    if (!version.isNullOrEmpty()) append(" Version $version.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class AboutSection(val title: String, val body: String)

private const val LEAD =
    "Accessible Cricket Simulator is a complete game of cricket, built for blind and low-vision players. " +
        "Nothing in it has to be seen. Every choice is a list you can move through with a swipe, " +
        "and everything that happens on the field is spoken to you."

private val ABOUT_SECTIONS = listOf(
    AboutSection(
        title = "Why it exists",
        body = "Most sports games are made to be watched. Cricket is a game of patience, rhythm and small " +
            "decisions, which means it can be played, and loved, entirely by ear. This game is built around " +
            "sound: the crack of the bat, the murmur of the crowd, the voices in the commentary box, and the " +
            "feel of a well-timed shot."
    ),
    AboutSection(
        title = "Your match",
        body = "Choose a format: T20, One Day or Test. Pick one of 101 real grounds, each with its own pitch, " +
            "weather and character, then choose your side from eighteen nations. Select your eleven, name your " +
            "captain and wicketkeeper, and call the toss."
    ),
    AboutSection(
        title = "With the ball",
        body = "Choose your angle, your line, your delivery and your length, then set your pace. Now listen for " +
            "the rhythm: a run of buzzes and ticks, the last one higher and louder. Release on it. Too early and " +
            "the ball drops short. Too late and it comes in too full."
    ),
    AboutSection(
        title = "With the bat",
        body = "You commit to your footwork before you see the ball. Then you are told exactly what is coming, " +
            "and how the captain has changed the field to meet you. Choose your shot and your intent, and swing " +
            "on the fifth tick. The timing of that swing decides what your shot is worth."
    ),
    AboutSection(
        title = "In the field",
        body = "Set your field one fielder at a time, or begin from a Normal, Defensive or Attacking field and " +
            "adjust it. A field that breaks the rules is a gift to the batter, and the game will tell you when " +
            "yours does."
    ),
    AboutSection(
        title = "A match that lives",
        body = "Rain can stop play and change the target. The opposition captains read the game: they attack " +
            "when they must, protect their wickets when they are in trouble, and reset the field for every ball. " +
            "Commentators react to what happens, from a milestone to a wicket, and the crowd swells and settles " +
            "with the tension."
    ),
    AboutSection(
        title = "Made for screen readers",
        body = "Every screen is designed around TalkBack. Headings mark each section, results are announced as " +
            "they happen, and the moments that need perfect timing use one large target, so there is nothing to " +
            "search for while the ball is on its way. Sound and vibration can each be turned off in Settings."
    ),
    AboutSection(
        title = "Always saved",
        body = "Your match is saved after every ball. Leave whenever you like, and pick it up again from the " +
            "home screen."
    )
)
