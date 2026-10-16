package com.cricketsim.logic

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

data class AuctionSet(val name: String, val playerIds: List<String>)

/**
 * A whole auction, saved after every player. Prices are in LAKHS (100 lakh = 1 crore).
 * `players` is this auction's own copy of the pool, so saves survive later rating changes.
 */
data class IplAuctionState(
    val version: Int,
    val userTeamId: String,
    val players: List<AuctionPlayer>,
    val sets: List<AuctionSet>,
    val setIndex: Int = 0,
    val lotIndex: Int = 0,
    val purse: Map<String, Int>,
    val squads: Map<String, List<String>>,
    val soldPrice: Map<String, Int> = emptyMap(),
    val soldTo: Map<String, String> = emptyMap(),
    val unsold: List<String> = emptyList(),
    val seed: Long = 1L,
    val reoffered: Boolean = false,
    val finished: Boolean = false,
    val createdAt: Long = 0L
)

/** The player currently under the hammer. `bidLakhs` 0 means nobody has bid yet. */
data class LotState(
    val playerId: String,
    val bidLakhs: Int = 0,
    val leaderId: String? = null,
    val userOut: Boolean = false,
    val log: List<String> = emptyList(),
    val closed: Boolean = false,
    /** 1 while the auctioneer says "going once", 2 for "going twice"; 0 otherwise. */
    val countdown: Int = 0
)

/**
 * The auction rules and the other nine teams' bidding. Pure Kotlin.
 *
 * RULES (as in the IPL mega auction): every team starts with a 120 crore purse and an empty squad;
 * a squad holds at most 25 players, at most 8 of them overseas (18 is the minimum); bids rise by 5
 * lakh up to 1 crore, 10 lakh up to 2 crore, 20 lakh up to 5 crore and 25 lakh after that; players
 * come up in sets (marquee first, then capped and uncapped batters, all-rounders, wicketkeepers,
 * fast bowlers and spinners, then an accelerated round); a player nobody wants goes unsold and gets
 * one more chance at the end. There are no retentions and no Right to Match cards in this auction.
 *
 * HOW A TEAM DECIDES. Each team has a most it will pay for each player: what the market would pay
 * for his rating (Indian players carry a premium, uncapped players a discount), more if the team
 * is short of that kind of player and much less if it has plenty, times the team's own appetite
 * for him (fixed per team and player, so the same team is not always the keenest), and never more
 * than it can afford while keeping enough money to fill a squad of 18.
 */
object IplAuction {

    private const val RESERVE_PER_SLOT = 30

    /** No bid goes past this (the real record is 27 crore). */
    private const val MAX_BID = 2800
    private val DESIRED = mapOf("BA" to 5, "AL" to 4, "WK" to 2, "FA" to 5, "SP" to 3)

    fun playerOf(state: IplAuctionState, id: String): AuctionPlayer? = state.players.firstOrNull { it.player.id == id }

    fun group(p: AuctionPlayer): String = when (p.player.role) {
        PlayerRole.WICKETKEEPER -> "WK"
        PlayerRole.ALL_ROUNDER -> "AL"
        PlayerRole.BOWLER -> if (p.player.bowlingStyle == BowlingStyle.SPIN) "SP" else "FA"
        else -> "BA"
    }

    fun overall(p: AuctionPlayer): Int = when (group(p)) {
        "AL" -> (p.player.battingRating + p.player.bowlingRating) / 2 + 12
        "FA", "SP" -> p.player.bowlingRating
        else -> p.player.battingRating
    }

    fun nextBid(current: Int): Int = when {
        current < 100 -> current + 5
        current < 200 -> current + 10
        current < 500 -> current + 20
        else -> current + 25
    }

    /** 125 -> "1 crore 25 lakh", 30 -> "30 lakh", 2700 -> "27 crore". */
    fun priceText(lakhs: Int): String {
        val crore = lakhs / 100
        val rest = lakhs % 100
        return when {
            crore == 0 -> "$rest lakh"
            rest == 0 -> "$crore crore"
            else -> "$crore crore $rest lakh"
        }
    }

    fun marketValue(p: AuctionPlayer): Int {
        // About 16 crore for an overseas star and 26 for an Indian one at the very top, 7 crore for an Indian
        // player rated 75, 3 crore at 65 (Indian players carry a premium, overseas players a discount, because
        // only eight overseas players fit in a squad and the real market paid Indian stars the most).
        val x = ((overall(p) - 52.0) / 38.5).coerceIn(0.0, 0.97)
        var v = -650.0 * ln(1.0 - x)
        v *= if (p.overseas) 0.7 else 1.2
        if (!p.capped) v *= 0.85
        return maxOf(p.baseLakhs, minOf(2600, v.roundToInt()))
    }

