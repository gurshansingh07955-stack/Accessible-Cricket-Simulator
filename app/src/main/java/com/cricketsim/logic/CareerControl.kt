package com.cricketsim.logic

/**
 * In a career match the player's team is picked and set by its captain. When the player IS the captain ([MatchState.careerCaptain]) the player decides
 * (the XI, the toss, the openers, the bowlers, the field, the next batter, declarations, the follow-on, the new ball), as the user does in an ordinary
 * match; otherwise the game decides for his team. In a match that is not a career match the user decides.
 */
val MatchState.userSelects: Boolean get() = careerPlayerId == null || careerCaptain
