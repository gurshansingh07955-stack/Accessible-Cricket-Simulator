package com.cricketsim.ui.custom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.BoundarySize
import com.cricketsim.logic.CustomStadium
import com.cricketsim.logic.CustomStadiums
import com.cricketsim.logic.PitchType
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.StadiumData
import com.cricketsim.logic.WeatherOption
import java.util.UUID

/**
 * Home > Advanced Customisation > Create Custom Stadiums.
 *
 * A question-by-question flow, as in Create Custom Format: the name (keyboard), the type of pitch, the weather, the
 * boundary size, then a summary that shows the average T20 score the game worked out from those three choices, and Save.
 * On the pitch, weather and boundary pages the player who cannot decide can copy that one thing from a ground the game
 * already has (a country list, then the grounds of that country). Saved grounds are listed under the country "Custom" in the
 * quick-match stadium list, in series and in the custom-tournament stadium picker.
 *
 * ACCESSIBILITY as everywhere: every page starts with its Back button, then a heading; the text box is focused as the page
 * opens; a mistake is a live region; choices are plain buttons that read their full meaning; delete asks first.
 */
private enum class SStep { List, Name, Pitch, Weather, Boundary, Summary, CopyCountry, CopyStadium }

private enum class CopyFor { PITCH, WEATHER, BOUNDARY }

private fun signed(n: Int): String = if (n >= 0) "+$n" else "$n"

