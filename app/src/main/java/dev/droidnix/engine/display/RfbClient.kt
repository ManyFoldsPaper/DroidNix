package dev.droidnix.engine.display

import kotlinx.coroutines.channels.SendChannel
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

/**
 * A small, self-contained RFB 3.3/3.7/3.8 client: VncAuth, Raw, CopyRect,
 * Hextile and the DesktopSize pseudo-encoding. It does no rendering and holds
 * no Android state, which keeps the protocol testable and lets the UI decide
 * how pixels are published.
 *
 * Read this as one blocking session: [run] connects, performs the handshake,
 * then loops over server messages until the socket closes or [close] is called.
 * All input methods are safe to call from any thread and are ignored once the
 * session has ended.
 */
internal class RfbClient(
    private val host: String,
    private val port: Int,
    private val password: String,
    private val events: SendChannel<DesktopEvent>,
) {

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var stopped = false

    private val writeLock = Any()

    @Volatile
    private var out: DataOutputStream? = null

    @Volatile
    private var fbWidth = 0

    @Volatile
    private var fbHeight = 0

    @Volatile
    private var lastX = 0

    @Volatile
    private var lastY = 0
    private var pixels = IntArray(0)
    private var raw = ByteArray(0)

    fun run() {
        try {
            connectAndRun()
        } catch (t: Throwable) {
            if (!stopped) events.trySend(DesktopEvent.Failure(describe(t)))
        } finally {
            runCatching { socket?.close() }
            socket = null
            out = null
            if (!stopped) events.trySend(DesktopEvent.Closed)
        }
    }

    fun close() {
        stopped = true
        runCatching { socket?.close() }
        socket = null
    }

    // ---- input ------------------------------------------------------------

    fun pointer(x: Int, y: Int, mask: Int) {
        if (fbWidth <= 0 || fbHeight <= 0) return
        val px = x.coerceIn(0, fbWidth - 1)
        val py = y.coerceIn(0, fbHeight - 1)
        lastX = px
        lastY = py
        val o = out ?: return
        synchronized(writeLock) {
            try {
                o.writeByte(5)
                o.writeByte(mask)
                o.writeShort(px)
                o.writeShort(py)
                o.flush()
            } catch (_: IOException) {
                close()
            }
        }
    }

    /** Wheel/horizontal wheel at the last pointer position. */
    fun wheel(mask: Int) {
        pointer(lastX, lastY, mask)
        pointer(lastX, lastY, 0)
    }

    fun key(keysym: Int, down: Boolean) {
        val o = out ?: return
        synchronized(writeLock) {
            try {
                o.writeByte(4)
                o.writeByte(if (down) 1 else 0)
                o.writeShort(0)
                o.writeInt(keysym)
                o.flush()
            } catch (_: IOException) {
                close()
            }
        }
    }

    fun cutText(text: String) {
        val o = out ?: return
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        synchronized(writeLock) {
            try {
                o.writeByte(6)
                o.writeByte(0)
                o.writeShort(0)
                o.writeInt(bytes.size)
                o.write(bytes)
                o.flush()
            } catch (_: IOException) {
                close()
            }
        }
    }

    // ---- connection -------------------------------------------------------

    private fun connectAndRun() {
        if (stopped) return
        val s = Socket()
        socket = s
        if (stopped) {
            runCatching { s.close() }
            return
        }
        try {
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        } catch (e: SocketTimeoutException) {
            throw RfbException("Timed out connecting to the desktop at $host:$port.")
        } catch (e: ConnectException) {
            throw RfbException(
                "Nothing is listening on $host:$port. Start the environment first.",
            )
        }
        s.tcpNoDelay = true
        s.keepAlive = true

        val input = DataInputStream(BufferedInputStream(s.getInputStream(), 64 * 1024))
        val output = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 64 * 1024))
        out = output

        handshake(input, output)
        if (stopped) return

        output.flush()
        events.trySend(DesktopEvent.Connected(fbWidth, fbHeight, desktopName))
        requestUpdate(incremental = false)

        while (!stopped) {
            val type = input.readUnsignedByte()
            when (type) {
                Rfb.MSG_FRAMEBUFFER_UPDATE -> {
                    val resized = readFramebufferUpdate(input)
                    requestUpdate(incremental = !resized)
                }
                Rfb.MSG_SET_COLOUR_MAP -> skipColourMap(input)
                Rfb.MSG_BELL -> Unit
                Rfb.MSG_SERVER_CUT_TEXT -> readServerCutText(input)
                else -> throw RfbException("Unsupported RFB message type $type.")
            }
        }
    }

    private var desktopName = ""

    private fun handshake(input: DataInputStream, output: DataOutputStream) {
        val versionBytes = ByteArray(12)
        input.readFully(versionBytes)
        val version = String(versionBytes, StandardCharsets.US_ASCII)
        if (!version.startsWith("RFB ")) {
            throw RfbException("The desktop did not answer as an RFB server.")
        }
        val minor = version.substring(8, 11).trim().toIntOrNull() ?: 3
        val modern = minor >= 8
        val use37 = minor >= 7

        output.write(
            (when {
                modern -> Rfb.VERSION_3_8
                use37 -> Rfb.VERSION_3_7
                else -> Rfb.VERSION_3_3
            }).toByteArray(StandardCharsets.US_ASCII),
        )
        output.flush()

        val securityType = if (use37) {
            val count = input.readUnsignedByte()
            if (count == 0) throw RfbException("The desktop refused the connection: ${readReason(input, modern)}")
            val offered = IntArray(count) { input.readUnsignedByte() }
            chooseSecurity(offered).also { output.writeByte(it) }
        } else {
            val type = input.readInt()
            if (type == 0) throw RfbException("The desktop refused the connection.")
            type
        }
        output.flush()

        if (securityType == Rfb.SEC_VNC_AUTH) {
            if (password.isEmpty()) {
                throw RfbException(
                    "This desktop requires a password. Set it in Settings → Desktop password.",
                )
            }
            val challenge = ByteArray(16)
            input.readFully(challenge)
            output.write(VncAuth.response(password, challenge))
            output.flush()
            readSecurityResult(input, modern)
        } else if (use37) {
            readSecurityResult(input, modern)
        }

        output.writeByte(1) // shared session
        output.flush()

        fbWidth = input.readUnsignedShort()
        fbHeight = input.readUnsignedShort()
        lastX = fbWidth / 2
        lastY = fbHeight / 2
        if (fbWidth <= 0 || fbHeight <= 0) throw RfbException("The desktop reported an invalid size.")
        input.skipBytes(16) // server pixel format; we always request our own
        val nameLength = input.readInt()
        desktopName = readString(input, nameLength).ifBlank { "Linux desktop" }
        pixels = IntArray(fbWidth * fbHeight) { BLACK }

        // 32bpp true colour, little endian, RGB in the low 24 bits.
        output.writeByte(0) // SetPixelFormat
        output.writeByte(0)
        output.writeByte(0)
        output.writeByte(0)
        output.writeByte(32)
        output.writeByte(24)
        output.writeByte(0)
        output.writeByte(1)
        output.writeShort(255)
        output.writeShort(255)
        output.writeShort(255)
        output.writeByte(16)
        output.writeByte(8)
        output.writeByte(0)
        output.writeByte(0)
        output.writeByte(0)
        output.writeByte(0)

        // Prefer Hextile; keep Raw and CopyRect for compatibility; learn resizes.
        output.writeByte(2) // SetEncodings
        output.writeByte(0)
        output.writeShort(4)
        output.writeInt(Rfb.ENC_HEXTILE)
        output.writeInt(Rfb.ENC_COPY_RECT)
        output.writeInt(Rfb.ENC_RAW)
        output.writeInt(Rfb.ENC_DESKTOP_SIZE)
        output.flush()
    }

    private fun chooseSecurity(offered: IntArray): Int {
        if (password.isNotEmpty() && Rfb.SEC_VNC_AUTH in offered) return Rfb.SEC_VNC_AUTH
        if (Rfb.SEC_NONE in offered) return Rfb.SEC_NONE
        if (Rfb.SEC_VNC_AUTH in offered) return Rfb.SEC_VNC_AUTH
        throw RfbException(
            "The desktop offered only unsupported security types (${offered.joinToString()}).",
        )
    }

    private fun readSecurityResult(input: DataInputStream, modern: Boolean) {
        val result = runCatching { input.readInt() }.getOrElse {
            throw RfbException(
                "The desktop rejected the password. Check Settings → Desktop password.",
            )
        }
        if (result == 0) return
        val reason = if (modern) runCatching { readReason(input, true) }.getOrDefault("") else ""
        throw RfbException(
            if (reason.isNotBlank()) reason
            else "VNC authentication failed. Check Settings → Desktop password.",
        )
    }

    // ---- server messages --------------------------------------------------

    private fun readFramebufferUpdate(input: DataInputStream): Boolean {
        input.skipBytes(1)
        val rects = input.readUnsignedShort()
        var dx0 = Int.MAX_VALUE
        var dy0 = Int.MAX_VALUE
        var dx1 = Int.MIN_VALUE
        var dy1 = Int.MIN_VALUE
        var resized = false

        repeat(rects) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            when (val encoding = input.readInt()) {
                Rfb.ENC_RAW -> {
                    readRawRect(input, x, y, w, h)
                    dx0 = minOf(dx0, x)
                    dy0 = minOf(dy0, y)
                    dx1 = maxOf(dx1, x + w)
                    dy1 = maxOf(dy1, y + h)
                }
                Rfb.ENC_COPY_RECT -> {
                    copyRect(input, x, y, w, h)
                    dx0 = minOf(dx0, x)
                    dy0 = minOf(dy0, y)
                    dx1 = maxOf(dx1, x + w)
                    dy1 = maxOf(dy1, y + h)
                }
                Rfb.ENC_HEXTILE -> {
                    readHextile(input, x, y, w, h)
                    dx0 = minOf(dx0, x)
                    dy0 = minOf(dy0, y)
                    dx1 = maxOf(dx1, x + w)
                    dy1 = maxOf(dy1, y + h)
                }
                Rfb.ENC_DESKTOP_SIZE -> {
                    resize(w, h)
                    resized = true
                }
                else -> throw RfbException("The desktop used an unsupported encoding ($encoding).")
            }
        }

        if (dx1 > dx0 && dy1 > dy0) {
            val w = dx1 - dx0
            val h = dy1 - dy0
            val copy = IntArray(w * h)
            for (row in 0 until h) {
                System.arraycopy(pixels, (dy0 + row) * fbWidth + dx0, copy, row * w, w)
            }
            events.trySend(DesktopEvent.Frame(dx0, dy0, w, h, copy))
        }
        return resized
    }

    private fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        fbWidth = width
        fbHeight = height
        pixels = IntArray(width * height) { BLACK }
        events.trySend(DesktopEvent.Resized(width, height))
    }

    private fun readRawRect(input: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        val needed = w * h * 4
        if (raw.size < needed) raw = ByteArray(needed)
        input.readFully(raw, 0, needed)
        var src = 0
        for (row in 0 until h) {
            val dst = (y + row) * fbWidth + x
            for (col in 0 until w) {
                pixels[dst + col] = pixelAt(raw, src)
                src += 4
            }
        }
    }

    private fun readHextile(input: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        var background = BLACK
        var foreground = BLACK
        var ty = 0
        while (ty < h) {
            var tx = 0
            val tileH = minOf(16, h - ty)
            while (tx < w) {
                val tileW = minOf(16, w - tx)
                val sub = input.readUnsignedByte()
                if (sub and 0x01 != 0) {
                    readRawRect(input, x + tx, y + ty, tileW, tileH)
                } else {
                    if (sub and 0x02 != 0) background = readPixel(input)
                    fillRect(x + tx, y + ty, tileW, tileH, background)
                    if (sub and 0x04 != 0) foreground = readPixel(input)
                    if (sub and 0x08 != 0) {
                        val count = input.readUnsignedByte()
                        repeat(count) {
                            val colour =
                                if (sub and 0x10 != 0) readPixel(input) else foreground
                            val xy = input.readUnsignedByte()
                            val wh = input.readUnsignedByte()
                            val sx = xy ushr 4
                            val sy = xy and 0x0F
                            val sw = (wh ushr 4).coerceAtLeast(1)
                            val sh = (wh and 0x0F).coerceAtLeast(1)
                            val cw = minOf(sw, tileW - sx)
                            val ch = minOf(sh, tileH - sy)
                            if (cw > 0 && ch > 0) {
                                fillRect(x + tx + sx, y + ty + sy, cw, ch, colour)
                            }
                        }
                    }
                }
                tx += 16
            }
            ty += 16
        }
    }

    private fun copyRect(input: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        val srcX = input.readUnsignedShort()
        val srcY = input.readUnsignedShort()
        if (srcX + w > fbWidth || srcY + h > fbHeight) {
            throw RfbException("The desktop sent an invalid CopyRect region.")
        }
        val tmp = IntArray(w * h)
        for (row in 0 until h) {
            System.arraycopy(pixels, (srcY + row) * fbWidth + srcX, tmp, row * w, w)
        }
        for (row in 0 until h) {
            System.arraycopy(tmp, row * w, pixels, (y + row) * fbWidth + x, w)
        }
    }

    private fun fillRect(x: Int, y: Int, w: Int, h: Int, colour: Int) {
        for (row in 0 until h) {
            val base = (y + row) * fbWidth + x
            pixels.fill(colour, base, base + w)
        }
    }

    private fun readPixel(input: DataInputStream): Int {
        val b0 = input.readUnsignedByte()
        val b1 = input.readUnsignedByte()
        val b2 = input.readUnsignedByte()
        input.readUnsignedByte() // alpha, always 0 in this format
        return BLACK or b0 or (b1 shl 8) or (b2 shl 16)
    }

    private fun pixelAt(buf: ByteArray, offset: Int): Int {
        val b0 = buf[offset].toInt() and 0xFF
        val b1 = buf[offset + 1].toInt() and 0xFF
        val b2 = buf[offset + 2].toInt() and 0xFF
        return BLACK or b0 or (b1 shl 8) or (b2 shl 16)
    }

    private fun skipColourMap(input: DataInputStream) {
        input.skipBytes(1)
        input.skipBytes(2)
        val count = input.readUnsignedShort()
        input.skipBytes(count * 6)
    }

    private fun readServerCutText(input: DataInputStream) {
        input.skipBytes(3)
        val length = input.readInt()
        if (length <= 0) return
        val text = readString(input, length)
        events.trySend(DesktopEvent.Clipboard(text))
    }

    private fun requestUpdate(incremental: Boolean) {
        val o = out ?: return
        synchronized(writeLock) {
            try {
                o.writeByte(3)
                o.writeByte(if (incremental) 1 else 0)
                o.writeShort(0)
                o.writeShort(0)
                o.writeShort(fbWidth)
                o.writeShort(fbHeight)
                o.flush()
            } catch (_: IOException) {
                close()
            }
        }
    }

    private fun readReason(input: DataInputStream, modern: Boolean): String {
        if (!modern) return ""
        return runCatching {
            val length = input.readInt()
            if (length <= 0 || length > 4096) "" else readString(input, length)
        }.getOrDefault("")
    }

    private fun readString(input: DataInputStream, length: Int): String {
        if (length <= 0) return ""
        if (length > 1 shl 20) throw RfbException("The desktop sent an oversized text field.")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.ISO_8859_1)
    }

    private fun describe(t: Throwable): String = when (t) {
        is RfbException -> t.message ?: "The desktop connection failed."
        is SocketTimeoutException -> "The desktop did not respond in time."
        is ConnectException -> "Nothing is listening on port $port. Start the environment first."
        is EOFException -> "The desktop closed the connection during the handshake."
        is SocketException -> "The desktop connection was lost."
        is IOException -> t.message ?: "Network error talking to the desktop."
        else -> t.message ?: "The desktop connection failed."
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5000
        val BLACK = 0xFF000000.toInt()
    }
}

/** Events produced by the RFB session; consumed on the main thread. */
sealed interface DesktopEvent {
    data class Connected(val width: Int, val height: Int, val name: String) : DesktopEvent
    data class Resized(val width: Int, val height: Int) : DesktopEvent
    data class Frame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    ) : DesktopEvent
    data class Clipboard(val text: String) : DesktopEvent
    data class Failure(val message: String) : DesktopEvent
    data object Closed : DesktopEvent
}
