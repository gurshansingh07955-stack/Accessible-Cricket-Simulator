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

private enum class Step { List, Name, Days, Overs, Innings, Declarations, FollowOn, FollowOnRuns, NewBall, NewBallOvers, Restrictions, RestrictionOvers, Fielders, Summary }

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
    // The multi-day settings (a format of two to five days plays like a Test match).
    var days by remember { mutableStateOf(1) }
    var testInnings by remember { mutableStateOf(4) }
    var declarations by remember { mutableStateOf(true) }
    var followOnOn by remember { mutableStateOf(true) }
    var followOnText by remember { mutableStateOf("200") }
    var newBallOn by remember { mutableStateOf(true) }
    var newBallText by remember { mutableStateOf("80") }

    LaunchedEffect(Unit) { formats = store.load() }

    fun startNew() {
        name = ""
        oversText = ""
        restrict = false
        restrictOversText = ""
        fielders = 2
        days = 1
        testInnings = 4
        declarations = true
        followOnOn = true
        followOnText = "200"
        newBallOn = true
        newBallText = "80"
        message = null
        step = Step.Name
    }

    val overs = oversText.toIntOrNull() ?: 0
    val restrictOvers = restrictOversText.toIntOrNull() ?: 0
    val draft = CustomFormat(
        id = "", name = name.trim(), overs = overs,
        restrictionsEnabled = days == 1 && restrict, restrictionOvers = if (days == 1 && restrict) restrictOvers else 0, fieldersOutside = fielders,
        days = days, testInnings = testInnings, declarations = declarations,
        followOnEnabled = followOnOn, followOnLead = followOnText.toIntOrNull() ?: 200,
        newBallEnabled = newBallOn, newBallOvers = newBallText.toIntOrNull() ?: 80
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
                        text = f.description() + (if (f.isMultiDay) "" else " Each bowler can bowl at most ${f.maxOversPerBowler} overs."),
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
            onContinue = { step = Step.Days },
            onBack = { step = Step.List }
        )

        Step.Days -> {
            val back = { step = Step.Name }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Number of days")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "How many days does a match last? One day is a limited-overs match. More than one day is played like a Test match, with sessions.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                for (n in 1..CustomFormat.MAX_DAYS) {
                    OptionButton(
                        title = if (n == 1) "1 day (the default)" else "$n days",
                        description = if (n == 1) "A limited-overs match with the overs you choose next." else "A multi-day match with lunch, tea and stumps every day.",
                        enabled = true,
                        emphasised = n == days,
                        onClick = { days = n; step = Step.Overs }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Step.Overs -> TextInputPage(
            title = if (days > 1) "Overs per day" else "Number of overs",
            hint = if (days > 1) {
                "How many overs are bowled each day? Type a number from ${CustomFormat.MIN_OVERS_PER_DAY} to ${CustomFormat.MAX_OVERS_PER_DAY}. Each day has three sessions: lunch, tea and stumps."
            } else {
                "How many overs does each side bat? Type a number from ${CustomFormat.MIN_OVERS} to ${CustomFormat.MAX_OVERS}."
            },
            label = if (days > 1) "Overs per day" else "Overs per side",
            value = oversText,
            onValueChange = { oversText = it.filter(Char::isDigit).take(3) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                val low = if (days > 1) CustomFormat.MIN_OVERS_PER_DAY else CustomFormat.MIN_OVERS
                val high = if (days > 1) CustomFormat.MAX_OVERS_PER_DAY else CustomFormat.MAX_OVERS
                if (n == null || n < low || n > high) "Please type a number from $low to $high." else null
            },
            onContinue = { step = if (days > 1) Step.Innings else Step.Restrictions },
            onBack = { step = Step.Days }
        )

        Step.Innings -> {
            val back = { step = Step.Overs }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Number of innings")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "How many innings does the match have? Two innings is one for each side. Four innings is two for each side, as in a Test match.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton("Two innings", "One innings for each side. There is no follow-on.", true, testInnings == 2) { testInnings = 2; step = Step.Declarations }
                Spacer(modifier = Modifier.height(8.dp))
                OptionButton("Four innings", "Two innings for each side, as in a Test match.", true, testInnings == 4) { testInnings = 4; step = Step.Declarations }
            }
        }

        Step.Declarations -> {
            val back = { step = Step.Innings }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Declarations")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Can a batting captain declare an innings closed before the side is all out?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton("Declarations on", "A captain may declare, and the computer's captain does too.", true, declarations) {
                    declarations = true
                    step = if (testInnings == 4) Step.FollowOn else Step.NewBall
                }
                Spacer(modifier = Modifier.height(8.dp))
                OptionButton("Declarations off", "Every innings ends only when the side is all out or time runs out.", true, !declarations) {
                    declarations = false
                    step = if (testInnings == 4) Step.FollowOn else Step.NewBall
                }
            }
        }

        Step.FollowOn -> {
            val back = { step = Step.Declarations }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Follow-on")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Can the side that bats first make the other side follow on when it leads by enough runs?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton("Follow-on on", "You choose how many runs of lead are needed next.", true, followOnOn) { followOnOn = true; step = Step.FollowOnRuns }
                Spacer(modifier = Modifier.height(8.dp))
                OptionButton("Follow-on off", "Each side always bats in turn.", true, !followOnOn) { followOnOn = false; step = Step.NewBall }
            }
        }

        Step.FollowOnRuns -> TextInputPage(
            title = "Runs for the follow-on",
            hint = "After how many runs of lead after the first innings may the follow-on be enforced? Type a number from 1 to ${CustomFormat.MAX_FOLLOW_ON_LEAD}. A Test match uses 200.",
            label = "Runs of lead",
            value = followOnText,
            onValueChange = { followOnText = it.filter(Char::isDigit).take(3) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                if (n == null || n < 1 || n > CustomFormat.MAX_FOLLOW_ON_LEAD) "Please type a number from 1 to ${CustomFormat.MAX_FOLLOW_ON_LEAD}." else null
            },
            onContinue = { step = Step.NewBall },
            onBack = { step = Step.FollowOn }
        )

        Step.NewBall -> {
            val back = { step = if (testInnings == 4) (if (followOnOn) Step.FollowOnRuns else Step.FollowOn) else Step.Declarations }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("New ball")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Can the fielding side take a new ball once the old one has been used for a number of overs?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton("New ball on", "You choose after how many overs it is available next.", true, newBallOn) { newBallOn = true; step = Step.NewBallOvers }
                Spacer(modifier = Modifier.height(8.dp))
                OptionButton("New ball off", "The same ball is used for the whole innings.", true, !newBallOn) { newBallOn = false; step = Step.Summary }
            }
        }

        Step.NewBallOvers -> TextInputPage(
            title = "Overs for the new ball",
            hint = "After how many overs with the old ball is the new ball available? Type a number from 1 to ${maxOf(overs * days, 1)}. A Test match uses 80.",
            label = "Overs with the old ball",
            value = newBallText,
            onValueChange = { newBallText = it.filter(Char::isDigit).take(3) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                val high = maxOf(overs * days, 1)
                if (n == null || n < 1 || n > high) "Please type a number from 1 to $high." else null
            },
            onContinue = { step = Step.Summary },
            onBack = { step = Step.NewBall }
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
            val back = { step = if (days > 1) (if (newBallOn) Step.NewBallOvers else Step.NewBall) else if (restrict) Step.Fielders else Step.Restrictions }
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
                    if (!draft.isMultiDay) Text(
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
