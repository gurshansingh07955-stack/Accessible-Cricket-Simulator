package com.cricketsim.ui.career

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CareerFinance
import com.cricketsim.logic.CareerNews
import com.cricketsim.logic.CareerShop
import com.cricketsim.logic.CareerHealth
import com.cricketsim.logic.CareerLeadership
import com.cricketsim.logic.CareerInternational
import com.cricketsim.logic.CareerLeagues
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerRecords
import com.cricketsim.logic.CareerSeason
import com.cricketsim.logic.CareerStatistics
import com.cricketsim.logic.DetailedRecord
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
    onRetire: () -> Unit,
    onCareer: (CareerProfile) -> Unit,
    onLeaveLeague: (String) -> String
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
        "record" -> CareerPage("Career record", back) { RecordPage(career) }
        "honours" -> CareerPage("Honours", back) {
            val honours = career.honours.orEmpty()
            if (honours.isEmpty()) BodyLine("None yet. Win a competition or be Player of the Series.") else honours.reversed().forEach { BodyLine(it) }
        }
        "rankings" -> CareerPage("Rankings", back) {
            if (rankLines.isEmpty()) BodyLine("Working out your rankings.") else rankLines.forEach { BodyLine(it) }
        }
        "captaincy" -> CareerPage("Captaincy", back) { CaptaincyPage(career, seasons, onCareer) }
        "earnings" -> CareerPage("Earnings", back) { EarningsPage(career, onCareer) }
        "leagues" -> CareerPage("League contracts", back) { LeagueContractsPage(career, seasons, onLeaveLeague, onCareer) }
        "news" -> CareerPage("In the news", back) { NewsPage(career) }
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
    if (career.isLeagueCaptain) parts.add("franchise captain")
    if (career.stateRole.isNotEmpty()) parts.add("state ${career.stateRole.lowercase()}")
    if (career.leadership.isNotEmpty()) parts.add("${if (career.stage == "School") "school" else if (career.stage == "College") "college" else "club"} ${career.leadership.lowercase()}")
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
    MenuItem("Career record", CareerStatistics.headline(career), "record", onOpen)
    val honours = career.honours.orEmpty()
    MenuItem("Honours", if (honours.isEmpty()) "None yet." else "${honours.size} ${if (honours.size == 1) "honour" else "honours"}. Latest: ${honours.last()}", "honours", onOpen)
    MenuItem("Rankings", rankLines.firstOrNull() ?: "Working out your rankings.", "rankings", onOpen)
    if (!career.retired) MenuItem("Captaincy", captaincyStatus(career), "captaincy", onOpen)
    GroupHeading("Money and the papers")
    MenuItem("Earnings", "Career earnings: ${CareerFinance.money(career.money)}. The shop is here too.", "earnings", onOpen)
    if (!career.retired && (career.atOrAboveState || career.leagueOffers.orEmpty().isNotEmpty() || CareerFinance.signedThisSeason(career).isNotEmpty())) {
        MenuItem("League contracts", CareerFinance.contractsLine(career), "leagues", onOpen)
    }
    val news = CareerNews.all(career)
    MenuItem("In the news", news.lastOrNull()?.text ?: "Nothing yet.", "news", onOpen)
    if (!career.retired) {
        GroupHeading("Changes")
        if (!career.isInternational && career.internationalMatches == 0 && career.clubs.isNotEmpty() && career.stage.isEmpty()) {
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

/** Earnings: two tabs, Earnings (the money) and Shop (gloves and pads that cut the chance of an injury). Buying asks first. */
@Composable
private fun EarningsPage(career: CareerProfile, onCareer: (CareerProfile) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var message by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("Earnings", "Shop").forEachIndexed { i, label ->
            val chosen = i == tab
            val modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { role = Role.Tab; selected = chosen }
            if (chosen) Button(onClick = { tab = i }, modifier = modifier) { Text(label) }
            else OutlinedButton(onClick = { tab = i }, modifier = modifier) { Text(label) }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (tab == 0) {
        CareerFinance.lines(career).forEach { BodyLine(it) }
    } else {
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(modifier = Modifier.height(8.dp))
        }
        CareerShop.statusLines(career).forEach { BodyLine(it) }
        Spacer(modifier = Modifier.height(8.dp))
        val pending = confirm
        if (pending != null) {
            val item = pending.first
            val tier = pending.second
            BodyLine("Buy ${CareerShop.tierName(tier)} $item for ${CareerFinance.money(CareerShop.price(item, tier))}?")
            Button(
                onClick = {
                    val bought = CareerShop.buy(career, item, tier)
                    message = if (bought === career) "That could not be bought."
                    else "You bought ${CareerShop.tierName(tier)} $item. You have ${CareerFinance.money(bought.money)} left."
                    confirm = null
                    if (bought !== career) onCareer(bought)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Yes, buy") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { confirm = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Cancel") }
            Spacer(modifier = Modifier.height(16.dp))
        }
        listOf("gloves", "pads").forEach { item ->
            GroupHeading(item.replaceFirstChar { it.uppercase() })
            (1..3).forEach { tier ->
                val reason = CareerShop.canBuy(career, item, tier)
                OptionButton(
                    "${CareerShop.tierName(tier)} $item, ${CareerFinance.money(CareerShop.price(item, tier))}",
                    (if (reason != null) "$reason " else "") + CareerShop.blurb(tier),
                    reason == null,
                    false
                ) { confirm = Pair(item, tier) }
            }
        }
    }
}

/** The news: two tabs, All (about you and world cricket) and My news; the newest first. */
@Composable
private fun NewsPage(career: CareerProfile) {
    var tab by remember { mutableStateOf(0) }
    val all = CareerNews.all(career)
    val shown = (if (tab == 0) all else all.filter { it.mine }).reversed()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("All", "My news").forEachIndexed { i, label ->
            val chosen = i == tab
            val modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { role = Role.Tab; selected = chosen }
            if (chosen) Button(onClick = { tab = i }, modifier = modifier) { Text(label) }
            else OutlinedButton(onClick = { tab = i }, modifier = modifier) { Text(label) }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (shown.isEmpty()) {
        BodyLine(if (tab == 0) "Nothing yet. The news is written after every match." else "Nothing about you yet. Play or simulate a match.")
    } else {
        shown.forEach { BodyLine(it.text) }
    }
}

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
private fun CaptaincyPage(career: CareerProfile, seasons: List<TournamentState>?, onCareer: (CareerProfile) -> Unit) {
    var confirm by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf("") }
    if (result.isNotEmpty()) {
        Text(result, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(modifier = Modifier.height(8.dp))
    }
    // Every captaincy he can hold: school, college or club; the state squad; the nation; a franchise (CareerLeadership).
    CareerLeadership.lines(career).forEach { BodyLine(it) }

    // The nation: give the role up, or ask the selectors for it back (part twenty-nine).
    val role = career.nationalRole
    val savedNational = career.nationalRoleLeft
    if (career.isInternational && role != null) {
        GroupHeading("National captaincy")
        val word = role.lowercase()
        if (confirm == "national") {
            BodyLine("Are you sure? You give up the $word of ${career.nation}, and the selectors will not name you again until you ask them.")
            OptionButton("Yes, give up the $word", "You stay in the national squad as a player.", true, true) {
                val c = CareerInternational.stepDown(career)
                // The side needs a captain: its best-rated player takes over.
                val intl = seasons.orEmpty().firstOrNull { CareerInternational.isInternational(it) }
                val next = intl?.let { CareerSeason.nextCaptainName(it, career, it.userTeamId) }
                result = c.text + (if (next != null) " $next, the best-rated player in the squad, is the new $word of ${career.nation}." else "")
                confirm = null
                onCareer(c.career)
            }
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton("No, keep the $word", "Nothing changes.", true, false) { confirm = null }
        } else {
            OptionButton("Give up the $word of ${career.nation}", "Step down. You can ask for it back here.", true, false) { confirm = "national" }
        }
        Spacer(modifier = Modifier.height(8.dp))
    } else if (career.isInternational && savedNational != null && savedNational.substringAfter("|") == career.nation) {
        GroupHeading("National captaincy")
        val saved = savedNational.substringBefore("|").lowercase()
        BodyLine("You gave up the $saved of ${career.nation}.")
        OptionButton("Take the $saved of ${career.nation} back", "Ask the selectors for the role you gave up.", true, true) {
            val c = CareerInternational.takeBack(career)
            result = c.text
            onCareer(c.career)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // The franchises, one league at a time: giving one up leaves the captaincy of the others as it is.
    val captained = CareerLeagues.captainedTeams(career)
    val gaveUp = if (career.leagueDecidedSeason == career.season) career.leagueCaptainLeft.orEmpty() else emptyList()
    val relevant = seasons.orEmpty().filter { CareerLeagues.leagueIdOf(it) != null && it.userTeamId.isNotEmpty() && (it.userTeamId in captained || it.userTeamId in gaveUp) }
    if (relevant.isNotEmpty()) {
        GroupHeading("Franchise captaincy, league by league")
        relevant.forEach { s ->
            val tid = s.userTeamId
            val league = CareerLeagues.nameOfId(CareerLeagues.leagueIdOf(s) ?: "")
            val team = CareerSeason.nameOf(s, tid)
            if (tid in captained) {
                if (confirm == tid) {
                    BodyLine("Are you sure? You give up the captaincy of $team in the $league. You stay captain in your other leagues.")
                    OptionButton("Yes, give up the captaincy of $team", "You stay in the squad as a player.", true, true) {
                        onCareer(CareerLeagues.stepDownFrom(career, tid))
                        // The side needs a captain: its best-rated player takes over.
                        val next = CareerSeason.nextCaptainName(s, career, tid)
                        result = "You have given up the captaincy of $team in the $league." +
                            (if (next != null) " $next, the best-rated player of $team, is the new captain." else "") + " You can take it back here."
                        confirm = null
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OptionButton("No, stay captain of $team", "Nothing changes.", true, false) { confirm = null }
                } else {
                    BodyLine("$league: you are the captain of $team.")
                    OptionButton("Give up the captaincy of $team in the $league", "Only this league. Your other captaincies stay.", true, false) { confirm = tid }
                }
            } else {
                BodyLine("$league: you gave up the captaincy of $team.")
                OptionButton("Take the captaincy of $team back", "Be the captain of $team in the $league again.", true, true) {
                    onCareer(CareerLeagues.takeBackCaptaincy(career, tid))
                    result = "You are the captain of $team in the $league again. The player who was captain in your place steps aside."
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * League contracts (part twenty-eight): the leagues he has signed for this season, each of which he may leave (he plays no more matches in it and is paid only for the matches he played,
 * out of the contract he signed), and the offers still on the table, each of which he may turn down. Leaving asks first.
 */
@Composable
private fun LeagueContractsPage(career: CareerProfile, seasons: List<TournamentState>?, onLeave: (String) -> String, onCareer: (CareerProfile) -> Unit) {
    var confirm by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf("") }
    val signed = CareerFinance.signedThisSeason(career)
    val left = career.leftLeagues.orEmpty()
    if (result.isNotEmpty()) {
        Text(result, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(modifier = Modifier.height(8.dp))
    }
    BodyLine("A league contract is paid when the season ends. If you leave a league you play no more matches in it, and you are paid only for the matches you played, out of the contract you signed.")
    GroupHeading("Leagues you have signed for this season")
    if (signed.isEmpty()) BodyLine("None. You can ask the franchises for offers on the Home tab.")
    signed.forEach { id ->
        val name = CareerLeagues.nameOfId(id)
        val state = seasons?.firstOrNull { CareerLeagues.leagueIdOf(it) == id }
        val deal = CareerFinance.dealOf(career, id)
        if (id in left) {
            BodyLine("$name: you have left this league. You are owed ${CareerFinance.money(deal.toLong())} for the matches you played, paid when the season ends.")
        } else if (state == null) {
            BodyLine("$name: contract ${CareerFinance.money(deal.toLong())}. This league's season could not be found.")
        } else if (CareerSeason.isComplete(state)) {
            BodyLine("$name: contract ${CareerFinance.money(deal.toLong())}. The league is over, so the contract is paid when the season ends.")
        } else {
            val terms = CareerLeagues.leaveTerms(career, state)
            BodyLine("$name: contract ${CareerFinance.money(deal.toLong())}. You have played ${terms.played} of ${terms.planned} league matches. If you leave now you are paid ${CareerFinance.money(terms.pay.toLong())}.")
            if (confirm == id) {
                BodyLine("Are you sure? You play no more matches in the $name.")
                OptionButton("Yes, leave the $name", "You are paid ${CareerFinance.money(terms.pay.toLong())} when the season ends.", true, true) {
                    result = onLeave(id)
                    confirm = null
                }
                Spacer(modifier = Modifier.height(6.dp))
                OptionButton("No, stay in the $name", "Keep your contract and your place.", true, false) { confirm = null }
            } else {
                OptionButton("Leave the $name", "End your contract; you are paid for the matches you played.", true, false) { confirm = id }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    GroupHeading("Offers on the table")
    val offers = career.leagueOffers.orEmpty()
    if (offers.isEmpty()) BodyLine("No offers at the moment.")
    offers.forEach { offer ->
        BodyLine("${offer.teamName}, ${offer.leagueName}. ${offer.note}")
        OptionButton("Turn down ${offer.teamName}", "Take this offer off the table.", true, false) {
            onCareer(career.copy(leagueOffers = career.leagueOffers.orEmpty().filter { it.teamId != offer.teamId || it.leagueId != offer.leagueId }))
        }
        Spacer(modifier = Modifier.height(8.dp))
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

// ---------------------------------------------------------------- the career record

/**
 * The Career record: first the kind of cricket (all cricket, club, state, international, franchise leagues); then, for club and international cricket, the tabs of the formats
 * (all, three-day, one-day, T20; all, Test, ODI, T20) and, for state cricket and the leagues, the tab of each tournament (all, Ranji Trophy, Vijay Hazare Trophy ... all, IPL, Big Bash ...);
 * then the record: batting, bowling, fielding, awards, results and captaincy, one statistic to a line.
 */
@Composable
private fun RecordPage(career: CareerProfile) {
    var group by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("All") }
    val g = group
    if (g == null) {
        BodyLine("Choose which record you want to see.")
        CareerStatistics.GROUPS.forEach { (key, title) ->
            val total = CareerStatistics.view(career, key, "All")
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(
                title,
                if (total.matches == 0) "No matches yet." else "${total.matches} matches, ${total.runs} runs, ${total.wickets} wickets.",
                true, false
            ) {
                group = key
                tab = "All"
            }
        }
        return
    }
    OptionButton("Choose another kind of cricket", "Return to the list of records.", true, false) { group = null }
    Spacer(modifier = Modifier.height(12.dp))
    val title = CareerStatistics.GROUPS.first { it.first == g }.second
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    val tabs = CareerStatistics.tabsOf(career, g)
    if (tabs.size > 1) {
        Spacer(modifier = Modifier.height(8.dp))
        BodyLine("Showing: $tab. Choose another tab:")
        tabs.forEach { t ->
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(if (t == tab) "$t (showing)" else t, "Show the record for $t.", t != tab, false) { tab = t }
        }
    }
    StatsView(CareerStatistics.view(career, g, if (tabs.contains(tab)) tab else "All"))
}

@Composable
private fun StatsView(record: DetailedRecord) {
    if (record.matches == 0) {
        Spacer(modifier = Modifier.height(8.dp))
        BodyLine("No matches yet in this record.")
        return
    }
    CareerStatistics.sections(record).forEach { (heading, lines) ->
        GroupHeading(heading)
        lines.forEach { BodyLine(it) }
    }
}
