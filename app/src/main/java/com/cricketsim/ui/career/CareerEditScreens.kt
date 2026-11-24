package com.cricketsim.ui.career

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CareerEdit
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerWorld
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading
import com.cricketsim.ui.custom.TextInputPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CareerEditStep { List, Jersey, Photo, Role, BatHand, BowlHand, BowlStyle, BatPosition, BatNumber, BowlPhase, FieldPos, Difficulty, RateBat, RateBowl, RateField }

/**
 * My Player > Player profile > Edit career (patch 143). A list of everything the player may change, each row reading its present value;
 * a row opens a page of plain choices (or a number box), and the choice brings him back to the list. Nothing is kept until he taps
 * "Save changes" (just under the heading, above the long list, so a screen reader reaches it first); leaving with unsaved changes asks first.
 * His name, age, country, state, club, school and college are not here: they cannot be changed. The photo is saved as soon as it is chosen.
 *
 * Choosing a role that adds bowling (or takes the wicketkeeping away) leads on through the pages that role needs, then back to the list.
 */
@Composable
internal fun CareerEditFlow(career: CareerProfile, onSave: (CareerProfile) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(CareerEdit.draftOf(career)) }
    var step by remember { mutableStateOf(CareerEditStep.List) }
    var chain by remember { mutableStateOf<List<CareerEditStep>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    var photoVersion by remember { mutableStateOf(0) }
    var photoImage by remember { mutableStateOf<Bitmap?>(null) }

    // The photo is read off the main thread (a picture file can be large to decode).
    LaunchedEffect(photoVersion, career.hasPhoto, career.id) {
        photoImage = if (career.hasPhoto) withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photoFile(context, career.id).path) } else null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
                if (bitmap == null) {
                    message = "That picture could not be opened."
                } else {
                    withContext(Dispatchers.IO) { savePhoto(context, career.id, bitmap) }
                    photoVersion++
                    message = "Your photo is saved."
                    onSave(career.copy(hasPhoto = true))
                }
            }
        }
    }

    val changed = draft != CareerEdit.draftOf(career)
    val go: (CareerEditStep) -> Unit = { message = null; step = it }
    val start: (List<CareerEditStep>) -> Unit = { steps -> message = null; chain = steps.drop(1); step = steps.first() }
    val next: () -> Unit = {
        val rest = chain
        if (rest.isEmpty()) {
            go(CareerEditStep.List)
        } else {
            chain = rest.drop(1)
            go(rest.first())
        }
    }
    val toList: () -> Unit = { chain = emptyList(); go(CareerEditStep.List) }
    val leave: () -> Unit = { if (changed) askDiscard = true else onBack() }
    val save: () -> Unit = {
        val updated = CareerEdit.apply(career, draft)
        onSave(updated)
        draft = CareerEdit.draftOf(updated)
        message = "Your changes are saved."
    }
    val bowls = CareerWorld.bowls(draft.role)

    when (step) {
        CareerEditStep.List -> {
            BackHandler(onBack = leave)
            PageColumn {
                BackButton(leave)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Edit career")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Change your playing choices and ratings. Your name, age, country, state, club, school and college cannot be changed. A new role, rating or batting number changes how the selectors judge you from your next match.",
                    style = MaterialTheme.typography.bodyMedium
                )
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = save, enabled = changed, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (changed) "Save changes" else "No changes to save")
                }
                Spacer(modifier = Modifier.height(12.dp))
                EditRow("Profile photo", if (career.hasPhoto) "You have a photo. Tap to change or remove it." else "No photo. Tap to add one.") { go(CareerEditStep.Photo) }
                EditRow("Jersey number", "${draft.jersey}. Tap to change.") { text = draft.jersey.toString(); go(CareerEditStep.Jersey) }
                EditRow("Role", "${draft.role.ifEmpty { "Not chosen" }}. Tap to change.") { go(CareerEditStep.Role) }
                EditRow("Batting hand", "${draft.battingHand.ifEmpty { "Not chosen" }}. Tap to change.") { go(CareerEditStep.BatHand) }
                if (bowls) {
                    EditRow("Bowling arm", "${draft.bowlingHand.ifEmpty { "Not chosen" }}. Tap to change.") { go(CareerEditStep.BowlHand) }
                    EditRow("Bowling style", "${draft.bowlingStyle.ifEmpty { "Not chosen" }}. Tap to change.") { go(CareerEditStep.BowlStyle) }
                    EditRow("Bowling phase", "${draft.bowlingPhase.ifEmpty { "Not chosen" }}. Tap to change.") { go(CareerEditStep.BowlPhase) }
                } else {
                    Text(
                        text = "You do not bowl, so there are no bowling choices. Choose a bowling role to add them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                EditRow("Batting position and number", "${draft.battingPosition.ifEmpty { "Not chosen" }}, number ${draft.battingNumber}. Tap to change.") { go(CareerEditStep.BatPosition) }
                if (draft.role == "Wicketkeeper-batsman") {
                    OptionButton("Fielding position", "Wicketkeeper. Set by your role.", false, false) { }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    EditRow("Fielding position", "${draft.fieldingPosition.ifEmpty { "Not chosen" }}. Tap to change.") { go(CareerEditStep.FieldPos) }
                }
                EditRow("Career difficulty", "${draft.difficulty.ifEmpty { "Not chosen" }}. Tap to change.") { go(CareerEditStep.Difficulty) }
                EditRow("Batting rating", "${draft.batting}. Ratings run from 1 to 95. Tap to change.") { text = draft.batting.toString(); go(CareerEditStep.RateBat) }
                EditRow("Bowling rating", "${draft.bowling}. Ratings run from 1 to 95. Tap to change.") { text = draft.bowling.toString(); go(CareerEditStep.RateBowl) }
                EditRow("Fielding rating", "${draft.fielding}. Ratings run from 1 to 95. Tap to change.") { text = draft.fielding.toString(); go(CareerEditStep.RateField) }
            }
        }

        CareerEditStep.Photo -> {
            BackHandler(onBack = toList)
            PageColumn {
                BackButton(toList)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Profile photo")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (career.hasPhoto) "You have a profile photo." else "You have no profile photo. You can add one from your gallery or your files.",
                    style = MaterialTheme.typography.bodyLarge
                )
                val image = photoImage
                if (career.hasPhoto && image != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(bitmap = image.asImageBitmap(), contentDescription = "Your profile photo", modifier = Modifier.size(120.dp))
                }
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (career.hasPhoto) "Choose a different photo" else "Choose a profile photo")
                }
                if (career.hasPhoto) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { photoFile(context, career.id).delete() }
                                photoImage = null
                                message = "Your photo is removed."
                                onSave(career.copy(hasPhoto = false))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) { Text("Remove the photo") }
                }
            }
        }

        CareerEditStep.Jersey -> TextInputPage(
            title = "Jersey number",
            hint = "Choose your shirt number. It can be any number from 0 to ${CareerWorld.MAX_JERSEY}.",
            label = "Jersey number",
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(3) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                if (n == null || n < 0 || n > CareerWorld.MAX_JERSEY) "Please type a number from 0 to ${CareerWorld.MAX_JERSEY}." else null
            },
            onContinue = { draft = draft.copy(jersey = (text.toIntOrNull() ?: draft.jersey).coerceIn(0, CareerWorld.MAX_JERSEY)); next() },
            onBack = toList
        )

        CareerEditStep.Role -> ChoicePage("Your role", "Choose your role in the team.", CareerWorld.ROLES, draft.role, toList) { _, role ->
            var d = draft.copy(role = role)
            val steps = ArrayList<CareerEditStep>()
            if (CareerWorld.bowls(role) && draft.bowlingStyle.isEmpty()) {
                steps.add(CareerEditStep.BowlHand)
                steps.add(CareerEditStep.BowlStyle)
                steps.add(CareerEditStep.BowlPhase)
            }
            if (role == "Wicketkeeper-batsman") {
                d = d.copy(fieldingPosition = CareerWorld.WICKETKEEPER)
            } else if (draft.fieldingPosition == CareerWorld.WICKETKEEPER || draft.fieldingPosition.isEmpty()) {
                steps.add(CareerEditStep.FieldPos)
            }
            draft = d
            if (steps.isEmpty()) toList() else start(steps)
        }

        CareerEditStep.BatHand -> ChoicePage(
            "Your batting hand", "Which hand do you bat with?",
            CareerWorld.BATTING_HANDS.map { CareerWorld.Option(it, if (it == "Right-handed") "A right-handed batter." else "A left-handed batter.") },
            draft.battingHand, toList
        ) { _, h -> draft = draft.copy(battingHand = h); next() }

        CareerEditStep.BowlHand -> ChoicePage(
            "Your bowling arm", "Which arm do you bowl with?",
            CareerWorld.BOWLING_HANDS.map { CareerWorld.Option(it, if (it == "Right-arm") "A right-arm bowler." else "A left-arm bowler.") },
            draft.bowlingHand, toList
        ) { _, h ->
            draft = if (draft.bowlingHand == h) draft.copy(bowlingHand = h) else draft.copy(bowlingHand = h, bowlingStyle = "")
            chain = chain.filter { it != CareerEditStep.BowlStyle }
            go(CareerEditStep.BowlStyle)
        }

        CareerEditStep.BowlStyle -> ChoicePage(
            "Your bowling style", "Choose how you bowl: pace, medium pace or a kind of spin.",
            CareerWorld.bowlingStyles(draft.bowlingHand), draft.bowlingStyle, toList
        ) { _, style -> draft = draft.copy(bowlingStyle = style); next() }

        CareerEditStep.BatPosition -> ChoicePage(
            "Your batting position", "Where do you like to bat? You choose your number next.",
            CareerWorld.BATTING_POSITIONS, draft.battingPosition, toList
        ) { _, position ->
            val numbers = CareerWorld.battingNumbers(position)
            draft = draft.copy(battingPosition = position, battingNumber = if (draft.battingNumber in numbers) draft.battingNumber else numbers.first())
            go(CareerEditStep.BatNumber)
        }

        CareerEditStep.BatNumber -> {
            val numbers = CareerWorld.battingNumbers(draft.battingPosition)
            ChoicePage(
                "Your batting number", "You chose ${draft.battingPosition.lowercase()}. Which number do you want to bat at?",
                numbers.map { CareerWorld.Option("Number $it", CareerWorld.numberDescription(it)) },
                "Number ${draft.battingNumber}", toList
            ) { index, _ -> draft = draft.copy(battingNumber = numbers[index]); next() }
        }

        CareerEditStep.BowlPhase -> ChoicePage(
            "Your bowling phase", "In which part of the innings do you like to bowl?",
            CareerWorld.BOWLING_PHASES, draft.bowlingPhase, toList
        ) { _, phase -> draft = draft.copy(bowlingPhase = phase); next() }

        CareerEditStep.FieldPos -> ChoicePage(
            "Your fielding position", "Where do you like to field?",
            CareerWorld.FIELDING_POSITIONS, draft.fieldingPosition, toList
        ) { _, position -> draft = draft.copy(fieldingPosition = position); next() }

        CareerEditStep.Difficulty -> ChoicePage(
            "Career difficulty",
            "How hard should it be to be selected as you move up the levels? The easier the difficulty, the more easily the selectors pick you; on Legend it is very hard.",
            CareerWorld.DIFFICULTIES, draft.difficulty, toList
        ) { _, level -> draft = draft.copy(difficulty = level); next() }

        CareerEditStep.RateBat -> RatingInput("Batting rating", text, { text = it }, { n -> draft = draft.copy(batting = n); next() }, toList)
        CareerEditStep.RateBowl -> RatingInput("Bowling rating", text, { text = it }, { n -> draft = draft.copy(bowling = n); next() }, toList)
        CareerEditStep.RateField -> RatingInput("Fielding rating", text, { text = it }, { n -> draft = draft.copy(fielding = n); next() }, toList)
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Leave without saving?") },
            text = { Text("You changed your career but did not save. Your changes will be lost if you leave.") },
            confirmButton = { TextButton(onClick = { askDiscard = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing") } }
        )
    }
}

