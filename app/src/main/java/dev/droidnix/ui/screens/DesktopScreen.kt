package dev.droidnix.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidnix.data.LinuxEnvironment
import dev.droidnix.engine.display.KeySyms
import dev.droidnix.engine.display.RfbFramebuffer
import dev.droidnix.ui.DesktopPhase
import dev.droidnix.ui.DesktopViewModel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Phase 2 display: an in-process RFB client rendered straight into Compose.
 * One finger drives the pointer, long-press is a right click, two fingers
 * scroll (or pan when zoomed) and pinch zooms.
 */
@Composable
fun DesktopScreen(
    env: LinuxEnvironment,
    password: String,
    onClose: () -> Unit,
    vm: DesktopViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val framebuffer = vm.framebuffer
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val port = env.config?.vncPort ?: 5901
    val remoteW = state.width
    val remoteH = state.height

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var panMode by remember { mutableStateOf(false) }
    var keyboardOn by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }

    val fitScale = if (remoteW > 0 && remoteH > 0 && canvasSize.width > 0 && canvasSize.height > 0) {
        min(
            canvasSize.width.toFloat() / remoteW.toFloat(),
            canvasSize.height.toFloat() / remoteH.toFloat(),
        )
    } else {
        1f
    }

    fun centred(scale: Float): Offset {
        if (remoteW <= 0 || remoteH <= 0) return Offset.Zero
        return Offset(
            (canvasSize.width - remoteW * scale) / 2f,
            (canvasSize.height - remoteH * scale) / 2f,
        )
    }

    // Refit when the desktop resizes, on first layout and on rotation.
    LaunchedEffect(remoteW, remoteH, canvasSize) {
        if (remoteW > 0 && remoteH > 0 && canvasSize.width > 0 && canvasSize.height > 0) {
            val f = min(
                canvasSize.width.toFloat() / remoteW.toFloat(),
                canvasSize.height.toFloat() / remoteH.toFloat(),
            )
            scale = f
            offset = centred(f)
        }
    }

    LaunchedEffect(env.rootDir, port, password) {
        vm.connect("127.0.0.1", port, password)
    }
    DisposableEffect(Unit) {
        onDispose { vm.disconnect() }
    }
    BackHandler(onBack = onClose)

    // A running desktop is something the user watches; do not dim mid-session.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Clipboard flows Linux -> Android automatically; the toolbar does the reverse.
    LaunchedEffect(state.clipboardSeq) {
        val text = state.clipboard
        if (state.clipboardSeq > 0 && !text.isNullOrEmpty()) {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "Clipboard received from Linux", Toast.LENGTH_SHORT).show()
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var committed by remember { mutableStateOf("") }

    fun clearField() {
        field = TextFieldValue("")
        committed = ""
    }

    fun showKeyboard() {
        keyboardOn = true
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    fun hideKeyboard() {
        keyboardOn = false
        keyboard?.hide()
        clearField()
    }

    fun sendTextDiff(next: String) {
        val old = committed
        when {
            next.startsWith(old) -> vm.typeText(next.substring(old.length), ctrl, alt)
            old.startsWith(next) -> repeat(old.length - next.length) {
                vm.tapKey(KeySyms.BACKSPACE, ctrl, alt)
            }
            else -> {
                repeat(old.length) { vm.tapKey(KeySyms.BACKSPACE, ctrl, alt) }
                vm.typeText(next, ctrl, alt)
            }
        }
        committed = next
        if (ctrl || alt) {
            ctrl = false
            alt = false
        }
    }

    // The toolbar is a sibling of the canvas, not an overlay: overlapping it
    // would make the top of the remote desktop (XFCE panel, title bars)
    // unreachable, because the toolbar eats those touches.
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TopBar(
            title = env.displayName,
            subtitle = when (state.phase) {
                DesktopPhase.CONNECTED -> "${state.width}×${state.height} · ${state.name}"
                DesktopPhase.CONNECTING -> "Connecting to 127.0.0.1:$port…"
                DesktopPhase.FAILED -> "Not connected"
                DesktopPhase.CLOSED -> "Disconnected"
                DesktopPhase.IDLE -> "127.0.0.1:$port"
            },
            keyboardOn = keyboardOn,
            ctrl = ctrl,
            alt = alt,
            panMode = panMode,
            canPaste = state.phase == DesktopPhase.CONNECTED,
            onBack = onClose,
            onKeyboard = { if (keyboardOn) hideKeyboard() else showKeyboard() },
            onFit = {
                scale = fitScale
                offset = centred(fitScale)
            },
            onActualSize = {
                scale = 1f
                offset = centred(1f)
            },
            onPan = { panMode = !panMode },
            onCtrl = { ctrl = !ctrl; alt = false },
            onAlt = { alt = !alt; ctrl = false },
            onPaste = {
                val text = clipboard.getText()?.text
                if (text.isNullOrEmpty()) {
                    Toast.makeText(context, "Android clipboard is empty", Toast.LENGTH_SHORT).show()
                } else {
                    vm.sendClipboard(text)
                    Toast.makeText(context, "Clipboard sent to Linux", Toast.LENGTH_SHORT).show()
                }
            },
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
        if (state.phase == DesktopPhase.CONNECTED && state.width > 0) {
            RemoteView(
                framebuffer = framebuffer,
                remoteW = remoteW,
                remoteH = remoteH,
                scale = scale,
                offset = offset,
                fitScale = fitScale,
                panMode = panMode,
                canvasSize = canvasSize,
                onCanvasSize = { canvasSize = it },
                onScale = { scale = it },
                onOffset = { offset = it },
                onPointer = vm::pointer,
                onTap = { x, y, right -> vm.tap(x, y, right) },
                onScroll = vm::scroll,
                onScrollH = vm::scrollHorizontal,
            )
        } else {
            ConnectionState(
                phase = state.phase,
                error = state.error,
                onRetry = { vm.connect("127.0.0.1", port, password) },
                onBack = onClose,
            )
        }

        if (keyboardOn) {
            KeyRow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding(),
                onKey = { keysym -> vm.tapKey(keysym, ctrl, alt) },
                onCtrl = { ctrl = !ctrl; alt = false },
                onAlt = { alt = !alt; ctrl = false },
                ctrl = ctrl,
                alt = alt,
            )
        }

        BasicTextField(
            value = field,
            onValueChange = { next ->
                if (next.composition == null) {
                    if (next.text != committed) sendTextDiff(next.text)
                }
                field = next
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event -> handleHardwareKey(event, vm) },
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.None,
            ),
        )
        }
    }
}

