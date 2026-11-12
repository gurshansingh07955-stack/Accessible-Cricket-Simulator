package com.cricketsim.ui.career

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CareerFinance
import com.cricketsim.logic.CareerHealth
import com.cricketsim.logic.CareerInternational
import com.cricketsim.logic.CareerLeagues
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerRecords
import com.cricketsim.logic.CareerSeason
import com.cricketsim.logic.CareerWorld
import com.cricketsim.logic.MoveRequest
import com.cricketsim.logic.TournamentState
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageHeading

/**
 * The career's Career tab (My Player, parts eleven to eighteen). It opens as a menu: groups with a heading (This season, Your career, Money and the papers, Changes), and under each
 * heading a button for every page with its state in one sentence ("2 of 3 reached", "Injured with a hamstring strain..."). A page opens with a Back button (first, so a screen reader
 * reaches it at once), then its heading and its plain sentences.
 */
@Composable
internal fun CareerRecordTab(
    career: CareerProfile,
    seasons: List<TournamentState>?,
    rankLines: List<String>,
    onFocus: (String?) -> Unit,
    onSpecialism: (String?) -> Unit,
    onMove: (MoveRequest?) -> Unit,
    onRetire: () -> Unit
) {
    var page by remember { mutableStateOf<String?>(null) }
    val open = page
    if (open == null) {
        CareerMenu(career, seasons, rankLines) { page = it }
        return
    }
    val back = { page = null }
    when (open) {
        "summary" -> CareerPage("Career summary", back) { CareerHealth.summaryLines(career).forEach { BodyLine(it) } }
        "objectives" -> CareerPage("Season objectives", back) {
            val lines = if (seasons == null) emptyList() else CareerRecords.objectiveLines(career, seasons)
            if (lines.isEmpty()) {
                BodyLine("Objectives appear when the season has loaded.")
            } else {
                lines.forEach { BodyLine(it) }
                BodyLine("Every objective you reach gives +1 to the rating it is about when the season ends.")
            }
        }
        "fitness" -> CareerPage("Fitness", back) {
            BodyLine(CareerHealth.statusLine(career) ?: "Fitness: you are fit and well.")
            BodyLine("Injuries are more likely in Test cricket, for bowlers, after thirty and when you are tired. From 33 your ratings slowly fall with age.")
        }
        "training" -> CareerPage("Training and specialism", back) { TrainingPage(career, onFocus, onSpecialism) }
        "record" -> CareerPage("Career record", back) {
            val record = CareerRecords.recordLines(career)
            if (record.isEmpty()) BodyLine("No matches yet. Your record grows with every match you play.") else record.forEach { BodyLine(it) }
        }
        "honours" -> CareerPage("Honours", back) {
            val honours = career.honours.orEmpty()
            if (honours.isEmpty()) BodyLine("None yet. Win a competition or be Player of the Series.") else honours.reversed().forEach { BodyLine(it) }
        }
        "rankings" -> CareerPage("Rankings", back) {
            if (rankLines.isEmpty()) BodyLine("Working out your rankings.") else rankLines.forEach { BodyLine(it) }
        }
        "captaincy" -> CareerPage("Captaincy", back) { CaptaincyPage(career) }
        "earnings" -> CareerPage("Earnings", back) { CareerFinance.lines(career).forEach { BodyLine(it) } }
        "news" -> CareerPage("In the news", back) {
            val news = career.media.orEmpty()
            if (news.isEmpty()) BodyLine("Nothing yet. The papers write about you when a competition ends.") else news.reversed().forEach { BodyLine(it) }
        }
        "move" -> CareerPage("Move to another country", back) { MovePage(career, onMove) }
        "retire" -> CareerPage("Retirement", back) { RetirementPage(career, seasons, onRetire) }
        else -> CareerMenu(career, seasons, rankLines) { page = it }
    }
}

// ---------------------------------------------------------------- the menu

private fun captaincyStatus(career: CareerProfile): String {
    val parts = ArrayList<String>()
    val role = career.nationalRole
    if (career.isInternational && role != null) parts.add("${role.lowercase()} of ${career.nation}")
    if (career.leagueDecidedSeason == career.season && career.leagueCaptainTeamId != null) parts.add("franchise captain")
    if (!career.atOrAboveState && career.leadership.isNotEmpty()) parts.add("club ${career.leadership.lowercase()}")
    return if (parts.isEmpty()) "No captaincy yet." else parts.joinToString(", ") + "."
}

