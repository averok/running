package app.running

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.running.ui.HomeTab
import app.running.ui.RunningTheme
import app.running.ui.SessionsScreen
import app.running.ui.SettingsScreen
import app.running.ui.UpdateDialogs
import app.running.ui.SessionResultDialog
import app.running.ui.ShoesScreen
import app.running.ui.WorkoutEditorScreen
import app.running.ui.WorkoutListScreen
import app.running.ui.WorkoutViewModel

class MainActivity : ComponentActivity() {
    private val vm: WorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunningTheme {
                val draft by vm.draft.collectAsStateWithLifecycle()
                val d = draft
                when {
                    d != null -> WorkoutEditorScreen(vm, d)
                    vm.tab == HomeTab.WORKOUTS -> WorkoutListScreen(vm)
                    vm.tab == HomeTab.SESSIONS -> SessionsScreen(vm)
                    vm.tab == HomeTab.SETTINGS -> SettingsScreen(vm)
                    else -> ShoesScreen(vm)
                }
                SessionResultDialog(vm)
                UpdateDialogs(vm)
            }
        }
    }
}
