package app.running.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class HomeTab(val label: String, val icon: ImageVector) {
    WORKOUTS("Entrenos", Icons.Default.FitnessCenter),
    SESSIONS("Historial", Icons.Default.CalendarMonth),
    SHOES("Zapatillas", Icons.Default.Hiking),
    SETTINGS("Ajustes", Icons.Default.Settings),
}

@Composable
fun HomeBar(vm: WorkoutViewModel) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        HomeTab.entries.forEach { t ->
            NavigationBarItem(
                selected = vm.tab == t,
                onClick = { vm.tab = t },
                icon = { androidx.compose.material3.Icon(t.icon, null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
