package com.cricketsim.ui.career

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerWorld
import com.cricketsim.persistence.CareerStore
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading
import com.cricketsim.ui.custom.TextInputPage
import kotlinx.coroutines.launch
import java.util.UUID
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class CStep { Menu, Resume, Details, Name, Age, Gender, Jersey, Country, State, Club, Role, BatHand, BowlHand, BowlStyle, BatPosition, BowlPhase, Created }

/**
 * Home > My Player: the career mode. PART ONE: the menu (Start new career, Resume career), up to five saved careers, and the
 * steps that make a new player: name, age, gender, jersey number, country, state, district club. The club's players, the
 * matches and the progress are later parts, so a saved career is for now the player's details.
 *
 * Every page begins with its Back button, then a heading (the same pattern as Advanced Customisation, whose page pieces it
 * uses); a mistake is read out as a live region; a choice that cannot be made is shown switched off with its reason.
 */
@Composable
fun CareerFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CareerStore(context) }
    val scope = rememberCoroutineScope()

    var careers by remember { mutableStateOf<List<CareerProfile>?>(null) }
    LaunchedEffect(Unit) { careers = store.load() }

    var step by remember { mutableStateOf(CStep.Menu) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var jerseyText by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<String?>(null) }
    var stateName by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<CareerProfile?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var clubName by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf<String?>(null) }
    var batHand by remember { mutableStateOf<String?>(null) }
    var bowlHand by remember { mutableStateOf<String?>(null) }
    var bowlStyle by remember { mutableStateOf<String?>(null) }
    var batPosition by remember { mutableStateOf<String?>(null) }
    var bowlPhase by remember { mutableStateOf<String?>(null) }
    // The optional profile photo, chosen on the name page (from the gallery or the files); saved with the career.
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
                if (bitmap == null) photoError = "That picture could not be opened." else { photo = bitmap; photoError = null }
            }
        }
    }

    val list = careers
    if (list == null) {
        BackHandler(onBack = onExit)
        PageColumn {
            PageHeading("My Player")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading your careers.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // Makes the career from everything chosen, saves it (and the photo), and shows the confirmation.
    val createCareer: () -> Unit = {
        val chosenCountry = country ?: CareerWorld.COUNTRIES.first()
        val chosenState = stateName ?: ""
        val bowls = CareerWorld.bowls(role)
        val id = UUID.randomUUID().toString()
        val bitmap = photo
        val profile = CareerProfile(
            id = id,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            age = ageText.toIntOrNull() ?: CareerWorld.MIN_AGE,
            gender = "Male",
            jersey = jerseyText.toIntOrNull() ?: 0,
            country = chosenCountry,
            state = chosenState,
            domesticTeams = CareerWorld.findState(chosenCountry, chosenState)?.domesticTeams ?: emptyList(),
            club = clubName ?: "",
            createdAt = System.currentTimeMillis(),
            role = role ?: "",
            battingHand = batHand ?: "",
            bowlingHand = if (bowls) bowlHand ?: "" else "",
            bowlingStyle = if (bowls) bowlStyle ?: "" else "",
            battingPosition = batPosition ?: "",
            bowlingPhase = if (bowls) bowlPhase ?: "" else "",
            hasPhoto = bitmap != null
        )
        val updated = (list + profile).take(CareerWorld.MAX_CAREERS)
        careers = updated
        selected = profile
        step = CStep.Created
        scope.launch {
            if (bitmap != null) withContext(Dispatchers.IO) { savePhoto(context, id, bitmap) }
            store.save(updated)
        }
    }

    when (step) {
        CStep.Menu -> {
            BackHandler(onBack = onExit)
            PageColumn {
                BackButton(onExit)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("My Player")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Create your own player and follow his career. You can keep up to ${CareerWorld.MAX_CAREERS} careers at a time. You have ${list.size}.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                val full = list.size >= CareerWorld.MAX_CAREERS
                OptionButton(
                    title = "Start new career",
                    description = if (full) "You already have ${CareerWorld.MAX_CAREERS} careers. Delete one in Resume career first." else "Create your player.",
                    enabled = !full,
                    emphasised = true
                ) {
                    firstName = ""; lastName = ""; ageText = ""; jerseyText = ""; country = null; stateName = null
                    clubName = null; role = null; batHand = null; bowlHand = null; bowlStyle = null; batPosition = null; bowlPhase = null
                    photo = null; photoError = null
                    step = CStep.Name
                }
                Spacer(modifier = Modifier.height(12.dp))
                OptionButton(
                    title = "Resume career",
                    description = if (list.isEmpty()) "No saved careers yet." else if (list.size == 1) "1 saved career." else "${list.size} saved careers.",
                    enabled = list.isNotEmpty(),
                    emphasised = false
                ) { step = CStep.Resume }
            }
        }

        CStep.Resume -> {
            val back = { step = CStep.Menu }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Resume career")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose the career to open.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                list.forEach { c ->
                    OptionButton(c.fullName, "Age ${c.age}. ${c.country}, ${c.state}. ${c.club}. Jersey number ${c.jersey}.", true, false) {
                        selected = c
                        step = CStep.Details
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.Details -> {
            val career = selected
            val back = { step = if (list.isEmpty()) CStep.Menu else CStep.Resume }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading(career?.fullName ?: "Career")
                Spacer(modifier = Modifier.height(8.dp))
                if (career != null) {
                    CareerPhoto(career)
                    Text(career.describe(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(career.playingStyle(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Domestic team${if (career.domesticTeams.size > 1) "s" else ""} of your state: ${career.domesticTeams.joinToString(", ")}.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Playing for your club comes in the next parts of career mode. For now this career keeps your player's details.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { askDelete = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) { Text("Delete this career") }
                }
            }
            if (askDelete && career != null) {
                AlertDialog(
                    onDismissRequest = { askDelete = false },
                    title = { Text("Delete this career?") },
                    text = { Text("${career.fullName}'s career will be deleted for good. This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            askDelete = false
                            val updated = list.filter { it.id != career.id }
                            careers = updated
                            selected = null
                            step = if (updated.isEmpty()) CStep.Menu else CStep.Resume
                            scope.launch {
                                withContext(Dispatchers.IO) { photoFile(context, career.id).delete() }
                                store.save(updated)
                            }
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Keep it") } }
                )
            }
        }

        CStep.Name -> NamePage(
            first = firstName,
            last = lastName,
            onFirst = { firstName = it },
            onLast = { lastName = it },
            photo = photo,
            photoError = photoError,
            onPickPhoto = { photoPicker.launch("image/*") },
            onRemovePhoto = { photo = null; photoError = null },
            onContinue = { step = CStep.Age },
            onBack = { step = CStep.Menu }
        )

        CStep.Age -> TextInputPage(
            title = "Your age",
            hint = "Type your age, from ${CareerWorld.MIN_AGE} to ${CareerWorld.MAX_AGE}. Ages ${CareerWorld.MIN_AGE} to ${CareerWorld.RECOMMENDED_MAX_AGE} are recommended: they give you the longest career.",
            label = "Age",
            value = ageText,
            onValueChange = { ageText = it.filter(Char::isDigit).take(2) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                if (n == null || n < CareerWorld.MIN_AGE || n > CareerWorld.MAX_AGE) "Please type an age from ${CareerWorld.MIN_AGE} to ${CareerWorld.MAX_AGE}." else null
            },
            onContinue = { step = CStep.Gender },
            onBack = { step = CStep.Name }
        )

        CStep.Gender -> {
            val back = { step = CStep.Age }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your gender")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Male is selected.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton("Male", "Selected. Tap to continue.", true, true) { step = CStep.Jersey }
                Spacer(modifier = Modifier.height(12.dp))
                OptionButton("Female", "Not available yet: the game has no women's cricket.", false, false) { }
            }
        }

        CStep.Jersey -> TextInputPage(
            title = "Jersey number",
            hint = "Choose your shirt number. It can be any number from 0 to ${CareerWorld.MAX_JERSEY}.",
            label = "Jersey number",
            value = jerseyText,
            onValueChange = { jerseyText = it.filter(Char::isDigit).take(3) },
            numeric = true,
            validate = { v ->
                val n = v.toIntOrNull()
                if (n == null || n < 0 || n > CareerWorld.MAX_JERSEY) "Please type a number from 0 to ${CareerWorld.MAX_JERSEY}." else null
            },
            onContinue = { step = CStep.Country },
            onBack = { step = CStep.Gender }
        )

        CStep.Country -> {
            val back = { step = CStep.Jersey }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your country")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose the country you will play for.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.COUNTRIES.forEach { c ->
                    OptionButton(c, "${CareerWorld.states(c).size} states with a domestic team in the game.", true, c == country) {
                        if (country != c) stateName = null
                        country = c
                        step = CStep.State
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.State -> {
            val chosenCountry = country ?: CareerWorld.COUNTRIES.first()
            val back = { step = CStep.Country }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your state")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose your state in $chosenCountry. Each one has a domestic team in the game.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.states(chosenCountry).forEach { s ->
                    OptionButton(s.name, "Domestic team${if (s.domesticTeams.size > 1) "s" else ""}: ${s.domesticTeams.joinToString(", ")}.", true, s.name == stateName) {
                        stateName = s.name
                        step = CStep.Club
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.Club -> {
            val chosenCountry = country ?: CareerWorld.COUNTRIES.first()
            val chosenState = stateName
            val back = { step = CStep.State }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your district club")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose the district club you will start with${if (chosenState != null) " in $chosenState" else ""}.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (chosenState != null) {
                    CareerWorld.clubs(chosenCountry, chosenState).forEach { club ->
                        OptionButton(club, "A club in $chosenState.", true, false) {
                            clubName = club
                            step = CStep.Role
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        CStep.Role -> {
            val back = { step = CStep.Club }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your role")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose your role in the team.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.ROLES.forEach { o ->
                    OptionButton(o.label, o.description, true, o.label == role) {
                        role = o.label
                        step = CStep.BatHand
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.BatHand -> {
            val back = { step = CStep.Role }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your batting hand")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Which hand do you bat with?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.BATTING_HANDS.forEach { h ->
                    OptionButton(h, if (h == "Right-handed") "A right-handed batter." else "A left-handed batter.", true, h == batHand) {
                        batHand = h
                        step = if (CareerWorld.bowls(role)) CStep.BowlHand else CStep.BatPosition
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.BowlHand -> {
            val back = { step = CStep.BatHand }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your bowling hand")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Which arm do you bowl with?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.BOWLING_HANDS.forEach { h ->
                    OptionButton(h, if (h == "Right-arm") "A right-arm bowler." else "A left-arm bowler.", true, h == bowlHand) {
                        if (bowlHand != h) bowlStyle = null
                        bowlHand = h
                        step = CStep.BowlStyle
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.BowlStyle -> {
            val back = { step = CStep.BowlHand }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your bowling style")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose how you bowl: pace, medium pace or a kind of spin.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.bowlingStyles(bowlHand).forEach { o ->
                    OptionButton(o.label, o.description, true, o.label == bowlStyle) {
                        bowlStyle = o.label
                        step = CStep.BatPosition
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.BatPosition -> {
            val back = { step = if (CareerWorld.bowls(role)) CStep.BowlStyle else CStep.BatHand }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your batting position")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Where do you like to bat?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.BATTING_POSITIONS.forEach { o ->
                    OptionButton(o.label, o.description, true, o.label == batPosition) {
                        batPosition = o.label
                        if (CareerWorld.bowls(role)) step = CStep.BowlPhase else createCareer()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.BowlPhase -> {
            val back = { step = CStep.BatPosition }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Your bowling phase")
                Spacer(modifier = Modifier.height(8.dp))
                Text("In which part of the innings do you like to bowl?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                CareerWorld.BOWLING_PHASES.forEach { o ->
                    OptionButton(o.label, o.description, true, o.label == bowlPhase) {
                        bowlPhase = o.label
                        createCareer()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CStep.Created -> {
            val career = selected
            val back = { step = CStep.Menu }
            BackHandler(onBack = back)
            PageColumn {
                PageHeading("Your career is created")
                Spacer(modifier = Modifier.height(8.dp))
                if (career != null) {
                    CareerPhoto(career)
                    Text(
                        text = career.describe(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(career.playingStyle(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "It is saved. You can open it from Resume career. The next parts of career mode, your club's players and your matches, are still to come.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = back, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Back to My Player") }
            }
        }
    }
}

/**
 * First and last name on one page, with an OPTIONAL profile photo from the gallery or the files: the first box is focused as the
 * page opens; a mistake is read out as a live region. The photo can be skipped, changed or removed; Continue never needs one.
 */
@Composable
private fun NamePage(
    first: String,
    last: String,
    onFirst: (String) -> Unit,
    onLast: (String) -> Unit,
    photo: Bitmap?,
    photoError: String?,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val lastFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // The box was not on screen yet; the player can still tap it.
        }
    }
    fun clean(s: String): Boolean = s.isNotBlank() && s.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' }
    val submit: () -> Unit = {
        error = when {
            !clean(first) -> "Please type your first name, using letters only."
            !clean(last) -> "Please type your last name, using letters only."
            else -> null
        }
        if (error == null) onContinue()
    }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Your name")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Type your first name and your last name. A profile photo is optional.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = first,
            onValueChange = { error = null; onFirst(it.take(CareerWorld.MAX_NAME_LENGTH)) },
            label = { Text("First name") },
            singleLine = true,
            isError = error != null && !clean(first),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { try { lastFocus.requestFocus() } catch (e: Exception) { } }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = last,
            onValueChange = { error = null; onLast(it.take(CareerWorld.MAX_NAME_LENGTH)) },
            label = { Text("Last name") },
            singleLine = true,
            isError = error != null && clean(first) && !clean(last),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(lastFocus)
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
        Spacer(modifier = Modifier.height(20.dp))
        Text("Profile photo (optional)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (photo == null) "You can add a photo from your gallery or your files, or skip this." else "Your photo is chosen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (photo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Image(bitmap = photo.asImageBitmap(), contentDescription = "Your profile photo", modifier = Modifier.size(120.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (photo == null) "Choose a profile photo" else "Choose a different photo")
        }
        if (photo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRemovePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Remove the photo") }
        }
        photoError?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue") }
    }
}

/** The career's profile photo, when it has one: decoration with a description, shown above the details. */
@Composable
private fun CareerPhoto(career: CareerProfile) {
    val context = LocalContext.current
    val image = remember(career.id, career.hasPhoto) {
        if (career.hasPhoto) BitmapFactory.decodeFile(photoFile(context, career.id).path)?.asImageBitmap() else null
    }
    if (image != null) {
        Image(bitmap = image, contentDescription = "Profile photo of ${career.fullName}", modifier = Modifier.size(120.dp))
        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun photoFile(context: Context, id: String): File = File(File(context.applicationContext.filesDir, "career_photos"), "$id.png")

/** Reads the picked picture and shrinks it (longest side 384 pixels): a profile photo needs no more. */
private fun loadPhoto(context: Context, uri: Uri): Bitmap? = try {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (raw == null) {
        null
    } else {
        val scale = 384f / maxOf(raw.width, raw.height)
        if (scale < 1f) {
            Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true)
        } else raw
    }
} catch (e: Exception) {
    null
}

private fun savePhoto(context: Context, id: String, bitmap: Bitmap) {
    try {
        val f = photoFile(context, id)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (e: Exception) {
        // The photo is only decoration; the career is saved either way.
    }
}
