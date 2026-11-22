package app.running.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class HomeTab(val label: String, val icon: ImageVector) {
    WORKOUTS("Entrenos", Icons.Default.FitnessCenter),
    SESSIONS("Historial", Icons.Default.CalendarMonth),
    SHOES("Zapatillas", Icons.Default.Hiking),
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

@Composable
fun ShoeAlertDialog(vm: WorkoutViewModel) {
    val alert by vm.alert.collectAsStateWithLifecycle()
    val a = alert ?: return
    AlertDialog(
        onDismissRequest = vm::dismissAlert,
        icon = { androidx.compose.material3.Icon(Icons.Default.Warning, null) },
        title = { Text(if (a.reached) "Límite de km alcanzado" else "Zapatillas casi al límite") },
        text = {
            val total = trimNumber(a.totalKm, 1)
            val limit = trimNumber(a.limitKm, 0)
            Text(
                if (a.reached) "${a.shoeName} lleva $total km y su límite era $limit km. Es hora de plantearse cambiarlas."
                else "${a.shoeName} lleva $total de $limit km. Te quedan ${trimNumber(a.limitKm - a.totalKm, 1)} km.",
            )
        },
        confirmButton = { TextButton(onClick = vm::dismissAlert) { Text("Entendido") } },
    )
}
