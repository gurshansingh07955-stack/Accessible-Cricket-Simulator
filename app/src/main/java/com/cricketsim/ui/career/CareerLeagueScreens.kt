package com.cricketsim.ui.career

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.LeagueOffer
import com.cricketsim.ui.custom.OptionButton

/**
 * The "League offers" part of the career's Home tab (My Player, part ten): ask the franchises, read their offers (each with its reason), sign for one
 * league for the season or turn them all down. Shown to players of the state and national squads. A heading first, then plain text and buttons,
 * so a screen reader can jump to it.
 */
@Composable
internal fun LeagueSection(
    career: CareerProfile,
    busy: Boolean,
    message: String,
    onCheck: () -> Unit,
    onAccept: (LeagueOffer) -> Unit,
    onDecline: () -> Unit
) {
    if (!career.atOrAboveState) return
    Spacer(modifier = Modifier.height(12.dp))
    Text("League offers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(modifier = Modifier.height(4.dp))
    val offers = career.leagueOffers.orEmpty()
    when {
        career.leagueDecidedSeason == career.season -> {
            Text(career.leagueLine ?: "You have settled your league for this season.", style = MaterialTheme.typography.bodyMedium)
        }
        offers.isNotEmpty() && career.leagueOffersSeason == career.season -> {
            Text(
                "These franchises want you. You can play one league this season, after your other cricket.",
                style = MaterialTheme.typography.bodyMedium
            )
            offers.forEach { offer ->
                Spacer(modifier = Modifier.height(8.dp))
                OptionButton("${offer.teamName}, ${offer.leagueName}", offer.note, !busy, false) { onAccept(offer) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OptionButton("Turn all the offers down", "Play only your other cricket this season.", !busy, false) { onDecline() }
        }
        else -> {
            Text(
                "Franchises pick players by their ratings against the squad, their T20 form and their reputation.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OptionButton("Check for league offers", "See which franchises would play you this season.", !busy, false) { onCheck() }
        }
    }
    if (message.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Spacer(modifier = Modifier.height(8.dp))
}
