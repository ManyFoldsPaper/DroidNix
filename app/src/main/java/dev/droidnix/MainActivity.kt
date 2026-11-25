package dev.droidnix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.droidnix.ui.DroidNixApp
import dev.droidnix.ui.MainViewModel
import dev.droidnix.ui.theme.DroidNixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidNixTheme {
                val vm: MainViewModel = viewModel()
                DroidNixApp(vm)
            }
        }
    }
}
