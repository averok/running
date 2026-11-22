package app.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.util.Locale

/** La interfaz de la app está en español, así que los nombres de meses y días también. */
val EsLocale: Locale = Locale.forLanguageTag("es-ES")

private val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")

/**
 * Cuadrícula de un mes (semana desde el lunes). Los días con sesiones muestran los km totales en un chip.
 * [metersByDay] va indexado por epochDay.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    metersByDay: Map<Long, Int>,
    selected: Long?,
    today: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // lunes = 0
    val days = month.lengthOfMonth()
    val rows = (offset + days + 6) / 7

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            WEEKDAYS.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth().height(56.dp)) {
                repeat(7) { c ->
                    val day = r * 7 + c - offset + 1
                    if (day in 1..days) {
                        val epochDay = month.atDay(day).toEpochDay()
                        DayCell(
                            day = day,
                            meters = metersByDay[epochDay],
                            isToday = epochDay == today,
                            isSelected = epochDay == selected,
                            onClick = { onSelect(epochDay) },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(day: Int, meters: Int?, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(1.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) cs.primary else cs.outlineVariant,
                shape = MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) cs.primary else cs.onSurface,
        )
        if (meters != null) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().background(cs.primary, MaterialTheme.shapes.small).padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    trimNumber(meters / 1000.0, 1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}
