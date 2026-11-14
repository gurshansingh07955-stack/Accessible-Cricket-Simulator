package com.cricketsim.logic

import kotlin.random.Random

/** What the press conference is about; kept on the career until it has been held (it survives closing the app). */
data class PressTrigger(
    /** "POM" (Player of the Match), "SERIES" (Player of the Series) or "TITLE" (a final won as the captain). */
    val kind: String = "POM",
    /** "international" or "league". */
    val level: String = "league",
    /** What he did: "bat", "bowl" or "all". */
    val perf: String = "bat",
    val won: Boolean = true,
    /** A knockout match, a final or a whole competition. */
    val big: Boolean = false,
    /** Picks the questions, so the same press conference comes back the same after the app is closed. */
    val seed: Long = 0L,
    /** The competition's name, for the news line and the opening words. */
    val place: String = "",
    /** He is the captain of the side that won (a title press conference asks about leading the team). */
    val captain: Boolean = false,
    /** His own play in the match was very good (Player of the Match, fifty or more runs, three or more wickets): the questions then ask about it too. */
    val goodPerf: Boolean = false
)

/** One choice. [id] names its recording (part three); [mood] is what the press make of it. */
class PressAnswer(val id: String, val tone: String, val mood: Int, val text: String)

/**
 * One question with its choices. The text never holds a name or a number, so a recording of it (part three) is always right.
 * The optional conditions say when it can be asked: for which award, what he did, whether the team won, whether it was a big occasion.
 */
data class PressQuestion(
    val id: String,
    val topic: String,
    val text: String,
    val answers: List<PressAnswer>,
    val kind: String? = null,
    val perfs: Set<String>? = null,
    val won: Boolean? = null,
    val big: Boolean? = null
)

/**
 * My Player, part twenty-four, second part: the press conference.
 *
 * WHEN: after a Player of the Match award (a match he played) or a Player of the Series award (the competition is over) in an international match, an
 * international tournament or a franchise league. It is a pop-up that comes by itself when he is back in the career hub (after a played or a simulated match).
 * Switch it off with [PRESS_CONFERENCES].
 *
 * WHAT: six questions (an opener, one about the award, one about what he did, one about the match, and two more from pressure, the team, what comes next
 * and his life), each with four or five answers in different tones. Several variants of every question exist; the context picks which one is asked.
 *
 * WHAT IT CHANGES: the press mood (what the papers make of his answers: Humble, Team first, Confident, Bold, Careful) and a line in his news. Nothing else:
 * it does not touch his ratings or the selectors.
 */
object CareerPress {
    const val PRESS_CONFERENCES = true
    private const val QUESTIONS_BEYOND_THE_FOUR = 2

    /** The reporters take turns: the question in position n (from 0) of a press conference is asked by reporter n mod 4, so no two in a row sound alike. */
    const val REPORTER_VOICES = 4

    /** The recording of a question as that reporter asks it (the clip id: <question id>_r0 to _r3). The player's answers are recorded as their own ids. */
    fun questionClipId(q: PressQuestion, position: Int): String = "${q.id}_r${position % REPORTER_VOICES}"

    private fun a(tone: String, mood: Int, text: String) = Triple(tone, mood, text)

    private fun q(id: String, topic: String, text: String, vararg answers: Triple<String, Int, String>): PressQuestion =
        PressQuestion(id, topic, text, answers.mapIndexed { i, (tone, mood, t) -> PressAnswer("${id}_${"abcde"[i]}", tone, mood, t) })

    // ---------------------------------------------------------------- the questions

