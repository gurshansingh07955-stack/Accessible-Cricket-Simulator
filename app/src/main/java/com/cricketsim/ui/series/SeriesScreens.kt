package com.cricketsim.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.AllStadiums
import com.cricketsim.logic.Customisation
import com.cricketsim.logic.CustomFormat
import com.cricketsim.logic.SeriesDef
import com.cricketsim.logic.SeriesKind
import com.cricketsim.logic.SeriesLeg
import com.cricketsim.logic.SeriesRules
import com.cricketsim.logic.SeriesTeamChoice
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.SeriesPlay
import com.cricketsim.persistence.MatchSaveStore
import com.cricketsim.persistence.SeriesStore
import com.cricketsim.persistence.TournamentSaveStore
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading
import com.cricketsim.ui.custom.TextInputPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Home > Play match (patch 146): the two ways to play. Quick match is the setup flow the game has always had; Play series is new.
 */
@Composable
fun PlayMenuScreen(onQuickMatch: () -> Unit, onSeries: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Play match")
        Spacer(modifier = Modifier.height(16.dp))
        OptionButton("Quick match", "One match: choose the format, the teams and the ground, and play it.", true, true) { onQuickMatch() }
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton("Play series", "Play a series of matches between two, three or four teams. Start a new series or resume a saved one.", true, false) { onSeries() }
    }
}

private enum class SeriesPage { Menu, Resume, Detail, Kind, Mode, Formats, Matches, Teams, Stadiums, Summary }

/**
 * Play series (patch 146, part 1). Menu: New series, Resume series (up to five are kept). New series asks, one page at a time: the kind of
 * series (one vs one, triangular, quadrangular), single or multi format, which format(s), how many matches (1 to 27, for each format in a
 * multi-format series), the teams (two, three or four), and the grounds (the countries of the chosen teams first, then the grounds of each
 * country to tick). Then a summary and Save. Playing a saved series is part 2: for now a saved series can be read and deleted.
 *
 * Every page starts with Back, then a heading, then (on the long pages) the Continue button, then the list, so a screen reader reaches
 * the buttons first.
 */
