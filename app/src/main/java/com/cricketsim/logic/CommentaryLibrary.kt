package com.cricketsim.logic

import kotlin.random.Random

/**
 * Ported from helpers/commentaryLibrary.tsx (the Floot/React web app
 * remains the source of truth for gameplay design). Pure Kotlin, no
 * Android dependencies.
 *
 * Defines the pre-built library of duo commentary banter used by the
 * AI voice commentary system. Each event category has several
 * variants; each variant is a pair of lines — an "excited" lead
 * commentator line and a "calm" co-commentator reply — designed to
 * sound like two people talking to each other during the match. A
 * random variant is chosen each time so common events (dot balls,
 * singles) don't feel repetitive.
 *
 * Categories are split wherever the on-screen/TTS commentary text can
 * describe the ball differently (clean shot vs. edge, bowled vs. lbw
 * vs. caught, edge-caught vs. caught in the outfield) so that
 * categorizeBallOutcome() always picks the AI voice category that
 * matches the same descriptive detail used in the text commentary and
 * the scorecard. See MatchEngine.generateCommentary and
 * BallOutcome.dismissalType / isEdge.
 *
 * Beyond per-ball categories, there are also "special event"
 * categories (milestones, back-to-back boundaries, three boundaries in
 * an over, tight/expensive overs, partnership milestones, bowler
 * wicket hauls, hat-tricks) that aren't derived from a single ball
 * outcome alone — the eventual match-screen equivalent of
 * pages/match.tsx needs to track the running state that detects these
 * (a batsman's running total, the previous ball's type, counts within
 * the current over, a bowler's consecutive-wicket streak) and enqueue
 * them alongside the normal per-ball commentary when they fire. The
 * getMilestoneCrossed / getPartnershipMilestoneCrossed /
 * getBowlerWicketMilestoneCrossed / getHatTrickCategory helpers below
 * are the pure functions that do that detection once the running state
 * is available; nothing here tracks that state itself.
 *
 * Audio is generated via ElevenLabs text-to-speech (Arnold voice for
 * the excited lead commentator, Antoni voice for the calm
 * co-commentator) and uploaded to Floot's static storage — an
 * infrastructure detail specific to the web app's hosting, not
 * reproduced here. All audioUrl values below are carried over verbatim
 * from the web source (`/_cdn/commentary/<line id>.mp3`); they won't
 * resolve from an Android build without the equivalent audio assets
 * being bundled or fetched from an equivalent CDN, which is out of
 * scope for this pure-logic pass (see PORTING_NOTES.md's "Audio"
 * entry). The ids and text are what actually matter for this file.
 *
 * PENDING GENERATION as of the web source's last note (regenerate via
 * its endpoints/commentary_tts_generate_POST.ts once the ElevenLabs
 * quota resets — not relevant to this Kotlin port, but kept here for
 * parity in case it's ever relevant to Android audio asset generation
 * too): partnership150_2, partnership200_1, partnership200_2,
 * bowler3wkts_1, bowler3wkts_2, bowler5wkts_1, bowler5wkts_2,
 * bowler10wkts_1, bowler10wkts_2, hattrick_1, hattrick_2,
 * onhattrick_1, onhattrick_2, doublehattrick_1, doublehattrick_2
 * (each as both _excited and _calm).
 *
 * Every category, every line's id/text/audioUrl, and all
 * milestone/hat-trick thresholds are exact matches to the web source.
 */

enum class CommentaryCategory {
    DOT, SINGLE, TWO, THREE, FIVE, FOUR_CLEAN, FOUR_EDGE, SIX_CLEAN, SIX_EDGE,
    WICKET_BOWLED, WICKET_LBW, WICKET_CAUGHT_EDGE, WICKET_CAUGHT_OUTFIELD,
    WIDE, NO_BALL, OVER_COMPLETE, INNINGS_BREAK, MATCH_WIN,
    MILESTONE_50, MILESTONE_100, MILESTONE_150, MILESTONE_200, MILESTONE_250,
    BACK_TO_BACK_FOUR, BACK_TO_BACK_SIX, TRIPLE_FOUR_OVER, TRIPLE_SIX_OVER,
    TIGHT_OVER, EXPENSIVE_OVER,
    PARTNERSHIP_50, PARTNERSHIP_100, PARTNERSHIP_150, PARTNERSHIP_200,
    BOWLER_3_WICKETS, BOWLER_5_WICKETS, BOWLER_10_WICKETS,
    HAT_TRICK, ON_HAT_TRICK, DOUBLE_HAT_TRICK,
    TOSS_BAT, TOSS_BOWL, FIELDING_CHANGE, RAIN_START, RAIN_STOP, DLS_REVISED
}

data class CommentaryLine(val id: String, val text: String, val audioUrl: String? = null)

data class CommentaryPair(val excited: CommentaryLine, val calm: CommentaryLine)

enum class CommentaryVoice { EXCITED, CALM }

/** One flat entry from CommentaryLibrary.getAllCommentaryLines() — used by a one-time audio generation script on the web side. */
data class CommentaryLineEntry(val id: String, val text: String, val voice: CommentaryVoice)

object CommentaryLibrary {