private fun handleHardwareKey(event: ComposeKeyEvent, vm: DesktopViewModel): Boolean {
    val native = event.nativeKeyEvent
    // Ignore virtual keyboards (deviceId <= 0): their text already arrives
    // through the IME, and forwarding both would double every Backspace.
    if (native.deviceId <= 0) return false
    if (native.action == android.view.KeyEvent.ACTION_MULTIPLE) {
        native.characters?.let { vm.typeText(it) }
        return true
    }
    val shortcut = native.isCtrlPressed || native.isAltPressed || native.isMetaPressed
    val keysym = KeySyms.fromAndroid(
        keyCode = native.keyCode,
        unicodeChar = native.unicodeChar,
        baseChar = native.getUnicodeChar(0),
        shortcut = shortcut,
    ) ?: return false
    when (native.action) {
        android.view.KeyEvent.ACTION_DOWN -> if (native.repeatCount == 0) vm.key(keysym, true)
        android.view.KeyEvent.ACTION_UP -> vm.key(keysym, false)
        else -> return false
    }
    return true
}

@Composable
private fun RemoteView(
    framebuffer: RfbFramebuffer,
    remoteW: Int,
    remoteH: Int,
    scale: Float,
    offset: Offset,
    fitScale: Float,
    panMode: Boolean,
    canvasSize: IntSize,
    onCanvasSize: (IntSize) -> Unit,
    onScale: (Float) -> Unit,
    onOffset: (Offset) -> Unit,
    onPointer: (Int, Int, Int) -> Unit,
    onTap: (Int, Int, Boolean) -> Unit,
    onScroll: (Boolean) -> Unit,
    onScrollH: (Boolean) -> Unit,
) {
    val liveScale = rememberUpdatedState(scale)
    val liveOffset = rememberUpdatedState(offset)
    val liveFit = rememberUpdatedState(fitScale)
    val liveSize = rememberUpdatedState(canvasSize)

    val density = LocalDensity.current
    val slop = with(density) { 12.dp.toPx() }
    val scrollStep = with(density) { 56.dp.toPx() }

    Canvas(
        Modifier
            .fillMaxSize()
            // A zoomed bitmap is larger than the canvas; without clipping it
            // would paint over the toolbar above it.
            .clipToBounds()
            .onSizeChanged { size -> onCanvasSize(size) }
            .pointerInput(remoteW, remoteH, panMode) {
                fun clampOffset(candidate: Offset, s: Float): Offset {
                    val size = liveSize.value
                    if (size.width <= 0 || size.height <= 0) return candidate
                    val sw = remoteW * s
                    val sh = remoteH * s
                    val x = if (sw <= size.width) (size.width - sw) / 2f
                    else candidate.x.coerceIn(size.width - sw, 0f)
                    val y = if (sh <= size.height) (size.height - sh) / 2f
                    else candidate.y.coerceIn(size.height - sh, 0f)
                    return Offset(x, y)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastPosition = down.position
                    var moved = false
                    var dragging = false
                    var rightClicked = false
                    var multi = false
                    var zooming = false
                    var lastSpan = 0f
                    var lastCentroid = down.position
                    var wheelY = 0f
                    var wheelX = 0f
                    var longPressDeadline = System.currentTimeMillis() + 450L

                    var gestureScale = liveScale.value
                    var gestureOffset = liveOffset.value

                    fun toRemote(position: Offset): Pair<Int, Int> {
                        val s = if (gestureScale <= 0f) 1f else gestureScale
                        val x = ((position.x - gestureOffset.x) / s).roundToInt()
                        val y = ((position.y - gestureOffset.y) / s).roundToInt()
                        return x.coerceIn(0, remoteW - 1) to y.coerceIn(0, remoteH - 1)
                    }

                    fun publish() {
                        onScale(gestureScale)
                        onOffset(gestureOffset)
                    }

                    fun panBy(delta: Offset) {
                        gestureOffset = clampOffset(gestureOffset + delta, gestureScale)
                        publish()
                    }

                    fun zoomAt(centroid: Offset, factor: Float) {
                        val next = (gestureScale * factor)
                            .coerceIn(liveFit.value * 0.8f, 4f)
                        if (next <= 0f || next == gestureScale) return
                        val ratio = next / gestureScale
                        gestureOffset = clampOffset(
                            centroid - (centroid - gestureOffset) * ratio,
                            next,
                        )
                        gestureScale = next
                        publish()
                    }

                    while (true) {
                        val wait = if (!moved && !multi && !rightClicked) {
                            (longPressDeadline - System.currentTimeMillis()).coerceAtLeast(1L)
                        } else {
                            100L
                        }
                        val event = withTimeoutOrNull(wait) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            if (!moved && !multi && !rightClicked) {
                                val (rx, ry) = toRemote(down.position)
                                onTap(rx, ry, true)
                                rightClicked = true
                            }
                            continue
                        }

                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            if (!multi) {
                                multi = true
                                if (dragging) {
                                    val (rx, ry) = toRemote(lastPosition)
                                    onPointer(rx, ry, 0)
                                    dragging = false
                                }
                                lastSpan = spanOf(pressed)
                                lastCentroid = centroidOf(pressed)
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val centroid = centroidOf(pressed)
                            val span = spanOf(pressed)
                            if (lastSpan > 1f && span > 1f) {
                                val ratio = span / lastSpan
                                if (abs(ratio - 1f) > 0.01f) {
                                    zooming = true
                                    zoomAt(centroid, ratio)
                                }
                            }
                            if (!zooming) {
                                val delta = centroid - lastCentroid
                                if (gestureScale > liveFit.value * 1.02f) {
                                    panBy(delta)
                                } else {
                                    wheelY += delta.y
                                    wheelX += delta.x
                                    while (wheelY >= scrollStep) {
                                        onScroll(false)
                                        wheelY -= scrollStep
                                    }
                                    while (wheelY <= -scrollStep) {
                                        onScroll(true)
                                        wheelY += scrollStep
                                    }
                                    while (wheelX >= scrollStep) {
                                        onScrollH(false)
                                        wheelX -= scrollStep
                                    }
                                    while (wheelX <= -scrollStep) {
                                        onScrollH(true)
                                        wheelX += scrollStep
                                    }
                                }
                            }
                            lastSpan = span
                            lastCentroid = centroid
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            val position = change.position
                            if (!multi) {
                                if (!moved && (position - down.position).getDistance() > slop) {
                                    moved = true
                                }
                                if (moved) {
                                    if (panMode) {
                                        panBy(position - lastPosition)
                                    } else {
                                        if (!dragging) {
                                            val (rx, ry) = toRemote(down.position)
                                            onPointer(rx, ry, 1)
                                            dragging = true
                                        }
                                        val (rx, ry) = toRemote(position)
                                        onPointer(rx, ry, 1)
                                    }
                                }
                            }
                            lastPosition = position
                            event.changes.forEach { it.consume() }
                        }
                    }

                    if (dragging) {
                        val (rx, ry) = toRemote(lastPosition)
                        onPointer(rx, ry, 0)
                    }
                    if (!moved && !multi && !rightClicked) {
                        val (rx, ry) = toRemote(down.position)
                        onTap(rx, ry, false)
                    }
                }
            },
    ) {
        // Reading the version here (draw phase, not composition) invalidates
        // only the drawing, so a 30 fps session does not recompose the UI.
        framebuffer.version
        val image = framebuffer.imageBitmap() ?: return@Canvas
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(framebuffer.width, framebuffer.height),
            dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
            dstSize = IntSize(
                (framebuffer.width * scale).roundToInt(),
                (framebuffer.height * scale).roundToInt(),
            ),
            filterQuality = FilterQuality.Low,
        )
    }
}

