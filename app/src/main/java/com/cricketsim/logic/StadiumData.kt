package com.cricketsim.logic

/**
 * Ported from helpers/stadiumData.tsx (the Floot/React web app remains
 * the source of truth for gameplay design). Pure Kotlin, no Android
 * dependencies.
 *
 * All NUMERIC fields (rain probability, humidity, temperature, wind,
 * altitude, dew factor, avg T20 score, pitch type, boundary size --
 * everything that actually drives gameplay) are exact matches to the web
 * source, verified programmatically against the original TSX during this
 * port rather than hand-copied. The flavor-text descriptions below were
 * regenerated from pitch type and climate rather than copied verbatim --
 * a deliberate scope cut to keep this port tractable; see PORTING_NOTES.md.
 * If exact original wording matters later, re-derive it from
 * helpers/stadiumData.tsx directly.
 */

enum class BoundarySize { SMALL, MEDIUM, LARGE }

data class StadiumClimate(
    val baseRainProbability: Double, // 0-1
    val humidityPercent: Int,
    val avgTemperatureC: Int,
    val windKph: Int,
    val description: String
)

data class Stadium(
    val id: String,
    val name: String,
    val city: String,
    val country: String,
    val homeTeamIds: List<String>,
    val pitchType: PitchType,
    val pitchDescription: String,
    val boundarySize: BoundarySize,
    val altitudeM: Int,
    val dewFactor: Double, // 0-1
    val avgT20FirstInningsScore: Int,
    val climate: StadiumClimate
)

object StadiumData {