    val BANK: List<PressQuestion> = listOf(
        // ---- the opener
        q("pq_open_1", "open", "Congratulations. How are you feeling after that performance?",
            a("Humble", 1, "Grateful, honestly. I just wanted to do my job, and it went my way today."),
            a("Confident", 1, "Feeling great. I prepared hard for this, and it is nice to see the work pay off."),
            a("Team first", 1, "Happy for the whole group. This is a team effort, and I am only one part of it."),
            a("Bold", -1, "Like a player who belongs on this stage. I knew this was coming."),
            a("Careful", 0, "Tired, to be honest, but very pleased. Let me enjoy it for a moment.")),
        q("pq_open_2", "open", "You must be delighted. What is going through your mind right now?",
            a("Humble", 1, "Mostly relief and thanks. There is a lot of luck in this game, and today I had my share."),
            a("Confident", 1, "That I can do it, again and again. It is a good feeling to carry forward."),
            a("Team first", 1, "The dressing room, really. The boys and the staff have backed me all the way."),
            a("Careful", 0, "Nothing too big. There is more cricket ahead, so I am already thinking about the next game."),
            a("Bold", 2, "That the best is still to come. Today was just a taste.")),
        q("pq_open_3", "open", "A big day for you. Take us through how it feels.",
            a("Humble", 1, "Special, and a little overwhelming. I will need a few days to take it in."),
            a("Confident", 1, "It feels like the result of years of hard work. I am proud of that."),
            a("Team first", 1, "It feels like we did it together. I will share this one with everyone in that dressing room."),
            a("Bold", 1, "It feels the way I imagined it would. I back myself in moments like this.")),

        // ---- the award
        q("pq_award_pom_1", "award", "You have been named Player of the Match. What does that mean to you?",
            a("Humble", 1, "It is a great honour, but cricket is a team game, and plenty of others deserved it too."),
            a("Confident", 1, "It means a lot. It is nice when a performance is noticed, and I want more of them."),
            a("Team first", 1, "I will happily take it, but I would swap it for a team trophy any day."),
            a("Bold", 2, "It means I am doing what I am here to do: win matches. I want this every week.")).copy(kind = "POM"),
        q("pq_award_pom_2", "award", "That is a Player of the Match award. How special is it to be recognised on this stage?",
            a("Humble", 1, "It is humbling. I just try to turn up and give everything for the team."),
            a("Confident", 1, "Very special, and it gives me confidence. I feel I am playing some of my best cricket."),
            a("Careful", 0, "It is nice, but I do not look at awards too much. I look at how I can improve."),
            a("Team first", 1, "Every award is shared with my teammates. They create the chances, and I try to take them.")).copy(kind = "POM"),
        q("pq_award_series_1", "award", "You have been named Player of the Series. How does that feel?",
            a("Humble", 1, "Unbelievable, really. I have had a lot of help from my teammates and my coaches along the way."),
            a("Confident", 1, "It feels right. I worked for this, and it shows that I belong at this level."),
            a("Team first", 1, "It belongs to the whole squad. I could only do what I did because of the people around me."),
            a("Bold", 2, "It feels like the start of something. I want to be the best in the world, and this is a step.")).copy(kind = "SERIES"),
        q("pq_award_series_2", "award", "Player of the Series is a big honour. What will you take away from this tournament?",
            a("Humble", 1, "That every day there is something new to learn. I am still a student of the game."),
            a("Confident", 1, "That I can be a match winner when it matters. That belief will stay with me."),
            a("Team first", 1, "The friendships and the memories with this group. They matter more than any award."),
            a("Careful", 0, "I will watch the videos first. There are always things to fix, even after a good run.")).copy(kind = "SERIES"),

        // ---- what he did: batting (also asked of an all-rounder)
        q("pq_perf_bat_1", "perf", "Talk us through your batting. What was the plan when you walked in?",
            a("Careful", 0, "Keep it simple: watch the ball, respect the good deliveries, and punish the bad ones."),
            a("Confident", 1, "To take the game on. I backed my strengths, and the plan worked."),
            a("Team first", 1, "To read the situation and play what the team needed. The runs were secondary."),
            a("Bold", 1, "To dominate. I wanted the bowlers to know they were in for a long day.")).copy(perfs = setOf("bat", "all")),
        q("pq_perf_bat_2", "perf", "You looked in control out there. Was it your best innings?",
            a("Humble", 1, "I do not know about that. There are always shots I would like to play better."),
            a("Confident", 1, "It is right up there. Everything came together today."),
            a("Careful", 0, "Maybe, maybe not. I will see how I feel about it after a few days."),
            a("Bold", 2, "So far, yes. And I think there is better to come.")).copy(perfs = setOf("bat", "all")),
        q("pq_perf_bat_3", "perf", "The bowlers tried everything. How did you stay so calm at the crease?",
            a("Humble", 1, "I did not feel calm at all. I just kept telling myself to take it one ball at a time."),
            a("Confident", 1, "I trust my game. When the ball is there to hit, I hit it, and when it is not, I wait."),
            a("Team first", 1, "My partners kept me relaxed. We talked all the time between the overs."),
            a("Careful", 0, "Years of practice and a lot of deep breaths. It really is about the next ball only.")).copy(perfs = setOf("bat", "all")),

        // ---- what he did: bowling (also asked of an all-rounder)
        q("pq_perf_bowl_1", "perf", "Your bowling changed the game. What was your thinking with the ball?",
            a("Careful", 0, "Hit the right areas and wait for the batters to make a mistake."),
            a("Confident", 1, "I wanted to attack the stumps. When I get my rhythm, I back myself against anyone."),
            a("Team first", 1, "To do the job my captain asked for. The plan was made by the whole bowling group."),
            a("Bold", 1, "To take wickets, not to contain. A batter who sees me should be worried.")).copy(perfs = setOf("bowl", "all")),
        q("pq_perf_bowl_2", "perf", "The batters struggled against you. What was working best for you?",
            a("Humble", 1, "A lot of it was the fielders and the pitch. I just got a few good balls in the right places."),
            a("Confident", 1, "My lengths and my variations. I had everything under control today."),
            a("Careful", 0, "Nothing special. I tried to stay patient and let the pressure do the work."),
            a("Bold", -1, "Quality. When I bowl well, even the best in the world find it hard.")).copy(perfs = setOf("bowl", "all")),
        q("pq_perf_bowl_3", "perf", "That was a wonderful spell. Is there a particular wicket you will remember?",
            a("Humble", 1, "The first one, because it gave the team some momentum."),
            a("Confident", 1, "The big one, the key batter. I had been planning that one for a while."),
            a("Team first", 1, "The catch that finished it off. A bowler is nothing without his fielders."),
            a("Careful", 0, "I will not pick one. They all came from the same plan, so I am happy with all of them.")).copy(perfs = setOf("bowl", "all")),

        // ---- what he did: both
        q("pq_perf_all_1", "perf", "You did well with bat and ball. How hard is it to balance the two roles?",
            a("Humble", 1, "It is not easy. I rely on a lot of good advice and a lot of rest."),
            a("Confident", 1, "I love it. Being in the game all the time is where I do my best work."),
            a("Team first", 1, "It is what the team needs from me, so it is not a burden. It is a privilege."),
            a("Bold", 2, "It is easy when you are good at both. I want to be the best all-rounder in the world.")).copy(perfs = setOf("all")),

        // ---- the match: a win
        q("pq_match_win_1", "match", "It was a good win for the team. What made the difference?",
            a("Team first", 1, "Everyone chipped in. When all eleven play their part, it is hard to beat us."),
            a("Confident", 1, "We were the better side today, and we played like it from start to finish."),
            a("Careful", 0, "A few key moments went our way. We will look at the video and see how to repeat them."),
            a("Humble", 1, "We got a bit lucky at times. Credit to the opposition, who made us work for it.")).copy(won = true),
        q("pq_match_win_2", "match", "Your side was brilliant. Can this win be a turning point for the team?",
            a("Team first", 1, "I hope so. The confidence in the dressing room is high, and we want to build on it."),
            a("Careful", 0, "Let us not get ahead of ourselves. It is one win, and we need to repeat it."),
            a("Bold", 1, "It should be. We have shown what we can do, and we are not slowing down."),
            a("Humble", 1, "Maybe. But we are still learning, and there is plenty to improve.")).copy(won = true),
        q("pq_match_win_3", "match", "The opposition fought hard. Were you ever worried during the match?",
            a("Humble", 1, "Of course. In cricket you are never safe, and there were a few nervous moments."),
            a("Confident", 1, "Not really. We had the game under control, and I trusted the guys around me."),
            a("Team first", 1, "We talked a lot during the breaks. As long as we stuck to the plan, I was comfortable."),
            a("Bold", 1, "Never. I wanted to be in the middle when the game was on the line.")).copy(won = true),

        // ---- the match: a defeat
        q("pq_match_loss_1", "match", "It was a tough loss for the team. What went wrong?",
            a("Team first", 1, "We were not good enough as a group. We win together, and we lose together."),
            a("Careful", 0, "A few moments did not go our way. We will look at them and fix them."),
            a("Humble", 1, "The opposition played better. We have to give them credit and learn from the day."),
            a("Bold", 0, "Nothing in our ability. We beat ourselves, and we will come back stronger.")).copy(won = false),
        q("pq_match_loss_2", "match", "Your performance was great, but it was not enough. How hard is that to take?",
            a("Humble", 1, "Very hard. A personal award means little when the team loses."),
            a("Team first", 1, "It hurts. I wanted to give the team a win, and we fell short."),
            a("Careful", 0, "That is sport. I will take the positives and move on to the next game."),
            a("Bold", 1, "I will keep delivering. Sooner or later the team will cross the line with me.")).copy(won = false),

        // ---- pressure
        q("pq_pressure_1", "pressure", "It was a big occasion. How did you handle the pressure?",
            a("Humble", 1, "I felt it, like everyone does. I tried to breathe and focus on the next ball."),
            a("Confident", 1, "I enjoy pressure. That is why we play the game."),
            a("Team first", 1, "The senior players kept us calm. We looked after each other."),
            a("Careful", 0, "I kept my routines simple. Same warm-up, same thoughts, one ball at a time.")).copy(big = true),
        q("pq_pressure_2", "pressure", "Many players fold under pressure, but you seem to grow. What is your secret?",
            a("Humble", 1, "There is no secret. I have failed plenty of times, and I have learned from it."),
            a("Confident", 1, "I believe in my skills. When you know you have done the work, the pressure goes."),
            a("Careful", 0, "Preparation, sleep and keeping things simple. It sounds boring, but it works."),
            a("Bold", 2, "I like it when the lights are brightest. Pressure is a privilege.")),
        q("pq_pressure_3", "pressure", "Did you feel the expectation of the crowd out there?",
            a("Humble", 1, "Yes. They were wonderful, and I wanted to give them something to cheer about."),
            a("Confident", 1, "I fed off it. A loud crowd lifts me."),
            a("Team first", 1, "We all did. We drew energy from each other."),
            a("Careful", 0, "I tried to block it out. The crowd is great, but the game is played in the mind.")),

        // ---- the team
        q("pq_role_1", "role", "What has been the role of your captain and coach in this success?",
            a("Team first", 1, "A huge one. They trust me and give me the freedom to play my game."),
            a("Humble", 1, "They have taught me so much. I am a better player because of them."),
            a("Careful", 0, "They gave me a clear role, and I have tried to do it."),
            a("Bold", 1, "They give me the ball or the bat and let me do my thing. That is the best way to get the best out of me.")),
        q("pq_role_2", "role", "Who in the dressing room deserves a special mention today?",
            a("Team first", 1, "Everyone. But the support staff work so hard behind the scenes, and nobody sees it."),
            a("Humble", 1, "The senior players. They have taught me patience, and it paid off today."),
            a("Confident", 1, "My teammates in the middle. Without their support, my job would have been much harder."),
            a("Careful", 0, "I would rather thank them in private. We are a close group, and we like to keep it that way.")),
        q("pq_role_3", "role", "There is a lot of talk about your role in the team. Are you happy with it?",
            a("Humble", 1, "I am happy with any role. I just want to help the team win."),
            a("Confident", 1, "I think I can do even more. I would like to take on more responsibility."),
            a("Team first", 1, "The role that helps the team is the one I am happy with. Titles do not matter."),
            a("Bold", 1, "I want the big moments. Give me the ball or the bat when the game is on the line.")),

        // ---- what comes next
        q("pq_next_1", "next", "What is next for you and the team?",
            a("Careful", 0, "Rest, recover, and then the next match. We take it one game at a time."),
            a("Confident", 1, "More wins. We have shown what we can do, and we want to keep doing it."),
            a("Team first", 1, "To keep improving as a group. There is still a lot of work to do."),
            a("Bold", 2, "The title. That is what we are here for, and I want to be the one to lift it.")),
        q("pq_next_2", "next", "Do you have a message for the fans who have been supporting you?",
            a("Humble", 1, "Thank you. Your support means more than you know."),
            a("Team first", 1, "We play for you. We will keep fighting until the end."),
            a("Confident", 1, "Keep believing, because we are not done yet."),
            a("Bold", 1, "Keep your voices ready. There is a lot more to cheer about.")),
        q("pq_next_3", "next", "You are in great form. Is there a milestone you are chasing?",
            a("Humble", 1, "Not really. I just want to keep playing and keep enjoying it."),
            a("Careful", 0, "Not publicly. I have personal goals, but I keep them to myself."),
            a("Confident", 1, "Yes, a few. I want to keep raising my own standards."),
            a("Bold", -1, "Every record. I do not set limits for myself.")),

        // ---- his life
        q("pq_personal_1", "personal", "How do you deal with the attention and the criticism that come with success?",
            a("Humble", 1, "I try to ignore both. Praise can make you lose focus, and criticism can make you lose belief."),
            a("Confident", 1, "Criticism is fine. I know what I am capable of, so I do not let it bother me."),
            a("Team first", 1, "My family and friends keep me grounded. They treat me the same, win or lose."),
            a("Careful", 0, "I stay off social media during the tournament. It is better for my mind.")),
        q("pq_personal_2", "personal", "What do you do to prepare mentally and physically for such matches?",
            a("Careful", 0, "Routine is everything: sleep, food, training and quiet time before the match."),
            a("Humble", 1, "A lot of hard work in the nets, and a lot of help from my physios and coaches."),
            a("Confident", 1, "I picture the situations. By the time I am out there, I feel like I have been there before."),
            a("Team first", 1, "I train with my teammates. We push each other every day, and it shows.")),
        q("pq_personal_3", "personal", "Who has inspired you the most in your cricket journey?",
            a("Humble", 1, "My family. They sacrificed a lot so that I could play."),
            a("Team first", 1, "My first coach, and every teammate since. I owe them all."),
            a("Confident", 1, "The great players I watched growing up. I want to be remembered the way they are."),
            a("Careful", 0, "Many people, in different ways. I would not like to leave anyone out by naming only one.")),

        // ---- a final won as captain: the opener
        q("pq_open_title_1", "open", "Congratulations, captain. You have won the title. What is going through your mind?",
            a("Humble", 1, "Mostly gratitude. So many people helped this group get here, and I am proud to lead them."),
            a("Confident", 1, "That all the hard work was worth it. We believed in this plan, and it came off."),
            a("Team first", 1, "That this belongs to every single person in the squad. I am just the one holding the trophy."),
            a("Bold", 2, "That this is only the beginning. This team is built to win a lot more than this."),
            a("Careful", 0, "I am still taking it in. I will enjoy tonight, and then we get back to work.")).copy(kind = "TITLE"),
        q("pq_open_title_2", "open", "Champions! How does it feel to lift the trophy as captain?",
            a("Humble", 1, "Unreal. I never imagined I would be doing this. I have to thank everybody who believed in me."),
            a("Confident", 1, "It feels like the reward for the way we have played the whole tournament."),
            a("Team first", 1, "Special, because I get to share it with the boys. I would not swap this group for anything."),
            a("Bold", 2, "Exactly how I pictured it. I told the dressing room we were going to finish this.")).copy(kind = "TITLE"),

        // ---- the title
        q("pq_title_1", "title", "It was a final, and you were the winners. When did you feel the title was yours?",
            a("Careful", 0, "Not until the last ball. In a final, nothing is certain until the job is done."),
            a("Confident", 1, "Once we took control in the middle overs. From there I felt we would not let it go."),
            a("Humble", 1, "I am not sure I did, even then. I just kept thinking about the next ball."),
            a("Team first", 1, "When I looked around at the faces in the field. Everyone was giving everything, and that is when I knew.")).copy(kind = "TITLE"),
        q("pq_title_2", "title", "What will you remember most about this tournament?",
            a("Humble", 1, "The little moments: the early mornings, the nets, the long bus rides together."),
            a("Team first", 1, "The way the group came together after tough days. That is what won us the title."),
            a("Confident", 1, "The moment we lifted the trophy. All the effort came together in that one second."),
            a("Bold", 1, "That we were the best side here, from the first match to the last. Nobody can argue with that.")).copy(kind = "TITLE"),

        // ---- his captaincy
        q("pq_captain_1", "captain", "As captain, what was your plan for the final, and how did it work out?",
            a("Careful", 0, "Keep it simple, back our best players, and adjust as the game changed. It worked out."),
            a("Confident", 1, "To take the game to them. I wanted us to be the side that made the first move."),
            a("Team first", 1, "To let the senior players guide the key moments, and trust the group. It paid off."),
            a("Bold", 1, "To win it by playing the way we believe in. We never changed our style.")).copy(kind = "TITLE"),
        q("pq_captain_2", "captain", "You made some bold decisions during the match. How hard is it to make those calls?",
            a("Humble", 1, "Very hard. Sometimes you get them right and sometimes not, and today we were lucky."),
            a("Confident", 1, "You have to trust your gut. I would rather make a decision and stand by it."),
            a("Team first", 1, "I do not make them alone. I talk to the bowlers and the coach all the time."),
            a("Careful", 0, "I look at the pitch, the conditions and the players, and then I go with the best choice at that moment.")).copy(kind = "TITLE"),
        q("pq_captain_3", "captain", "What kind of team culture have you tried to build as captain?",
            a("Team first", 1, "One where everyone feels they matter, from the star player to the youngest in the squad."),
            a("Humble", 1, "An honest one. We talk openly, we learn from mistakes, and we do not blame each other."),
            a("Confident", 1, "A winning one. We expect to win, and we prepare like it every day."),
            a("Careful", 0, "A calm one. I want players to enjoy the game and play without fear.")).copy(kind = "TITLE"),
        q("pq_captain_4", "captain", "How did you handle the pressure of leading the side in a final?",
            a("Humble", 1, "I felt it all the way. But the players around me helped me stay calm."),
            a("Confident", 1, "I enjoy it. Leading in the big games is why I wanted to be captain."),
            a("Careful", 0, "I broke it down into small jobs: one over at a time, one decision at a time."),
            a("Team first", 1, "I leaned on my vice-captain and the senior guys. A captain is only as good as his team.")).copy(kind = "TITLE"),
        q("pq_captain_5", "captain", "Who helped you the most in your growth as a captain?",
            a("Humble", 1, "My coach and the captains before me. They shared so much with me, and I am still learning."),
            a("Team first", 1, "The whole dressing room. They gave me their trust, and that is what makes a captain."),
            a("Confident", 1, "Every tough game I have led. Mistakes teach you more than wins ever do."),
            a("Careful", 0, "I do not want to name just one. A lot of people gave me small pieces of advice, and I used them all.")).copy(kind = "TITLE")
    )

