package com.cricketsim.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.StadiumData

/**
 * Step 2 of the pre-match setup flow: the country-filtered stadium
 * picker (matches pages/play-match.tsx's step 2 in the web app), over
 * StadiumData.kt's 101 stadiums.
 *
 * Implemented as a two-step drill-down (pick a country, then pick a
 * stadium within it) rather than a single combined
 * country-dropdown-plus-inline-list control. Both steps are the same
 * single-column, single-swipe list pattern FormatSelectionScreen
 * established — a drill-down keeps every screen a flat list a
 * screen-reader user can swipe through top to bottom, rather than
 * nesting an expandable control inside a list item, which is harder to
 * predict the swipe order of.
 *
 * Country and stadium rows use `Modifier.clickable(..., role =
 * Role.Button)` rather than `selectable`/`Role.RadioButton` — unlike
 * FormatSelectionScreen's options, choosing a country or stadium here
 * is a one-way navigation action (drilling in, or leaving the screen
 * entirely), not a persistent selection state sitting next to
 * unchosen alternatives on the same screen.
 */
@Composable
fun StadiumSelectionScreen(onStadiumSelected: (Stadium) -> Unit, onBack: () -> Unit) {
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    val country = selectedCountry

    if (country == null) {
        CountryListStep(
            onCountrySelected = { selectedCountry = it },
            onBack = onBack
        )
    } else {
        StadiumListStep(
            country = country,
            onStadiumSelected = onStadiumSelected,
            onBack = { selectedCountry = null }
        )
    }
}

@Composable
private fun CountryListStep(onCountrySelected: (String) -> Unit, onBack: () -> Unit) {
    val countries = remember { StadiumData.getAllFilterCountries() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Choose a country",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(countries) { country ->
                Text(
                    text = country,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "View stadiums in $country",
                            role = Role.Button,
                            onClick = { onCountrySelected(country) }
                        )
                        .padding(vertical = 14.dp, horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun StadiumListStep(country: String, onStadiumSelected: (Stadium) -> Unit, onBack: () -> Unit) {
    val stadiums = remember(country) { StadiumData.getStadiumsForCountry(country) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Stadiums in $country",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(stadiums) { stadium ->
                StadiumRow(stadium = stadium, onClick = { onStadiumSelected(stadium) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to countries")
        }
    }
}

@Composable
private fun StadiumRow(stadium: Stadium, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Select ${stadium.name}",
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(text = stadium.name, style = MaterialTheme.typography.titleMedium)
        Text(text = stadium.city, style = MaterialTheme.typography.bodyMedium)
        Text(text = stadium.pitchDescription, style = MaterialTheme.typography.bodySmall)
    }
}
