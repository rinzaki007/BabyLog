package com.rinzaki.babylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DateTimeOptionDialog(
    initial: LocalDateTime,
    includeTime: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    var selected by remember(initial) { mutableStateOf(initial) }
    val currentYear = LocalDate.now().year
    val dateLabel = selected.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
    val timeLabel = selected.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (includeTime) "设置日期与时间" else "选择日期", fontWeight = FontWeight.Bold)
                Text("记录会保存为你选择的时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (includeTime) Text(timeLabel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("快速选择日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("今天" to LocalDate.now(), "昨天" to LocalDate.now().minusDays(1), "前天" to LocalDate.now().minusDays(2)).forEach { (label, day) ->
                        val active = selected.toLocalDate() == day
                        if (active) FilledTonalButton(onClick = { selected = day.atTime(selected.toLocalTime()) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)) { Text(label) }
                        else OutlinedButton(onClick = { selected = day.atTime(selected.toLocalTime()) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)) { Text(label) }
                    }
                }
                Text("选择具体日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionPicker("年", selected.year.toString(), (1900..currentYear).toList().map(Int::toString), modifier = Modifier.weight(1.25f)) { value ->
                        val year = value.toInt()
                        val maxDay = selected.month.length(java.time.Year.isLeap(year.toLong()))
                        selected = selected.withYear(year).withDayOfMonth(selected.dayOfMonth.coerceAtMost(maxDay))
                    }
                    OptionPicker("月", "%02d".format(selected.monthValue), (1..12).toList().map { "%02d".format(it) }, modifier = Modifier.weight(1f)) { value ->
                        val month = value.toInt()
                        val maxDay = selected.withMonth(month).month.length(java.time.Year.isLeap(selected.year.toLong()))
                        selected = selected.withMonth(month).withDayOfMonth(selected.dayOfMonth.coerceAtMost(maxDay))
                    }
                    OptionPicker("日", "%02d".format(selected.dayOfMonth), (1..selected.month.length(java.time.Year.isLeap(selected.year.toLong()))).toList().map { "%02d".format(it) }, modifier = Modifier.weight(1f)) { value ->
                        selected = selected.withDayOfMonth(value.toInt())
                    }
                }
                if (includeTime) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("设置时间", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("24 小时制", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("小时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OptionPicker("时", "%02d".format(selected.hour), (0..23).toList().map { "%02d".format(it) }) { selected = selected.withHour(it.toInt()) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OptionPicker("分", "%02d".format(selected.minute), (0..59).toList().map { "%02d".format(it) }) { selected = selected.withMinute(it.toInt()) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selected) }) { Text(if (includeTime) "确认时间" else "确认日期") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun OptionPicker(
    label: String,
    selected: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), shape = RoundedCornerShape(13.dp)) {
            Text("$selected $label", fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