/** One row of the edit list: the thing and its present value, read together. */
@Composable
private fun EditRow(title: String, description: String, onClick: () -> Unit) {
    OptionButton(title, description, true, false, onClick)
    Spacer(modifier = Modifier.height(8.dp))
}

/** A page of plain choices, the present one marked "Selected". [onChoose] gets the choice's place in the list and its label. */
@Composable
private fun ChoicePage(
    title: String,
    intro: String,
    options: List<CareerWorld.Option>,
    current: String?,
    onBack: () -> Unit,
    onChoose: (Int, String) -> Unit
) {
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading(title)
        Spacer(modifier = Modifier.height(8.dp))
        Text(intro, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        options.forEachIndexed { index, o ->
            val on = o.label == current
            OptionButton(o.label, if (on) "Selected. ${o.description}" else o.description, true, on) { onChoose(index, o.label) }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** A rating from 1 to 95 typed into a number box (focused as the page opens, a mistake read out). */
@Composable
private fun RatingInput(title: String, value: String, onValue: (String) -> Unit, onDone: (Int) -> Unit, onBack: () -> Unit) {
    TextInputPage(
        title = title,
        hint = "Type your ${title.lowercase()}, from 1 to 95. It grows as you play, and your selectors and your matches use it.",
        label = title,
        value = value,
        onValueChange = { onValue(it.filter(Char::isDigit).take(2)) },
        numeric = true,
        validate = { v ->
            val n = v.toIntOrNull()
            if (n == null || n < 1 || n > 95) "Please type a number from 1 to 95." else null
        },
        onContinue = { onDone((value.toIntOrNull() ?: 1).coerceIn(1, 95)) },
        onBack = onBack
    )
}