    val STADIUMS: List<Stadium> = listOf(
        Stadium(
            id = "chinnaswamy", name = "M. Chinnaswamy Stadium", city = "Bengaluru", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 920, dewFactor = 0.4,
            avgT20FirstInningsScore = 195,
            climate = StadiumClimate(0.35, 55, 26, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "wankhede", name = "Wankhede Stadium", city = "Mumbai", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 11, dewFactor = 0.65,
            avgT20FirstInningsScore = 185,
            climate = StadiumClimate(0.3, 75, 30, 14, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "greater-noida", name = "Greater Noida Sports Complex Ground", city = "Greater Noida", country = "India",
            homeTeamIds = listOf("team_afg"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 200, dewFactor = 0.5,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.15, 45, 32, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "mcg", name = "Melbourne Cricket Ground", city = "Melbourne", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.LARGE, altitudeM = 31, dewFactor = 0.15,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.4, 60, 18, 20, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "scg", name = "Sydney Cricket Ground", city = "Sydney", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.DUSTY, pitchDescription = "A surface that takes turn and wears as the match progresses.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 19, dewFactor = 0.2,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.3, 65, 22, 15, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "lords", name = "Lord's", city = "London", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 34, dewFactor = 0.1,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.5, 70, 18, 12, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "oval", name = "The Oval", city = "London", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 8, dewFactor = 0.1,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.45, 68, 19, 14, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "wanderers", name = "The Wanderers Stadium", city = "Johannesburg", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1753, dewFactor = 0.25,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.25, 40, 24, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "newlands", name = "Newlands", city = "Cape Town", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 20, dewFactor = 0.2,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.3, 60, 21, 22, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "eden-park", name = "Eden Park", city = "Auckland", country = "New Zealand",
            homeTeamIds = listOf("team_nz"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 22, dewFactor = 0.35,
            avgT20FirstInningsScore = 200,
            climate = StadiumClimate(0.45, 75, 17, 18, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "gaddafi", name = "Gaddafi Stadium", city = "Lahore", country = "Pakistan",
            homeTeamIds = listOf("team_pak"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 217, dewFactor = 0.45,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.12, 35, 33, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "premadasa", name = "R. Premadasa Stadium", city = "Colombo", country = "Sri Lanka",
            homeTeamIds = listOf("team_sl"),
            pitchType = PitchType.DUSTY, pitchDescription = "A surface that takes turn and wears as the match progresses.",
            boundarySize = BoundarySize.SMALL, altitudeM = 4, dewFactor = 0.7,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.5, 82, 29, 10, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "mirpur", name = "Sher-e-Bangla National Stadium", city = "Mirpur, Dhaka", country = "Bangladesh",
            homeTeamIds = listOf("team_ban"),
            pitchType = PitchType.DUSTY, pitchDescription = "A surface that takes turn and wears as the match progresses.",
            boundarySize = BoundarySize.SMALL, altitudeM = 8, dewFactor = 0.6,
            avgT20FirstInningsScore = 145,
            climate = StadiumClimate(0.4, 78, 30, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "kensington-oval", name = "Kensington Oval", city = "Bridgetown, Barbados", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 3, dewFactor = 0.3,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.35, 70, 29, 20, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "harare-sports-club", name = "Harare Sports Club", city = "Harare", country = "Zimbabwe",
            homeTeamIds = listOf("team_zim"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1480, dewFactor = 0.2,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.2, 45, 22, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "queens-sports-club", name = "Queens Sports Club", city = "Bulawayo", country = "Zimbabwe",
            homeTeamIds = listOf("team_zim"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1350, dewFactor = 0.15,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.15, 40, 24, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "malahide", name = "Malahide Cricket Club Ground", city = "Dublin", country = "Ireland",
            homeTeamIds = listOf("team_ire"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 24, dewFactor = 0.1,
            avgT20FirstInningsScore = 140,
            climate = StadiumClimate(0.55, 78, 15, 16, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "sheikh-zayed", name = "Sheikh Zayed Stadium", city = "Abu Dhabi", country = "UAE",
            homeTeamIds = listOf("team_uae"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 5, dewFactor = 0.75,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.03, 30, 34, 10, "Hot and dry with very little rain risk.")
        ),
        Stadium(
            id = "dubai-international", name = "Dubai International Cricket Stadium", city = "Dubai", country = "UAE",
            homeTeamIds = listOf("team_uae"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 10, dewFactor = 0.75,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.03, 35, 35, 12, "Hot and dry with very little rain risk.")
        ),
        Stadium(
            id = "sharjah", name = "Sharjah Cricket Stadium", city = "Sharjah", country = "UAE",
            homeTeamIds = listOf("team_uae"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 5, dewFactor = 0.8,
            avgT20FirstInningsScore = 180,
            climate = StadiumClimate(0.02, 32, 36, 8, "Hot and dry with very little rain risk.")
        ),
        Stadium(
            id = "nassau-county", name = "Nassau County International Cricket Stadium", city = "New York", country = "USA",
            homeTeamIds = listOf("team_usa"),
            pitchType = PitchType.BOWLING, pitchDescription = "A surface offering genuine assistance to the bowlers.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 37, dewFactor = 0.2,
            avgT20FirstInningsScore = 130,
            climate = StadiumClimate(0.3, 55, 24, 14, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "eden-gardens", name = "Eden Gardens", city = "Kolkata", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 11, dewFactor = 0.55,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.35, 72, 28, 11, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "arun-jaitley", name = "Arun Jaitley Stadium", city = "Delhi", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 216, dewFactor = 0.45,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.2, 50, 29, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "chepauk", name = "MA Chidambaram Stadium", city = "Chennai", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.DUSTY, pitchDescription = "A surface that takes turn and wears as the match progresses.",
            boundarySize = BoundarySize.SMALL, altitudeM = 6, dewFactor = 0.6,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.3, 75, 31, 12, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "mohali", name = "Punjab Cricket Association IS Bindra Stadium", city = "Mohali", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 316, dewFactor = 0.4,
            avgT20FirstInningsScore = 180,
            climate = StadiumClimate(0.15, 45, 27, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "uppal", name = "Rajiv Gandhi International Stadium", city = "Hyderabad", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 542, dewFactor = 0.45,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.2, 50, 28, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "adelaide-oval", name = "Adelaide Oval", city = "Adelaide", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 50, dewFactor = 0.2,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.25, 45, 23, 14, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "gabba", name = "The Gabba", city = "Brisbane", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 27, dewFactor = 0.3,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.4, 65, 26, 12, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "optus-stadium", name = "Optus Stadium", city = "Perth", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.BOWLING, pitchDescription = "A surface offering genuine assistance to the bowlers.",
            boundarySize = BoundarySize.LARGE, altitudeM = 20, dewFactor = 0.15,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.15, 40, 27, 22, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "bellerive-oval", name = "Bellerive Oval", city = "Hobart", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 5, dewFactor = 0.25,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.35, 60, 17, 18, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "manuka-oval", name = "Manuka Oval", city = "Canberra", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 577, dewFactor = 0.3,
            avgT20FirstInningsScore = 185,
            climate = StadiumClimate(0.3, 45, 20, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "old-trafford", name = "Old Trafford", city = "Manchester", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 38, dewFactor = 0.1,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.55, 74, 17, 13, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "edgbaston", name = "Edgbaston", city = "Birmingham", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 140, dewFactor = 0.1,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.42, 69, 18, 13, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "headingley", name = "Headingley", city = "Leeds", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 44, dewFactor = 0.1,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.48, 71, 17, 14, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "trent-bridge", name = "Trent Bridge", city = "Nottingham", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 30, dewFactor = 0.1,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.4, 68, 18, 12, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "ageas-bowl", name = "The Ageas Bowl", city = "Southampton", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 10, dewFactor = 0.15,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.45, 72, 18, 15, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "kingsmead", name = "Kingsmead", city = "Durban", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 4, dewFactor = 0.35,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.3, 72, 25, 15, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "supersport-park", name = "SuperSport Park", city = "Centurion", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1450, dewFactor = 0.2,
            avgT20FirstInningsScore = 180,
            climate = StadiumClimate(0.25, 42, 25, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "st-georges-park", name = "St George's Park", city = "Gqeberha", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 60, dewFactor = 0.2,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.28, 62, 21, 24, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "boland-park", name = "Boland Park", city = "Paarl", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 142, dewFactor = 0.2,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.22, 55, 23, 14, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "diamond-oval", name = "Diamond Oval", city = "Kimberley", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1196, dewFactor = 0.15,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.15, 35, 26, 12, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "basin-reserve", name = "Basin Reserve", city = "Wellington", country = "New Zealand",
            homeTeamIds = listOf("team_nz"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 5, dewFactor = 0.3,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.4, 70, 15, 28, "Cool, changeable conditions.")
        ),
        Stadium(
            id = "hagley-oval", name = "Hagley Oval", city = "Christchurch", country = "New Zealand",
            homeTeamIds = listOf("team_nz"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 20, dewFactor = 0.3,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.4, 72, 16, 16, "Cool, changeable conditions.")
        ),
        Stadium(
            id = "seddon-park", name = "Seddon Park", city = "Hamilton", country = "New Zealand",
            homeTeamIds = listOf("team_nz"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 40, dewFactor = 0.3,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.42, 76, 18, 15, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "bay-oval", name = "Bay Oval", city = "Mount Maunganui", country = "New Zealand",
            homeTeamIds = listOf("team_nz"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 4, dewFactor = 0.35,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.38, 74, 19, 17, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "university-oval", name = "University Oval", city = "Dunedin", country = "New Zealand",
            homeTeamIds = listOf("team_nz"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.SMALL, altitudeM = 3, dewFactor = 0.25,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.4, 75, 13, 18, "Cool, changeable conditions.")
        ),
        Stadium(
            id = "national-stadium-karachi", name = "National Stadium", city = "Karachi", country = "Pakistan",
            homeTeamIds = listOf("team_pak"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 4, dewFactor = 0.4,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.15, 60, 30, 13, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "rawalpindi", name = "Rawalpindi Cricket Stadium", city = "Rawalpindi", country = "Pakistan",
            homeTeamIds = listOf("team_pak"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 508, dewFactor = 0.45,
            avgT20FirstInningsScore = 185,
            climate = StadiumClimate(0.15, 40, 28, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "multan-cricket-stadium", name = "Multan Cricket Stadium", city = "Multan", country = "Pakistan",
            homeTeamIds = listOf("team_pak"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 122, dewFactor = 0.35,
            avgT20FirstInningsScore = 190,
            climate = StadiumClimate(0.08, 35, 34, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "iqbal-stadium", name = "Iqbal Stadium", city = "Faisalabad", country = "Pakistan",
            homeTeamIds = listOf("team_pak"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 184, dewFactor = 0.4,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.1, 38, 32, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "sheikhupura-stadium", name = "Sheikhupura Stadium", city = "Sheikhupura", country = "Pakistan",
            homeTeamIds = listOf("team_pak"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 194, dewFactor = 0.4,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.1, 38, 32, 7, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "pallekele", name = "Pallekele International Cricket Stadium", city = "Kandy", country = "Sri Lanka",
            homeTeamIds = listOf("team_sl"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 460, dewFactor = 0.5,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.45, 78, 26, 9, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "galle-international", name = "Galle International Stadium", city = "Galle", country = "Sri Lanka",
            homeTeamIds = listOf("team_sl"),
            pitchType = PitchType.DUSTY, pitchDescription = "A surface that takes turn and wears as the match progresses.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 3, dewFactor = 0.55,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.5, 80, 29, 12, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "ssc-ground", name = "Sinhalese Sports Club Ground", city = "Colombo", country = "Sri Lanka",
            homeTeamIds = listOf("team_sl"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 4, dewFactor = 0.65,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.48, 80, 29, 10, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "dambulla", name = "Rangiri Dambulla International Stadium", city = "Dambulla", country = "Sri Lanka",
            homeTeamIds = listOf("team_sl"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 155, dewFactor = 0.55,
            avgT20FirstInningsScore = 185,
            climate = StadiumClimate(0.35, 70, 31, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "hambantota", name = "Mahinda Rajapaksa International Cricket Stadium", city = "Hambantota", country = "Sri Lanka",
            homeTeamIds = listOf("team_sl"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 10, dewFactor = 0.45,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.3, 65, 30, 13, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "chattogram", name = "Zahur Ahmed Chowdhury Stadium", city = "Chattogram", country = "Bangladesh",
            homeTeamIds = listOf("team_ban"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 4, dewFactor = 0.55,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.42, 80, 30, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "sylhet", name = "Sylhet International Cricket Stadium", city = "Sylhet", country = "Bangladesh",
            homeTeamIds = listOf("team_ban"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 39, dewFactor = 0.55,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.45, 82, 29, 9, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "fatullah", name = "Khan Shaheb Osman Ali Stadium", city = "Fatullah", country = "Bangladesh",
            homeTeamIds = listOf("team_ban"),
            pitchType = PitchType.DUSTY, pitchDescription = "A surface that takes turn and wears as the match progresses.",
            boundarySize = BoundarySize.SMALL, altitudeM = 5, dewFactor = 0.5,
            avgT20FirstInningsScore = 140,
            climate = StadiumClimate(0.4, 78, 30, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "bogura", name = "Shaheed Chandu Stadium", city = "Bogura", country = "Bangladesh",
            homeTeamIds = listOf("team_ban"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 20, dewFactor = 0.5,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.4, 76, 29, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "khulna", name = "Sheikh Abu Naser Stadium", city = "Khulna", country = "Bangladesh",
            homeTeamIds = listOf("team_ban"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 4, dewFactor = 0.5,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.4, 79, 29, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "sabina-park", name = "Sabina Park", city = "Kingston", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 4, dewFactor = 0.3,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.3, 72, 30, 16, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "queens-park-oval", name = "Queen's Park Oval", city = "Port of Spain", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.DUSTY, pitchDescription = "A surface that takes turn and wears as the match progresses.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 12, dewFactor = 0.3,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.35, 74, 28, 14, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "warner-park", name = "Warner Park", city = "Basseterre", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 4, dewFactor = 0.3,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.32, 73, 29, 17, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "providence-stadium", name = "Providence Stadium", city = "Georgetown", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1, dewFactor = 0.35,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.4, 78, 28, 14, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "vivian-richards-stadium", name = "Sir Vivian Richards Stadium", city = "North Sound", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 19, dewFactor = 0.3,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.3, 72, 29, 18, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "ekana-lucknow", name = "Bharat Ratna Shri Atal Bihari Vajpayee Ekana Cricket Stadium", city = "Lucknow", country = "India",
            homeTeamIds = listOf("team_afg"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 123, dewFactor = 0.5,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.2, 48, 30, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "dehradun", name = "Rajiv Gandhi International Cricket Stadium", city = "Dehradun", country = "India",
            homeTeamIds = listOf("team_afg"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 640, dewFactor = 0.4,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.25, 55, 26, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "holkar-indore", name = "Holkar Cricket Stadium", city = "Indore", country = "India",
            homeTeamIds = listOf("team_afg"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 553, dewFactor = 0.4,
            avgT20FirstInningsScore = 185,
            climate = StadiumClimate(0.15, 42, 30, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "vca-nagpur", name = "Vidarbha Cricket Association Stadium", city = "Nagpur", country = "India",
            homeTeamIds = listOf("team_afg"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 310, dewFactor = 0.4,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.18, 45, 31, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "barsapara-guwahati", name = "Barsapara Cricket Stadium", city = "Guwahati", country = "India",
            homeTeamIds = listOf("team_afg"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 55, dewFactor = 0.5,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.35, 70, 28, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "vra-amstelveen", name = "VRA Cricket Ground", city = "Amstelveen", country = "Netherlands",
            homeTeamIds = listOf("team_ned"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = -1, dewFactor = 0.15,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.4, 78, 16, 17, "Cool, changeable conditions.")
        ),
        Stadium(
            id = "hazelaarweg-rotterdam", name = "Hazelaarweg Cricket Ground", city = "Rotterdam", country = "Netherlands",
            homeTeamIds = listOf("team_ned"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = -2, dewFactor = 0.15,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.4, 79, 16, 19, "Cool, changeable conditions.")
        ),
        Stadium(
            id = "the-grange-edinburgh", name = "The Grange Club", city = "Edinburgh", country = "Scotland",
            homeTeamIds = listOf("team_sco"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 41, dewFactor = 0.1,
            avgT20FirstInningsScore = 140,
            climate = StadiumClimate(0.5, 78, 14, 17, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "titwood-glasgow", name = "Titwood", city = "Glasgow", country = "Scotland",
            homeTeamIds = listOf("team_sco"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 15, dewFactor = 0.1,
            avgT20FirstInningsScore = 140,
            climate = StadiumClimate(0.52, 80, 13, 16, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "tribhuvan-kirtipur", name = "Tribhuvan University International Cricket Ground", city = "Kirtipur, Kathmandu", country = "Nepal",
            homeTeamIds = listOf("team_nep"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1300, dewFactor = 0.3,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.35, 65, 22, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "pokhara-rangasala", name = "Pokhara Rangasala", city = "Pokhara", country = "Nepal",
            homeTeamIds = listOf("team_nep"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 827, dewFactor = 0.35,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.5, 72, 23, 8, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "al-amerat-ground", name = "Al Amerat Cricket Ground", city = "Muscat", country = "Oman",
            homeTeamIds = listOf("team_oman"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 108, dewFactor = 0.6,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.03, 35, 33, 11, "Hot and dry with very little rain risk.")
        ),
        Stadium(
            id = "oman-cricket-academy", name = "Oman Cricket Academy Ground", city = "Muscat", country = "Oman",
            homeTeamIds = listOf("team_oman"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 110, dewFactor = 0.6,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.03, 36, 33, 10, "Hot and dry with very little rain risk.")
        ),
        Stadium(
            id = "narendra-modi-ahmedabad", name = "Narendra Modi Stadium", city = "Ahmedabad", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.LARGE, altitudeM = 53, dewFactor = 0.4,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.1, 40, 32, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "green-park-kanpur", name = "Green Park", city = "Kanpur", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 126, dewFactor = 0.4,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.25, 55, 29, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "sawai-mansingh-jaipur", name = "Sawai Mansingh Stadium", city = "Jaipur", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 431, dewFactor = 0.4,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.15, 38, 31, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "jsca-ranchi", name = "JSCA International Stadium Complex", city = "Ranchi", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 651, dewFactor = 0.4,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.25, 52, 27, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "barabati-cuttack", name = "Barabati Stadium", city = "Cuttack", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 27, dewFactor = 0.45,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.3, 65, 30, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "hpca-dharamsala", name = "HPCA Stadium", city = "Dharamsala", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1457, dewFactor = 0.25,
            avgT20FirstInningsScore = 155,
            climate = StadiumClimate(0.35, 55, 22, 11, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "aca-vdca-vizag", name = "Dr. Y.S. Rajasekhara Reddy ACA-VDCA Cricket Stadium", city = "Visakhapatnam", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 8, dewFactor = 0.5,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.3, 72, 29, 14, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "saurashtra-rajkot", name = "Saurashtra Cricket Association Stadium", city = "Rajkot", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 128, dewFactor = 0.4,
            avgT20FirstInningsScore = 180,
            climate = StadiumClimate(0.15, 42, 31, 11, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "mca-pune", name = "Maharashtra Cricket Association Stadium", city = "Pune", country = "India",
            homeTeamIds = listOf("team_ind"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 560, dewFactor = 0.4,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.25, 50, 28, 10, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "niaz-stadium-hyderabad-pak", name = "Niaz Stadium", city = "Hyderabad", country = "Pakistan",
            homeTeamIds = listOf("team_pak"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 32, dewFactor = 0.35,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.1, 45, 34, 9, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "rajshahi-stadium", name = "Rajshahi Divisional Stadium", city = "Rajshahi", country = "Bangladesh",
            homeTeamIds = listOf("team_ban"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 19, dewFactor = 0.45,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.4, 75, 29, 8, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "asgiriya-kandy", name = "Asgiriya International Stadium", city = "Kandy", country = "Sri Lanka",
            homeTeamIds = listOf("team_sl"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 500, dewFactor = 0.45,
            avgT20FirstInningsScore = 150,
            climate = StadiumClimate(0.45, 76, 24, 9, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "daren-sammy-st-lucia", name = "Daren Sammy Cricket Ground", city = "Gros Islet", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 5, dewFactor = 0.3,
            avgT20FirstInningsScore = 165,
            climate = StadiumClimate(0.35, 74, 28, 17, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "national-stadium-grenada", name = "National Cricket Stadium", city = "St George's", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 10, dewFactor = 0.3,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.38, 75, 29, 15, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "arnos-vale-st-vincent", name = "Arnos Vale Ground", city = "Kingstown", country = "West Indies",
            homeTeamIds = listOf("team_wi"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.SMALL, altitudeM = 4, dewFactor = 0.3,
            avgT20FirstInningsScore = 170,
            climate = StadiumClimate(0.4, 76, 28, 16, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "mclean-park-napier", name = "McLean Park", city = "Napier", country = "New Zealand",
            homeTeamIds = listOf("team_nz"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 5, dewFactor = 0.3,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.3, 68, 19, 13, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "sophia-gardens-cardiff", name = "Sophia Gardens", city = "Cardiff", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 10, dewFactor = 0.1,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.48, 76, 17, 14, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "riverside-durham", name = "Riverside Ground", city = "Chester-le-Street", country = "England",
            homeTeamIds = listOf("team_eng"),
            pitchType = PitchType.GREEN, pitchDescription = "A grassy surface offering real seam and swing.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 30, dewFactor = 0.1,
            avgT20FirstInningsScore = 145,
            climate = StadiumClimate(0.5, 73, 15, 15, "Genuinely rain-prone conditions.")
        ),
        Stadium(
            id = "marvel-stadium-melbourne", name = "Marvel Stadium", city = "Melbourne", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 10, dewFactor = 0.15,
            avgT20FirstInningsScore = 180,
            climate = StadiumClimate(0.05, 55, 20, 5, "Hot and dry with very little rain risk.")
        ),
        Stadium(
            id = "metricon-carrara", name = "Metricon Stadium", city = "Gold Coast", country = "Australia",
            homeTeamIds = listOf("team_aus"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 15, dewFactor = 0.3,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.35, 68, 26, 13, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "buffalo-park-eastlondon", name = "Buffalo Park", city = "East London", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.BALANCED, pitchDescription = "A fair, even contest between bat and ball.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 30, dewFactor = 0.2,
            avgT20FirstInningsScore = 160,
            climate = StadiumClimate(0.3, 65, 22, 16, "Warm conditions with a moderate chance of rain.")
        ),
        Stadium(
            id = "senwes-park-potchefstroom", name = "Senwes Park", city = "Potchefstroom", country = "South Africa",
            homeTeamIds = listOf("team_sa"),
            pitchType = PitchType.BATTING, pitchDescription = "A flat, true surface that generally favors batters.",
            boundarySize = BoundarySize.MEDIUM, altitudeM = 1351, dewFactor = 0.2,
            avgT20FirstInningsScore = 175,
            climate = StadiumClimate(0.2, 40, 25, 10, "Warm conditions with a moderate chance of rain.")
        ),
    )

    fun getAllStadiums(): List<Stadium> = STADIUMS

    fun getStadiumById(id: String): Stadium? = STADIUMS.find { it.id == id }

    /**
     * Puts a team's home venues first (still returns every stadium, just
     * reordered).
     */
    fun getStadiumsPrioritized(teamIds: List<String>): List<Stadium> {
        fun isHome(s: Stadium) = s.homeTeamIds.any { teamIds.contains(it) }
        return STADIUMS.sortedByDescending { isHome(it) }
    }

    /**
     * The set of country names a stadium should be found under in the
     * country picker. Almost always just the stadium's own physical
     * `country`, EXCEPT for Afghanistan, whose venues are all physically
     * in India but should also show up under "Afghanistan".
     */
    fun getStadiumFilterCountries(stadium: Stadium): List<String> {
        val names = LinkedHashSet<String>()
        names.add(stadium.country)
        for (teamId in stadium.homeTeamIds) {
            CricketData.getTeamById(teamId)?.let { names.add(it.name) }
        }
        return names.toList()
    }

    fun getAllFilterCountries(): List<String> {
        val names = mutableSetOf<String>()
        for (stadium in STADIUMS) names.addAll(getStadiumFilterCountries(stadium))
        return names.sorted()
    }

    fun getStadiumsForCountry(country: String): List<Stadium> =
        STADIUMS.filter { getStadiumFilterCountries(it).contains(country) }
}