    private fun owned(state: IplAuctionState, teamId: String): List<AuctionPlayer> =
        state.squads[teamId].orEmpty().mapNotNull { playerOf(state, it) }

    /** The most this team will pay; 0 if it will not bid at all. */
    fun maxBid(state: IplAuctionState, teamId: String, p: AuctionPlayer): Int {
        val squad = state.squads[teamId].orEmpty()
        if (squad.size >= IplData.MAX_SQUAD) return 0
        val mine = owned(state, teamId)
        if (p.overseas && mine.count { it.overseas } >= IplData.MAX_OVERSEAS) return 0
        val purse = state.purse[teamId] ?: return 0
        val reserve = RESERVE_PER_SLOT * maxOf(0, IplData.MIN_SQUAD - squad.size - 1)
        val affordable = purse - reserve
        if (affordable < p.baseLakhs) return 0
        val g = group(p)
        val want = DESIRED[g] ?: 3
        val have = mine.count { group(it) == g }
        val need = when {
            have < want -> 1.1
            have >= want + 2 -> 0.65
            else -> 1.0
        }
        val appetite = 0.85 + 0.35 * Random(state.seed xor teamId.hashCode().toLong() xor (p.player.id.hashCode().toLong() shl 7)).nextDouble()
        return minOf((marketValue(p) * need * appetite).toInt(), affordable, MAX_BID)
    }

    private fun asking(lot: LotState, p: AuctionPlayer): Int = if (lot.bidLakhs == 0) p.baseLakhs else nextBid(lot.bidLakhs)

    fun askingPrice(lot: LotState, p: AuctionPlayer): Int = asking(lot, p)

    fun userCanBid(state: IplAuctionState, lot: LotState, p: AuctionPlayer): Boolean {
        if (lot.closed || lot.userOut || lot.leaderId == state.userTeamId) return false
        val squad = state.squads[state.userTeamId].orEmpty()
        if (squad.size >= IplData.MAX_SQUAD) return false
        if (p.overseas && owned(state, state.userTeamId).count { it.overseas } >= IplData.MAX_OVERSEAS) return false
        return (state.purse[state.userTeamId] ?: 0) >= asking(lot, p)
    }

    /** One other team raises the bid, or null if none of them will. */
    private fun aiRespond(state: IplAuctionState, lot: LotState, p: AuctionPlayer): LotState? {
        val price = asking(lot, p)
        val keen = IplData.TEAMS.map { it.id }
            .filter { it != state.userTeamId && it != lot.leaderId }
            .map { it to maxBid(state, it, p) }
            .filter { it.second >= price }
        if (keen.isEmpty()) return null
        val rnd = Random(state.seed xor p.player.id.hashCode().toLong() xor (lot.bidLakhs.toLong() shl 20))
        var roll = rnd.nextDouble() * keen.sumOf { (it.second - price + 10).toDouble() }
        var pick = keen.first().first
        for ((team, most) in keen) {
            roll -= (most - price + 10).toDouble()
            if (roll <= 0) { pick = team; break }
        }
        return lot.copy(bidLakhs = price, leaderId = pick, log = lot.log + "${IplData.shortName(pick)} bid ${priceText(price)}.")
    }

    /** One other team raises the bid, or null if none of them will (the timed bidding in the hall uses this). */
    fun aiBid(state: IplAuctionState, lot: LotState): LotState? {
        val p = playerOf(state, lot.playerId) ?: return null
        return aiRespond(state, lot, p)?.copy(countdown = 0)
    }

    fun userBid(state: IplAuctionState, lot: LotState): LotState {
        val p = playerOf(state, lot.playerId) ?: return lot
        val price = asking(lot, p)
        val me = IplData.shortName(state.userTeamId)
        return lot.copy(bidLakhs = price, leaderId = state.userTeamId, log = lot.log + "$me bid ${priceText(price)}.")
    }

    /**
     * Lets the other teams bid until it is the user's turn again (or the hammer falls). The user is
     * asked after every bid from another team, as long as he can still bid and has not passed.
     */
    fun advance(state: IplAuctionState, start: LotState): LotState {
        val p = playerOf(state, start.playerId) ?: return start.copy(closed = true)
        var lot = start
        var guard = 0
        while (guard++ < 500) {
            if (lot.closed) return lot
            val userTurn = userCanBid(state, lot, p)
            if (lot.leaderId == state.userTeamId) {
                val reply = aiRespond(state, lot, p) ?: return lot.copy(closed = true)
                lot = reply
                if (userCanBid(state, lot, p)) return lot
                continue
            }
            if (lot.leaderId == null) {
                val reply = aiRespond(state, lot, p)
                if (reply != null) {
                    lot = reply
                    if (userCanBid(state, lot, p)) return lot
                    continue
                }
                return if (userTurn) lot else lot.copy(closed = true)
            }
            // another team leads
            if (userTurn) return lot
            val reply = aiRespond(state, lot, p) ?: return lot.copy(closed = true)
            lot = reply
        }
        return lot.copy(closed = true)
    }

