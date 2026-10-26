package com.cricketsim.ui.tournament

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CustomTournamentDef
import com.cricketsim.logic.CustomTournaments
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.PageHeading

/**
 * Tournament > Custom tournaments: the tournaments the player designed in Advanced Customisation, each one a button
 * that opens its start page (new tournament or resume), the same pages as every other tournament.
 */
@Composable
internal fun CustomTournamentListPage(defs: List<CustomTournamentDef>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Custom tournaments")
        Spacer(modifier = Modifier.height(12.dp))
        if (defs.isEmpty()) {
            Text(
                "You have not made any custom tournaments yet. Make one in Advanced Customisation, then Create Custom Tournaments.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        defs.forEach { d ->
            OutlinedButton(
                onClick = { onOpen(d.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    Text(CustomTournaments.introOf(d.id), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
