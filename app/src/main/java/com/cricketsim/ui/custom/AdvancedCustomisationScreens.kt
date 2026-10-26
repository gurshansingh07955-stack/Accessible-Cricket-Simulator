package com.cricketsim.ui.custom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CustomFormat
import com.cricketsim.persistence.CustomFormatStore
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Home > Advanced Customisation.
 *
 * Three choices: Create Custom Format (built), Create Custom Teams and Players and Create Custom Tournaments
 * (both "coming soon", shown but switched off so the three stay in the same three places). Create Custom Format is
 * a short question-by-question flow: the name (keyboard), the number of overs (keyboard), fielding restrictions
 * yes or no, then, only for yes, how many overs the restriction lasts (keyboard) and how many fielders may stay
 * outside the circle. A summary closes the flow and the format is saved.
 *
 * ACCESSIBILITY, as everywhere in the app: every page starts with its Back button, then a heading; a text box is
 * focused as its page opens (so TalkBack reads it and the keyboard is ready); a mistake is announced as a live
 * region; choices are plain buttons that read their full meaning; nothing relies on sight.
 */
@Composable
fun AdvancedCustomisationFlow(onExit: () -> Unit) {
    // 0 = the menu, 1 = Create Custom Format, 2 = Create Custom Teams and Players
    var section by remember { mutableStateOf(0) }
    when (section) {
        1 -> CustomFormatFlow(onExit = { section = 0 })
        2 -> CustomTeamsFlow(onExit = { section = 0 })
        3 -> CustomTournamentsFlow(onExit = { section = 0 })
        else -> {
            BackHandler(onBack = onExit)
            AdvancedMenu(onCreateFormat = { section = 1 }, onCreateTeams = { section = 2 }, onCreateTournaments = { section = 3 }, onBack = onExit)
        }
    }
}