    fun currentPlayerId(state: IplAuctionState): String? =
        state.sets.getOrNull(state.setIndex)?.playerIds?.getOrNull(state.lotIndex)

    fun currentSetName(state: IplAuctionState): String = state.sets.getOrNull(state.setIndex)?.name ?: ""

    /** Records the sale (or no sale) and moves to the next player. */
    fun finishLot(state: IplAuctionState, lot: LotState): IplAuctionState {
        val leader = lot.leaderId
        var s = state
        if (leader != null && lot.bidLakhs > 0) {
            s = s.copy(
                purse = s.purse + (leader to ((s.purse[leader] ?: 0) - lot.bidLakhs)),
                squads = s.squads + (leader to (s.squads[leader].orEmpty() + lot.playerId)),
                soldPrice = s.soldPrice + (lot.playerId to lot.bidLakhs),
                soldTo = s.soldTo + (lot.playerId to leader)
            )
        } else {
            s = s.copy(unsold = s.unsold + lot.playerId)
        }
        return moveNext(s)
    }

    private fun moveNext(s: IplAuctionState): IplAuctionState {
        var si = s.setIndex
        var li = s.lotIndex + 1
        if (li >= (s.sets.getOrNull(si)?.playerIds?.size ?: 0)) {
            si++
            li = 0
        }
        if (si >= s.sets.size) {
            if (!s.reoffered && s.unsold.isNotEmpty()) {
                val again = AuctionSet("Unsold players, second chance", s.unsold)
                return s.copy(sets = s.sets + again, setIndex = si, lotIndex = 0, reoffered = true, unsold = emptyList())
            }
            return fillSquads(s.copy(setIndex = si, lotIndex = 0, finished = true))
        }
        return s.copy(setIndex = si, lotIndex = li)
    }

    /**
     * At the very end EVERY team (yours too) still short of the minimum squad takes the best unsold
     * players it can afford at base price, so nobody finishes below IplData.MIN_SQUAD. A team keeps
     * enough money back for the places still to fill; if even that is impossible it takes the cheapest.
     * Your team is filled last, so the other nine get first pick of what is left.
     */
    private fun fillSquads(start: IplAuctionState): IplAuctionState {
        var s = start
        val order = IplData.TEAMS.map { it.id }.sortedBy { if (it == start.userTeamId) 1 else 0 }
        for (teamId in order) {
            while ((s.squads[teamId]?.size ?: 0) < IplData.MIN_SQUAD) {
                val mine = owned(s, teamId)
                val purse = s.purse[teamId] ?: 0
                val placesAfter = IplData.MIN_SQUAD - mine.size - 1
                val candidates = s.unsold.mapNotNull { playerOf(s, it) }
                    .filter { it.baseLakhs <= purse && !(it.overseas && mine.count { o -> o.overseas } >= IplData.MAX_OVERSEAS) }
                val keepBack = purse - RESERVE_PER_SLOT * placesAfter
                val pick = candidates.filter { it.baseLakhs <= keepBack }.maxByOrNull { overall(it) }
                    ?: candidates.minByOrNull { it.baseLakhs } ?: break
                s = s.copy(
                    purse = s.purse + (teamId to purse - pick.baseLakhs),
                    squads = s.squads + (teamId to (s.squads[teamId].orEmpty() + pick.id)),
                    soldPrice = s.soldPrice + (pick.id to pick.baseLakhs),
                    soldTo = s.soldTo + (pick.id to teamId),
                    unsold = s.unsold - pick.id
                )
            }
        }
        return s
    }

    /** The user sits out this player: the other teams settle it, and the auction moves on. */
    fun autoLot(state: IplAuctionState): IplAuctionState {
        val id = currentPlayerId(state) ?: return state
        val lot = advance(state, LotState(id, userOut = true))
        return finishLot(state, lot)
    }

    fun runToEndOfSet(start: IplAuctionState): IplAuctionState {
        var s = start
        val set = s.setIndex
        var guard = 0
        while (!s.finished && s.setIndex == set && guard++ < 5000) s = autoLot(s)
        return s
    }

