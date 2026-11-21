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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Credits page (Home, after Settings; patch 137): who made the game, and who and what its voices come from.
 *
 * Four credits, in this order:
 *   1. Developed by Technical Accessibility World (YouTube channel link).
 *   2. The English commentary, umpires, toss presenter, appeal sounds and some other sound effects: ElevenLabs, with a link to open it
 *      and the reason it was used.
 *   3. The Hindi commentary: original, recorded professionally by IAS Production Studios (YouTube channel link), copyright reserved.
 *   4. Special game supporters: the 17 beta testers, thanked.
 *
 * Built like the About page: one calm column, Back FIRST in the reading order, a real heading for every credit so heading navigation
 * jumps between them. Each link is a full-width button that says where it goes and that it opens outside the game. A link that cannot be
 * opened (no browser installed) says so in a live region instead of failing silently.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var linkProblem by remember { mutableStateOf(false) }

    fun open(url: String) {
        val opened = runCatching { uriHandler.openUri(url) }.isSuccess
        linkProblem = !opened
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
                text = "Credits",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "The people and tools behind the game.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (linkProblem) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sorry, that link could not be opened. Check that this phone has a web browser.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            // 1. The developer.
            CreditHeading("Developed by")
            CreditBody("Accessible Cricket Simulator is developed by Technical Accessibility World.")
            CreditLink("Open Technical Accessibility World on YouTube") { open(TECHNICAL_ACCESSIBILITY_WORLD_URL) }

            // 2. English commentary, umpires, toss presenter, appeals: ElevenLabs.
            CreditHeading("English commentary, umpires and sound effects")
            CreditBody(
                "The English commentary, the umpires, the toss presenter, the appeal sounds and some of the other sound effects " +
                    "in this game are made with ElevenLabs."
            )
            CreditBody(
                "Why ElevenLabs: its voices are generated, not recorded from or copied from any real commentator or broadcaster. " +
                    "No one's commentary is reused without permission, which keeps the English commentary clear of copyright trouble. " +
                    "The voices are also clear and steady, which matters to a player who plays by ear."
            )
            CreditLink("Open ElevenLabs") { open(ELEVENLABS_URL) }

            // 2b. More sound effects: Delta Productions.
            CreditHeading("More sound effects")
            CreditBody(
                "Delta Productions helped with some of the sound effects in this game, such as the sound of the stumps going down " +
                    "when a batter is bowled out, and others. Thank you, Delta Productions."
            )
            CreditBody("You can find their game on the Google Play Store, and visit their website.")
            CreditLink("Open the Delta Productions game on Google Play") { open(DELTA_PRODUCTIONS_PLAY_STORE_URL) }
            Spacer(modifier = Modifier.height(4.dp))
            CreditLink("Open the Delta Productions website") { open(DELTA_PRODUCTIONS_WEBSITE_URL) }

            // 3. Hindi commentary: IAS Production Studios.
            CreditHeading("Hindi commentary")
            CreditBody(
                "The Hindi commentary in this game is original, and it was recorded professionally by IAS Production Studios. " +
                    "All copyright is reserved by them."
            )
            CreditBody(
                "If you would like to work with IAS Production Studios, or to contact them, visit their YouTube channel."
            )
            CreditLink("Open IAS Production Studios on YouTube") { open(IAS_PRODUCTION_STUDIOS_URL) }

            // 4. The beta testers.
            CreditHeading("Special game supporters")
            CreditBody(
                "Our special game supporters are the 17 beta testers who played this game before anyone else. " +
                    "Thank you for testing it, for your time and your patience, and for your honest feedback. " +
                    "The game is better because of every one of you."
            )

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
                text = "Thank you for playing.",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreditHeading(text: String) {
    Spacer(modifier = Modifier.height(36.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun CreditBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun CreditLink(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "$label. Opens in your web browser or app." }
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private const val TECHNICAL_ACCESSIBILITY_WORLD_URL = "https://youtube.com/@technicalaccessibilityworl"
private const val ELEVENLABS_URL = "https://elevenlabs.io"
private const val IAS_PRODUCTION_STUDIOS_URL = "https://youtube.com/@anoopsinghspecialtv"
private const val DELTA_PRODUCTIONS_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.fantacycricket.game"
private const val DELTA_PRODUCTIONS_WEBSITE_URL = "https://deltaproduction.me/"
