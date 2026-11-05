package com.quotaroom.member

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PinStore {
    private const val FILE = "pin_store"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isSet(ctx: Context): Boolean = prefs(ctx).contains("hash")

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        prefs(ctx).edit().putString("salt", saltStr).putString("hash", hash(saltStr, pin)).apply()
    }

    fun check(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString("salt", null) ?: return false
        return p.getString("hash", null) == hash(salt, pin)
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }
}
