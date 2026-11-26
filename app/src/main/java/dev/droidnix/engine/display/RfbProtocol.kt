package dev.droidnix.engine.display

import javax.crypto.Cipher
import javax.crypto.spec.DESKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * RFB (VNC) protocol constants and the pieces of the protocol that are pure
 * computation: VncAuth's DES challenge response and X11 keysym translation.
 *
 * Everything here is deliberately small and dependency-free; the Android
 * platform already provides DES through JCA.
 */
internal object Rfb {
    const val VERSION_3_3 = "RFB 003.003\n"
    const val VERSION_3_7 = "RFB 003.007\n"
    const val VERSION_3_8 = "RFB 003.008\n"

    const val SEC_NONE = 1
    const val SEC_VNC_AUTH = 2

    const val ENC_RAW = 0
    const val ENC_COPY_RECT = 1
    const val ENC_HEXTILE = 5
    const val ENC_DESKTOP_SIZE = -223

    const val MSG_FRAMEBUFFER_UPDATE = 0
    const val MSG_SET_COLOUR_MAP = 1
    const val MSG_BELL = 2
    const val MSG_SERVER_CUT_TEXT = 3

    const val BUTTON_LEFT = 1
    const val BUTTON_MIDDLE = 2
    const val BUTTON_RIGHT = 4
    const val BUTTON_WHEEL_UP = 8
    const val BUTTON_WHEEL_DOWN = 16
    const val BUTTON_WHEEL_LEFT = 32
    const val BUTTON_WHEEL_RIGHT = 64
}

/** A protocol-level problem whose message is safe to show to the user. */
internal class RfbException(message: String) : Exception(message)

/**
 * VncAuth response: the password bytes are bit-reversed (VNC's historical
 * quirk), padded to 8 bytes and used as a DES key to encrypt the 16-byte
 * challenge with ECB. That is the entire scheme.
 */
internal object VncAuth {

    fun response(password: String, challenge: ByteArray): ByteArray {
        require(challenge.size == 16) { "VNC challenge must be 16 bytes" }
        val raw = password.toByteArray(Charsets.ISO_8859_1)
        val key = ByteArray(8)
        for (i in 0 until minOf(8, raw.size)) key[i] = reverseBits(raw[i])
        val spec = DESKeySpec(key)
        val secret = SecretKeySpec(spec.key, "DES")
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        return cipher.doFinal(challenge)
    }

    private fun reverseBits(b: Byte): Byte {
        var v = b.toInt() and 0xFF
        var out = 0
        repeat(8) {
            out = (out shl 1) or (v and 1)
            v = v shr 1
        }
        return out.toByte()
    }
}

/** X11 keysyms for the keys a tablet can actually send. */
object KeySyms {
    const val BACKSPACE = 0xFF08
    const val TAB = 0xFF09
    const val RETURN = 0xFF0D
    const val ESCAPE = 0xFF1B
    const val DELETE = 0xFFFF
    const val HOME = 0xFF50
    const val LEFT = 0xFF51
    const val UP = 0xFF52
    const val RIGHT = 0xFF53
    const val DOWN = 0xFF54
    const val PAGE_UP = 0xFF55
    const val PAGE_DOWN = 0xFF56
    const val END = 0xFF57
    const val INSERT = 0xFF63
    const val SHIFT_L = 0xFFE1
    const val CONTROL_L = 0xFFE3
    const val CAPS_LOCK = 0xFFE5
    const val ALT_L = 0xFFE9
    const val SUPER_L = 0xFFEB

    fun fromCodePoint(cp: Int): Int =
        if (cp in 0x20..0xFF) cp else 0x01000000 or cp

    /**
     * Maps an Android key event to a keysym. [baseChar] is the character with
     * no modifiers (used for Ctrl+letter so the shortcut reaches X, not a
     * control code), [unicodeChar] is the character with the current
     * modifiers. [shortcut] is true when Ctrl/Alt/Meta is held.
     */
    fun fromAndroid(keyCode: Int, unicodeChar: Int, baseChar: Int, shortcut: Boolean): Int? {
        special(keyCode)?.let { return it }
        if (keyCode in 29..54) { // A..Z
            val base = 'a'.code + (keyCode - 29)
            return if (!shortcut && unicodeChar in 0x20..0x7E) unicodeChar else base
        }
        if (keyCode in 7..16) { // 0..9
            val base = '0'.code + (keyCode - 7)
            return if (!shortcut && unicodeChar in 0x20..0x7E) unicodeChar else base
        }
        if (!shortcut && unicodeChar > 0) return fromCodePoint(unicodeChar)
        if (shortcut && baseChar > 0) return fromCodePoint(baseChar)
        return when (keyCode) {
            62 -> 0x20 // space
            66 -> RETURN
            67 -> BACKSPACE
            else -> null
        }
    }

    private fun special(keyCode: Int): Int? = when (keyCode) {
        19 -> UP
        20 -> DOWN
        21 -> LEFT
        22 -> RIGHT
        23 -> RETURN
        61 -> TAB
        67 -> BACKSPACE
        111 -> ESCAPE
        112 -> DELETE
        122 -> HOME
        123 -> END
        124 -> INSERT
        92 -> PAGE_UP
        93 -> PAGE_DOWN
        59 -> SHIFT_L
        60 -> SHIFT_L
        113 -> CONTROL_L
        114 -> CONTROL_L
        57 -> ALT_L
        58 -> ALT_L
        115 -> CAPS_LOCK
        117 -> SUPER_L
        118 -> SUPER_L
        in 131..142 -> 0xFFBE + (keyCode - 131) // F1..F12
        else -> null
    }
}