@Composable
fun SeriesFlow(onExit: () -> Unit, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SeriesStore(context) }
    var saved by remember { mutableStateOf<List<SeriesDef>?>(null) }
    LaunchedEffect(Unit) { saved = store.load() }
    val customFormats by produceState(emptyList<CustomFormat>()) { value = withContext(Dispatchers.IO) { Customisation.customFormats() } }
    val options = remember(customFormats) { SeriesRules.formatOptions(customFormats) }
    // Ground names for the summaries, read in the background (listing every ground the game has is slow on the main thread).
    val groundNames by produceState<Map<String, String>>(emptyMap()) { value = withContext(Dispatchers.Default) { AllStadiums.list().associate { it.id to it.name } } }

    var page by remember { mutableStateOf(SeriesPage.Menu) }
    var message by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }

    // The series being set up.
    var kind by remember { mutableStateOf(SeriesKind.ONE_VS_ONE) }
    var multi by remember { mutableStateOf(false) }
    var formatKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var matchIndex by remember { mutableStateOf(0) }
    var countText by remember { mutableStateOf("") }
    var teamKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var stadiumIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // The chosen formats in the order of the format list (the same order every time).
    val orderedKeys = options.map { it.key }.filter { it in formatKeys }

    val toMenu: () -> Unit = { page = SeriesPage.Menu }

    when (page) {
        SeriesPage.Menu -> {
            BackHandler(onBack = onExit)
            val list = saved
            PageColumn {
                BackButton(onExit)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Play series")
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (list == null) {
                    Text("Loading your series. Please wait.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    val full = list.size >= SeriesRules.MAX_SAVED
                    OptionButton(
                        "New series",
                        if (full) "You have ${SeriesRules.MAX_SAVED} saved series, the most you can keep. Delete one in Resume series to make room."
                        else "Set up a one vs one, triangular or quadrangular series. You can keep up to ${SeriesRules.MAX_SAVED} series.",
                        !full, true
                    ) {
                        kind = SeriesKind.ONE_VS_ONE; multi = false; formatKeys = emptyList(); counts = emptyMap(); matchIndex = 0
                        teamKeys = emptyList(); stadiumIds = emptySet(); message = null
                        page = SeriesPage.Kind
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OptionButton(
                        "Resume series",
                        if (list.isEmpty()) "You have no saved series yet." else if (list.size == 1) "1 saved series." else "${list.size} saved series.",
                        list.isNotEmpty(), false
                    ) { message = null; page = SeriesPage.Resume }
                }
            }
        }

        SeriesPage.Resume -> {
            BackHandler(onBack = toMenu)
            PageColumn {
                BackButton(toMenu)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Resume series")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose a series to play or read. Your series stay saved until you delete them.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                val lines = remember(saved, options) { (saved ?: emptyList()).map { it to seriesLine(it, options) } }
                lines.forEach { (s, line) ->
                    OptionButton(s.name, line, true, false) { openId = s.id; page = SeriesPage.Detail }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        SeriesPage.Detail -> {
            val s = (saved ?: emptyList()).find { it.id == openId }
            val back: () -> Unit = { page = if ((saved ?: emptyList()).isEmpty()) SeriesPage.Menu else SeriesPage.Resume }
            BackHandler(onBack = back)
            if (s == null) {
                PageColumn { BackButton(back) }
            } else {
                PageColumn {
                    BackButton(back)
                    Spacer(modifier = Modifier.height(16.dp))
                    PageHeading(s.name)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(remember(s, options, groundNames) { seriesDetail(s, options, groundNames) }, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val def = SeriesPlay.defFor(s)
                            Customisation.saveCustomTournament(def)
                            onPlay("custom:" + def.id)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) { Text("Play this series") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You choose your team, or carry on where you stopped. The series is saved after every match.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Delete this series") }
                }
                if (askDelete) {
                    AlertDialog(
                        onDismissRequest = { askDelete = false },
                        title = { Text("Delete this series?") },
                        text = { Text("${s.name} will be deleted for good.") },
                        confirmButton = {
                            TextButton(onClick = {
                                askDelete = false
                                val updated = (saved ?: emptyList()).filter { it.id != s.id }
                                saved = updated
                                scope.launch {
                                    store.save(updated)
                                    val tid = SeriesPlay.tournamentId(s.id)
                                    Customisation.deleteCustomTournament(tid)
                                    TournamentSaveStore(context, "saved_tournament_$tid.json").delete()
                                    MatchSaveStore(context, "saved_${tid}_match.json").clear()
                                }
                                message = "Series deleted."
                                page = if (updated.isEmpty()) SeriesPage.Menu else SeriesPage.Resume
                            }) { Text("Delete") }
                        },
                        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Keep it") } }
                    )
                }
            }
        }

        SeriesPage.Kind -> {
            BackHandler(onBack = toMenu)
            PageColumn {
                BackButton(toMenu)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Type of series")
                Spacer(modifier = Modifier.height(8.dp))
                Text("What type of series do you want to play?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                SeriesKind.values().forEach { k ->
                    OptionButton(k.label, k.text, true, false) {
                        if (kind != k) teamKeys = emptyList()
                        kind = k
                        page = SeriesPage.Mode
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        SeriesPage.Mode -> {
            val back: () -> Unit = { page = SeriesPage.Kind }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Format of the series")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Do you want to play the whole series in one format, or in more than one?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton("Single format", "Every match of the series is played in the same format.", true, false) { multi = false; formatKeys = formatKeys.take(1); page = SeriesPage.Formats }
                Spacer(modifier = Modifier.height(8.dp))
                OptionButton("Multi format", "Choose more than one format, and how many matches to play in each.", true, false) { multi = true; page = SeriesPage.Formats }
            }
        }

        SeriesPage.Formats -> {
            val back: () -> Unit = { page = SeriesPage.Mode }
            BackHandler(onBack = back)
            val ready = if (multi) formatKeys.size >= 2 else formatKeys.size == 1
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading(if (multi) "Formats" else "Format")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (multi) "Choose two or more formats. You choose the number of matches in each next."
                    else "Choose the format of the series.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (multi) (if (formatKeys.size == 1) "1 format chosen. Choose at least one more." else "${formatKeys.size} formats chosen.")
                    else (if (formatKeys.isEmpty()) "No format chosen yet." else "${SeriesRules.formatName(formatKeys.first(), options)} chosen."),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { matchIndex = 0; countText = counts[orderedKeys.first()]?.toString() ?: ""; page = SeriesPage.Matches },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Continue") }
                Spacer(modifier = Modifier.height(12.dp))
                if (multi) {
                    options.forEach { o ->
                        CheckRow(o.name, o.description, o.key in formatKeys) { on -> formatKeys = if (on) formatKeys + o.key else formatKeys - o.key }
                    }
                } else {
                    Column(modifier = Modifier.selectableGroup()) {
                        options.forEach { o ->
                            RadioRow(o.name, o.description, o.key in formatKeys) { formatKeys = listOf(o.key) }
                        }
                    }
                }
            }
        }

        SeriesPage.Matches -> {
            val index = matchIndex.coerceIn(0, (orderedKeys.size - 1).coerceAtLeast(0))
            val key = orderedKeys.getOrNull(index)
            val back: () -> Unit = {
                if (index == 0) {
                    page = SeriesPage.Formats
                } else {
                    matchIndex = index - 1
                    countText = counts[orderedKeys[index - 1]]?.toString() ?: ""
                }
            }
            if (key == null) {
                BackHandler(onBack = { page = SeriesPage.Formats })
                PageColumn { BackButton { page = SeriesPage.Formats } }
            } else {
                val name = SeriesRules.formatName(key, options)
                key(index) { TextInputPage(
                    title = if (multi) "$name matches" else "Number of matches",
                    hint = if (multi) "How many $name matches do you want to play in the series? Choose from ${SeriesRules.MIN_MATCHES} to ${SeriesRules.MAX_MATCHES}."
                    else "How many matches do you want to play in the series? Choose from ${SeriesRules.MIN_MATCHES} to ${SeriesRules.MAX_MATCHES}.",
                    label = "Number of matches",
                    value = countText,
                    onValueChange = { countText = it.filter(Char::isDigit).take(2) },
                    numeric = true,
                    validate = { v ->
                        val n = v.toIntOrNull()
                        if (n == null || n < SeriesRules.MIN_MATCHES || n > SeriesRules.MAX_MATCHES) "Please type a number from ${SeriesRules.MIN_MATCHES} to ${SeriesRules.MAX_MATCHES}." else null
                    },
                    onContinue = {
                        val n = (countText.toIntOrNull() ?: 1).coerceIn(SeriesRules.MIN_MATCHES, SeriesRules.MAX_MATCHES)
                        counts = counts + (key to n)
                        if (index + 1 < orderedKeys.size) {
                            matchIndex = index + 1
                            countText = counts[orderedKeys[index + 1]]?.toString() ?: ""
                        } else {
                            page = SeriesPage.Teams
                        }
                    },
                    onBack = back
                ) }
            }
        }

        SeriesPage.Teams -> {
            val back: () -> Unit = {
                matchIndex = (orderedKeys.size - 1).coerceAtLeast(0)
                countText = counts[orderedKeys.lastOrNull()]?.toString() ?: ""
                page = SeriesPage.Matches
            }
            BackHandler(onBack = back)
            val choices by produceState<List<SeriesTeamChoice>?>(null) { value = withContext(Dispatchers.Default) { SeriesRules.teamChoices() } }
            val need = kind.teams
            val full = teamKeys.size >= need
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Teams")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose $need teams for the ${kind.label.lowercase()} series.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (full) "You have chosen all $need teams. Untick one to change." else "${teamKeys.size} of $need teams chosen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { page = SeriesPage.Stadiums }, enabled = teamKeys.size == need, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue") }
                Spacer(modifier = Modifier.height(12.dp))
                val list = choices
                if (list == null) {
                    Text("Loading the teams. Please wait.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    var shownCustomHeading = false
                    list.forEachIndexed { i, c ->
                        if (i == 0) {
                            Text("International teams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        }
                        if (c.custom && !shownCustomHeading) {
                            shownCustomHeading = true
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Your custom teams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        }
                        val on = c.key in teamKeys
                        CheckRow(c.team.name, if (c.custom) "Custom team" else "", on, enabled = on || !full) { checked ->
                            teamKeys = if (checked) teamKeys + c.key else teamKeys - c.key
                        }
                    }
                }
            }
        }

        SeriesPage.Stadiums -> {
            val back: () -> Unit = { page = SeriesPage.Teams }
            BackHandler(onBack = back)
            val teams = remember(teamKeys) { SeriesRules.teamsFor(teamKeys) }
            val grounds by produceState<Pair<List<Stadium>, List<String>>?>(null, teamKeys) { value = withContext(Dispatchers.Default) { SeriesRules.groundsFor(teams) } }
            var showAll by remember { mutableStateOf(false) }
            var country by remember { mutableStateOf<String?>(null) }
            val data = grounds
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Grounds")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose the grounds for the series. Pick a country, then tick its grounds. The matches use the grounds you tick in turn.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (stadiumIds.isEmpty()) "No ground chosen yet." else if (stadiumIds.size == 1) "1 ground chosen." else "${stadiumIds.size} grounds chosen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { page = SeriesPage.Summary }, enabled = stadiumIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue") }
                Spacer(modifier = Modifier.height(12.dp))
                if (data == null) {
                    Text("Loading the grounds. Please wait.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    val allCountries = data.first.map { it.country }.distinct().sorted()
                    val countries = if (showAll || data.second.isEmpty()) allCountries else data.second
                    val current = country?.takeIf { it in countries } ?: countries.firstOrNull()
                    Text(
                        if (showAll || data.second.isEmpty()) "Countries" else "Countries of your teams",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Column(modifier = Modifier.selectableGroup()) {
                        countries.forEach { c ->
                            val chosen = data.first.count { it.country == c && it.id in stadiumIds }
                            RadioRow(c, if (chosen == 0) "" else "$chosen chosen", c == current) { country = c }
                        }
                    }
                    if (data.second.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(if (showAll) "Show only the countries of my teams" else "Show grounds from other countries too")
                        }
                    }
                    if (current != null) {
                        val here = data.first.filter { it.country == current }.sortedBy { it.name }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Grounds in $current", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Spacer(modifier = Modifier.height(4.dp))
                        val allOn = here.all { it.id in stadiumIds }
                        OutlinedButton(
                            onClick = { stadiumIds = if (allOn) stadiumIds - here.map { it.id }.toSet() else stadiumIds + here.map { it.id } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        ) { Text(if (allOn) "Clear all grounds in $current" else "Choose all grounds in $current") }
                        here.forEach { s ->
                            CheckRow(s.name, s.city, s.id in stadiumIds) { on -> stadiumIds = if (on) stadiumIds + s.id else stadiumIds - s.id }
                        }
                    }
                }
            }
        }

        SeriesPage.Summary -> {
            val back: () -> Unit = { page = SeriesPage.Stadiums }
            BackHandler(onBack = back)
            val teams = remember(teamKeys) { SeriesRules.teamsFor(teamKeys) }
            val legs = orderedKeys.map { SeriesLeg(it, counts[it] ?: 1) }
            val draftDef = SeriesDef(
                kind = kind.name, multiFormat = multi, legs = legs, teamKeys = teamKeys, stadiumIds = stadiumIds.toList()
            )
            val names = (saved ?: emptyList()).map { it.name }
            val name = SeriesRules.uniqueName(SeriesRules.autoName(kind, teams.map { it.name }), names)
            val room = (saved ?: emptyList()).size < SeriesRules.MAX_SAVED
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Summary")
                Spacer(modifier = Modifier.height(8.dp))
                Text(name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(remember(draftDef, name, options, groundNames) { seriesDetail(draftDef.copy(name = name), options, groundNames) }, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val def = draftDef.copy(id = SeriesRules.newId(), name = name, createdAt = System.currentTimeMillis())
                        val updated = (saved ?: emptyList()) + def
                        saved = updated
                        scope.launch { store.save(updated) }
                        message = "Series saved: $name."
                        page = SeriesPage.Menu
                    },
                    enabled = room && teams.size == kind.teams,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Save series") }
                if (!room) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You already have ${SeriesRules.MAX_SAVED} saved series. Delete one first.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** One line for the list of saved series. */
private fun seriesLine(s: SeriesDef, options: List<com.cricketsim.logic.FormatOption>): String {
    val teams = SeriesRules.teamsFor(s.teamKeys).joinToString(", ") { it.name }
    return SeriesRules.kindOf(s).label + ". " + SeriesRules.legsText(s.legs, options) + ". " + teams + "."
}

/** The whole series in plain sentences. */
private fun seriesDetail(s: SeriesDef, options: List<com.cricketsim.logic.FormatOption>, groundNames: Map<String, String>): String {
    val teams = SeriesRules.teamsFor(s.teamKeys).joinToString(", ") { it.name }
    val total = SeriesRules.totalMatches(s)
    val grounds = groundsText(s.stadiumIds, groundNames)
    return buildString {
        append("Type: ").append(SeriesRules.kindOf(s).label).append(". ")
        append(if (s.multiFormat) "Multi format. " else "Single format. ")
        append("Matches: ").append(SeriesRules.legsText(s.legs, options)).append(". ")
        append(if (total == 1) "1 match in all. " else "$total matches in all. ")
        append("Teams: ").append(teams).append(". ")
        append("Grounds: ").append(grounds).append(".")
    }
}

/** The names of the grounds behind a list of ids: the names when there are few, a count when there are many (or the names are not loaded yet). */
private fun groundsText(ids: List<String>, names: Map<String, String>): String {
    if (ids.isEmpty()) return "none"
    val list = ids.mapNotNull { names[it] }
    if (list.size != ids.size) return if (ids.size == 1) "1 ground" else "${ids.size} grounds"
    return if (list.size <= 4) list.joinToString(", ") else list.take(4).joinToString(", ") + " and ${list.size - 4} more"
}

/** A tickable row: the label and a line of detail, read together as one checkbox. */
@Composable
private fun CheckRow(label: String, detail: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A row of a group where one is chosen: the label and a line of detail, read together as one radio button. */
@Composable
private fun RadioRow(label: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
