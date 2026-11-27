package com.rinzaki.babylog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun selectionRowColumnCount(optionCount: Int, availableWidthDp: Float): Int {
    if (optionCount <= 0) return 1
    return when {
        optionCount <= 2 -> optionCount
        optionCount == 4 -> 2
        availableWidthDp < 360f -> 2
        optionCount == 3 -> 3
        else -> 3
    }
}

@Composable
internal fun SelectionRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = selectionRowColumnCount(options.size, maxWidth.value)
        val rows = options.chunked(columns)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            rows.forEach { group ->
                val missing = columns - group.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (missing > 0) Spacer(Modifier.weight(missing / 2f))
                    group.forEach { option ->
                        val itemModifier = Modifier.weight(1f)
                        if (selected == option) {
                            FilledTonalButton(
                                onClick = { onSelect(option) },
                                modifier = itemModifier.heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "✓",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        option,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSelect(option) },
                                modifier = itemModifier.heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    option,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    if (missing > 0) Spacer(Modifier.weight(missing / 2f))
                }
            }
        }
    }
}
