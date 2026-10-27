package com.cricketsim.logic

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** An edit to one player of one team (every field optional; only what the player changed). */
data class PlayerEdit(
    val teamId: String = "",
    val name: String = "",
    val role: String? = null,
    val batting: Int? = null,
    val bowling: Int? = null,
    val fielding: Int? = null,
    /** "R" or "L": the batting hand. */
    val hand: String? = null,
    /** The bowling style as shown, for example "Right-arm leg break"; an empty text means "does not bowl". */
    val bowlLabel: String? = null
)

/** A player the user created, in any team (a game team or a custom team). */
data class AddedPlayer(
    val id: String = "",
    val teamId: String = "",
    val name: String = "",
    val role: String = "BATSMAN",
    val batting: Int = 50,
    val bowling: Int = 20,
    val fielding: Int = 50,
    val hand: String = "R",
    val bowlLabel: String = ""
)

/** A team the user created. Its players are [AddedPlayer]s with this team's id. */
data class CustomTeam(
    val id: String = "",
    val name: String = "",
    val hasLogo: Boolean = false
)

/** Everything the user has customised (the one file customisation.json). */
data class CustomData(
    val teamNames: Map<String, String>? = null,
    val playerEdits: List<PlayerEdit>? = null,
    val addedPlayers: List<AddedPlayer>? = null,
    val customTeams: List<CustomTeam>? = null,
    val tournamentEdits: Map<String, TournamentEdit>? = null,
    val customTournaments: List<CustomTournamentDef>? = null
)

/**
 * Home > Advanced Customisation > Create Custom Teams and Players.
 *
 * The user's changes live in one small file and are applied to the game's teams WHEN THEY ARE READ
 * ([applyTo], called by CricketData.getAllTeams/getTeamById and at the end of every tournament's buildTeams), so
 * nothing in the game's own data is rewritten and an edit shows up the next time a team is read.
 *
 *  - A player edit is keyed by team id + player name, so editing Virat Kohli in India does not touch his copy in
 *    a league team (league teams copy international players when they are built, so they DO pick the change up
 *    when the player is copied; editing him inside a league team changes only that league team).
 *  - Players the user creates are appended to their team; custom teams are separate from the game's teams (they
 *    are not part of CricketData.getAllTeams) and are for custom tournaments, which are still to come.
 *  - The IPL teams are made by the auction, so they are not editable here.
 *
 * Call [init] once at start-up (MainActivity.onCreate) before any team is read.
 */
object Customisation {
    const val MAX_BATTING = 95
    const val MAX_BOWLING = 97
    const val MAX_NAME_LENGTH = 30

