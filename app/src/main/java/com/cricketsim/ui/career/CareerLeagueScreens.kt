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
 * The "League offers" part of the career's Home tab (My Player, parts ten and twenty-two): ask the franchises, read their offers (each with its reason and contract), and sign for as many
 * leagues as you like (one franchise in each league). When two matches of yours fall on the same day, international cricket comes first, then the better league, then state cricket, and the
 * match that loses is played without you. Shown to players of the state and national squads. A heading first, then plain text and buttons, so a screen reader can jump to it.
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
    if (!career.atOrAboveState || career.retired) return
    Spacer(modifier = Modifier.height(12.dp))
    Text("League offers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(modifier = Modifier.height(4.dp))
    val decided = career.leagueDecidedSeason == career.season
    val closed = career.leagueClosedSeason == career.season
    val offers = if (career.leagueOffersSeason == career.season) career.leagueOffers.orEmpty() else emptyList()
    val line = career.leagueLine
    if (decided && line != null) {
        Text(line, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
    }
    if (offers.isNotEmpty()) {
        Text(
            "These franchises want you. You can sign for as many leagues as you like, one franchise in each. " +
                "When two of your matches are on the same day, international cricket comes first, then the better league, then state cricket.",
            style = MaterialTheme.typography.bodyMedium
        )
        offers.forEach { offer ->
            Spacer(modifier = Modifier.height(8.dp))
            OptionButton("${offer.teamName}, ${offer.leagueName}", offer.note, !busy, false) { onAccept(offer) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OptionButton("Turn down the other offers", "Stop looking for league offers this season.", !busy, false) { onDecline() }
    } else if (!closed) {
        Text(
            if (decided) "You can look for offers from more leagues."
            else "Franchises pick players by their ratings against the squad, their T20 form and their reputation.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OptionButton(
            if (decided) "Look for more league offers" else "Check for league offers",
            "See which franchises would play you this season.",
            !busy, false
        ) { onCheck() }
        if (decided) {
            Spacer(modifier = Modifier.height(8.dp))
            OptionButton("That is all my leagues this season", "Stop looking for league offers this season.", !busy, false) { onDecline() }
        }
    }
    if (message.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Spacer(modifier = Modifier.height(8.dp))
}
