package dev.droidnix.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidnix.ui.screens.CompatibilityScreen
import dev.droidnix.ui.screens.DisclaimerScreen
import dev.droidnix.ui.screens.EnvironmentsScreen
import dev.droidnix.ui.screens.HomeScreen
import dev.droidnix.ui.screens.InstallScreen
import dev.droidnix.ui.screens.LoadingScreen
import dev.droidnix.ui.screens.RootRequiredScreen
import dev.droidnix.ui.screens.SettingsScreen

const val engineLabel: String =
    "Display engine: in-process VNC (RFB) client — Phase 2. No Termux:X11, AVNC or browser is used."

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Default.Home),
    Tab("Install", Icons.Default.Add),
    Tab("Environments", Icons.Default.List),
    Tab("Settings", Icons.Default.Settings),
)

@Composable
fun DroidNixApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var accepted by remember { mutableStateOf(vm.disclaimerAccepted) }
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    when {
        !accepted -> DisclaimerScreen(onAccept = {
            accepted = true
            vm.acceptDisclaimer()
        })

        state.checking -> LoadingScreen()

        state.root?.usable != true -> RootRequiredScreen(
            detail = state.root?.detail ?: "No root shell responded.",
            onRetry = { vm.refreshAll() },
            onOpenMagisk = {
                val intent = context.packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")
                if (intent != null) {
                    context.startActivity(intent)
                }
            },
        )

        state.compat?.blocking == true -> CompatibilityScreen(
            report = state.compat!!,
            onRetry = { vm.refreshAll() },
        )

        else -> MainScaffold(
            state = state,
            tab = tab,
            onTab = { tab = it },
            vm = vm,
        )
    }
}

@Composable
private fun MainScaffold(
    state: UiState,
    tab: Int,
    onTab: (Int) -> Unit,
    vm: MainViewModel,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.dismissMessage()
    }

    // Root polling costs a shell process per tick; stop it in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onForeground()
                Lifecycle.Event.ON_STOP -> vm.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { onTab(index) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    state = state,
                    onStart = vm::startEnvironment,
                    onStop = vm::stopEnvironment,
                    onRefreshLog = vm::refreshLog,
                    onOpenInstall = { onTab(1) },
                )

                1 -> InstallScreen(
                    state = state,
                    onAdopt = vm::adoptSelected,
                    onRefresh = vm::refreshEnvironments,
                    onRefreshStorage = vm::refreshStorage,
                    onSelectStorage = vm::selectStorage,
                )

                2 -> EnvironmentsScreen(
                    state = state,
                    onSelect = vm::select,
                    onStart = vm::startEnvironment,
                    onStop = vm::stopEnvironment,
                    onDelete = vm::deleteSelected,
                    onRefresh = vm::refreshEnvironments,
                )

                else -> SettingsScreen(
                    state = state,
                    onDiagnostics = vm::loadDiagnostics,
                    onRecheck = vm::refreshAll,
                )
            }
        }
    }
}
