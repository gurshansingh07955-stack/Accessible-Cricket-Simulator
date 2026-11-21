package com.cricketsim.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.audio.MatchAudioDirector
import com.cricketsim.logic.BattingDecision
import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.BowlingQualityTier
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.Difficulty
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.FootworkType
import com.cricketsim.logic.MatchEngine
import com.cricketsim.logic.MatchStateMachine
import com.cricketsim.logic.NamedShot
import com.cricketsim.logic.NetPractice
import com.cricketsim.logic.Player
import com.cricketsim.logic.PlayerProfiles
import com.cricketsim.logic.BowlingSystem
import com.cricketsim.logic.ResolvedBowlingDecision
import com.cricketsim.logic.ShotCompatibilityTier
import com.cricketsim.logic.SixDistance
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading
import com.cricketsim.ui.match.AutoBowlPanel
import com.cricketsim.ui.match.BattingScreen
import com.cricketsim.ui.match.BowlingSetupMemory
import com.cricketsim.ui.match.DeliveryReveal
import com.cricketsim.ui.match.FieldingScreen
import com.cricketsim.ui.match.MatchLines
import com.cricketsim.ui.match.PitchingScreen
import com.cricketsim.ui.match.fieldSetMessage
import kotlinx.coroutines.delay

/**
 * Home > Net practice (patch 135: batting practice, patch 136: bowling practice).
 *
 * Pages: Net practice (Batting practice, Bowling practice = coming next) > kind of bowling (Spin / Fast) > for fast, the speed
 * (five bands) > first batsman > second batsman (both from the game's 90-and-over batsmen) > instructions > the session.
 *
 * THE SESSION plays real balls: the real BattingScreen (footwork is chosen on the "Face next ball" control exactly as in a match,
 * or by the Settings alternatives), then MatchEngine.simulateBall resolves the ball at the Settings difficulty. After each ball the
 * coach speaks: the match's own summary, then footwork, shot choice and timing advice. Nothing ends the session but "Stop practice":
 * a wicket is counted and the batter carries on. Every six balls the other batsman takes strike and a new bowler bowls.
 *
 * ACCESSIBILITY: every page starts with Back, then its heading; the main action comes before the long text; the result is spoken
 * once, after the ball's own sounds have started; Back inside the session never throws a session away without the summary.
 */

private enum class NetKind { SPIN, FAST }

private sealed interface NetPage {
    object Menu : NetPage
    object BatType : NetPage
    object BatSpeed : NetPage
    object BowlType : NetPage
    data class BowlPick(val kind: NetKind) : NetPage
    data class BowlInstructions(val kind: NetKind, val bowler: Player) : NetPage
    data class BowlSession(val kind: NetKind, val bowler: Player, val run: Int = 0) : NetPage
    data class BatFirst(val kind: NetKind, val band: NetPractice.SpeedBand?) : NetPage
    data class BatSecond(val kind: NetKind, val band: NetPractice.SpeedBand?, val first: Player) : NetPage
    data class BatInstructions(val kind: NetKind, val band: NetPractice.SpeedBand?, val first: Player, val second: Player) : NetPage
    data class BatSession(
        val kind: NetKind,
        val band: NetPractice.SpeedBand?,
        val first: Player,
        val second: Player,
        val run: Int = 0
    ) : NetPage
}