private fun spanOf(points: List<androidx.compose.ui.input.pointer.PointerInputChange>): Float {
    if (points.size < 2) return 0f
    return (points[0].position - points[1].position).getDistance()
}

private fun centroidOf(points: List<androidx.compose.ui.input.pointer.PointerInputChange>): Offset {
    var sum = Offset.Zero
    for (p in points) sum += p.position
    return sum / points.size.toFloat()
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    keyboardOn: Boolean,
    ctrl: Boolean,
    alt: Boolean,
    panMode: Boolean,
    canPaste: Boolean,
    onBack: () -> Unit,
    onKeyboard: () -> Unit,
    onFit: () -> Unit,
    onActualSize: () -> Unit,
    onPan: () -> Unit,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onPaste: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.62f))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close desktop", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarChip("Keys", active = keyboardOn, enabled = true, onClick = onKeyboard)
            BarChip("Fit", active = false, enabled = true, onClick = onFit)
            BarChip("1:1", active = false, enabled = true, onClick = onActualSize)
            BarChip("Pan", active = panMode, enabled = true, onClick = onPan)
            BarChip("Ctrl", active = ctrl, enabled = true, onClick = onCtrl)
            BarChip("Alt", active = alt, enabled = true, onClick = onAlt)
            BarChip("Paste", active = false, enabled = canPaste, onClick = onPaste)
        }
    }
}

