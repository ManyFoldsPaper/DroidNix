package dev.droidnix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.droidnix.engine.display.DesktopEvent
import dev.droidnix.engine.display.KeySyms
import dev.droidnix.engine.display.RfbClient
import dev.droidnix.engine.display.RfbFramebuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DesktopPhase { IDLE, CONNECTING, CONNECTED, FAILED, CLOSED }

data class DesktopState(
    val phase: DesktopPhase = DesktopPhase.IDLE,
    val width: Int = 0,
    val height: Int = 0,
    val name: String = "",
    val error: String? = null,
    val clipboard: String? = null,
    val clipboardSeq: Long = 0L,
)

/**
 * Owns one RFB session. Network and decoding happen off the main thread; frames
 * are copied into [framebuffer] on the main thread only.
 */
class DesktopViewModel : ViewModel() {

    private val _state = MutableStateFlow(DesktopState())
    val state: StateFlow<DesktopState> = _state.asStateFlow()

    val framebuffer = RfbFramebuffer()

    private var client: RfbClient? = null
    private var events: Channel<DesktopEvent>? = null
    private var readerJob: Job? = null
    private var eventJob: Job? = null

    fun connect(host: String, port: Int, password: String) {
        stopSession()
        framebuffer.clear()
        _state.value = DesktopState(phase = DesktopPhase.CONNECTING, name = "Connecting…")

        val channel = Channel<DesktopEvent>(Channel.UNLIMITED)
        events = channel
        val session = RfbClient(host, port, password, channel)
        client = session

        eventJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            for (event in channel) handle(event)
        }
        readerJob = viewModelScope.launch(Dispatchers.IO) { session.run() }
    }

    fun disconnect() {
        stopSession()
        framebuffer.clear()
        _state.value = DesktopState()
    }

    /** Stops the socket and consumer without emitting a spurious failure. */
    private fun stopSession() {
        client?.close()
        client = null
        readerJob?.cancel()
        readerJob = null
        eventJob?.cancel()
        eventJob = null
        events?.close()
        events = null
    }

    private fun handle(event: DesktopEvent) {
        when (event) {
            is DesktopEvent.Connected -> {
                framebuffer.reset(event.width, event.height)
                _state.update {
                    it.copy(
                        phase = DesktopPhase.CONNECTED,
                        width = event.width,
                        height = event.height,
                        name = event.name,
                        error = null,
                    )
                }
            }
            is DesktopEvent.Resized -> {
                framebuffer.reset(event.width, event.height)
                _state.update { it.copy(width = event.width, height = event.height) }
            }
            is DesktopEvent.Frame -> {
                framebuffer.apply(event.x, event.y, event.width, event.height, event.pixels)
            }
            is DesktopEvent.Clipboard -> {
                if (event.text.isNotEmpty() && event.text != _state.value.clipboard) {
                    _state.update {
                        it.copy(clipboard = event.text, clipboardSeq = it.clipboardSeq + 1)
                    }
                }
            }
            is DesktopEvent.Failure -> {
                _state.update { it.copy(phase = DesktopPhase.FAILED, error = event.message) }
                client?.close()
            }
            DesktopEvent.Closed -> {
                _state.update {
                    if (it.phase == DesktopPhase.FAILED) it
                    else it.copy(phase = DesktopPhase.CLOSED, error = "The desktop closed the connection.")
                }
            }
        }
    }

    // ---- input ------------------------------------------------------------

    fun pointer(x: Int, y: Int, mask: Int) {
        client?.pointer(x, y, mask)
    }

    fun tap(x: Int, y: Int, rightClick: Boolean) {
        val mask = if (rightClick) 4 else 1
        client?.pointer(x, y, mask)
        client?.pointer(x, y, 0)
    }

    /** Wheel buttons are momentary: press then release at the same position. */
    fun scroll(down: Boolean) {
        client?.wheel(if (down) 16 else 8)
    }

    fun scrollHorizontal(right: Boolean) {
        client?.wheel(if (right) 64 else 32)
    }

    fun key(keysym: Int, down: Boolean) {
        client?.key(keysym, down)
    }

    /** Sends a key with optional sticky Ctrl/Alt held around it. */
    fun tapKey(keysym: Int, ctrl: Boolean = false, alt: Boolean = false) {
        val c = client ?: return
        if (ctrl) c.key(KeySyms.CONTROL_L, true)
        if (alt) c.key(KeySyms.ALT_L, true)
        c.key(keysym, true)
        c.key(keysym, false)
        if (alt) c.key(KeySyms.ALT_L, false)
        if (ctrl) c.key(KeySyms.CONTROL_L, false)
    }

    fun typeText(text: String, ctrl: Boolean = false, alt: Boolean = false) {
        for (ch in text) {
            when (ch) {
                '\n', '\r' -> tapKey(KeySyms.RETURN, ctrl, alt)
                '\b' -> tapKey(KeySyms.BACKSPACE, ctrl, alt)
                else -> tapKey(KeySyms.fromCodePoint(ch.code), ctrl, alt)
            }
        }
    }

    fun sendClipboard(text: String) {
        if (text.isNotBlank()) client?.cutText(text)
    }

    override fun onCleared() {
        stopSession()
        super.onCleared()
    }
}