    // ---------------------------------------------------------------- choosing the questions

    /** Six questions for this press conference: the opener, the award, what he did, the match, then two more. The same trigger always gives the same six. */
    fun questionsFor(t: PressTrigger): List<PressQuestion> {
        val random = Random(t.seed)
        fun fits(q: PressQuestion): Boolean =
            (q.kind == null || q.kind == t.kind) && (q.perfs == null || t.perf in q.perfs) &&
                (q.won == null || q.won == t.won) && (q.big == null || q.big == t.big)
        val used = HashSet<String>()
        fun pickFrom(topic: String): PressQuestion? {
            val options = BANK.filter { it.topic == topic && fits(it) && it.id !in used }
            val pick = if (options.isEmpty()) null else options[random.nextInt(options.size)]
            if (pick != null) used.add(pick.id)
            return pick
        }
        val out = ArrayList<PressQuestion>()
        if (t.kind == "TITLE") {
            // A final won as captain: the title, his captaincy (twice if his own play was ordinary, once if it was very good), what he did if it
            // was very good, then the match and the pressure of a final. Six questions.
            val openers = BANK.filter { it.topic == "open" && it.kind == "TITLE" }
            if (openers.isNotEmpty()) {
                val opener = openers[random.nextInt(openers.size)]
                used.add(opener.id)
                out.add(opener)
            }
            listOf("title", "captain", if (t.goodPerf) "perf" else "captain", "match", "pressure").forEach { topic -> pickFrom(topic)?.let { out.add(it) } }
            return out
        }
        listOf("open", "award", "perf", "match").forEach { topic -> pickFrom(topic)?.let { out.add(it) } }
        val extras = listOf("pressure", "role", "next", "personal").shuffled(random)
        // A big occasion always gets its question about pressure.
        val order = if (t.big) listOf("pressure") + extras.filter { it != "pressure" } else extras
        order.take(QUESTIONS_BEYOND_THE_FOUR).forEach { topic -> pickFrom(topic)?.let { out.add(it) } }
        return out
    }

