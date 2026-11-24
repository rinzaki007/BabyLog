package com.rinzaki.babylog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SelectionRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val columns = if (options.size <= 4) options.size.coerceAtLeast(1) else 3
    val rows = options.chunked(columns)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        rows.forEachIndexed { rowIndex, group ->
            val missing = columns - group.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (missing > 0) Spacer(Modifier.weight(missing / 2f))
                group.forEach { option ->
                    val modifier = Modifier.weight(1f)
                    if (selected == option) {
                        FilledTonalButton(
                            onClick = { onSelect(option) },
                            modifier = modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("✓", style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(option, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(option) },
                            modifier = modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(option, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (missing > 0) Spacer(Modifier.weight(missing / 2f))
            }
        }
    }
}


