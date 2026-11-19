package com.rinzaki.babylog.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BabyLogNavigationBar(selectedIndex: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding().height(68.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0.dp)
    ) {
        BabyLogDestination.entries.forEachIndexed { index, destination ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = when (destination) {
                            BabyLogDestination.HOME -> Icons.Filled.Home
                            BabyLogDestination.RECORDS -> Icons.Filled.Add
                            BabyLogDestination.GROWTH -> Icons.Filled.ShowChart
                            BabyLogDestination.ALBUM -> Icons.Filled.Photo
                            BabyLogDestination.MORE -> Icons.Filled.MoreHoriz
                        },
                        contentDescription = destination.label
                    )
                },
                label = {
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            )
        }
    }
}