    // ---------------------------------------------------------------- when a press conference is called

    private fun eligible(state: TournamentState): Boolean = CareerInternational.isInternational(state) || CareerLeagues.isLeague(state)

    private fun roleFallback(career: CareerProfile): String {
        val r = career.role.lowercase()
        return when {
            r.contains("all") -> "all"
            r.contains("bowler") -> "bowl"
            else -> "bat"
        }
    }

    /** After a match of his: a press conference is called if he played and was named Player of the Match, or if he is the captain and his side won a final. */
    fun afterMyMatch(career: CareerProfile, state: TournamentState, fixtureId: Int, before: PlayerStats?, after: PlayerStats?, random: Random): CareerProfile {
        if (!PRESS_CONFERENCES || career.retired || !eligible(state)) return career
        val fixture = state.fixtures.find { it.id == fixtureId } ?: return career
        val result = fixture.result ?: return career
        val pid = CareerSeason.playerIdIn(state, career) ?: return career
        if (after == null || after.matches <= (before?.matches ?: 0)) return career
        val pom = result.playerOfMatchId == pid
        val won = result.winnerId == state.userTeamId
        // A final won as the captain gets its own press conference, award or not (the questions mix his captaincy and, if it was very good, his play).
        val title = fixture.stage == "Final" && won && CareerSeason.captainIn(career, state)
        if (!pom && !title) return career
        val runs = after.runs - (before?.runs ?: 0)
        val wickets = after.wickets - (before?.wickets ?: 0)
        val perf = when {
            runs >= 30 && wickets >= 2 -> "all"
            wickets >= 2 -> "bowl"
            runs >= 20 -> "bat"
            else -> roleFallback(career)
        }
        val trigger = PressTrigger(
            kind = if (title) "TITLE" else "POM",
            level = if (CareerInternational.isInternational(state)) "international" else "league",
            perf = perf,
            won = won,
            big = fixture.stage != CplData.LEAGUE,
            seed = random.nextLong(),
            place = CareerSeason.competitionOf(state).label,
            captain = title,
            goodPerf = pom || runs >= 50 || wickets >= 3
        )
        return career.copy(pendingPress = trigger)
    }

