package dev.droidnix.engine.display

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Protocol tests against a mock RFB server. No Android, no device: these cover
 * the handshake, VncAuth, Raw and Hextile decoding and the clipboard.
 */
class RfbClientTest {

    @Test
    fun vncAuthMatchesKnownVector() {
        val challenge = ByteArray(16) { it.toByte() }
        val response = VncAuth.response("archlinux", challenge)
        assertEquals(
            "7c050170e1381512f913c669250f527b",
            response.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun rawFrameAndClipboard() {
        val frame = exchange(expectClipboard = true) { _, out ->
            out.writeByte(0) // FramebufferUpdate
            out.writeByte(0)
            out.writeShort(1) // one rect
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(4)
            out.writeShort(2)
            out.writeInt(Rfb.ENC_RAW)
            repeat(8) { i ->
                when (i) {
                    0 -> writePixel(out, 0xFFFF0000.toInt())
                    1 -> writePixel(out, 0xFF00FF00.toInt())
                    else -> writePixel(out, 0xFF0000FF.toInt())
                }
            }
            // Cut text can follow immediately; the client requests the next
            // frame on its own before reading this message.
            out.writeByte(3) // ServerCutText
            out.writeByte(0)
            out.writeShort(0)
            val text = "hello from Linux".toByteArray(Charsets.ISO_8859_1)
            out.writeInt(text.size)
            out.write(text)
            out.flush()
        } ?: error("no frame received")

        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertEquals(0xFFFF0000.toInt(), frame.pixels[0])
        assertEquals(0xFF00FF00.toInt(), frame.pixels[1])
        assertEquals(0xFF0000FF.toInt(), frame.pixels[2])
    }

    @Test
    fun hextileBackgroundAndSubrect() {
        val frame = exchange(expectClipboard = false) { _, out ->
            out.writeByte(0)
            out.writeByte(0)
            out.writeShort(1)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(2)
            out.writeShort(2)
            out.writeInt(Rfb.ENC_HEXTILE)
            // BackgroundSpecified | AnySubrects | SubrectsColoured
            out.writeByte(0x02 or 0x08 or 0x10)
            writePixel(out, 0xFF0000FF.toInt()) // background blue
            out.writeByte(1) // one subrect
            writePixel(out, 0xFFFF0000.toInt()) // red
            out.writeByte(0x11) // x=1, y=1
            out.writeByte(0x11) // w=1, h=1
            out.flush()
        } ?: error("no frame received")

        assertEquals(0xFF0000FF.toInt(), frame.pixels[0])
        assertEquals(0xFFFF0000.toInt(), frame.pixels[3])
    }

    private fun writePixel(out: DataOutputStream, argb: Int) {
        out.writeByte(argb and 0xFF) // B
        out.writeByte((argb shr 8) and 0xFF) // G
        out.writeByte((argb shr 16) and 0xFF) // R
        out.writeByte(0)
    }

    private data class Frame(val width: Int, val height: Int, val pixels: IntArray)

    private fun exchange(
        expectClipboard: Boolean,
        script: (DataInputStream, DataOutputStream) -> Unit,
    ): Frame? = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val failure = AtomicReference<Throwable?>(null)
        val events = Channel<DesktopEvent>(Channel.UNLIMITED)
        val challenge = ByteArray(16) { (it * 5 + 1).toByte() }

        val serverThread = thread(name = "mock-rfb") {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = DataInputStream(socket.getInputStream())
                    val out = DataOutputStream(socket.getOutputStream())

                    out.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    val version = ByteArray(12)
                    input.readFully(version)
                    assertEquals("RFB 003.008\n", String(version, Charsets.US_ASCII))

                    out.writeByte(1) // one security type
                    out.writeByte(Rfb.SEC_VNC_AUTH)
                    out.flush()
                    assertEquals(Rfb.SEC_VNC_AUTH, input.readUnsignedByte())

                    out.write(challenge)
                    out.flush()
                    val response = ByteArray(16)
                    input.readFully(response)
                    assertEquals(
                        VncAuth.response("archlinux", challenge).toList(),
                        response.toList(),
                    )
                    out.writeInt(0) // SecurityResult OK
                    out.flush()

                    assertEquals(1, input.readUnsignedByte()) // ClientInit shared

                    out.writeShort(4)
                    out.writeShort(2)
                    out.write(ByteArray(16))
                    val name = "Mock Desktop".toByteArray(Charsets.ISO_8859_1)
                    out.writeInt(name.size)
                    out.write(name)
                    out.flush()

                    val pixelFormat = ByteArray(20) // SetPixelFormat
                    input.readFully(pixelFormat)
                    assertEquals(32, pixelFormat[4].toInt() and 0xFF)
                    assertEquals(2, input.readUnsignedByte()) // SetEncodings
                    input.readUnsignedByte()
                    val encodings = input.readUnsignedShort()
                    repeat(encodings) { input.readInt() }
                    readUpdateRequest(input) // initial full request

                    script(input, out)
                    Thread.sleep(250)
                }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }

        val client = RfbClient("127.0.0.1", server.localPort, "archlinux", events)
        val job = launch(Dispatchers.IO) { client.run() }

        var frame: Frame? = null
        var clipboard: String? = null
        try {
            withTimeout(8000) {
                while (true) {
                    when (val event = events.receive()) {
                        is DesktopEvent.Frame -> if (frame == null) {
                            frame = Frame(event.width, event.height, event.pixels)
                        }
                        is DesktopEvent.Clipboard -> clipboard = event.text
                        is DesktopEvent.Failure -> error(event.message)
                        is DesktopEvent.Closed -> return@withTimeout
                        else -> Unit
                    }
                    if (frame != null && (!expectClipboard || clipboard != null)) break
                }
            }
            if (expectClipboard) {
                assertEquals("hello from Linux", clipboard)
            }
        } finally {
            client.close()
            job.cancel()
            server.close()
            serverThread.join(2000)
        }
        assertNull("server error: ${failure.get()?.message}", failure.get())
        frame
    }

    private fun readUpdateRequest(input: DataInputStream) {
        assertEquals(3, input.readUnsignedByte())
        input.readUnsignedByte()
        input.readUnsignedShort()
        input.readUnsignedShort()
        input.readUnsignedShort()
        input.readUnsignedShort()
    }
}
