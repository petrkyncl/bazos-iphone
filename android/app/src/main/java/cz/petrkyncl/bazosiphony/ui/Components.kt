package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.petrkyncl.bazosiphony.data.Deal
import cz.petrkyncl.bazosiphony.domain.Grade

@Composable
fun gradeColors(grade: Grade): Pair<Color, Color> {
    val s = LocalStatus.current
    return when (grade) {
        Grade.APLUS, Grade.A -> s.good to s.goodBg
        Grade.B -> s.info to s.infoBg
        Grade.C -> s.warn to s.warnBg
        Grade.D -> s.bad to s.badBg
    }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier, bold: Boolean = false, large: Boolean = false) {
    Text(
        text,
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = if (large) 10.dp else 6.dp, vertical = if (large) 4.dp else 2.dp),
        color = fg,
        fontSize = if (large) 16.sp else 12.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
    )
}

@Composable
fun GradeChip(grade: Grade, faded: Boolean = false) {
    val (fg, bg) = gradeColors(grade)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(grade.code) }
            append(" ")
            append(grade.title)
        },
        modifier = Modifier
            .background(if (faded) bg.copy(alpha = 0.5f) else bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = if (faded) fg.copy(alpha = 0.7f) else fg,
        fontSize = 12.sp,
    )
}

@Composable
fun ProfitPill(profit: Int, large: Boolean = false) {
    val s = LocalStatus.current
    val (fg, bg) = if (profit > 0) s.good to s.goodBg else s.bad to s.badBg
    Pill(profitText(profit), fg, bg, bold = true, large = large)
}

@Composable
fun Tag(text: String, tone: Tone = Tone.NEUTRAL) {
    val s = LocalStatus.current
    val (fg, bg) = when (tone) {
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHighest
        Tone.WARN -> s.warn to s.warnBg
        Tone.BAD -> s.bad to s.badBg
    }
    Pill(text, fg, bg)
}

enum class Tone { NEUTRAL, WARN, BAD }

@Composable
fun DealDot(deal: Deal, modifier: Modifier = Modifier) {
    val s = LocalStatus.current
    val color = when (deal) {
        Deal.GOOD -> s.good
        Deal.BAD -> s.bad
        Deal.SUSPICIOUS -> s.warn
        Deal.UNKNOWN, Deal.EXCLUDED -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.size(8.dp).background(color, CircleShape))
}