@Composable
private fun AdvancedMenu(onCreateFormat: () -> Unit, onCreateTeams: () -> Unit, onCreateTournaments: () -> Unit, onBack: () -> Unit) {
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Advanced Customisation")
        Spacer(modifier = Modifier.height(16.dp))
        OptionButton(
            title = "Create Custom Format",
            description = "Design your own match format: its overs and its fielding restrictions.",
            enabled = true,
            emphasised = true,
            onClick = onCreateFormat
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton(
            title = "Create Custom Teams and Players",
            description = "Create your own teams and players, and edit the game's teams and players.",
            enabled = true,
            emphasised = false,
            onClick = onCreateTeams
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton(
            title = "Create Custom Tournaments",
            description = "Design your own tournament, or change the name and format of the game's tournaments.",
            enabled = true,
            emphasised = false,
            onClick = onCreateTournaments
        )
    }
}

private enum class Step { List, Name, Overs, Restrictions, RestrictionOvers, Fielders, Summary }

@Composable
private fun CustomFormatFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CustomFormatStore(context) }
    val scope = rememberCoroutineScope()

    var formats by remember { mutableStateOf<List<CustomFormat>>(emptyList()) }
    var step by remember { mutableStateOf(Step.List) }
    var message by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<CustomFormat?>(null) }

    var name by remember { mutableStateOf("") }
    var oversText by remember { mutableStateOf("") }
    var restrict by remember { mutableStateOf(false) }
    var restrictOversText by remember { mutableStateOf("") }
    var fielders by remember { mutableStateOf(2) }

    LaunchedEffect(Unit) { formats = store.load() }

    fun startNew() {
        name = ""
        oversText = ""
        restrict = false
        restrictOversText = ""
        fielders = 2
        message = null
        step = Step.Name
    }

    val overs = oversText.toIntOrNull() ?: 0
    val restrictOvers = restrictOversText.toIntOrNull() ?: 0
    val draft = CustomFormat(
        id = "", name = name.trim(), overs = overs,
        restrictionsEnabled = restrict, restrictionOvers = if (restrict) restrictOvers else 0, fieldersOutside = fielders
    )

    when (step) {
        Step.List -> {
            BackHandler(onBack = onExit)
            PageColumn {
                BackButton(onExit)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Create Custom Format")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A custom format is kept for custom tournaments. It does not appear on the Play match format screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { startNew() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Create a new format") }
                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Saved formats (${formats.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                if (formats.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No custom formats yet.", style = MaterialTheme.typography.bodyMedium)
                }
                formats.forEach { f ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = f.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = f.description() + " Each bowler can bowl at most ${f.maxOversPerBowler} overs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { toDelete = f },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Delete ${f.name}") }
                }
            }
            toDelete?.let { f ->
                AlertDialog(
                    onDismissRequest = { toDelete = null },
                    title = { Text("Delete format?") },
                    text = { Text("Delete ${f.name}? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            val rest = formats.filter { it.id != f.id }
                            formats = rest
                            message = "Deleted ${f.name}."
                            toDelete = null
                            scope.launch { store.save(rest) }
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
                )
            }
        }

        Step.Name -> TextInputPage(
            title = "Name your format",
            hint = "Type a name for the format, for example Twenty20 or Fifty-over Cup.",
            label = "Format name",
            value = name,
            onValueChange = { name = it.take(CustomFormat.MAX_NAME_LENGTH) },
            numeric = false,
            validate = { v ->
                val t = v.trim()
                when {
                    t.isEmpty() -> "Please type a name."
                    formats.any { it.name.equals(t, ignoreCase = true) } -> "You already have a format called $t. Please choose another name."
                    else -> null
                }
            },
            onContinue = { step = Step.Overs },
            onBack = { step = Step.List }
        )

        Step.Overs -> TextInputPage(
            title = "Number of overs",
            hint = "How many overs does each side bat? Type a number from ${CustomFormat.MIN_OVERS} to ${CustomFormat.MAX_OVERS}.",
            label = "Overs per side",
            value = oversText,
            onValueChange = { oversText = it.filter(Char::isDigit).take(3) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                if (n == null || n < CustomFormat.MIN_OVERS || n > CustomFormat.MAX_OVERS) {
                    "Please type a number from ${CustomFormat.MIN_OVERS} to ${CustomFormat.MAX_OVERS}."
                } else null
            },
            onContinue = { step = Step.Restrictions },
            onBack = { step = Step.Name }
        )

        Step.Restrictions -> {
            BackHandler(onBack = { step = Step.Overs })
            PageColumn {
                BackButton { step = Step.Overs }
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Fielding restrictions")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Should the first overs of each innings have a fielding restriction, a period when only some fielders may stand outside the circle?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { restrict = true; step = Step.RestrictionOvers },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Yes, add fielding restrictions") }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { restrict = false; step = Step.Summary },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("No fielding restrictions") }
            }
        }

        Step.RestrictionOvers -> TextInputPage(
            title = "Overs of restriction",
            hint = "For how many overs at the start of each innings? Type a number from 1 to $overs.",
            label = "Restriction overs",
            value = restrictOversText,
            onValueChange = { restrictOversText = it.filter(Char::isDigit).take(3) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                if (n == null || n < 1 || n > overs) "Please type a number from 1 to $overs." else null
            },
            onContinue = { step = Step.Fielders },
            onBack = { step = Step.Restrictions }
        )

        Step.Fielders -> {
            BackHandler(onBack = { step = Step.RestrictionOvers })
            PageColumn {
                BackButton { step = Step.RestrictionOvers }
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Fielders outside the circle")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "During the first $restrictOvers ${if (restrictOvers == 1) "over" else "overs"}, how many fielders may stay outside the circle?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                for (count in 0..CustomFormat.MAX_FIELDERS_OUTSIDE) {
                    val label = when (count) {
                        0 -> "None: all fielders inside the circle"
                        1 -> "1 fielder outside the circle"
                        else -> "$count fielders outside the circle"
                    }
                    OutlinedButton(
                        onClick = { fielders = count; step = Step.Summary },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) { Text(label) }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Step.Summary -> {
            val back = { step = if (restrict) Step.Fielders else Step.Restrictions }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Check your format")
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(text = draft.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = draft.description(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Each bowler can bowl at most ${draft.maxOversPerBowler} overs (a fifth of the innings, rounded up).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        val saved = draft.copy(id = UUID.randomUUID().toString())
                        val all = formats + saved
                        formats = all
                        message = "Saved ${saved.name}. It is kept for custom tournaments."
                        step = Step.List
                        scope.launch { store.save(all) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Save format") }
            }
        }
    }
}

/** A page with one text box: focused as it opens, a mistake read out as a live region, Continue below. */
@Composable
internal fun TextInputPage(
    title: String,
    hint: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean,
    validate: (String) -> String?,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // The box was not on screen yet; the user can still tap it.
        }
    }
    val submit: () -> Unit = {
        val problem = validate(value)
        if (problem == null) {
            error = null
            onContinue()
        } else {
            error = problem
        }
    }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading(title)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = hint, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = value,
            onValueChange = {
                error = null
                onValueChange(it)
            },
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Continue") }
    }
}

@Composable
internal fun PageColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        content()
    }
}

@Composable
internal fun PageHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) { Text("Back") }
}

/** One menu choice that reads its title and description together; switched off, it is reported as unavailable. */
@Composable
internal fun OptionButton(title: String, description: String, enabled: Boolean, emphasised: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (emphasised) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { content() }
    }
}
