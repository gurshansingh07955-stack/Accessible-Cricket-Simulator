package com.cricketsim.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's front door: four choices, in this order, and nothing else.
 *
 *   1. Play match     a new match (the setup flow)
 *   2. Resume match   the autosaved match, when there is one
 *   3. Settings       difficulty, sound, vibration, commentary
 *   4. About          what this game is and how it is played
 *
 * DESIGN. Deliberately spare: a small decorative cricket ball, the name,
 * one line describing the game, then the four choices as generously sized
 * rows, left-aligned, with one clear primary action. Play match is filled;
 * the others are outlined, so the eye (and a low-vision user's) has one
 * obvious place to go. The whole column is capped at a comfortable reading
 * width and centred, so it stays calm on a tablet or a landscape phone.
 *
 * ACCESSIBILITY.
 * - The order above is also the TalkBack reading order, and every row is
 *   ONE item that reads its title and its description together ("Play
 *   match. Choose a format, a ground and your team. Button."), so a
 *   screen-reader user hears what each does before choosing.
 * - The brand ("Accessible" over "Cricket Simulator") is one merged
 *   heading, so it is a single stop, not two.
 * - RESUME KEEPS ITS PLACE when there is no saved match. It is shown as
 *   unavailable, with "No saved match yet." as its description, rather than
 *   disappearing: the four choices are always in the same four positions,
 *   which matters for anyone who navigates by memory, and a button that
 *   moves depending on state is worse for a screen-reader user than one
 *   that is plainly switched off. When there IS a saved match its
 *   description is the spoken summary (teams, format, innings, score), so
 *   the user knows what they would be resuming before committing.
 * - Every row is at least 72dp tall, and the whole row is the tap target.
 * - The cricket ball is purely decorative: its semantics are cleared, so it
 *   never becomes a stop.
 *
 * @param savedMatchSummary the spoken description of the saved match, or
 *   null when there isn't one.
 */
@Composable
fun HomeScreen(
    savedMatchSummary: String?,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Spacer(modifier = Modifier.height(16.dp))
            CricketBallMark()
            Spacer(modifier = Modifier.height(28.dp))

            // One merged heading, so "Accessible" and "Cricket Simulator"
            // are a single stop rather than two.
            Column(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(
                    text = "Accessible",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "Cricket Simulator",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "A full game of cricket, played by ear and by touch.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(44.dp))

            MenuButton(
                title = "Play match",
                description = "Choose a format, a ground and your team.",
                emphasised = true,
                enabled = true,
                onClick = onPlay
            )
            Spacer(modifier = Modifier.height(14.dp))
            MenuButton(
                title = "Resume match",
                description = savedMatchSummary ?: "No saved match yet.",
                emphasised = false,
                enabled = savedMatchSummary != null,
                onClick = onResume
            )
            Spacer(modifier = Modifier.height(14.dp))
            MenuButton(
                title = "Settings",
                description = "Difficulty, sound, vibration and commentary.",
                emphasised = false,
                enabled = true,
                onClick = onSettings
            )
            Spacer(modifier = Modifier.height(14.dp))
            MenuButton(
                title = "About",
                description = "What this game is, and how it is played.",
                emphasised = false,
                enabled = true,
                onClick = onAbout
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * One row of the menu. The whole row is a single clickable node, so its
 * title and description merge into one TalkBack item, and when it is
 * disabled TalkBack reports it as unavailable.
 */
@Composable
private fun MenuButton(
    title: String,
    description: String,
    emphasised: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val titleColor = if (emphasised) colors.onPrimary else colors.onBackground
    val descriptionColor = if (emphasised) colors.onPrimary.copy(alpha = 0.88f) else colors.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (emphasised) colors.primary else Color.Transparent)
            .then(if (emphasised) Modifier else Modifier.border(1.5.dp, colors.outline, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = titleColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = descriptionColor)
    }
}

/**
 * A small cricket ball: a warm red disc with two dashed seams, drawn on a
 * canvas so there is no image file. Decorative only — its semantics are
 * cleared so screen readers skip it entirely. The seam is drawn in the
 * background colour, so it reads correctly in both the light and dark
 * schemes.
 */
@Composable
private fun CricketBallMark() {
    val ball = MaterialTheme.colorScheme.tertiary
    val seam = MaterialTheme.colorScheme.background
    Canvas(modifier = Modifier.size(56.dp).clearAndSetSemantics { }) {
        val radius = size.minDimension / 2f
        val middle = center
        drawCircle(color = ball, radius = radius, center = middle)
        val stitches = Stroke(
            width = radius * 0.07f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius * 0.12f, radius * 0.14f))
        )
        for (offset in floatArrayOf(-0.1f, 0.1f)) {
            val x = middle.x + offset * radius
            val path = Path().apply {
                moveTo(x - 0.5f * radius, middle.y - 0.82f * radius)
                cubicTo(
                    x + 0.2f * radius, middle.y - 0.35f * radius,
                    x + 0.2f * radius, middle.y + 0.35f * radius,
                    x - 0.5f * radius, middle.y + 0.82f * radius
                )
            }
            drawPath(path = path, color = seam, style = stitches)
        }
    }
}