    fun runAll(start: IplAuctionState): IplAuctionState {
        var s = start
        var guard = 0
        while (!s.finished && guard++ < 5000) s = autoLot(s)
        return s
    }

    // ---- Sets ---------------------------------------------------------------------------------

    fun buildSets(pool: List<AuctionPlayer>): List<AuctionSet> {
        val sets = ArrayList<AuctionSet>()
        val used = HashSet<String>()
        fun take(players: List<AuctionPlayer>, name: String) {
            if (players.isEmpty()) return
            sets.add(AuctionSet(name, players.map { it.id }))
            players.forEach { used.add(it.id) }
        }
        take(pool.filter { it.setCode == "M1" }, "Marquee players 1")
        take(pool.filter { it.setCode == "M2" }, "Marquee players 2")
        // Marquee 3 to 6: the biggest names the 2025 auction never saw (retained by their teams), so Kohli,
        // Rohit, Dhoni, Bumrah and the rest come up right after the real marquee sets.
        val legends = listOf(
            listOf("Virat Kohli", "Rohit Sharma", "MS Dhoni", "Jasprit Bumrah", "Suryakumar Yadav", "Hardik Pandya"),
            listOf("Ravindra Jadeja", "Shubman Gill", "Rashid Khan", "Andre Russell", "Sunil Narine", "Heinrich Klaasen"),
            listOf("Travis Head", "Pat Cummins", "Nicholas Pooran", "Sanju Samson", "Yashasvi Jaiswal", "Axar Patel"),
            listOf("Kuldeep Yadav", "Trent Boult", "Josh Hazlewood", "Mitchell Marsh", "Rinku Singh", "Ruturaj Gaikwad")
        )
        legends.forEachIndexed { i, names ->
            take(names.mapNotNull { n -> pool.firstOrNull { it.id !in used && it.player.name == n } }, "Marquee players ${i + 3}")
        }
        val rest = pool.filter { it.id !in used }
        val groups = listOf("BA", "AL", "WK", "FA", "SP")
        val names = mapOf("BA" to "batters", "AL" to "all-rounders", "WK" to "wicketkeepers", "FA" to "fast bowlers", "SP" to "spin bowlers")
        fun chunks(capped: Boolean, g: String): List<List<AuctionPlayer>> =
            rest.filter { it.capped == capped && group(it) == g }.sortedByDescending { overall(it) }.chunked(8)
        val capped = groups.associateWith { chunks(true, it) }
        val uncapped = groups.associateWith { chunks(false, it) }
        for (round in 0 until 7) {
            for (g in groups) capped[g]?.getOrNull(round)?.let { take(it, "Capped ${names[g]} ${round + 1}") }
            for (g in groups) uncapped[g]?.getOrNull(round)?.let { take(it, "Uncapped ${names[g]} ${round + 1}") }
        }
        pool.filter { it.id !in used }.sortedByDescending { overall(it) }.chunked(20)
            .forEachIndexed { i, chunk -> take(chunk, "Accelerated round ${i + 1}") }
        return sets
    }

    fun newAuction(userTeamId: String, pool: List<AuctionPlayer>, seed: Long): IplAuctionState =
        IplAuctionState(
            version = IplData.SAVE_VERSION,
            userTeamId = userTeamId,
            players = pool,
            sets = buildSets(pool),
            purse = IplData.TEAMS.associate { it.id to IplData.PURSE_LAKHS },
            squads = IplData.TEAMS.associate { it.id to emptyList<String>() },
            seed = seed,
            createdAt = System.currentTimeMillis()
        )

    // ---- Words for the auctioneer ---------------------------------------------------------------

    fun countryName(code: String): String = when (code) {
        "IND" -> "India"; "AUS" -> "Australia"; "ENG" -> "England"; "RSA" -> "South Africa"; "NZ" -> "New Zealand"
        "SL" -> "Sri Lanka"; "WI" -> "West Indies"; "BAN" -> "Bangladesh"; "AFG" -> "Afghanistan"; "ZIM" -> "Zimbabwe"
        "IRE" -> "Ireland"; "NED" -> "the Netherlands"; "SCO" -> "Scotland"; "NEP" -> "Nepal"; "UAE" -> "the UAE"
        "OMA" -> "Oman"; "USA" -> "the USA"
        else -> code
    }

    fun roleText(p: AuctionPlayer): String = when (group(p)) {
        "WK" -> "wicketkeeper"
        "AL" -> "all-rounder"
        "FA" -> "fast bowler"
        "SP" -> "spin bowler"
        else -> "batter"
    }
}