    private val gson = Gson()
    private var file: File? = null
    private var logoDir: File? = null
    private var formatsFile: File? = null
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "customisation-writer").apply { isDaemon = true } }

    @Volatile private var data = CustomData()
    @Volatile private var editIndex: Map<String, PlayerEdit> = emptyMap()
    @Volatile private var addedById: Map<String, AddedPlayer> = emptyMap()
    @Volatile private var addedByTeam: Map<String, List<AddedPlayer>> = emptyMap()

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    private fun editKey(teamId: String, name: String) = teamId + "|" + norm(name)

    fun init(context: Context) {
        val dir = context.applicationContext.filesDir
        file = File(dir, "customisation.json")
        logoDir = File(dir, "team_logos")
        formatsFile = File(dir, "custom_formats.json")
        data = try {
            val f = file
            if (f != null && f.exists()) gson.fromJson(f.readText(), CustomData::class.java) ?: CustomData() else CustomData()
        } catch (e: Exception) {
            CustomData()
        }
        reindex()
    }

    private fun reindex() {
        val d = data
        editIndex = (d.playerEdits ?: emptyList()).associateBy { editKey(it.teamId, it.name) }
        val added = d.addedPlayers ?: emptyList()
        addedById = added.associateBy { it.id }
        addedByTeam = added.groupBy { it.teamId }
    }

    private fun update(change: (CustomData) -> CustomData) {
        synchronized(this) {
            data = change(data)
            reindex()
            val json = gson.toJson(data)
            val f = file ?: return
            writer.execute {
                try {
                    val temp = File(f.parentFile, f.name + ".tmp")
                    temp.writeText(json)
                    if (!temp.renameTo(f)) {
                        f.delete()
                        temp.renameTo(f)
                    }
                } catch (e: Exception) {
                    // A failed save must never crash the game; the next change tries again.
                }
            }
        }
    }

    private fun isEmpty(): Boolean {
        val d = data
        return d.teamNames.isNullOrEmpty() && d.playerEdits.isNullOrEmpty() && d.addedPlayers.isNullOrEmpty()
    }

    // ---- Reading (used by the game and by PlayerProfiles) -------------------------------------

    private fun roleOf(s: String?): PlayerRole? = try {
        if (s == null) null else PlayerRole.valueOf(s)
    } catch (e: Exception) {
        null
    }

    private fun isSpin(label: String): Boolean {
        val l = label.lowercase()
        return l.contains("break") || l.contains("orthodox") || l.contains("wrist") || l.contains("spin")
    }

    fun roleFor(p: Player): PlayerRole? = editIndex[editKey(p.teamId, p.name)]?.role?.let { roleOf(it) }

    fun handFor(p: Player): Char? =
        (editIndex[editKey(p.teamId, p.name)]?.hand ?: addedById[p.id]?.hand)?.firstOrNull()

    /** null = not edited; "" = does not bowl; otherwise the style text. */
    fun bowlLabelFor(p: Player): String? =
        editIndex[editKey(p.teamId, p.name)]?.bowlLabel ?: addedById[p.id]?.bowlLabel

    fun isAdded(playerId: String): Boolean = addedById.containsKey(playerId)

    private fun rate(players: List<Player>, team: Team): Team {
        fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)
        return team.copy(
            battingRating = avg(players.map { it.battingRating }.sortedDescending().take(8)),
            bowlingRating = avg(players.map { it.bowlingRating }.sortedDescending().take(5)),
            fieldingRating = avg(players.map { it.fieldingRating }),
            players = players
        )
    }

    private fun applyEdit(p: Player, e: PlayerEdit): Player {
        val label = e.bowlLabel
        return p.copy(
            role = roleOf(e.role) ?: p.role,
            battingRating = e.batting ?: p.battingRating,
            bowlingRating = e.bowling ?: p.bowlingRating,
            fieldingRating = e.fielding ?: p.fieldingRating,
            bowlingStyle = if (label != null && label.isNotEmpty()) {
                if (isSpin(label)) BowlingStyle.SPIN else BowlingStyle.PACE
            } else p.bowlingStyle
        )
    }

    private fun toPlayer(a: AddedPlayer): Player = Player(
        id = a.id, teamId = a.teamId, name = a.name, role = roleOf(a.role) ?: PlayerRole.BATSMAN,
        battingRating = a.batting, bowlingRating = a.bowling, fieldingRating = a.fielding,
        bowlingStyle = if (a.bowlLabel.isNotEmpty() && isSpin(a.bowlLabel)) BowlingStyle.SPIN else BowlingStyle.PACE
    )

    /**
     * The team as the user has customised it: a new name, edited players and, when [includeAdded], the players
     * the user created in it. A team nobody touched is returned as it is.
     */
    fun applyTo(team: Team, includeAdded: Boolean = true): Team {
        if (isEmpty()) return team
        val newName = data.teamNames?.get(team.id)
        var changed = newName != null && newName != team.name
        val players = ArrayList<Player>(team.players.size + 2)
        for (p in team.players) {
            val e = editIndex[editKey(team.id, p.name)]
            if (e == null) {
                players.add(p)
            } else {
                players.add(applyEdit(p, e))
                changed = true
            }
        }
        if (includeAdded) {
            for (a in addedByTeam[team.id] ?: emptyList()) {
                if (players.none { it.id == a.id }) {
                    players.add(toPlayer(a))
                    changed = true
                }
            }
        }
        if (!changed) return team
        return rate(players, team).copy(name = newName ?: team.name)
    }

    /** The same for a tournament's built teams; created players also get a plain squad tag. */
    fun applyToBuilt(
        built: Pair<List<Team>, Map<String, SquadTag>>,
        includeAdded: Boolean
    ): Pair<List<Team>, Map<String, SquadTag>> {
        if (isEmpty()) return built
        val tags = LinkedHashMap(built.second)
        val teams = built.first.map { t ->
            val r = applyTo(t, includeAdded)
            if (includeAdded) {
                for (p in r.players) if (tags[p.id] == null) tags[p.id] = SquadTag.DOMESTIC
            }
            r
        }
        return Pair(teams, tags)
    }

    // ---- Changing (used by the Create Custom Teams and Players screens) -------------------------

    fun setTeamName(teamId: String, name: String) {
        update { d -> d.copy(teamNames = (d.teamNames ?: emptyMap()) + (teamId to name)) }
    }

    /** Saves an edit to a player of a game team (or a custom team's player: see [updateAdded]). */
    fun saveEdit(
        teamId: String, playerName: String, role: PlayerRole, batting: Int, bowling: Int, fielding: Int,
        hand: Char, bowlLabel: String
    ) {
        val key = editKey(teamId, playerName)
        val edit = PlayerEdit(teamId, playerName, role.name, batting, bowling, fielding, hand.toString(), bowlLabel)
        update { d ->
            val rest = (d.playerEdits ?: emptyList()).filter { editKey(it.teamId, it.name) != key }
            d.copy(playerEdits = rest + edit)
        }
    }

    fun addPlayer(
        teamId: String, name: String, role: PlayerRole, batting: Int, bowling: Int, fielding: Int,
        hand: Char, bowlLabel: String
    ): String {
        val id = UUID.randomUUID().toString()
        val added = AddedPlayer(id, teamId, name, role.name, batting, bowling, fielding, hand.toString(), bowlLabel)
        update { d -> d.copy(addedPlayers = (d.addedPlayers ?: emptyList()) + added) }
        return id
    }

    fun updateAdded(
        id: String, role: PlayerRole, batting: Int, bowling: Int, fielding: Int, hand: Char, bowlLabel: String
    ) {
        update { d ->
            d.copy(addedPlayers = (d.addedPlayers ?: emptyList()).map {
                if (it.id == id) it.copy(
                    role = role.name, batting = batting, bowling = bowling, fielding = fielding,
                    hand = hand.toString(), bowlLabel = bowlLabel
                ) else it
            })
        }
    }

    fun deleteAdded(id: String) {
        update { d -> d.copy(addedPlayers = (d.addedPlayers ?: emptyList()).filter { it.id != id }) }
    }

    const val MIN_PLAYERS = 11

    /** What a team still needs before it can play a match (null = ready). */
    fun playProblem(team: Team): String? {
        val roles = team.players.map { PlayerProfiles.displayRole(it) }
        return when {
            team.players.size < MIN_PLAYERS -> "it needs at least $MIN_PLAYERS players (it has ${team.players.size})"
            roles.none { it == PlayerRole.WICKETKEEPER } -> "it needs a wicketkeeper"
            roles.count { it == PlayerRole.BOWLER || it == PlayerRole.ALL_ROUNDER } < 4 -> "it needs at least 4 bowlers or all-rounders"
            else -> null
        }
    }

    /** The custom teams that are ready to play a match. */
    fun playableCustomTeams(): List<Team> = customTeamsAsTeams().filter { playProblem(it) == null }

    /** The player's custom match formats (read from their file; used when a match is set up). */
    fun customFormats(): List<CustomFormat> = try {
        val f = formatsFile
        if (f != null && f.exists()) (gson.fromJson(f.readText(), Array<CustomFormat>::class.java)?.toList() ?: emptyList()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    // ---- Tournaments ----------------------------------------------------------------------------

    fun tournamentEdit(id: String): TournamentEdit? = data.tournamentEdits?.get(id)

    /** The tournament's name as the player has set it (the game's own name when not renamed). */
    fun tournamentName(id: String, default: String): String = tournamentEdit(id)?.name ?: default

    /** The format key the player has chosen for one of the game's tournaments (the original when not changed). */
    fun tournamentFormatKey(id: String, default: String): String = tournamentEdit(id)?.formatKey ?: default

    /** Saves a new name and/or format for one of the game's tournaments; both null removes the edit. */
    fun setTournamentEdit(id: String, name: String?, formatKey: String?) {
        update { d ->
            val rest = (d.tournamentEdits ?: emptyMap()) - id
            d.copy(tournamentEdits = if (name == null && formatKey == null) rest else rest + (id to TournamentEdit(name, formatKey)))
        }
    }

    fun customTournaments(): List<CustomTournamentDef> = data.customTournaments ?: emptyList()

    /** Adds a custom tournament, or replaces the one with the same id. */
    fun saveCustomTournament(def: CustomTournamentDef) {
        update { d ->
            val all = d.customTournaments ?: emptyList()
            d.copy(customTournaments = if (all.any { it.id == def.id }) all.map { if (it.id == def.id) def else it } else all + def)
        }
    }

    fun deleteCustomTournament(id: String) {
        update { d -> d.copy(customTournaments = (d.customTournaments ?: emptyList()).filter { it.id != id }) }
    }

    // ---- Custom teams ---------------------------------------------------------------------------

    fun customTeams(): List<CustomTeam> = data.customTeams ?: emptyList()

    /** The custom teams as ordinary [Team]s (their players are the players created in them). */
    fun customTeamsAsTeams(): List<Team> = customTeams().map { c ->
        val players = (addedByTeam[c.id] ?: emptyList()).map { toPlayer(it) }
        rate(players, Team(id = c.id, name = c.name, countryCode = "CUS", battingRating = 50, bowlingRating = 50, fieldingRating = 50, players = players))
    }

    fun createCustomTeam(name: String, hasLogo: Boolean): CustomTeam {
        val team = CustomTeam(id = "custom_" + UUID.randomUUID().toString().take(8), name = name, hasLogo = hasLogo)
        update { d -> d.copy(customTeams = (d.customTeams ?: emptyList()) + team) }
        return team
    }

    fun renameCustomTeam(id: String, name: String) {
        update { d -> d.copy(customTeams = (d.customTeams ?: emptyList()).map { if (it.id == id) it.copy(name = name) else it }) }
    }

    fun deleteCustomTeam(id: String) {
        update { d ->
            d.copy(
                customTeams = (d.customTeams ?: emptyList()).filter { it.id != id },
                addedPlayers = (d.addedPlayers ?: emptyList()).filter { it.teamId != id }
            )
        }
        try {
            logoFile(id)?.delete()
        } catch (e: Exception) {
            // The logo file is only decoration.
        }
    }

    /** Where a custom team's logo picture is kept (the file may not exist). */
    fun logoFile(teamId: String): File? = logoDir?.let { File(it, "$teamId.png") }
}