    val LIBRARY: Map<CommentaryCategory, List<CommentaryPair>> = mapOf(
        CommentaryCategory.DOT to listOf(
            CommentaryPair(
                CommentaryLine("dot_1_excited", "Beaten! That was a fantastic ball, right on the money!", "/_cdn/commentary/dot_1_excited.mp3"),
                CommentaryLine("dot_1_calm", "Yes, good discipline there, nothing to score off.", "/_cdn/commentary/dot_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("dot_2_excited", "No run there, but what a battle between bat and ball!", "/_cdn/commentary/dot_2_excited.mp3"),
                CommentaryLine("dot_2_calm", "Exactly, patience is key at this stage of the innings.", "/_cdn/commentary/dot_2_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("dot_3_excited", "Dot ball! Superb line and length there, nothing given away!", "/_cdn/commentary/dot_3_excited.mp3"),
                CommentaryLine("dot_3_calm", "Yes, really tight bowling, building the pressure nicely.", "/_cdn/commentary/dot_3_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("dot_4_excited", "Left alone, smart leave there, reading the length well!", "/_cdn/commentary/dot_4_excited.mp3"),
                CommentaryLine("dot_4_calm", "Good judgment outside off, no room for anything loose.", "/_cdn/commentary/dot_4_calm.mp3")
            )
        ),
        CommentaryCategory.SINGLE to listOf(
            CommentaryPair(
                CommentaryLine("runs_1_excited", "They're off and running, quick single taken there!", "/_cdn/commentary/runs_1_excited.mp3"),
                CommentaryLine("runs_1_calm", "Smart cricket, it's always good to rotate the strike.", "/_cdn/commentary/runs_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("runs_2_excited", "Good running between the wickets, they've stolen a run!", "/_cdn/commentary/runs_2_excited.mp3"),
                CommentaryLine("runs_2_calm", "Yes, that's how you build pressure, one run at a time.", "/_cdn/commentary/runs_2_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("single_3_excited", "Quick single, well judged there by the batters!", "/_cdn/commentary/single_3_excited.mp3"),
                CommentaryLine("single_3_calm", "Good communication in the middle, that's how singles are found.", "/_cdn/commentary/single_3_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("single_4_excited", "Nicely worked away for one, keeping the scoreboard moving!", "/_cdn/commentary/single_4_excited.mp3"),
                CommentaryLine("single_4_calm", "Sensible cricket, ticking the total along steadily.", "/_cdn/commentary/single_4_calm.mp3")
            )
        ),
        CommentaryCategory.TWO to listOf(
            CommentaryPair(
                CommentaryLine("two_1_excited", "They've come back for two! Good hustle between the wickets!", "/_cdn/commentary/two_1_excited.mp3"),
                CommentaryLine("two_1_calm", "Excellent running there, that's worth its weight in gold.", "/_cdn/commentary/two_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("two_2_excited", "Two runs added, quick thinking to turn back for the second!", "/_cdn/commentary/two_2_excited.mp3"),
                CommentaryLine("two_2_calm", "Smart cricket, always looking for that extra run.", "/_cdn/commentary/two_2_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("two_3_excited", "Well spotted, straight into the gap and they've nicked two!", "/_cdn/commentary/two_3_excited.mp3"),
                CommentaryLine("two_3_calm", "Good placement, finding the space between the fielders.", "/_cdn/commentary/two_3_calm.mp3")
            )
        ),
        CommentaryCategory.THREE to listOf(
            CommentaryPair(
                CommentaryLine("three_1_excited", "Three runs! Brilliant running, they've really pushed hard there!", "/_cdn/commentary/three_1_excited.mp3"),
                CommentaryLine("three_1_calm", "Tremendous effort, that's not easy to find in this format.", "/_cdn/commentary/three_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("three_2_excited", "They've turned a two into a three, superb hustle!", "/_cdn/commentary/three_2_excited.mp3"),
                CommentaryLine("three_2_calm", "Fantastic fitness on display, that third run is priceless.", "/_cdn/commentary/three_2_calm.mp3")
            )
        ),
        CommentaryCategory.FIVE to listOf(
            CommentaryPair(
                CommentaryLine("five_1_excited", "Five runs! That is a rare sight, what a mix-up out there!", "/_cdn/commentary/five_1_excited.mp3"),
                CommentaryLine("five_1_calm", "Doesn't happen often, overthrows will have contributed to that total.", "/_cdn/commentary/five_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("five_2_excited", "Five on the board! A real bonus for the batting side there!", "/_cdn/commentary/five_2_excited.mp3"),
                CommentaryLine("five_2_calm", "You don't see that every day, credit to some alert running.", "/_cdn/commentary/five_2_calm.mp3")
            )
        ),
        CommentaryCategory.FOUR_CLEAN to listOf(
            CommentaryPair(
                CommentaryLine("four_1_excited", "Four! What a magnificent shot, that raced away to the boundary!", "/_cdn/commentary/four_1_excited.mp3"),
                CommentaryLine("four_1_calm", "Beautifully timed, the fielder had no chance there.", "/_cdn/commentary/four_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("four_2_excited", "Cracking shot! That's four more runs on the board!", "/_cdn/commentary/four_2_excited.mp3"),
                CommentaryLine("four_2_calm", "Textbook technique, you could not have placed that any better.", "/_cdn/commentary/four_2_calm.mp3")
            )
        ),
        CommentaryCategory.FOUR_EDGE to listOf(
            CommentaryPair(
                CommentaryLine("four_3_excited", "Edged and away for four! Lives to fight another ball!", "/_cdn/commentary/four_3_excited.mp3"),
                CommentaryLine("four_3_calm", "A thick outside edge, but full value for the runs all the same.", "/_cdn/commentary/four_3_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("four_4_excited", "Chance there! It flies through the gap and races for four!", "/_cdn/commentary/four_4_excited.mp3"),
                CommentaryLine("four_4_calm", "Not the cleanest connection, but it will only count as four.", "/_cdn/commentary/four_4_calm.mp3")
            )
        ),
        CommentaryCategory.SIX_CLEAN to listOf(
            CommentaryPair(
                CommentaryLine("six_1_excited", "Six! That is absolutely massive, into the stands it goes!", "/_cdn/commentary/six_1_excited.mp3"),
                CommentaryLine("six_1_calm", "Incredible power, that ball has landed a long way back.", "/_cdn/commentary/six_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("six_2_excited", "Huge hit! The crowd is on its feet for that one!", "/_cdn/commentary/six_2_excited.mp3"),
                CommentaryLine("six_2_calm", "Remarkable clean striking, that's as good as it gets.", "/_cdn/commentary/six_2_calm.mp3")
            )
        ),
        CommentaryCategory.SIX_EDGE to listOf(
            CommentaryPair(
                CommentaryLine("six_3_excited", "Top edge! But it's carried all the way for six, what fortune!", "/_cdn/commentary/six_3_excited.mp3"),
                CommentaryLine("six_3_calm", "Not the intended shot, but the batting side won't mind at all.", "/_cdn/commentary/six_3_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("six_4_excited", "Miscued it, but it's gone the distance regardless, six runs!", "/_cdn/commentary/six_4_excited.mp3"),
                CommentaryLine("six_4_calm", "Pure luck there, but six is six on the scoreboard.", "/_cdn/commentary/six_4_calm.mp3")
            )
        ),
        CommentaryCategory.WICKET_BOWLED to listOf(
            CommentaryPair(
                CommentaryLine("wicket_1_excited", "Out! What a moment, the crowd erupts as the wicket falls!", "/_cdn/commentary/wicket_1_excited.mp3"),
                CommentaryLine("wicket_1_calm", "A big breakthrough there, that changes the complexion of the innings.", "/_cdn/commentary/wicket_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("wicket_bowled_2_excited", "Bowled him! All timber, that's absolutely castled!", "/_cdn/commentary/wicket_bowled_2_excited.mp3"),
                CommentaryLine("wicket_bowled_2_calm", "Peach of a delivery, nothing the batter could do about that.", "/_cdn/commentary/wicket_bowled_2_calm.mp3")
            )
        ),
        CommentaryCategory.WICKET_LBW to listOf(
            CommentaryPair(
                CommentaryLine("wicket_2_excited", "Got him! That is a huge wicket at this stage!", "/_cdn/commentary/wicket_2_excited.mp3"),
                CommentaryLine("wicket_2_calm", "Composed bowling, patience finally rewarded with a wicket.", "/_cdn/commentary/wicket_2_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("wicket_lbw_2_excited", "LBW! Given out plumb in front, the umpire had no hesitation!", "/_cdn/commentary/wicket_lbw_2_excited.mp3"),
                CommentaryLine("wicket_lbw_2_calm", "That looked out from the moment it struck the pad.", "/_cdn/commentary/wicket_lbw_2_calm.mp3")
            )
        ),
        CommentaryCategory.WICKET_CAUGHT_EDGE to listOf(
            CommentaryPair(
                CommentaryLine("wicket_3_excited", "Edged and taken! A sharp catch to remove the danger man!", "/_cdn/commentary/wicket_3_excited.mp3"),
                CommentaryLine("wicket_3_calm", "Fine take behind the stumps, the faintest of edges there.", "/_cdn/commentary/wicket_3_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("wicket_4_excited", "There's the edge, and safely pouched! Another wicket falls!", "/_cdn/commentary/wicket_4_excited.mp3"),
                CommentaryLine("wicket_4_calm", "Good carry to the keeper, a well deserved reward for that probing line.", "/_cdn/commentary/wicket_4_calm.mp3")
            )
        ),
        CommentaryCategory.WICKET_CAUGHT_OUTFIELD to listOf(
            CommentaryPair(
                CommentaryLine("wicket_caught_outfield_1_excited", "Skied it! And it's safely taken in the deep, what a catch!", "/_cdn/commentary/wicket_caught_outfield_1_excited.mp3"),
                CommentaryLine("wicket_caught_outfield_1_calm", "Completely mistimed that shot, straight into the fielder's hands.", "/_cdn/commentary/wicket_caught_outfield_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("wicket_caught_outfield_2_excited", "Caught! Well judged in the outfield, that's a well earned wicket!", "/_cdn/commentary/wicket_caught_outfield_2_excited.mp3"),
                CommentaryLine("wicket_caught_outfield_2_calm", "Good take under pressure, the batter will be disappointed with that shot selection.", "/_cdn/commentary/wicket_caught_outfield_2_calm.mp3")
            )
        ),
        CommentaryCategory.WIDE to listOf(
            CommentaryPair(
                CommentaryLine("wide_1_excited", "Wide ball there, the umpire's arm goes out!", "/_cdn/commentary/wide_1_excited.mp3"),
                CommentaryLine("wide_1_calm", "A little bit of pressure creeping in for the bowler.", "/_cdn/commentary/wide_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("wide_2_excited", "Strays down the leg side, that's called wide!", "/_cdn/commentary/wide_2_excited.mp3"),
                CommentaryLine("wide_2_calm", "Small lapse in discipline, an extra run added to the total.", "/_cdn/commentary/wide_2_calm.mp3")
            )
        ),
        CommentaryCategory.NO_BALL to listOf(
            CommentaryPair(
                CommentaryLine("noball_1_excited", "No ball! And that means a free hit is coming up!", "/_cdn/commentary/noball_1_excited.mp3"),
                CommentaryLine("noball_1_calm", "Costly overstep there, the batting side will be pleased.", "/_cdn/commentary/noball_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("noball_2_excited", "Front foot error, no ball called by the umpire!", "/_cdn/commentary/noball_2_excited.mp3"),
                CommentaryLine("noball_2_calm", "That will sting the bowler, handing away a free run.", "/_cdn/commentary/noball_2_calm.mp3")
            )
        ),
        CommentaryCategory.OVER_COMPLETE to listOf(
            CommentaryPair(
                CommentaryLine("over_1_excited", "That's the end of the over, what a passage of play that was!", "/_cdn/commentary/over_1_excited.mp3"),
                CommentaryLine("over_1_calm", "A solid over overall, let's see who comes on to bowl next.", "/_cdn/commentary/over_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("over_2_excited", "Over complete! The energy out there is fantastic right now!", "/_cdn/commentary/over_2_excited.mp3"),
                CommentaryLine("over_2_calm", "Good tidy work there, keeping things in check.", "/_cdn/commentary/over_2_calm.mp3")
            )
        ),
        CommentaryCategory.INNINGS_BREAK to listOf(
            CommentaryPair(
                CommentaryLine("innings_1_excited", "And that's the innings wrapped up, what a total to chase!", "/_cdn/commentary/innings_1_excited.mp3"),
                CommentaryLine("innings_1_calm", "A competitive total on the board, the second innings should be fascinating.", "/_cdn/commentary/innings_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("innings_2_excited", "Innings over! Now it's time for the chase to begin!", "/_cdn/commentary/innings_2_excited.mp3"),
                CommentaryLine("innings_2_calm", "Both teams will fancy their chances from here.", "/_cdn/commentary/innings_2_calm.mp3")
            )
        ),
        CommentaryCategory.MATCH_WIN to listOf(
            CommentaryPair(
                CommentaryLine("win_1_excited", "That's the winning moment! What an incredible finish to this match!", "/_cdn/commentary/win_1_excited.mp3"),
                CommentaryLine("win_1_calm", "A thoroughly deserved victory, both sides gave it everything today.", "/_cdn/commentary/win_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("win_2_excited", "Game over! What a thrilling contest that turned out to be!", "/_cdn/commentary/win_2_excited.mp3"),
                CommentaryLine("win_2_calm", "A fitting finish, we've witnessed a wonderful contest of cricket.", "/_cdn/commentary/win_2_calm.mp3")
            )
        ),
        CommentaryCategory.MILESTONE_50 to listOf(
            CommentaryPair(
                CommentaryLine("milestone50_1_excited", "Fifty! A superb knock, raising the bat to the crowd!", "/_cdn/commentary/milestone50_1_excited.mp3"),
                CommentaryLine("milestone50_1_calm", "Well constructed innings, a real platform being built there.", "/_cdn/commentary/milestone50_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("milestone50_2_excited", "Half-century! What a milestone to reach at this stage!", "/_cdn/commentary/milestone50_2_excited.mp3"),
                CommentaryLine("milestone50_2_calm", "Composed and classy, exactly the innings the team needed.", "/_cdn/commentary/milestone50_2_calm.mp3")
            )
        ),
        CommentaryCategory.MILESTONE_100 to listOf(
            CommentaryPair(
                CommentaryLine("milestone100_1_excited", "A century! What a magnificent achievement, take a bow!", "/_cdn/commentary/milestone100_1_excited.mp3"),
                CommentaryLine("milestone100_1_calm", "Superbly crafted hundred, that is a special innings.", "/_cdn/commentary/milestone100_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("milestone100_2_excited", "Three figures! The crowd rises for a wonderful century!", "/_cdn/commentary/milestone100_2_excited.mp3"),
                CommentaryLine("milestone100_2_calm", "A captain's innings if ever there was one, brilliantly played.", "/_cdn/commentary/milestone100_2_calm.mp3")
            )
        ),
        CommentaryCategory.MILESTONE_150 to listOf(
            CommentaryPair(
                CommentaryLine("milestone150_1_excited", "One hundred and fifty! An extraordinary display of batting!", "/_cdn/commentary/milestone150_1_excited.mp3"),
                CommentaryLine("milestone150_1_calm", "Remarkable consistency, this innings just keeps growing.", "/_cdn/commentary/milestone150_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("milestone150_2_excited", "A magnificent one-fifty, this is a career-defining innings!", "/_cdn/commentary/milestone150_2_excited.mp3"),
                CommentaryLine("milestone150_2_calm", "Outstanding concentration to still be building at this total.", "/_cdn/commentary/milestone150_2_calm.mp3")
            )
        ),
        CommentaryCategory.MILESTONE_200 to listOf(
            CommentaryPair(
                CommentaryLine("milestone200_1_excited", "A double century! This is an innings for the history books!", "/_cdn/commentary/milestone200_1_excited.mp3"),
                CommentaryLine("milestone200_1_calm", "Extraordinary hunger for runs, a truly monumental effort.", "/_cdn/commentary/milestone200_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("milestone200_2_excited", "Two hundred! What an incredible achievement out in the middle!", "/_cdn/commentary/milestone200_2_excited.mp3"),
                CommentaryLine("milestone200_2_calm", "Sheer class from start to finish, a genuinely special innings.", "/_cdn/commentary/milestone200_2_calm.mp3")
            )
        ),
        CommentaryCategory.MILESTONE_250 to listOf(
            CommentaryPair(
                CommentaryLine("milestone250_1_excited", "Two hundred and fifty! This innings is simply breathtaking!", "/_cdn/commentary/milestone250_1_excited.mp3"),
                CommentaryLine("milestone250_1_calm", "Words don't do justice to an innings of this magnitude.", "/_cdn/commentary/milestone250_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("milestone250_2_excited", "An astonishing two-fifty, one of the great innings you will see!", "/_cdn/commentary/milestone250_2_excited.mp3"),
                CommentaryLine("milestone250_2_calm", "Remarkable endurance and skill on show for the entire day.", "/_cdn/commentary/milestone250_2_calm.mp3")
            )
        ),
        CommentaryCategory.BACK_TO_BACK_FOUR to listOf(
            CommentaryPair(
                CommentaryLine("b2bfour_1_excited", "Another four! Back to back boundaries, this batter is flowing!", "/_cdn/commentary/b2bfour_1_excited.mp3"),
                CommentaryLine("b2bfour_1_calm", "Two in a row now, real fluency starting to show.", "/_cdn/commentary/b2bfour_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("b2bfour_2_excited", "Back to back fours! The bowler will want this over to end!", "/_cdn/commentary/b2bfour_2_excited.mp3"),
                CommentaryLine("b2bfour_2_calm", "Consecutive boundaries, the pressure has shifted completely.", "/_cdn/commentary/b2bfour_2_calm.mp3")
            )
        ),
        CommentaryCategory.BACK_TO_BACK_SIX to listOf(
            CommentaryPair(
                CommentaryLine("b2bsix_1_excited", "Another six! Back to back maximums, this is astonishing hitting!", "/_cdn/commentary/b2bsix_1_excited.mp3"),
                CommentaryLine("b2bsix_1_calm", "Two sixes in a row, that is serious power.", "/_cdn/commentary/b2bsix_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("b2bsix_2_excited", "Back to back sixes! The crowd cannot believe what they're seeing!", "/_cdn/commentary/b2bsix_2_excited.mp3"),
                CommentaryLine("b2bsix_2_calm", "Consecutive maximums, complete control from the batter right now.", "/_cdn/commentary/b2bsix_2_calm.mp3")
            )
        ),
        CommentaryCategory.TRIPLE_FOUR_OVER to listOf(
            CommentaryPair(
                CommentaryLine("triplefour_1_excited", "Three fours in the over! What a punishing spell for the batting side!", "/_cdn/commentary/triplefour_1_excited.mp3"),
                CommentaryLine("triplefour_1_calm", "That over has completely gotten away from the bowler.", "/_cdn/commentary/triplefour_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("triplefour_2_excited", "A hat-trick of boundaries this over, the batter is dominating!", "/_cdn/commentary/triplefour_2_excited.mp3"),
                CommentaryLine("triplefour_2_calm", "Three boundaries in one over, real momentum building now.", "/_cdn/commentary/triplefour_2_calm.mp3")
            )
        ),
        CommentaryCategory.TRIPLE_SIX_OVER to listOf(
            CommentaryPair(
                CommentaryLine("triplesix_1_excited", "Three sixes in the over! That is simply extraordinary hitting!", "/_cdn/commentary/triplesix_1_excited.mp3"),
                CommentaryLine("triplesix_1_calm", "An over the bowler will want to forget in a hurry.", "/_cdn/commentary/triplesix_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("triplesix_2_excited", "A hat-trick of maximums this over, unbelievable power on display!", "/_cdn/commentary/triplesix_2_excited.mp3"),
                CommentaryLine("triplesix_2_calm", "Three sixes in a single over, total carnage from the batter.", "/_cdn/commentary/triplesix_2_calm.mp3")
            )
        ),
        CommentaryCategory.TIGHT_OVER to listOf(
            CommentaryPair(
                CommentaryLine("tightover_1_excited", "Excellent over! Barely anything given away there, superb control!", "/_cdn/commentary/tightover_1_excited.mp3"),
                CommentaryLine("tightover_1_calm", "Really disciplined bowling, exactly what the team needed.", "/_cdn/commentary/tightover_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("tightover_2_excited", "What a tight over! The pressure is building beautifully!", "/_cdn/commentary/tightover_2_excited.mp3"),
                CommentaryLine("tightover_2_calm", "Outstanding execution, dot balls doing the job nicely.", "/_cdn/commentary/tightover_2_calm.mp3")
            )
        ),
        CommentaryCategory.EXPENSIVE_OVER to listOf(
            CommentaryPair(
                CommentaryLine("expensiveover_1_excited", "What an expensive over! The batting side has taken full toll!", "/_cdn/commentary/expensiveover_1_excited.mp3"),
                CommentaryLine("expensiveover_1_calm", "That over will hurt the bowling figures significantly.", "/_cdn/commentary/expensiveover_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("expensiveover_2_excited", "Runs flowing freely there, a costly over for the bowler!", "/_cdn/commentary/expensiveover_2_excited.mp3"),
                CommentaryLine("expensiveover_2_calm", "Line and length went missing in that over, a tough one to take.", "/_cdn/commentary/expensiveover_2_calm.mp3")
            )
        ),
        CommentaryCategory.PARTNERSHIP_50 to listOf(
            CommentaryPair(
                CommentaryLine("partnership50_1_excited", "Fifty partnership! A really valuable stand building here!", "/_cdn/commentary/partnership50_1_excited.mp3"),
                CommentaryLine("partnership50_1_calm", "Good understanding between these two, steadying the innings nicely.", "/_cdn/commentary/partnership50_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("partnership50_2_excited", "That's fifty for the partnership, exactly what the team needed!", "/_cdn/commentary/partnership50_2_excited.mp3"),
                CommentaryLine("partnership50_2_calm", "Solid teamwork out there, complementing each other well.", "/_cdn/commentary/partnership50_2_calm.mp3")
            )
        ),
        CommentaryCategory.PARTNERSHIP_100 to listOf(
            CommentaryPair(
                CommentaryLine("partnership100_1_excited", "A hundred partnership! What a fantastic stand between these two!", "/_cdn/commentary/partnership100_1_excited.mp3"),
                CommentaryLine("partnership100_1_calm", "Excellent teamwork, this partnership has really taken the game away.", "/_cdn/commentary/partnership100_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("partnership100_2_excited", "Century stand! This has been a match-defining partnership!", "/_cdn/commentary/partnership100_2_excited.mp3"),
                CommentaryLine("partnership100_2_calm", "Two players in complete control, a wonderful display of batting.", "/_cdn/commentary/partnership100_2_calm.mp3")
            )
        ),
        CommentaryCategory.PARTNERSHIP_150 to listOf(
            CommentaryPair(
                CommentaryLine("partnership150_1_excited", "One hundred and fifty for this partnership, absolutely outstanding!", "/_cdn/commentary/partnership150_1_excited.mp3"),
                CommentaryLine("partnership150_1_calm", "Remarkable consistency between these two batters.", "/_cdn/commentary/partnership150_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("partnership150_2_excited", "A magnificent one-fifty stand, this is taking the game away completely!", "/_cdn/commentary/partnership150_2_excited.mp3"),
                CommentaryLine("partnership150_2_calm", "Superb concentration and understanding, a truly special partnership.", "/_cdn/commentary/partnership150_2_calm.mp3")
            )
        ),
        CommentaryCategory.PARTNERSHIP_200 to listOf(
            CommentaryPair(
                CommentaryLine("partnership200_1_excited", "Two hundred for the partnership! An extraordinary stand out there!", "/_cdn/commentary/partnership200_1_excited.mp3"),
                CommentaryLine("partnership200_1_calm", "This is now one of the great partnerships of the match.", "/_cdn/commentary/partnership200_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("partnership200_2_excited", "A double century stand! What an incredible display of batting together!", "/_cdn/commentary/partnership200_2_excited.mp3"),
                CommentaryLine("partnership200_2_calm", "Sustained excellence from both batters, a truly memorable partnership.", "/_cdn/commentary/partnership200_2_calm.mp3")
            )
        ),
        CommentaryCategory.BOWLER_3_WICKETS to listOf(
            CommentaryPair(
                CommentaryLine("bowler3wkts_1_excited", "Three wickets now for the bowler, a superb spell in progress!", "/_cdn/commentary/bowler3wkts_1_excited.mp3"),
                CommentaryLine("bowler3wkts_1_calm", "Really impressive work, right among the wickets today.", "/_cdn/commentary/bowler3wkts_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("bowler3wkts_2_excited", "Three scalps in the bag, this bowler is right on top!", "/_cdn/commentary/bowler3wkts_2_excited.mp3"),
                CommentaryLine("bowler3wkts_2_calm", "A fine display of skill and control from the bowler.", "/_cdn/commentary/bowler3wkts_2_calm.mp3")
            )
        ),
        CommentaryCategory.BOWLER_5_WICKETS to listOf(
            CommentaryPair(
                CommentaryLine("bowler5wkts_1_excited", "Five wickets! What a magnificent haul for the bowler!", "/_cdn/commentary/bowler5wkts_1_excited.mp3"),
                CommentaryLine("bowler5wkts_1_calm", "A superb five-for, that is a career highlight.", "/_cdn/commentary/bowler5wkts_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("bowler5wkts_2_excited", "Five-wicket haul complete! An outstanding bowling performance!", "/_cdn/commentary/bowler5wkts_2_excited.mp3"),
                CommentaryLine("bowler5wkts_2_calm", "Brilliant skill on display, thoroughly deserved reward.", "/_cdn/commentary/bowler5wkts_2_calm.mp3")
            )
        ),
        CommentaryCategory.BOWLER_10_WICKETS to listOf(
            CommentaryPair(
                CommentaryLine("bowler10wkts_1_excited", "Ten wickets! An extraordinary achievement for the bowler!", "/_cdn/commentary/bowler10wkts_1_excited.mp3"),
                CommentaryLine("bowler10wkts_1_calm", "That is a truly outstanding all-round bowling performance.", "/_cdn/commentary/bowler10wkts_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("bowler10wkts_2_excited", "Every single wicket! What a phenomenal display from the bowler!", "/_cdn/commentary/bowler10wkts_2_excited.mp3"),
                CommentaryLine("bowler10wkts_2_calm", "One of the finest bowling performances you will ever see.", "/_cdn/commentary/bowler10wkts_2_calm.mp3")
            )
        ),
        CommentaryCategory.HAT_TRICK to listOf(
            CommentaryPair(
                CommentaryLine("hattrick_1_excited", "A hat-trick! Unbelievable scenes, three wickets in three balls!", "/_cdn/commentary/hattrick_1_excited.mp3"),
                CommentaryLine("hattrick_1_calm", "Extraordinary achievement, that will go down in the history books.", "/_cdn/commentary/hattrick_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("hattrick_2_excited", "Hat-trick! The bowler has done it, what an incredible feat!", "/_cdn/commentary/hattrick_2_excited.mp3"),
                CommentaryLine("hattrick_2_calm", "Remarkable skill under pressure, a truly special moment.", "/_cdn/commentary/hattrick_2_calm.mp3")
            )
        ),
        CommentaryCategory.ON_HAT_TRICK to listOf(
            CommentaryPair(
                CommentaryLine("onhattrick_1_excited", "The bowler is on a hat-trick! The tension in the crowd is unbelievable!", "/_cdn/commentary/onhattrick_1_excited.mp3"),
                CommentaryLine("onhattrick_1_calm", "Everyone on their feet, this could be a special moment right here.", "/_cdn/commentary/onhattrick_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("onhattrick_2_excited", "Hat-trick ball coming up! What a moment this is for the bowler!", "/_cdn/commentary/onhattrick_2_excited.mp3"),
                CommentaryLine("onhattrick_2_calm", "The whole ground holds its breath, anticipation building beautifully.", "/_cdn/commentary/onhattrick_2_calm.mp3")
            )
        ),
        CommentaryCategory.DOUBLE_HAT_TRICK to listOf(
            CommentaryPair(
                CommentaryLine("doublehattrick_1_excited", "A double hat-trick! This is simply sensational, four wickets in a row!", "/_cdn/commentary/doublehattrick_1_excited.mp3"),
                CommentaryLine("doublehattrick_1_calm", "Utterly remarkable, one of the rarest feats in the sport.", "/_cdn/commentary/doublehattrick_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("doublehattrick_2_excited", "Four in a row! An extraordinary double hat-trick from the bowler!", "/_cdn/commentary/doublehattrick_2_excited.mp3"),
                CommentaryLine("doublehattrick_2_calm", "Words cannot describe what we have just witnessed out there.", "/_cdn/commentary/doublehattrick_2_calm.mp3")
            )
        ),
        CommentaryCategory.TOSS_BAT to listOf(
            CommentaryPair(
                CommentaryLine("tossbat_1_excited", "And they've won the toss, and this side will bat first!", "/_cdn/commentary/tossbat_1_excited.mp3"),
                CommentaryLine("tossbat_1_calm", "Good decision, get some runs on the board and see how the pitch behaves.", "/_cdn/commentary/tossbat_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("tossbat_2_excited", "The toss goes their way, and it's an easy choice, batting first!", "/_cdn/commentary/tossbat_2_excited.mp3"),
                CommentaryLine("tossbat_2_calm", "Sensible call, put a total up and put the pressure back on the chase.", "/_cdn/commentary/tossbat_2_calm.mp3")
            )
        ),
        CommentaryCategory.TOSS_BOWL to listOf(
            CommentaryPair(
                CommentaryLine("tossbowl_1_excited", "They've won the toss, and they're sending the opposition in to bat first!", "/_cdn/commentary/tossbowl_1_excited.mp3"),
                CommentaryLine("tossbowl_1_calm", "Bowl first, defend a target, a fairly common strategy in these conditions.", "/_cdn/commentary/tossbowl_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("tossbowl_2_excited", "Toss goes their way, and straight away the decision is to bowl!", "/_cdn/commentary/tossbowl_2_excited.mp3"),
                CommentaryLine("tossbowl_2_calm", "Good thinking, have a look at the pitch and know exactly what you're chasing.", "/_cdn/commentary/tossbowl_2_calm.mp3")
            )
        ),
        CommentaryCategory.FIELDING_CHANGE to listOf(
            CommentaryPair(
                CommentaryLine("fieldingchange_1_excited", "And we've got a change in the field here, the captain's clearly got a plan!", "/_cdn/commentary/fieldingchange_1_excited.mp3"),
                CommentaryLine("fieldingchange_1_calm", "Yes, a little rethink from the fielding side, trying to cut off the scoring options.", "/_cdn/commentary/fieldingchange_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("fieldingchange_2_excited", "Fielders on the move! Something's been set up for this next delivery!", "/_cdn/commentary/fieldingchange_2_excited.mp3"),
                CommentaryLine("fieldingchange_2_calm", "Smart captaincy, always adjusting based on how the batter is playing.", "/_cdn/commentary/fieldingchange_2_calm.mp3")
            )
        ),
        CommentaryCategory.RAIN_START to listOf(
            CommentaryPair(
                CommentaryLine("rainstart_1_excited", "And there's the rain! The players are being taken off the field right away!", "/_cdn/commentary/rainstart_1_excited.mp3"),
                CommentaryLine("rainstart_1_calm", "Yes, the covers are coming on, we'll have to wait this one out.", "/_cdn/commentary/rainstart_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("rainstart_2_excited", "Play has been stopped for rain! Not the interruption anyone wanted at this stage!", "/_cdn/commentary/rainstart_2_excited.mp3"),
                CommentaryLine("rainstart_2_calm", "Frustrating for both sides, but nothing to be done until it clears.", "/_cdn/commentary/rainstart_2_calm.mp3")
            )
        ),
        CommentaryCategory.RAIN_STOP to listOf(
            CommentaryPair(
                CommentaryLine("rainstop_1_excited", "Good news, the rain has passed and we are ready to continue!", "/_cdn/commentary/rainstop_1_excited.mp3"),
                CommentaryLine("rainstop_1_calm", "The ground staff have done a fine job getting us back under way.", "/_cdn/commentary/rainstop_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("rainstop_2_excited", "We are back in business! Play is set to resume right now!", "/_cdn/commentary/rainstop_2_excited.mp3"),
                CommentaryLine("rainstop_2_calm", "A shortened passage of play now, so things will move quickly from here.", "/_cdn/commentary/rainstop_2_calm.mp3")
            )
        ),
        CommentaryCategory.DLS_REVISED to listOf(
            CommentaryPair(
                CommentaryLine("dlsrevised_1_excited", "And here comes the revised target, straight off the Duckworth-Lewis-Stern method!", "/_cdn/commentary/dlsrevised_1_excited.mp3"),
                CommentaryLine("dlsrevised_1_calm", "A whole new equation for the chasing side to work out now.", "/_cdn/commentary/dlsrevised_1_calm.mp3")
            ),
            CommentaryPair(
                CommentaryLine("dlsrevised_2_excited", "The par score has been recalculated, and the target has changed!", "/_cdn/commentary/dlsrevised_2_excited.mp3"),
                CommentaryLine("dlsrevised_2_calm", "Everyone will need to recheck the numbers before the next ball.", "/_cdn/commentary/dlsrevised_2_calm.mp3")
            )
        )
    )

    /**
     * Maps a ball outcome to the commentary category used for the AI
     * voice duo banter. Reads the exact same fields (dismissalType,
     * isEdge) that MatchEngine.generateCommentary() used to pick the
     * on-screen text, so the AI voice and the text always describe the
     * ball the same way.
     */
    fun categorizeBallOutcome(outcome: BallOutcome): CommentaryCategory {
        if (outcome.isWicket) {
            if (outcome.dismissalType == DismissalType.BOWLED) return CommentaryCategory.WICKET_BOWLED
            if (outcome.dismissalType == DismissalType.LBW) return CommentaryCategory.WICKET_LBW
            // caught
            return if (outcome.isEdge == true) CommentaryCategory.WICKET_CAUGHT_EDGE else CommentaryCategory.WICKET_CAUGHT_OUTFIELD
        }
        if (outcome.isWide) return CommentaryCategory.WIDE
        if (outcome.isNoBall) return CommentaryCategory.NO_BALL
        if (outcome.runs == 6) return if (outcome.isEdge == true) CommentaryCategory.SIX_EDGE else CommentaryCategory.SIX_CLEAN
        if (outcome.runs == 4) return if (outcome.isEdge == true) CommentaryCategory.FOUR_EDGE else CommentaryCategory.FOUR_CLEAN
        if (outcome.runs == 5) return CommentaryCategory.FIVE
        if (outcome.runs == 3) return CommentaryCategory.THREE
        if (outcome.runs == 2) return CommentaryCategory.TWO
        if (outcome.runs == 1) return CommentaryCategory.SINGLE
        return CommentaryCategory.DOT
    }

    private val MILESTONE_THRESHOLDS: List<Pair<Int, CommentaryCategory>> = listOf(
        250 to CommentaryCategory.MILESTONE_250,
        200 to CommentaryCategory.MILESTONE_200,
        150 to CommentaryCategory.MILESTONE_150,
        100 to CommentaryCategory.MILESTONE_100,
        50 to CommentaryCategory.MILESTONE_50
    )

    /**
     * Given a batsman's runs total just before and just after a ball,
     * finds the highest milestone category crossed by that ball (if
     * any). Returns null if no milestone (50/100/150/200/250) was
     * crossed.
     */
    fun getMilestoneCrossed(prevRuns: Int, newRuns: Int): CommentaryCategory? {
        for ((threshold, category) in MILESTONE_THRESHOLDS) {
            if (prevRuns < threshold && newRuns >= threshold) return category
        }
        return null
    }

    private val PARTNERSHIP_MILESTONE_THRESHOLDS: List<Pair<Int, CommentaryCategory>> = listOf(
        200 to CommentaryCategory.PARTNERSHIP_200,
        150 to CommentaryCategory.PARTNERSHIP_150,
        100 to CommentaryCategory.PARTNERSHIP_100,
        50 to CommentaryCategory.PARTNERSHIP_50
    )

    /**
     * Given a partnership's total runs just before and just after a
     * ball, finds the highest partnership milestone crossed (if any).
     * Returns null if no milestone (50/100/150/200) was crossed.
     */
    fun getPartnershipMilestoneCrossed(prevRuns: Int, newRuns: Int): CommentaryCategory? {
        for ((threshold, category) in PARTNERSHIP_MILESTONE_THRESHOLDS) {
            if (prevRuns < threshold && newRuns >= threshold) return category
        }
        return null
    }

    private val BOWLER_WICKET_MILESTONE_THRESHOLDS: List<Pair<Int, CommentaryCategory>> = listOf(
        10 to CommentaryCategory.BOWLER_10_WICKETS,
        5 to CommentaryCategory.BOWLER_5_WICKETS,
        3 to CommentaryCategory.BOWLER_3_WICKETS
    )

    /**
     * Given a bowler's wicket count just before and just after a ball,
     * finds the highest wicket-haul milestone crossed (if any). Returns
     * null if no milestone (3/5/10) was crossed.
     */
    fun getBowlerWicketMilestoneCrossed(prevWickets: Int, newWickets: Int): CommentaryCategory? {
        for ((threshold, category) in BOWLER_WICKET_MILESTONE_THRESHOLDS) {
            if (prevWickets < threshold && newWickets >= threshold) return category
        }
        return null
    }

    /**
     * Given a bowler's current consecutive-wicket streak (after taking
     * a wicket on this ball), returns the matching commentary category:
     * 2 in a row means the next ball is a hat-trick ball, 3 in a row is
     * the hat-trick itself, 4 in a row is a double hat-trick. Returns
     * null for any other streak length.
     */
    fun getHatTrickCategory(streak: Int): CommentaryCategory? = when (streak) {
        2 -> CommentaryCategory.ON_HAT_TRICK
        3 -> CommentaryCategory.HAT_TRICK
        4 -> CommentaryCategory.DOUBLE_HAT_TRICK
        else -> null
    }

    fun getRandomCommentaryPair(category: CommentaryCategory): CommentaryPair {
        val variants = LIBRARY.getValue(category)
        val index = (Random.nextDouble() * variants.size).toInt().coerceAtMost(variants.size - 1)
        return variants[index]
    }

    /**
     * Flat list of every commentary line in the library — used by the
     * web source's one-time audio generation script. Kept for parity;
     * an Android equivalent would use this the same way if it ever
     * needs to drive its own audio-asset generation/bundling pipeline.
     */
    fun getAllCommentaryLines(): List<CommentaryLineEntry> {
        val lines = mutableListOf<CommentaryLineEntry>()
        for (category in CommentaryCategory.values()) {
            for (pair in LIBRARY.getValue(category)) {
                lines.add(CommentaryLineEntry(pair.excited.id, pair.excited.text, CommentaryVoice.EXCITED))
                lines.add(CommentaryLineEntry(pair.calm.id, pair.calm.text, CommentaryVoice.CALM))
            }
        }
        return lines
    }
}
