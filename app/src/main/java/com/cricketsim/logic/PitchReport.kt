package com.cricketsim.logic

import com.cricketsim.ui.match.ScorecardText

/** One part of the pitch report: a heading and what it says. */
class PitchReportSection(val heading: String, val text: String)

/**
 * The pitch report (patch 134): the text for the screen, and the recorded commentary to play, in order: the category and whether the lead (true, excited) or the
 * co-commentator (false, calm) says it.
 */
class PitchReportData(val title: String, val sections: List<PitchReportSection>, val voice: List<Pair<CommentaryCategory, Boolean>>)

/**
 * What the commentators say before the toss. Nothing in it is random: it comes from the ground (its pitch type and description, boundary size, altitude, dew factor
 * and average score, in logic/StadiumData.kt) and from the weather rolled for it (logic/WeatherSystem.kt, shared with the match through [MatchWeatherCache]), and
 * ends with what a captain would do at the toss given all that.
 */
object PitchReport {
    private enum class Advice { BAT, BOWL, EITHER }

    fun build(stadium: Stadium, weather: WeatherSnapshot): PitchReportData {
        val pitchWords = when (stadium.pitchType) {
            PitchType.BATTING -> "Expect true bounce and plenty of runs. The bowlers will have to be very accurate."
            PitchType.BOWLING -> "There is pace, bounce and carry here. The batters must be patient against the new ball."
            PitchType.BALANCED -> "A fair contest between bat and ball, and good cricket will be rewarded."
            PitchType.DUSTY -> "Dry and dusty. It will slow down and turn as the game goes on, so the spinners will matter and batting last will be harder."
            PitchType.GREEN -> "There is a covering of grass. The seamers will get movement early, and it should ease later."
        }
        val pitchCategory = when (stadium.pitchType) {
            PitchType.BATTING -> CommentaryCategory.PITCH_BATTING
            PitchType.BOWLING -> CommentaryCategory.PITCH_BOWLING
            PitchType.BALANCED -> CommentaryCategory.PITCH_BALANCED
            PitchType.DUSTY -> CommentaryCategory.PITCH_DUSTY
            PitchType.GREEN -> CommentaryCategory.PITCH_GREEN
        }

        val boundaryWords = when (stadium.boundarySize) {
            BoundarySize.SMALL -> "Short boundaries: expect sixes, and the bowlers must be careful with their lengths."
            BoundarySize.MEDIUM -> "A standard sized ground: fours and sixes have to be earned."
            BoundarySize.LARGE -> "A big ground: the twos come easily and the fielders have a lot of ground to cover."
        }
        val boundaryCategory = when (stadium.boundarySize) {
            BoundarySize.SMALL -> CommentaryCategory.BOUNDARY_SMALL
            BoundarySize.MEDIUM -> CommentaryCategory.BOUNDARY_MEDIUM
            BoundarySize.LARGE -> CommentaryCategory.BOUNDARY_LARGE
        }
        val altitudeWords = if (stadium.altitudeM >= 800) " It is ${stadium.altitudeM} metres above sea level: the air is thin and the ball travels further." else " Altitude ${stadium.altitudeM} metres."

        val heavyDew = stadium.dewFactor >= 0.5
        val dewWords = ScorecardText.dewName(stadium.dewFactor).replaceFirstChar { it.uppercase() } + ". " +
            if (heavyDew) "The ball will get slippery later and the spinners will struggle to grip it, so chasing is easier."
            else "The ball should stay dry, so the toss matters less."
        val dewCategory = if (heavyDew) CommentaryCategory.DEW_HEAVY else CommentaryCategory.DEW_LIGHT

        val weatherImpact = when (weather.condition) {
            WeatherCondition.CLEAR -> "A clear sky: no help for the swing bowlers and no rain to worry about."
            WeatherCondition.PARTLY_CLOUDY -> "A little cloud: the fast bowlers may get some swing early on."
            WeatherCondition.OVERCAST -> "Heavy cloud: the ball should swing, and the new ball could be a handful."
            WeatherCondition.HUMID -> "Humid and sticky: the ball may swing a little and the fielders will tire quickly."
            WeatherCondition.LIGHT_RAIN -> "Rain is around: interruptions are possible, and a revised target could decide the match."
        }
        val weatherCategory = when (weather.condition) {
            WeatherCondition.CLEAR -> CommentaryCategory.WEATHER_CLEAR
            WeatherCondition.PARTLY_CLOUDY -> CommentaryCategory.WEATHER_PARTLY_CLOUDY
            WeatherCondition.OVERCAST -> CommentaryCategory.WEATHER_OVERCAST
            WeatherCondition.HUMID -> CommentaryCategory.WEATHER_HUMID
            WeatherCondition.LIGHT_RAIN -> CommentaryCategory.WEATHER_LIGHT_RAIN
        }
        val weatherText = "${ScorecardText.weatherName(weather.condition)}, ${weather.temperatureC} degrees Celsius, humidity ${weather.humidityPercent} percent, " +
            "wind ${weather.windKph} kilometres an hour. Chance of rain ${weather.rainChancePercent} percent. $weatherImpact"

        // What a captain would do at the toss, from the pitch, the dew and the sky.
        var bat = 0
        var bowl = 0
        val reasons = ArrayList<String>()
        when (stadium.pitchType) {
            PitchType.DUSTY -> { bat += 2; reasons.add("the pitch will break up and turn later") }
            PitchType.BATTING -> { bat += 1; reasons.add("a flat pitch rewards a big total") }
            PitchType.GREEN, PitchType.BOWLING -> { bowl += 1; reasons.add("the bowlers will get help early") }
            PitchType.BALANCED -> Unit
        }
        if (heavyDew) { bowl += 2; reasons.add("the dew will make chasing easier") }
        if (weather.condition == WeatherCondition.OVERCAST || weather.condition == WeatherCondition.LIGHT_RAIN) { bowl += 1; reasons.add("the cloud will help the swing bowlers") }
        val advice = when {
            bat - bowl >= 2 -> Advice.BAT
            bowl - bat >= 2 -> Advice.BOWL
            else -> Advice.EITHER
        }
        val adviceText = when (advice) {
            Advice.BAT -> "Most captains would bat first: ${reasons.joinToString(", ")}."
            Advice.BOWL -> "Most captains would bowl first: ${reasons.joinToString(", ")}."
            Advice.EITHER -> "It is a close call at the toss: nothing here clearly favours batting or bowling first."
        }
        val adviceCategory = when (advice) {
            Advice.BAT -> CommentaryCategory.ADVICE_BAT
            Advice.BOWL -> CommentaryCategory.ADVICE_BOWL
            Advice.EITHER -> CommentaryCategory.ADVICE_EITHER
        }

        val sections = listOf(
            PitchReportSection("The pitch", "${ScorecardText.pitchName(stadium.pitchType)}. ${stadium.pitchDescription} $pitchWords"),
            PitchReportSection("The ground", "${stadium.name}, ${stadium.city}, ${stadium.country}. Boundaries: ${ScorecardText.boundaryName(stadium.boundarySize)}. $boundaryWords$altitudeWords"),
            PitchReportSection("Dew", dewWords),
            PitchReportSection("Par score", "The average first innings score here in a T20 match is ${stadium.avgT20FirstInningsScore}."),
            PitchReportSection("The weather", weatherText),
            PitchReportSection("At the toss", adviceText)
        )
        // The lead commentator and his co-commentator take turns.
        val voice = listOf(
            CommentaryCategory.PITCH_INTRO to true,
            pitchCategory to false,
            boundaryCategory to true,
            dewCategory to false,
            weatherCategory to true,
            adviceCategory to false
        )
        return PitchReportData("Pitch report: ${stadium.name}, ${stadium.city}", sections, voice)
    }
}