@Composable
private fun BarChip(
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun KeyRow(
    modifier: Modifier,
    onKey: (Int) -> Unit,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    ctrl: Boolean,
    alt: Boolean,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarChip("Esc", active = false) { onKey(KeySyms.ESCAPE) }
            BarChip("Tab", active = false) { onKey(KeySyms.TAB) }
            BarChip("Ctrl", active = ctrl, enabled = true, onClick = onCtrl)
            BarChip("Alt", active = alt, enabled = true, onClick = onAlt)
            Spacer(Modifier.weight(1f))
            BarChip("←", active = false) { onKey(KeySyms.LEFT) }
            BarChip("↑", active = false) { onKey(KeySyms.UP) }
            BarChip("↓", active = false) { onKey(KeySyms.DOWN) }
            BarChip("→", active = false) { onKey(KeySyms.RIGHT) }
            BarChip("⏎", active = false) { onKey(KeySyms.RETURN) }
            BarChip("⌫", active = false) { onKey(KeySyms.BACKSPACE) }
        }
    }
}

@Composable
private fun ConnectionState(
    phase: DesktopPhase,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (phase) {
                DesktopPhase.CONNECTED -> Unit
                DesktopPhase.CONNECTING, DesktopPhase.IDLE -> {
                    CircularProgressIndicator(color = Color.White)
                    Text("Connecting to the desktop…", color = Color.White)
                }
                DesktopPhase.FAILED, DesktopPhase.CLOSED -> {
                    Text(
                        if (phase == DesktopPhase.FAILED) "Could not open the desktop" else "Desktop disconnected",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        error ?: "The connection ended.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onRetry) { Text("Retry") }
                        TextButton(onClick = onBack) { Text("Back") }
                    }
                }
            }
        }
    }
}