@Composable
fun NetPracticeFlow(onExit: () -> Unit) {
    var page by remember { mutableStateOf<NetPage>(NetPage.Menu) }
    var coachHints by remember { mutableStateOf(true) }

    fun back() {
        when (val p = page) {
            is NetPage.Menu -> onExit()
            is NetPage.BatType -> page = NetPage.Menu
            is NetPage.BatSpeed -> page = NetPage.BatType
            is NetPage.BatFirst -> page = if (p.kind == NetKind.FAST) NetPage.BatSpeed else NetPage.BatType
            is NetPage.BatSecond -> page = NetPage.BatFirst(p.kind, p.band)
            is NetPage.BatInstructions -> page = NetPage.BatSecond(p.kind, p.band, p.first)
            is NetPage.BatSession -> page = NetPage.BatInstructions(p.kind, p.band, p.first, p.second)
            is NetPage.BowlType -> page = NetPage.Menu
            is NetPage.BowlPick -> page = NetPage.BowlType
            is NetPage.BowlInstructions -> page = NetPage.BowlPick(p.kind)
            is NetPage.BowlSession -> page = NetPage.BowlInstructions(p.kind, p.bowler)
        }
    }

    // Inside a session the session handles Back itself (it asks for the summary).
    BackHandler(enabled = page !is NetPage.BatSession && page !is NetPage.BowlSession) { back() }

    when (val p = page) {
        is NetPage.Menu -> NetMenuPage(
            onBatting = { page = NetPage.BatType },
            onBowling = { page = NetPage.BowlType },
            onBack = { back() }
        )
        is NetPage.BatType -> NetBatTypePage(
            onSpin = { page = NetPage.BatFirst(NetKind.SPIN, null) },
            onFast = { page = NetPage.BatSpeed },
            onBack = { back() }
        )
        is NetPage.BatSpeed -> NetBatSpeedPage(
            onPick = { band -> page = NetPage.BatFirst(NetKind.FAST, band) },
            onBack = { back() }
        )
        is NetPage.BatFirst -> NetPickBatterPage(
            title = "Choose your first batsman",
            intro = "He faces the first over. The batsmen take turns, an over each.",
            exclude = null,
            onPick = { player -> page = NetPage.BatSecond(p.kind, p.band, player) },
            onBack = { back() }
        )
        is NetPage.BatSecond -> NetPickBatterPage(
            title = "Choose your second batsman",
            intro = "He faces the second over. ${p.first.name} is your first batsman.",
            exclude = p.first,
            onPick = { player -> page = NetPage.BatInstructions(p.kind, p.band, p.first, player) },
            onBack = { back() }
        )
        is NetPage.BatInstructions -> NetInstructions(
            heading = "How batting practice works",
            sections = BAT_HELP,
            coachHints = coachHints,
            onToggleCoach = { coachHints = !coachHints },
            primaryLabel = "Start practice",
            onPrimary = { page = NetPage.BatSession(p.kind, p.band, p.first, p.second) },
            onBack = { back() }
        )
        is NetPage.BowlType -> NetBowlTypePage(
            onSpin = { page = NetPage.BowlPick(NetKind.SPIN) },
            onFast = { page = NetPage.BowlPick(NetKind.FAST) },
            onBack = { back() }
        )
        is NetPage.BowlPick -> NetPickBowlerPage(
            kind = p.kind,
            onPick = { player -> page = NetPage.BowlInstructions(p.kind, player) },
            onBack = { back() }
        )
        is NetPage.BowlInstructions -> NetInstructions(
            heading = "How bowling practice works",
            sections = bowlHelp(p.kind),
            coachHints = coachHints,
            onToggleCoach = { coachHints = !coachHints },
            primaryLabel = "Start practice",
            onPrimary = { page = NetPage.BowlSession(p.kind, p.bowler) },
            onBack = { back() }
        )
        is NetPage.BowlSession -> key(p.run) {
            NetBowlingSession(
                kind = p.kind,
                bowler = p.bowler,
                startCoach = coachHints,
                onRestart = { page = p.copy(run = p.run + 1) },
                onChangeSetup = { page = NetPage.BowlType },
                onExit = { page = NetPage.Menu }
            )
        }
        is NetPage.BatSession -> key(p.run) {
            NetBattingSession(
                kind = p.kind,
                band = p.band,
                first = p.first,
                second = p.second,
                startCoach = coachHints,
                onRestart = { page = p.copy(run = p.run + 1) },
                onChangeSetup = { page = NetPage.BatType },
                onExit = { page = NetPage.Menu }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Menu pages
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun NetMenuPage(onBatting: () -> Unit, onBowling: () -> Unit, onBack: () -> Unit) {
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Net practice")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Practise away from a match, with a coach who explains every ball.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))
        OptionButton(
            title = "Batting practice",
            description = "Face spin or fast bowling, ball after ball. Choose two of the best batsmen in the game, and learn how to play every shot.",
            enabled = true,
            emphasised = true,
            onClick = onBatting
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton(
            title = "Bowling practice",
            description = "Bowl your choice of the best fast bowlers or spinners to random top batsmen. Learn how to bowl, how to set a legal field, and hear how good every ball was.",
            enabled = true,
            emphasised = false,
            onClick = onBowling
        )
    }
}

@Composable
private fun NetBatTypePage(onSpin: () -> Unit, onFast: () -> Unit, onBack: () -> Unit) {
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("What do you want to face?")
        Spacer(modifier = Modifier.height(16.dp))
        OptionButton(
            title = "Spin bowling practice",
            description = "Face ${NetPractice.spinBowlers().joinToString(", ") { it.name }}.",
            enabled = true,
            emphasised = false,
            onClick = onSpin
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton(
            title = "Fast bowling practice",
            description = "Face the quickest bowlers in the game. You choose how fast.",
            enabled = true,
            emphasised = false,
            onClick = onFast
        )
    }
}

@Composable
private fun NetBatSpeedPage(onPick: (NetPractice.SpeedBand) -> Unit, onBack: () -> Unit) {
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("How fast should the bowling be?")
        Spacer(modifier = Modifier.height(16.dp))
        NetPractice.SpeedBand.values().forEach { band ->
            OptionButton(
                title = band.label,
                description = "Every ball is bowled at this speed.",
                enabled = true,
                emphasised = false,
                onClick = { onPick(band) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NetPickBatterPage(
    title: String,
    intro: String,
    exclude: Player?,
    onPick: (Player) -> Unit,
    onBack: () -> Unit
) {
    val players = remember(exclude?.id) { NetPractice.batters().filter { it.id != exclude?.id } }
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading(title)
        Spacer(modifier = Modifier.height(8.dp))
        Text(intro, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        players.forEach { player ->
            val country = CricketData.getTeamById(player.teamId)?.name.orEmpty()
            OptionButton(
                title = player.name,
                description = "${PlayerProfiles.battingText(player)} $country.".trim(),
                enabled = true,
                emphasised = false,
                onClick = { onPick(player) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Instructions
// ---------------------------------------------------------------------------------------------------------------------

private val SHOT_LIST: String =
    BattingSystem.SHOT_OPTIONS.mapIndexed { index, shot -> "${index + 1}, ${shot.label}" }.joinToString("; ")

private val BAT_HELP: List<Pair<String, String>> = listOf(
    "The nets" to
        "You are in the nets. One bowler bowls to you, ball after ball, and you play every ball yourself. A wicket costs nothing here: " +
        "it is counted and you carry on. After every ball your coach tells you what happened and why. " +
        "Every six balls the other batsman takes strike and a new bowler comes on. Practice uses the difficulty you chose in Settings.",
    "Step 1. Face the ball" to
        "The practice screen has one big button, Face next ball. Tap it once to stand on your front foot. " +
        "Press and hold it to stand on your back foot. You choose your footwork before you know what is coming. " +
        "As a guide, the front foot suits full balls and the back foot suits short balls. " +
        "If you have turned on Batting without gestures in Settings, this button opens lists instead. " +
        "If you have turned off Face next ball in Settings, the ball comes by itself after a countdown, and you tilt the phone to choose: " +
        "left for front foot, right for back foot.",
    "Step 2. Hear the delivery" to
        "The game then tells you the delivery: the length, the line, the kind of delivery, the angle and the speed. " +
        "With coach tips on, it also tells you which foot and which shots suit that ball. " +
        "The lengths, from the shortest to the fullest, are: bouncer, short, back of a length, good length, full, half volley and yorker. " +
        "A full toss or a long hop is a bowler's mistake, and can be punished.",
    "Step 3. Choose your shot" to
        "The whole screen is the shot area. Touch the screen: where your finger lands, from the top to the bottom, picks the shot. " +
        "There are twenty shots, each in a band one twentieth of the screen high. From the top: $SHOT_LIST. " +
        "Slide your finger up or down and the game says each shot as you cross into it. Lift your finger to play the shot you are on. " +
        "A very short, still touch is ignored, so a stray tap never plays a shot. " +
        "If you choose Leave, the ball is simply let go, with no intent and no timing.",
    "Step 4. Choose your intent" to
        "Intent is how boldly you play the shot. After you choose a shot, swipe once with two fingers. " +
        "Up is Aggressive, aerial: hit it in the air, for fours and sixes, but it is riskier. " +
        "Left is Aggressive, grounded: hit it hard along the ground. " +
        "Down is Defensive: play safe and take a single if there is one. " +
        "Right is Step out: advance down the pitch to the bowler. " +
        "The two defensive shots, Forward Defense and Backward Defense, skip this step.",
    "Step 5. Time the ball" to
        "Now you get five buzzes and ticks. The fifth is the highest and the loudest. Tap the screen on the fifth buzz. " +
        "If you use a screen reader, double tap anywhere on the screen. " +
        "Tapping exactly on the fifth buzz is a perfect swing. The further away you are, the worse the timing: perfect, ideal, good, bad and very bad. " +
        "Tap before the fifth buzz and you swung early. Tap after it and you swung late. If you do not tap at all, you miss. " +
        "Faster balls leave less time between the buzzes.",
    "After the ball" to
        "Your coach tells you the result, the ball quality, the shot you played and how you timed it. " +
        "Then he tells you whether your footwork suited the length, whether your shot suited the ball, " +
        "and, if your timing was not perfect, whether you were early or late and how to fix it. " +
        "Choose Next ball to carry on, or Stop practice to hear your summary.",
    "Good to know" to
        "Sweep shots work against spin, not against fast bowling. A shot that does not suit the ball is the biggest risk. " +
        "Defensive shots are never a disaster, but they score little."
)

@Composable
private fun NetInstructions(
    heading: String,
    sections: List<Pair<String, String>>,
    coachHints: Boolean,
    onToggleCoach: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit
) {
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(primaryLabel) }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onToggleCoach, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(
                if (coachHints) "Coach tips before each ball: on. Tap to turn off."
                else "Coach tips before each ball: off. Tap to turn on."
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        PageHeading(heading)
        sections.forEach { (title, body) ->
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// The batting session
// ---------------------------------------------------------------------------------------------------------------------

private enum class NetPhase { READY, FACING, RESULT, SUMMARY }

@Composable
private fun NetBattingSession(
    kind: NetKind,
    band: NetPractice.SpeedBand?,
    first: Player,
    second: Player,
    startCoach: Boolean,
    onRestart: () -> Unit,
    onChangeSetup: () -> Unit,
    onExit: () -> Unit
) {
    val services = LocalGameServices.current
    val settings = services?.settings
    val director = remember(services) { services?.let { MatchAudioDirector(it) } }
    val stadium = remember { NetPractice.netStadium() }
    val isFast = kind == NetKind.FAST

    var phase by remember { mutableStateOf(NetPhase.READY) }
    var showHelp by remember { mutableStateOf(false) }
    var coach by remember { mutableStateOf(startCoach) }
    var overNo by remember { mutableStateOf(1) }
    var ballsInOver by remember { mutableStateOf(0) }
    var overRuns by remember { mutableStateOf(0) }
    var overDone by remember { mutableStateOf(false) }
    var firstFacing by remember { mutableStateOf(true) }
    var bowler by remember { mutableStateOf(NetPractice.nextBowler(isFast, null)) }
    var freeHit by remember { mutableStateOf(false) }
    var pendingFootwork by remember { mutableStateOf(FootworkType.FRONT_FOOT) }
    var tallies by remember {
        mutableStateOf(mapOf(first.id to NetPractice.Tally(first.name), second.id to NetPractice.Tally(second.name)))
    }
    var resultLines by remember { mutableStateOf(emptyList<String>()) }
    var resultKey by remember { mutableStateOf(0) }

    val striker = if (firstFacing) first else second
    val partner = if (firstFacing) second else first
    val currentBowler = bowler
    // One practice state per batter-and-bowler pairing; it never advances (see NetPractice).
    val netState = remember(striker.id, currentBowler.id) { NetPractice.buildNetState(striker, partner, currentBowler) }

    // The start of every over: who faces whom.
    LaunchedEffect(overNo) {
        services?.announceSpoken("Over $overNo. ${striker.name} faces ${currentBowler.name}.")
    }
    // The coach, a moment after the ball's own sounds have started.
    LaunchedEffect(resultKey) {
        if (resultKey > 0) {
            delay(600)
            services?.announceSpoken(resultLines.joinToString(" "))
        }
    }
    // The summary, spoken as it opens.
    LaunchedEffect(phase) {
        if (phase == NetPhase.SUMMARY) {
            delay(300)
            services?.announceSpoken(NetPractice.summaryLines(tallies.values.toList()).joinToString(" "))
        }
    }

    // Resolves one ball played on the real BattingScreen.
    fun resolve(delivery: ResolvedBowlingDecision, decision: BattingDecision, swingNote: String?) {
        val before = netState.copy(freeHit = freeHit)
        director?.onDelivery()
        val outcome = MatchEngine.simulateBall(
            matchState = before,
            difficulty = settings?.difficulty ?: Difficulty.MEDIUM,
            pitchType = before.pitchType,
            bowlingDecision = delivery,
            battingDecision = decision,
            illegalField = false,
            stadiumEffects = null
        )
        val after = MatchStateMachine.applyBallOutcome(before, outcome)
        director?.onBallResolved(before, after, outcome)

        val sixMetres = SixDistance.metres(outcome, decision, delivery.speedKmh, stadium, striker.battingRating)
        val summary = MatchLines.ballSummary(outcome, decision, swingNote, sixMetres)

        val legal = !outcome.isWide && !outcome.isNoBall
        val shotPlayed = decision.shot != NamedShot.LEAVE
        val suited = shotPlayed &&
            BattingSystem.computeShotCompatibility(decision.shot, delivery.actualLength, delivery.bowlingStyle) != ShotCompatibilityTier.BAD
        val note = swingNote.orEmpty()
        val old = tallies[striker.id] ?: NetPractice.Tally(striker.name)
        tallies = tallies + (striker.id to old.copy(
            balls = old.balls + if (outcome.isWide) 0 else 1,
            runs = old.runs + outcome.runs,
            outs = old.outs + if (outcome.isWicket) 1 else 0,
            fours = old.fours + if (outcome.runs == 4) 1 else 0,
            sixes = old.sixes + if (outcome.runs == 6) 1 else 0,
            shots = old.shots + if (shotPlayed) 1 else 0,
            perfect = old.perfect + if (shotPlayed && decision.timingTier == BowlingQualityTier.PERFECT) 1 else 0,
            sensible = old.sensible + if (suited) 1 else 0,
            early = old.early + if (shotPlayed && note.contains("early")) 1 else 0,
            late = old.late + if (shotPlayed && note.contains("late")) 1 else 0
        ))

        val newBalls = ballsInOver + if (legal) 1 else 0
        val newRuns = overRuns + outcome.runs
        val lines = ArrayList<String>()
        lines.add(summary)
        lines.addAll(NetPractice.feedback(delivery, decision, swingNote))
        if (newBalls >= NetPractice.BALLS_PER_OVER) {
            lines.add("That completes over $overNo. ${striker.name} scored $newRuns from the over. Next, ${partner.name} faces a new bowler.")
        }
        ballsInOver = newBalls
        overRuns = newRuns
        overDone = newBalls >= NetPractice.BALLS_PER_OVER
        freeHit = outcome.isNoBall
        resultLines = lines
        resultKey += 1
        phase = NetPhase.RESULT
    }

    fun nextBall() {
        if (overDone) {
            overNo += 1
            ballsInOver = 0
            overRuns = 0
            overDone = false
            firstFacing = !firstFacing
            bowler = NetPractice.nextBowler(isFast, bowler)
        }
        phase = NetPhase.READY
    }

    if (showHelp) {
        BackHandler { showHelp = false }
        NetInstructions(
            heading = "How batting practice works",
            sections = BAT_HELP,
            coachHints = coach,
            onToggleCoach = { coach = !coach },
            primaryLabel = "Back to practice",
            onPrimary = { showHelp = false },
            onBack = { showHelp = false }
        )
        return
    }

    if (phase == NetPhase.SUMMARY) {
        BackHandler { onExit() }
        PageColumn {
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Practise again") }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onChangeSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Choose a different practice") }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Back to Net practice") }
            Spacer(modifier = Modifier.height(24.dp))
            PageHeading("Practice summary")
            Spacer(modifier = Modifier.height(12.dp))
            NetPractice.summaryLines(tallies.values.toList()).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        return
    }

    if (phase == NetPhase.FACING) {
        // Leaving a ball in progress returns to the ready screen, as in a match.
        BackHandler { phase = NetPhase.READY }
        BattingScreen(
            batsman = striker,
            footwork = pendingFootwork,
            menuMode = settings?.menuBatting == true,
            generateDelivery = {
                val delivery = NetPractice.deliveryFor(isFast, band, currentBowler)
                DeliveryReveal(delivery, if (coach) NetPractice.coachTip(delivery) else "", freeHit = freeHit)
            },
            onBallPlayed = { delivery, decision, swingNote -> resolve(delivery, decision, swingNote) }
        )
        return
    }

    if (phase == NetPhase.RESULT) {
        BackHandler { phase = NetPhase.SUMMARY }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Button(onClick = { nextBall() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(if (overDone) "Next over" else "Next ball")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { phase = NetPhase.SUMMARY }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Stop practice")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Ball result",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))
            resultLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        return
    }

    // READY: the next ball is about to be faced.
    BackHandler { phase = NetPhase.SUMMARY }
    val styleText = PlayerProfiles.bowlLabelOf(currentBowler)
        ?.takeIf { it.isNotEmpty() }
        ?.let { label -> ", a ${label.replaceFirstChar { c -> c.lowercase() }} bowler" }
        .orEmpty()
    val speedText = if (isFast && band != null) ", bowling at ${band.label}" else ""
    val menuMode = settings?.menuBatting == true
    val autoMode = settings?.faceNextBall == false
    val score = tallies[striker.id]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Net practice: batting",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Over $overNo, ball ${ballsInOver + 1} of ${NetPractice.BALLS_PER_OVER}.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "${striker.name} is batting. ${currentBowler.name} is bowling$styleText$speedText.",
            style = MaterialTheme.typography.bodyLarge
        )
        if (freeHit) {
            Text("The next ball is a free hit.", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
        when {
            menuMode -> Button(
                onClick = {
                    pendingFootwork = FootworkType.FRONT_FOOT
                    phase = NetPhase.FACING
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Face next ball") }
            autoMode -> AutoBowlPanel(
                seconds = settings?.autoBowlSeconds ?: 10,
                holdTimer = false,
                onBowl = { footwork ->
                    pendingFootwork = footwork
                    phase = NetPhase.FACING
                }
            )
            else -> NetFaceNextBall(
                onSelected = { footwork ->
                    pendingFootwork = footwork
                    phase = NetPhase.FACING
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (score != null) {
            Text("${score.name}: ${score.runs} runs from ${score.balls} balls.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
        }
        OutlinedButton(onClick = { showHelp = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Instructions")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { phase = NetPhase.SUMMARY }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Stop practice")
        }
    }
}

/** The same control as a match's "Face next ball": tap for front foot, press and hold for back foot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NetFaceNextBall(onSelected: (FootworkType) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Front foot",
                onLongClickLabel = "Back foot",
                role = Role.Button,
                onClick = { onSelected(FootworkType.FRONT_FOOT) },
                onLongClick = { onSelected(FootworkType.BACK_FOOT) }
            ),
        shape = ButtonDefaults.shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Face next ball", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Tap for front foot. Press and hold for back foot.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Bowling practice (part 2)
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun NetBowlTypePage(onSpin: () -> Unit, onFast: () -> Unit, onBack: () -> Unit) {
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("What do you want to bowl?")
        Spacer(modifier = Modifier.height(16.dp))
        OptionButton(
            title = "Fast bowling practice",
            description = "Choose one of the quickest bowlers in the game: every fast bowler rated 90 or more.",
            enabled = true,
            emphasised = false,
            onClick = onFast
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton(
            title = "Spin bowling practice",
            description = "Choose ${NetPractice.spinBowlers().joinToString(", ") { it.name }}.",
            enabled = true,
            emphasised = false,
            onClick = onSpin
        )
    }
}

@Composable
private fun NetPickBowlerPage(kind: NetKind, onPick: (Player) -> Unit, onBack: () -> Unit) {
    val players = remember(kind) { if (kind == NetKind.FAST) NetPractice.fastBowlers() else NetPractice.spinBowlers() }
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Choose your bowler")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You bowl to batsmen chosen at random from the best batsmen in the game, a new one every over.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        players.forEach { player ->
            val country = CricketData.getTeamById(player.teamId)?.name.orEmpty()
            OptionButton(
                title = player.name,
                description = "${PlayerProfiles.bowlingText(player)} $country.".trim(),
                enabled = true,
                emphasised = false,
                onClick = { onPick(player) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun bowlHelp(kind: NetKind): List<Pair<String, String>> {
    val variations = (if (kind == NetKind.FAST) BowlingSystem.PACE_VARIATIONS else BowlingSystem.SPIN_VARIATIONS)
        .joinToString(", ") { it.label }
    val lines = BowlingSystem.LINE_OPTIONS.joinToString(", ") { it.label }
    return listOf(
        "The nets" to
            "You are in the nets with your chosen bowler. You bowl to batsmen chosen at random from the best batsmen in the game, " +
            "and a new batsman comes in every over, which is six legal balls. Wides and no balls do not count towards the six. " +
            "Runs, wickets and ball quality are counted, and nothing ends the practice but Stop practice. " +
            "After every ball your coach tells you how good it was and what cost it quality. Practice uses the difficulty you chose in Settings.",
        "Step 1. Set your field" to
            "Before your first ball, and whenever you like, choose Set field. The field screen says whether your field is legal and how many " +
            "fielders are in the deep. You have nine fielders besides the wicketkeeper and the bowler. " +
            "To start quickly, choose a ready-made field: Normal field; Defensive field, with the most riders in the deep; or Attacking field, " +
            "with catchers close in and only two in the deep. Or choose a fielder from the list, then choose where he goes, " +
            "then, where there is a choice, how deep: close, short or deep. The places are: Slip and Gully, Leg Slip, Third Man, Point, Cover, " +
            "Mid-Off, Long Stop, Mid-On, Mid-Wicket, Cow Corner, Square Leg and Fine Leg. " +
            "If you choose a place where someone already stands, the two fielders swap. When you are happy, choose Set field.",
        "Is your field legal?" to
            "Deep means outside the thirty-yard circle. In normal overs at most five fielders may be in the deep. " +
            "During the powerplay, the first six overs of a T20, at most three. " +
            "An illegal field is spotted by the umpire: every ball is a no ball, with a free hit after it, until you fix the field. " +
            "The field screen tells you Field is legal or Field is illegal, how many fielders are in the deep, and what is wrong. " +
            "In practice, the Field rules button switches between normal rules and powerplay rules, so you can learn both. " +
            "Your field changes what happens to the ball: a defensive field saves boundaries but gets fewer wickets, and an attacking field gets wickets but leaks boundaries.",
        "Step 2. Bowl" to
            "Choose the Bowl button to open the aim screen. Everything about the ball is set on that one screen before you bowl.",
        "Step 3. Angle, line and variation" to
            "Swipe with two fingers; without a screen reader, one finger works. Each swipe changes one thing by one step, and the game says it. " +
            "To change it again, lift your fingers and swipe again. " +
            "Swipe left to change the angle: over the wicket, which is the easiest to control, or around the wicket, a tighter angle with more threat but harder to keep on line. " +
            "Swipe right to change the line: $lines. " +
            "Swipe up to change the delivery. Yours are: $variations.",
        "Step 4. Choose the length" to
            "Swipe down with two fingers and keep them on the screen. The lower you go, the fuller the ball. " +
            "The area is split into seven equal bands. From the top: Bouncer, Short, Back of a Length, Good Length, Full, Half Volley and Yorker. " +
            "The game says each length as you cross into it. In practice it also says the ball quality as you near the middle of a band: " +
            "Perfect Ball, Ideal Ball, Good Ball, Bad Ball or Very Bad Ball. Lift your fingers to set the length, and the game says the length and the ball quality again. " +
            "To swing the ball, curve your drag as you go down: curve to the left for an inswing, to the right for an outswing. " +
            "You cannot bowl until the length is set.",
        "What makes a perfect ball" to
            "The ball quality is a score out of one hundred: eighty-five or more is Perfect, seventy Ideal, fifty Good, thirty Bad, and below that Very Bad. " +
            "Three things take points off. First, where your drag ends: the middle of the band is best. " +
            "Second, smoothness: dragging past where you want and pulling back costs points. Drag down in one smooth movement and stop. " +
            "Third, speed: a ball faster than you can control costs points, and a better bowler controls more speed. " +
            "A poor ball can come out as a different length from the one you aimed for: a full toss, a low full toss or a long hop. " +
            "A very bad ball can even be a beamer, which is an automatic no ball, and overstepping can also happen.",
        "Step 5. Choose your speed" to
            "Under the aim area is the speed slider. With a screen reader, swipe up or down on it to change the speed. " +
            "Every bowler and every delivery has its own range of speed.",
        "Step 6. Bowl, and listen" to
            "Choose Bowl. You hear the ball quality, the actual length, and any swing. Choose Continue and the computer batsman plays his shot. " +
            "Your coach then tells you the result, how good the ball was, what cost it quality, and whether your field was legal. " +
            "Choose Next ball to carry on, or Stop practice to hear your summary."
    )
}

private enum class BowlPhase { READY, FIELD, AIMING, RESULT, SUMMARY }

@Composable
private fun NetBowlingSession(
    kind: NetKind,
    bowler: Player,
    startCoach: Boolean,
    onRestart: () -> Unit,
    onChangeSetup: () -> Unit,
    onExit: () -> Unit
) {
    val services = LocalGameServices.current
    val settings = services?.settings
    val director = remember(services) { services?.let { MatchAudioDirector(it) } }
    val stadium = remember { NetPractice.netStadium() }

    var phase by remember { mutableStateOf(BowlPhase.READY) }
    var showHelp by remember { mutableStateOf(false) }
    var coach by remember { mutableStateOf(startCoach) }
    var powerplay by remember { mutableStateOf(false) }
    var overNo by remember { mutableStateOf(1) }
    var ballsInOver by remember { mutableStateOf(0) }
    var overRuns by remember { mutableStateOf(0) }
    var overDone by remember { mutableStateOf(false) }
    var freeHit by remember { mutableStateOf(false) }
    var batters by remember { mutableStateOf(NetPractice.randomBatters(null)) }
    var setup by remember { mutableStateOf<BowlingSetupMemory?>(null) }
    var tally by remember { mutableStateOf(NetPractice.BowlTally()) }
    var resultLines by remember { mutableStateOf(emptyList<String>()) }
    var resultKey by remember { mutableStateOf(0) }

    val striker = batters.first
    val partner = batters.second
    // One practice state per pair of batsmen; its own field is replaced by the user's field ball by ball.
    val netState = remember(striker.id, partner.id) { NetPractice.buildBowlingState(bowler, striker, partner) }
    var field by remember { mutableStateOf(netState.fieldPlacements) }

    val deep = FieldingSystem.countDeepFielders(field)
    val fieldLegal = FieldingSystem.isFieldLegal(field, powerplay)
    val fieldLine = if (fieldLegal) {
        "Your field is legal, with $deep in the deep."
    } else {
        "Your field is illegal. ${FieldingSystem.getIllegalFieldReason(field, powerplay).orEmpty()}"
    }

    LaunchedEffect(overNo) {
        services?.announceSpoken("Over $overNo. ${bowler.name} bowls to ${striker.name}.")
    }
    LaunchedEffect(resultKey) {
        if (resultKey > 0) {
            delay(600)
            services?.announceSpoken(resultLines.joinToString(" "))
        }
    }
    LaunchedEffect(phase) {
        if (phase == BowlPhase.SUMMARY) {
            delay(300)
            services?.announceSpoken(NetPractice.bowlSummaryLines(tally, bowler.name).joinToString(" "))
        }
    }

    // Resolves one ball bowled on the real PitchingScreen.
    fun resolve(delivery: ResolvedBowlingDecision) {
        val before = netState.copy(fieldPlacements = field, freeHit = freeHit)
        val decision = NetPractice.batterDecision(striker, delivery)
        val illegal = !FieldingSystem.isFieldLegal(field, powerplay)
        director?.onDelivery()
        val outcome = MatchEngine.simulateBall(
            matchState = before,
            difficulty = settings?.difficulty ?: Difficulty.MEDIUM,
            pitchType = before.pitchType,
            bowlingDecision = delivery,
            battingDecision = decision,
            illegalField = illegal,
            stadiumEffects = null
        )
        val after = MatchStateMachine.applyBallOutcome(before, outcome)
        director?.onBallResolved(before, after, outcome)

        val sixMetres = SixDistance.metres(outcome, decision, delivery.speedKmh, stadium, striker.battingRating)
        val summary = MatchLines.ballSummary(outcome, decision, null, sixMetres)

        val legal = !outcome.isWide && !outcome.isNoBall
        val conceded = outcome.runs + outcome.extraRuns
        val quality = delivery.quality
        tally = tally.copy(
            balls = tally.balls + if (legal) 1 else 0,
            runs = tally.runs + conceded,
            wickets = tally.wickets + if (outcome.isWicket) 1 else 0,
            dots = tally.dots + if (legal && conceded == 0 && !outcome.isWicket && outcome.byes + outcome.legByes == 0) 1 else 0,
            boundaries = tally.boundaries + if (outcome.runs == 4 || outcome.runs == 6) 1 else 0,
            wides = tally.wides + if (outcome.isWide) 1 else 0,
            noBalls = tally.noBalls + if (outcome.isNoBall) 1 else 0,
            perfect = tally.perfect + if (delivery.qualityTier == BowlingQualityTier.PERFECT) 1 else 0,
            ideal = tally.ideal + if (delivery.qualityTier == BowlingQualityTier.IDEAL) 1 else 0,
            good = tally.good + if (delivery.qualityTier == BowlingQualityTier.GOOD) 1 else 0,
            bad = tally.bad + if (delivery.qualityTier == BowlingQualityTier.BAD) 1 else 0,
            veryBad = tally.veryBad + if (delivery.qualityTier == BowlingQualityTier.VERY_BAD) 1 else 0,
            delivered = tally.delivered + 1,
            precisionLoss = tally.precisionLoss + (100.0 - quality.lengthPrecision),
            smoothLoss = tally.smoothLoss + quality.smoothnessPenalty,
            speedLoss = tally.speedLoss + quality.speedRiskPenalty
        )

        val newBalls = ballsInOver + if (legal) 1 else 0
        val newRuns = overRuns + conceded
        val lines = ArrayList<String>()
        lines.add(summary)
        lines.addAll(NetPractice.bowlFeedback(delivery, if (illegal) FieldingSystem.getIllegalFieldReason(field, powerplay) else null))
        if (newBalls >= NetPractice.BALLS_PER_OVER) {
            lines.add("That completes over $overNo: ${newRuns} runs. Next, ${bowler.name} bowls to a new batsman.")
        }
        ballsInOver = newBalls
        overRuns = newRuns
        overDone = newBalls >= NetPractice.BALLS_PER_OVER
        freeHit = outcome.isNoBall
        resultLines = lines
        resultKey += 1
        phase = BowlPhase.RESULT
    }

    fun nextBall() {
        if (overDone) {
            overNo += 1
            ballsInOver = 0
            overRuns = 0
            overDone = false
            batters = NetPractice.randomBatters(striker)
        }
        phase = BowlPhase.READY
    }

    if (showHelp) {
        BackHandler { showHelp = false }
        NetInstructions(
            heading = "How bowling practice works",
            sections = bowlHelp(kind),
            coachHints = coach,
            onToggleCoach = { coach = !coach },
            primaryLabel = "Back to practice",
            onPrimary = { showHelp = false },
            onBack = { showHelp = false }
        )
        return
    }

    if (phase == BowlPhase.SUMMARY) {
        BackHandler { onExit() }
        PageColumn {
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Practise again") }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onChangeSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Choose a different practice") }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Back to Net practice") }
            Spacer(modifier = Modifier.height(24.dp))
            PageHeading("Practice summary")
            Spacer(modifier = Modifier.height(12.dp))
            NetPractice.bowlSummaryLines(tally, bowler.name).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        return
    }

    if (phase == BowlPhase.FIELD) {
        BackHandler { phase = BowlPhase.READY }
        FieldingScreen(
            teamName = netState.bowlingTeam.name,
            players = netState.bowlingTeam.players,
            placements = field,
            isPowerplay = powerplay,
            isReadOnly = false,
            onConfirm = { placements ->
                field = placements
                services?.announceSpoken(fieldSetMessage(placements, powerplay))
                phase = BowlPhase.READY
            },
            onBack = { phase = BowlPhase.READY }
        )
        return
    }

    if (phase == BowlPhase.AIMING) {
        // Leaving the aim screen returns to the ready screen; nothing has been bowled.
        BackHandler { phase = BowlPhase.READY }
        PitchingScreen(
            bowler = netState.currentBowler,
            onDeliveryResolved = { delivery -> resolve(delivery) },
            onBack = { phase = BowlPhase.READY },
            initialSetup = setup,
            onSetupChanged = { setup = it },
            coach = coach
        )
        return
    }

    if (phase == BowlPhase.RESULT) {
        BackHandler { phase = BowlPhase.SUMMARY }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Button(onClick = { nextBall() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(if (overDone) "Next over" else "Next ball")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { phase = BowlPhase.SUMMARY }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Stop practice")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Ball result",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))
            resultLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        return
    }

    // READY: the next ball is about to be bowled.
    BackHandler { phase = BowlPhase.SUMMARY }
    val rulesText = if (powerplay) {
        "Field rules: powerplay, at most ${FieldingSystem.powerplayDeepCap} in the deep. Tap for normal rules."
    } else {
        "Field rules: normal, at most ${FieldingSystem.MAX_DEEP_FIELDERS_NORMAL} in the deep. Tap for powerplay rules."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Net practice: bowling",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Over $overNo, ball ${ballsInOver + 1} of ${NetPractice.BALLS_PER_OVER}.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "${bowler.name} is bowling to ${striker.name}, batting rating ${striker.battingRating}.",
            style = MaterialTheme.typography.bodyLarge
        )
        if (freeHit) {
            Text("The next ball is a free hit.", style = MaterialTheme.typography.bodyLarge)
        }
        Text(fieldLine, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { phase = BowlPhase.AIMING },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        ) { Text("Bowl") }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { phase = BowlPhase.FIELD }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Set field")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                powerplay = !powerplay
                val nowLegal = FieldingSystem.isFieldLegal(field, powerplay)
                val reason = FieldingSystem.getIllegalFieldReason(field, powerplay).orEmpty()
                val which = if (powerplay) "Powerplay rules." else "Normal rules."
                services?.announceSpoken(if (nowLegal) "$which Your field is legal." else "$which Your field is now illegal. $reason")
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text(rulesText) }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { showHelp = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Instructions")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { phase = BowlPhase.SUMMARY }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Stop practice")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "${tally.runs} runs, ${tally.wickets} wickets from ${tally.balls} balls.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
