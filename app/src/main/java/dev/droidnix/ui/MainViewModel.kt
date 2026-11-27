package dev.droidnix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.droidnix.core.Compatibility
import dev.droidnix.core.CompatibilityReport
import dev.droidnix.core.DeviceInfo
import dev.droidnix.core.DeviceInfoReader
import dev.droidnix.core.RootManager
import dev.droidnix.core.RootStatus
import dev.droidnix.data.EnvironmentRepo
import dev.droidnix.data.EnvironmentStatus
import dev.droidnix.data.EnvConfig
import dev.droidnix.data.LinuxEnvironment
import dev.droidnix.data.Settings
import dev.droidnix.data.StorageTarget
import dev.droidnix.engine.ChrootController
import dev.droidnix.engine.ScriptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val checking: Boolean = true,
    val root: RootStatus? = null,
    val device: DeviceInfo? = null,
    val compat: CompatibilityReport? = null,
    val environments: List<LinuxEnvironment> = emptyList(),
    val selected: String? = null,
    val status: EnvironmentStatus? = null,
    val busy: String? = null,
    val message: String? = null,
    val log: String = "",
    val diagnostics: String = "",
    val scriptsError: String? = null,
    val storageTargets: List<StorageTarget> = emptyList(),
    val installBase: String? = null,
    val storageBusy: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val scripts = ScriptStore(app)
    private val repo = EnvironmentRepo(scripts)
    private val controller = ChrootController(scripts)

    private val _state = MutableStateFlow(UiState(installBase = settings.installBase))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val disclaimerAccepted: Boolean get() = settings.onboardingDone

    /** Fallback VNC password for environments that have no droidnix.json. */
    val desktopPassword: String get() = settings.vncPassword

    fun setDesktopPassword(value: String) {
        settings.vncPassword = value
    }

    private var pollJob: Job? = null

    init {
        // Returning users skip the disclaimer, so the check must start by itself.
        if (settings.onboardingDone) refreshAll()
    }

    fun acceptDisclaimer() {
        settings.disclaimerAcceptedAt = System.currentTimeMillis()
        settings.onboardingDone = true
        _state.update { it.copy(message = null) }
        refreshAll()
    }

    private data class BootResult(
        val root: RootStatus,
        val device: DeviceInfo? = null,
        val compat: CompatibilityReport? = null,
        val deployError: String? = null,
    )

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(checking = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                val root = RootManager.check()
                if (!root.usable) return@withContext BootResult(root)
                val device = DeviceInfoReader.read(getApplication(), root)
                BootResult(
                    root = root,
                    device = device,
                    compat = Compatibility.evaluate(device),
                    deployError = scripts.missingScript()?.let { "Bundled script missing: $it" },
                )
            }
            _state.update {
                it.copy(
                    checking = false,
                    root = result.root,
                    device = result.device,
                    compat = result.compat,
                    scriptsError = result.deployError,
                )
            }
            if (result.root.usable) {
                refreshEnvironments()
                startPolling()
            }
        }
    }

    /**
     * Polling spawns a root shell each tick, which is expensive on a 2 GB
     * tablet, so it only runs while the UI is actually visible.
     */
    fun onForeground() {
        if (_state.value.root?.usable != true) return
        refreshStatus()
        startPolling()
    }

    fun onBackground() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshEnvironments() {
        viewModelScope.launch {
            var list = withContext(Dispatchers.IO) { repo.discover() }
            // The first root shell after install can race the su grant dialog,
            // which makes a perfectly good image look absent. Give it a moment.
            var attempt = 0
            while (list.isEmpty() && _state.value.root?.usable == true && attempt < 2) {
                delay(1200L * (attempt + 1))
                list = withContext(Dispatchers.IO) { repo.discover() }
                attempt++
            }
            val keep = _state.value.selected
            val selected = when {
                keep != null && list.any { it.rootDir == keep } -> keep
                else -> list.firstOrNull { it.hasImage }?.rootDir ?: list.firstOrNull()?.rootDir
            }
            _state.update { it.copy(environments = list, selected = selected) }
            if (selected != null) refreshStatus()
        }
    }

    fun select(dir: String) {
        _state.update { it.copy(selected = dir, status = null) }
        refreshStatus()
    }

    fun refreshStatus() {
        val dir = _state.value.selected ?: return
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { repo.status(dir) }
            val log = withContext(Dispatchers.IO) { repo.readLog(dir, 60) }
            _state.update { it.copy(status = status, log = log) }
        }
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(6000)
                val dir = _state.value.selected ?: continue
                if (_state.value.busy != null) continue
                val status = withContext(Dispatchers.IO) { repo.status(dir) }
                _state.update { if (it.selected == dir) it.copy(status = status) else it }
            }
        }
    }

    fun startEnvironment() {
        val env = selectedEnvironment() ?: return
        val cfg = env.config
        _state.update { it.copy(busy = "Starting ${env.displayName}…", message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                controller.start(
                    envDir = env.rootDir,
                    geometry = cfg?.geometry ?: "1280x800",
                    dpi = cfg?.dpi ?: 160,
                    port = cfg?.vncPort ?: 5901,
                )
            }
            val status = withContext(Dispatchers.IO) { repo.status(env.rootDir) }
            val log = withContext(Dispatchers.IO) { repo.readLog(env.rootDir, 120) }
            _state.update {
                it.copy(busy = null, status = status, log = log, message = result.message)
            }
            settings.lastEnvironmentDir = env.rootDir
        }
    }

    fun stopEnvironment() {
        val env = selectedEnvironment() ?: return
        _state.update { it.copy(busy = "Stopping ${env.displayName}…", message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { controller.stop(env.rootDir) }
            val status = withContext(Dispatchers.IO) { repo.status(env.rootDir) }
            _state.update { it.copy(busy = null, status = status, message = result.message) }
        }
    }

    fun refreshLog() {
        val dir = _state.value.selected ?: return
        viewModelScope.launch {
            val log = withContext(Dispatchers.IO) { repo.readLog(dir, 300) }
            _state.update { it.copy(log = log) }
        }
    }

    fun loadDiagnostics() {
        viewModelScope.launch {
            _state.update { it.copy(busy = "Collecting diagnostics…") }
            val text = withContext(Dispatchers.IO) { repo.diagnostics() }
            _state.update { it.copy(busy = null, diagnostics = text) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun adoptSelected() {
        val env = selectedEnvironment() ?: return
        _state.update { it.copy(busy = "Saving configuration…", message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.writeConfig(
                    env.rootDir,
                    EnvConfig(
                        name = "Arch Linux",
                        distro = "arch",
                        desktop = "xfce",
                        created = System.currentTimeMillis(),
                    ),
                )
            }
            val list = withContext(Dispatchers.IO) { repo.discover() }
            _state.update {
                it.copy(
                    busy = null,
                    environments = list,
                    selected = env.rootDir,
                    message = result.fold(
                        onSuccess = { "DroidNix now manages ${env.rootDir}" },
                        onFailure = { e -> e.message ?: "Could not save configuration" },
                    ),
                )
            }
            refreshStatus()
        }
    }

    fun selectedEnvironment(): LinuxEnvironment? {
        val s = _state.value
        return s.environments.firstOrNull { it.rootDir == s.selected }
    }

    // ---- storage targets ----------------------------------------------------

    /** Probes internal storage and every mounted SD card for real. */
    fun refreshStorage() {
        if (_state.value.root?.usable != true) return
        viewModelScope.launch {
            _state.update { it.copy(storageBusy = true) }
            val targets = withContext(Dispatchers.IO) { repo.storageTargets() }
            _state.update { s ->
                val stored = s.installBase
                val valid = stored != null && targets.any { it.base == stored && it.usable }
                s.copy(
                    storageTargets = targets,
                    storageBusy = false,
                    installBase = if (valid) stored else null,
                )
            }
            if (_state.value.installBase == null) settings.installBase = null
        }
    }

    fun selectStorage(base: String) {
        val target = _state.value.storageTargets.firstOrNull { it.base == base } ?: return
        if (!target.usable) return
        settings.installBase = base
        _state.update { it.copy(installBase = base) }
    }

    // ---- deletion -----------------------------------------------------------

    /**
     * Deletes the selected environment. The UI asks for a typed confirmation
     * first; the shell script independently re-checks that the environment is
     * stopped and that the path really is an environment folder.
     */
    fun deleteSelected() {
        val env = selectedEnvironment() ?: return
        val s = _state.value
        if (s.status?.mounted == true || s.status?.xvnc == true) {
            _state.update {
                it.copy(message = "Stop the environment before deleting it.")
            }
            return
        }
        _state.update { it.copy(busy = "Deleting ${env.displayName}…", message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { controller.delete(env.rootDir) }
            val list = withContext(Dispatchers.IO) { repo.discover() }
            val fallback = list.firstOrNull { it.hasImage }?.rootDir ?: list.firstOrNull()?.rootDir
            val stillThere = list.any { it.rootDir == env.rootDir }
            val selected = if (stillThere) env.rootDir else fallback
            if (!stillThere && settings.lastEnvironmentDir == env.rootDir) settings.lastEnvironmentDir = null
            _state.update {
                it.copy(
                    busy = null,
                    environments = list,
                    selected = selected,
                    status = null,
                    log = "",
                    message = result.message,
                )
            }
            if (selected != null) refreshStatus()
        }
    }
}