    /** After a step of the season: a press conference is called for each competition that has just ended in which he is the Player of the Series. */
    fun afterSeries(career: CareerProfile, before: List<TournamentState>, after: List<TournamentState>, random: Random): CareerProfile {
        if (!PRESS_CONFERENCES || career.retired) return career
        var out = career
        after.forEachIndexed { i, s ->
            val old = before.getOrNull(i) ?: return@forEachIndexed
            if (!eligible(s) || CareerSeason.isComplete(old) || !CareerSeason.isComplete(s)) return@forEachIndexed
            // The captain's title press conference of this competition already covers it.
            if (out.pendingPress?.kind == "TITLE" && out.pendingPress?.place == CareerSeason.competitionOf(s).label) return@forEachIndexed
            val pid = CareerSeason.playerIdIn(s, out) ?: return@forEachIndexed
            val st = s.playerStats[pid] ?: return@forEachIndexed
            if (st.matches == 0) return@forEachIndexed
            val best = s.playerStats.values.maxWithOrNull(compareBy<PlayerStats>({ it.impact }, { it.runs + it.wickets * 20 })) ?: return@forEachIndexed
            if (best.playerId != pid || best.impact <= 0.0) return@forEachIndexed
            val perf = when {
                st.runs >= 150 && st.wickets >= 6 -> "all"
                st.wickets * 25 > st.runs -> "bowl"
                else -> "bat"
            }
            out = out.copy(
                pendingPress = PressTrigger(
                    kind = "SERIES",
                    level = if (CareerInternational.isInternational(s)) "international" else "league",
                    perf = perf,
                    won = CareerSeason.champion(s)?.id == s.userTeamId,
                    big = true,
                    seed = random.nextLong(),
                    place = CareerSeason.competitionOf(s).label
                )
            )
        }
        return out
    }