@Composable
internal fun CustomStadiumsFlow(onExit: () -> Unit) {
    var stadiums by remember { mutableStateOf(CustomStadiums.list()) }
    var step by remember { mutableStateOf(SStep.List) }
    var message by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<CustomStadium?>(null) }

    var name by remember { mutableStateOf("") }
    var pitch by remember { mutableStateOf(PitchType.BALANCED) }
    var boundary by remember { mutableStateOf(BoundarySize.MEDIUM) }
    var weather by remember { mutableStateOf(CustomStadiums.WEATHER_OPTIONS[1]) }
    var copyFor by remember { mutableStateOf(CopyFor.PITCH) }
    var copyCountry by remember { mutableStateOf<String?>(null) }

    fun startNew() {
        name = ""
        pitch = PitchType.BALANCED
        boundary = BoundarySize.MEDIUM
        weather = CustomStadiums.WEATHER_OPTIONS[1]
        message = null
        step = SStep.Name
    }

    fun pageOf(f: CopyFor): SStep = when (f) {
        CopyFor.PITCH -> SStep.Pitch
        CopyFor.WEATHER -> SStep.Weather
        CopyFor.BOUNDARY -> SStep.Boundary
    }

    when (step) {
        SStep.List -> {
            BackHandler(onBack = onExit)
            PageColumn {
                BackButton(onExit)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Create Custom Stadiums")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A custom stadium is listed under the country Custom, in quick matches, in series and when you choose stadiums for a custom tournament.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { startNew() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Create a new stadium") }
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
                    text = "Saved stadiums (${stadiums.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                if (stadiums.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No custom stadiums yet.", style = MaterialTheme.typography.bodyMedium)
                }
                stadiums.forEach { s ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = s.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${CustomStadiums.pitchLabel(s.pitch)}, ${s.weatherText} ${CustomStadiums.boundaryLabel(s.boundary)}. " +
                            "Average T20 score ${s.averageScore}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { toDelete = s },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Delete ${s.name}") }
                }
            }
            toDelete?.let { s ->
                AlertDialog(
                    onDismissRequest = { toDelete = null },
                    title = { Text("Delete stadium?") },
                    text = {
                        Text(
                            "Delete ${s.name}? A saved tournament or series that uses it will be played at a plain neutral ground instead. " +
                                "This cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            CustomStadiums.delete(s.id)
                            stadiums = CustomStadiums.list()
                            message = "Deleted ${s.name}."
                            toDelete = null
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
                )
            }
        }

        SStep.Name -> TextInputPage(
            title = "Name your stadium",
            hint = "Type a name for the stadium, for example Riverside Park or My Backyard Oval.",
            label = "Stadium name",
            value = name,
            onValueChange = { name = it.take(CustomStadiums.MAX_NAME_LENGTH) },
            numeric = false,
            validate = { v ->
                val t = v.trim()
                when {
                    t.isEmpty() -> "Please type a name."
                    stadiums.any { it.name.equals(t, ignoreCase = true) } -> "You already have a stadium called $t. Please choose another name."
                    else -> null
                }
            },
            onContinue = { step = SStep.Pitch },
            onBack = { step = SStep.List }
        )

        SStep.Pitch -> {
            val back = { step = SStep.Name }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Type of pitch")
                Spacer(modifier = Modifier.height(8.dp))
                Text("What kind of pitch does ${name.trim()} have?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                for (p in listOf(PitchType.BATTING, PitchType.BALANCED, PitchType.GREEN, PitchType.DUSTY, PitchType.BOWLING)) {
                    OptionButton(CustomStadiums.pitchLabel(p), CustomStadiums.pitchChoiceText(p), true, pitch == p) {
                        pitch = p
                        step = SStep.Weather
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { copyFor = CopyFor.PITCH; copyCountry = null; step = SStep.CopyCountry },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Not sure? Copy the pitch from a stadium in the game") }
            }
        }

        SStep.Weather -> {
            val back = { step = SStep.Pitch }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Weather")
                Spacer(modifier = Modifier.height(8.dp))
                Text("What is the weather usually like at ${name.trim()}?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                for (w in CustomStadiums.WEATHER_OPTIONS) {
                    OptionButton(w.label, w.detail, true, weather === w) {
                        weather = w
                        step = SStep.Boundary
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { copyFor = CopyFor.WEATHER; copyCountry = null; step = SStep.CopyCountry },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Not sure? Copy the weather from a stadium in the game") }
            }
        }

        SStep.Boundary -> {
            val back = { step = SStep.Weather }
            BackHandler(onBack = back)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Boundary size")
                Spacer(modifier = Modifier.height(8.dp))
                Text("How big is the ground at ${name.trim()}?", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                for (b in listOf(BoundarySize.SMALL, BoundarySize.MEDIUM, BoundarySize.LARGE)) {
                    OptionButton(CustomStadiums.boundaryLabel(b), CustomStadiums.boundaryChoiceText(b), true, boundary == b) {
                        boundary = b
                        step = SStep.Summary
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { copyFor = CopyFor.BOUNDARY; copyCountry = null; step = SStep.CopyCountry },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Not sure? Copy the boundary size from a stadium in the game") }
            }
        }

        SStep.CopyCountry -> {
            val back = { step = pageOf(copyFor) }
            BackHandler(onBack = back)
            val countries = remember { StadiumData.STADIUMS.map { it.country }.distinct().sorted() }
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Choose a country")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Pick the country of a stadium in the game, then the stadium. Only its " +
                        when (copyFor) {
                            CopyFor.PITCH -> "pitch"
                            CopyFor.WEATHER -> "weather"
                            CopyFor.BOUNDARY -> "boundary size"
                        } + " is copied.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                for (c in countries) {
                    OutlinedButton(
                        onClick = { copyCountry = c; step = SStep.CopyStadium },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(c) }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        SStep.CopyStadium -> {
            val back = { step = SStep.CopyCountry }
            BackHandler(onBack = back)
            val country = copyCountry ?: ""
            val grounds = remember(country) { StadiumData.STADIUMS.filter { it.country == country }.sortedBy { it.name } }
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Stadiums in $country")
                Spacer(modifier = Modifier.height(16.dp))
                for (g in grounds) {
                    val what = when (copyFor) {
                        CopyFor.PITCH -> "Pitch: " + g.pitchDescription
                        CopyFor.WEATHER -> "Weather: " + g.climate.description
                        CopyFor.BOUNDARY -> "Boundary size: " + CustomStadiums.boundaryLabel(g.boundarySize).lowercase()
                    }
                    OptionButton(g.name + ", " + g.city, what, true, false) {
                        when (copyFor) {
                            CopyFor.PITCH -> { pitch = g.pitchType; step = SStep.Weather }
                            CopyFor.WEATHER -> { weather = weatherOf(g); step = SStep.Boundary }
                            CopyFor.BOUNDARY -> { boundary = g.boundarySize; step = SStep.Summary }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        SStep.Summary -> {
            val back = { step = SStep.Boundary }
            BackHandler(onBack = back)
            val score = CustomStadiums.score(pitch, boundary, weather)
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Check your stadium")
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(text = name.trim(), style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pitch: ${CustomStadiums.pitchLabel(pitch)}. ${CustomStadiums.pitchText(pitch)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Weather: ${weather.label}. ${weather.detail}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Boundary: ${CustomStadiums.boundaryLabel(boundary)}.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Average T20 first-innings score: ${score.total}.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Worked out from a base of ${score.base}, with ${signed(score.pitch)} for the pitch, " +
                            "${signed(score.boundary)} for the boundary size and ${signed(score.weather)} for the weather, " +
                            "rounded to the nearest 5.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        val saved = CustomStadium(
                            id = CustomStadiums.ID_PREFIX + UUID.randomUUID().toString().take(8),
                            name = name.trim(), pitch = pitch, boundary = boundary,
                            baseRain = weather.rain, humidity = weather.humidity, temperature = weather.temperature,
                            wind = weather.wind, dew = weather.dew, weatherText = weather.text, averageScore = score.total
                        )
                        CustomStadiums.add(saved)
                        stadiums = CustomStadiums.list()
                        message = "Saved ${saved.name}. Find it under the country Custom."
                        step = SStep.List
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Save stadium") }
            }
        }
    }
}

/** The weather of a game ground, as a weather the player can pick. */
private fun weatherOf(g: Stadium): WeatherOption = WeatherOption(
    label = "Like ${g.name}",
    detail = g.climate.description,
    rain = g.climate.baseRainProbability,
    humidity = g.climate.humidityPercent,
    temperature = g.climate.avgTemperatureC,
    wind = g.climate.windKph,
    dew = g.dewFactor,
    text = g.climate.description
)