@Composable
private fun CareerMenu(career: CareerProfile, seasons: List<TournamentState>?, rankLines: List<String>, onOpen: (String) -> Unit) {
    PageHeading("Career")
    Spacer(modifier = Modifier.height(8.dp))
    if (career.retired) {
        GroupHeading("Your career")
        MenuItem("Career summary", "Your career in a few sentences.", "summary", onOpen)
    } else {
        GroupHeading("This season")
        val objectives = if (seasons == null) emptyList() else CareerRecords.objectiveLines(career, seasons)
        MenuItem(
            "Season objectives",
            if (objectives.isEmpty()) "Loading." else "${objectives.count { it.endsWith("done.") }} of ${objectives.size} reached.",
            "objectives", onOpen
        )
        MenuItem("Fitness", CareerHealth.statusLine(career) ?: "You are fit and well.", "fitness", onOpen)
        val spec = when (career.specialism) {
            "Test" -> "Test specialist"
            "White-ball" -> "white-ball specialist"
            else -> "all formats"
        }
        MenuItem("Training and specialism", "Training: ${career.trainingFocus ?: "balanced"}. Format: $spec.", "training", onOpen)
    }
    GroupHeading("Your career")
    val record = CareerRecords.recordLines(career)
    MenuItem("Career record", record.lastOrNull() ?: "No matches yet.", "record", onOpen)
    val honours = career.honours.orEmpty()
    MenuItem("Honours", if (honours.isEmpty()) "None yet." else "${honours.size} ${if (honours.size == 1) "honour" else "honours"}. Latest: ${honours.last()}", "honours", onOpen)
    MenuItem("Rankings", rankLines.firstOrNull() ?: "Working out your rankings.", "rankings", onOpen)
    if (!career.retired) MenuItem("Captaincy", captaincyStatus(career), "captaincy", onOpen)
    GroupHeading("Money and the papers")
    MenuItem("Earnings", "Career earnings: ${CareerFinance.money(career.money)}.", "earnings", onOpen)
    val news = career.media.orEmpty()
    MenuItem("In the news", news.lastOrNull() ?: "Nothing yet.", "news", onOpen)
    if (!career.retired) {
        GroupHeading("Changes")
        if (!career.isInternational && career.internationalMatches == 0 && career.clubs.isNotEmpty()) {
            val pending = career.pendingMove
            MenuItem(
                "Move to another country",
                if (pending != null) "Planned: ${pending.club}, ${pending.country}, at the start of the next season." else "Not planned.",
                "move", onOpen
            )
        }
        MenuItem("Retirement", "You are ${career.age}. Players usually retire between 36 and 42.", "retire", onOpen)
    }
}

@Composable
private fun MenuItem(title: String, description: String, key: String, onOpen: (String) -> Unit) {
    Spacer(modifier = Modifier.height(6.dp))
    OptionButton(title, description, true, false) { onOpen(key) }
}

@Composable
private fun CareerPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    OptionButton("Back to the Career menu", "Return to the list.", true, false) { onBack() }
    Spacer(modifier = Modifier.height(12.dp))
    PageHeading(title)
    Spacer(modifier = Modifier.height(8.dp))
    content()
}

@Composable
private fun GroupHeading(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun BodyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
}

// ---------------------------------------------------------------- the pages

@Composable
private fun TrainingPage(career: CareerProfile, onFocus: (String?) -> Unit, onSpecialism: (String?) -> Unit) {
    BodyLine("Training focus. The skill you work on grows faster after matches, the others a little slower. Now: ${career.trainingFocus ?: "balanced"}.")
    listOf<Pair<String?, String>>(
        null to "Balanced",
        "Batting" to "Batting",
        "Bowling" to "Bowling",
        "Fielding" to "Fielding"
    ).forEach { (value, label) ->
        val chosen = career.trainingFocus == value
        Spacer(modifier = Modifier.height(6.dp))
        OptionButton(
            if (chosen) "$label (chosen)" else label,
            if (value == null) "All three skills grow at the normal rate." else "$label grows faster, the other skills slower.",
            !chosen, false
        ) { onFocus(value) }
    }
    if (career.atOrAboveState) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Format specialism", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(4.dp))
        BodyLine(
            "A specialist is preferred by the selectors in his format (a place in his favour) and less in the other (a place against). " +
                "A white-ball specialist also gets more franchise league offers; a Test specialist gets fewer. Now: ${career.specialism?.let { if (it == "Test") "Test specialist" else "white-ball specialist" } ?: "all formats"}."
        )
        listOf<Pair<String?, String>>(
            null to "All formats",
            "Test" to "Test specialist",
            "White-ball" to "White-ball specialist"
        ).forEach { (value, label) ->
            val chosen = career.specialism == value
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(
                if (chosen) "$label (chosen)" else label,
                when (value) {
                    "Test" -> "Test series are yours; one-day and T20 matches and leagues are harder to get into."
                    "White-ball" -> "One-day and T20 matches and leagues are yours; Test series are harder to get into."
                    else -> "No preference: every format is judged the same."
                },
                !chosen, false
            ) { onSpecialism(value) }
        }
    }
}

