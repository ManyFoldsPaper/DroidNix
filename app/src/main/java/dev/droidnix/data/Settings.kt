package dev.droidnix.data

import android.content.Context

/** Small persistent settings store. No secrets beyond the VNC display password. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("droidnix", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    var lastEnvironmentDir: String?
        get() = prefs.getString("last_env", null)
        set(value) = prefs.edit().putString("last_env", value).apply()

    var disclaimerAcceptedAt: Long
        get() = prefs.getLong("disclaimer_at", 0L)
        set(value) = prefs.edit().putLong("disclaimer_at", value).apply()

    /** Chosen install location (a path under a probed storage target). */
    var installBase: String?
        get() = prefs.getString("install_base", null)
        set(value) = prefs.edit().putString("install_base", value).apply()

    /**
     * VNC password for environments without a droidnix.json (the legacy
     * install). It is only ever sent to 127.0.0.1, but it is still a secret:
     * keep it in app-private storage and never log it.
     */
    var vncPassword: String
        get() = prefs.getString("vnc_password", "archlinux") ?: "archlinux"
        set(value) = prefs.edit().putString("vnc_password", value).apply()
}