    // ---------------------------------------------------------------- after the questions

    fun pressLine(career: CareerProfile): String = when {
        career.pressConferences == 0 -> ""
        career.pressMood >= 6 -> "The press are warm towards you."
        career.pressMood <= -4 -> "The press are cool towards you."
        else -> "The press are neutral towards you."
    }

    /** The press conference is over: the press mood moves, a line goes into the news. Returns the career and one spoken sentence. */
    fun finish(career: CareerProfile, trigger: PressTrigger, answers: List<PressAnswer>): Pair<CareerProfile, String> {
        val total = answers.sumOf { it.mood }
        val dominant = answers.groupBy { it.tone }.maxByOrNull { it.value.size }?.key ?: "Careful"
        val headline = when (dominant) {
            "Humble" -> "you come across as humble and grateful."
            "Team first" -> "you put the team first, and the papers like it."
            "Confident" -> "your confidence impresses the reporters."
            "Bold" -> if (total < 0) "your words split opinion in the papers." else "your bold words make the headlines."
            else -> "you give little away, and the reporters go home with little to write."
        }
        val updated = CareerNews.add(
            career.copy(
                pendingPress = null,
                pressMood = (career.pressMood + total).coerceIn(-30, 30),
                pressConferences = career.pressConferences + 1
            ),
            listOf(NewsItem("${trigger.place} press conference: $headline", true))
        )
        val feeling = when {
            total >= 5 -> "The reporters leave happy with you."
            total >= 0 -> "A fair press conference: the reporters have what they need."
            else -> "Some of your answers did not go down well with the press."
        }
        return Pair(updated, "Press conference over. $feeling ${pressLine(updated)}".trim())
    }

    /** He leaves without answering: the press notice. */
    fun skip(career: CareerProfile, trigger: PressTrigger): Pair<CareerProfile, String> {
        val updated = CareerNews.add(
            career.copy(
                pendingPress = null,
                pressMood = (career.pressMood - 1).coerceIn(-30, 30),
                pressConferences = career.pressConferences + 1
            ),
            listOf(NewsItem("${trigger.place}: you skip the press conference, and the reporters notice.", true))
        )
        return Pair(updated, "You skipped the press conference. ${pressLine(updated)}".trim())
    }
}