@Composable
private fun CaptaincyPage(career: CareerProfile) {
    val club = career.leadershipLine()
    if (club.isNotEmpty() && !career.atOrAboveState) BodyLine(club)
    if (career.isInternational) {
        BodyLine(CareerInternational.captaincyLine(career))
    } else {
        BodyLine("National captaincy: it comes after you have played for your country.")
    }
    if (career.leagueDecidedSeason == career.season && career.leagueCaptainTeamId != null) {
        BodyLine("Franchise captaincy: you are the captain of your franchise this season. You pick the XI and do the toss in its matches.")
    } else {
        BodyLine(
            "Franchise captaincy: after ${CareerLeagues.CAPTAIN_MATCHES} league matches a franchise may offer you its captaincy with its contract, if your form and your standing in its squad are good. " +
                "You have played ${career.leagueMatches} league matches."
        )
    }
}

@Composable
private fun MovePage(career: CareerProfile, onMove: (MoveRequest?) -> Unit) {
    val pending = career.pendingMove
    if (pending != null) {
        BodyLine("Planned: a move to ${pending.club}, ${pending.state}, ${pending.country}. It happens when you start the next season.")
        Spacer(modifier = Modifier.height(6.dp))
        OptionButton("Cancel the move", "Stay where you are.", true, false) { onMove(null) }
        return
    }
    BodyLine(
        "You can move your career to a district club in another country that has domestic cricket in the game. You start again at that club, keeping your ratings, age, money, honours and record. " +
            "The national selectors of the new country want you to have lived there for ${CareerInternational.RESIDENCY_SEASONS} seasons. " +
            "The move happens when you start the next season, and you can move until your first international match."
    )
    var pickCountry by remember { mutableStateOf<String?>(null) }
    var pickState by remember { mutableStateOf<String?>(null) }
    val c = pickCountry
    val st = pickState
    if (c == null) {
        CareerWorld.COUNTRIES.filter { it != career.country }.forEach { country ->
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(country, "Choose a state of $country.", true, false) { pickCountry = country }
        }
    } else if (st == null) {
        BodyLine("Country: $c. Now choose a state.")
        CareerWorld.states(c).forEach { state ->
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(state.name, "Domestic team${if (state.domesticTeams.size > 1) "s" else ""}: ${state.domesticTeams.joinToString(", ")}.", true, false) { pickState = state.name }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OptionButton("Back", "Choose another country.", true, false) { pickCountry = null }
    } else {
        BodyLine("State: $st, $c. Now choose a district club.")
        CareerWorld.clubs(c, st).forEach { club ->
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(club, "Move to this club at the start of the next season.", true, true) {
                onMove(MoveRequest(c, st, club))
                pickCountry = null
                pickState = null
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OptionButton("Back", "Choose another state.", true, false) { pickState = null }
    }
}

@Composable
private fun RetirementPage(career: CareerProfile, seasons: List<TournamentState>?, onRetire: () -> Unit) {
    BodyLine("You are ${career.age}. Players usually retire between 36 and 42, earlier if their ratings fall.")
    var confirm by remember { mutableStateOf(false) }
    val seasonDone = seasons != null && seasons.isNotEmpty() && seasons.all { CareerSeason.isComplete(it) }
    Spacer(modifier = Modifier.height(6.dp))
    if (!confirm) {
        OptionButton(
            "Retire from cricket",
            if (seasonDone) "End your career and see your career summary." else "You can retire when the season is finished.",
            seasonDone, false
        ) { confirm = true }
    } else {
        BodyLine("Are you sure? Retiring ends your career for good.")
        OptionButton("Yes, retire now", "Your career ends and the summary appears here.", true, true) {
            confirm = false
            onRetire()
        }
        Spacer(modifier = Modifier.height(6.dp))
        OptionButton("No, keep playing", "Go back to your career.", true, false) { confirm = false }
    }
}
