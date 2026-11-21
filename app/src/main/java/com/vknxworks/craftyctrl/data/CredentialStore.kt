package com.vknxworks.craftyctrl.data

import android.content.Context
import android.util.Base64
import com.vknxworks.craftyctrl.data.crypto.KeystoreAes
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.data.lock.LockVault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str

enum class AuthMode(@StringRes private val labelRes: Int) {
    API_KEY(R.string.auth_api_key),
    PASSWORD(R.string.auth_password);

    val label: String get() = str(labelRes)
}

/**
 * Who is signed in. The token itself isn't part of it: the screens get it from [com.vknxworks.craftyctrl.data.lock.Lock.liveToken],
 * which is wiped while the app is locked.
 */
data class Session(
    val baseUrl: String,
    val mode: AuthMode,
    val username: String?,
)

/** A panel CraftyCtrl is signed in to but that isn't the one on screen (see [CredentialStore.others]). */
data class SavedPanel(
    val baseUrl: String,
    val mode: AuthMode,
    val username: String?,
) {
    /** Tells panels apart: the same address signed in to as two users counts as two panels. */
    val id: String get() = "$baseUrl|${mode.name}|${username.orEmpty()}"
}

/**
 * Keeps the bearer token encrypted with an AES key that never leaves the Android Keystore. With the app
 * lock on, the token is also encrypted with the lock's vault key, so it can only be read after unlocking.
 * Passwords are never stored — password sign-in only keeps the token Crafty hands back.
 *
 * Several panels: one is the active one (the keys below, as before); the others wait in [KEY_PANELS], each
 * with its token encrypted exactly like the active one's. Switching swaps the stored values, so no token is
 * decrypted to switch. Server alerts only watch the active panel.
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("crafty_session", Context.MODE_PRIVATE)
    private val vault = Lock.vault(context)

    val lastUrl: String get() = prefs.getString(KEY_URL, "").orEmpty()
    val lastUsername: String get() = prefs.getString(KEY_USER, "").orEmpty()

    /** The signed-in server, or null when signed out. Works while locked; the token isn't needed. */
    fun load(): Session? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        if (!prefs.contains(KEY_TOKEN)) return null
        val mode = runCatching { AuthMode.valueOf(prefs.getString(KEY_MODE, "").orEmpty()) }
            .getOrDefault(AuthMode.API_KEY)
        return Session(url, mode, prefs.getString(KEY_USER, null))
    }

    /** The token, or null while it's sealed by the app lock and the app is locked. */
    fun token(): String? {
        val plain = prefs.getString(KEY_TOKEN, null)?.let(::decrypt) ?: return null
        return if (LockVault.isSealed(plain)) vault.open(plain) else plain
    }

    /**
     * The token for server alerts, which run with the app closed or locked. While the lock is on and
     * alerts are on, they use their own Keystore-only copy (see [syncBackgroundCopy]).
     */
    fun backgroundToken(): String? {
        val plain = prefs.getString(KEY_TOKEN, null)?.let(::decrypt) ?: return null
        if (!LockVault.isSealed(plain)) return plain
        return vault.open(plain) ?: prefs.getString(KEY_TOKEN_BG, null)?.let(::decrypt)
    }

    fun save(session: Session, token: String) {
        // Signing in again to a panel that was waiting with the others replaces that entry.
        val id = SavedPanel(session.baseUrl, session.mode, session.username).id
        val editor = prefs.edit()
        writeOthers(editor, readOthers().filter { it.toPanel()?.id != id })
        editor
            .putString(KEY_URL, session.baseUrl)
            .putString(KEY_TOKEN, encrypt(wrap(token)))
            .putString(KEY_MODE, session.mode.name)
        if (session.username != null) editor.putString(KEY_USER, session.username) else editor.remove(KEY_USER)
        editor.commit()
    }

    private fun wrap(token: String): String =
        if (vault.enabled) vault.seal(token) ?: error(str(R.string.unlock_craftyctrl_first)) else token

    /** The app lock was turned on (vault open): encrypt the saved token with its key too. */
    fun sealToken() {
        resealOthers(seal = true)
        val token = token() ?: return
        prefs.edit().putString(KEY_TOKEN, encrypt(wrap(token))).commit()
    }

    /** The app lock is being turned off (vault open): back to Keystore-only encryption. */
    fun unsealToken() {
        resealOthers(seal = false)
        val token = token() ?: return
        prefs.edit().putString(KEY_TOKEN, encrypt(token)).commit()
    }

    /**
     * Server alerts need the token while the app is locked. With the lock and alerts both on, they get a
     * Keystore-only copy of it; otherwise the copy is deleted. Only changes it while the token can be read.
     */
    fun syncBackgroundCopy(alertsOn: Boolean) {
        if (vault.enabled && alertsOn) {
            val token = token() ?: return
            prefs.edit().putString(KEY_TOKEN_BG, encrypt(token)).commit()
        } else {
            prefs.edit().remove(KEY_TOKEN_BG).commit()
        }
    }

    // ------------------------------------------------------------ several panels

    private fun readOthers(): List<JsonObject> = runCatching {
        (Json.parseToJsonElement(prefs.getString(KEY_PANELS, "[]").orEmpty()) as? JsonArray)
            ?.filterIsInstance<JsonObject>().orEmpty()
    }.getOrDefault(emptyList())

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.toPanel(): SavedPanel? {
        val url = text("url") ?: return null
        if (text("token") == null) return null
        val mode = runCatching { AuthMode.valueOf(text("mode").orEmpty()) }.getOrDefault(AuthMode.API_KEY)
        return SavedPanel(url, mode, text("user"))
    }

    private fun writeOthers(editor: android.content.SharedPreferences.Editor, list: List<JsonObject>) {
        if (list.isEmpty()) editor.remove(KEY_PANELS) else editor.putString(KEY_PANELS, JsonArray(list).toString())
    }

    /** The other panels CraftyCtrl is signed in to, in the order they were added. Works while locked. */
    fun others(): List<SavedPanel> = readOthers().mapNotNull { it.toPanel() }

    /** The active panel as an entry for [KEY_PANELS], or null when signed out. */
    private fun activeEntry(): JsonObject? {
        val session = load() ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return buildJsonObject {
            put("url", session.baseUrl)
            put("mode", session.mode.name)
            session.username?.let { put("user", it) }
            put("token", token)
        }
    }

    /**
     * Puts the active panel with the others, leaving nobody active (the sign-in form is next, or
     * [activate]). The alerts' copy of its token is dropped: alerts only follow the active panel.
     */
    fun stashActive() {
        val entry = activeEntry() ?: return
        val id = entry.toPanel()?.id
        val list = readOthers().filter { it.toPanel()?.id != id } + entry
        val editor = prefs.edit()
        writeOthers(editor, list)
        editor.remove(KEY_TOKEN).remove(KEY_TOKEN_BG).remove(KEY_MODE).commit()
    }

    /** Makes [panel] the active one; the one that was active joins the others. False if it isn't saved. */
    fun activate(panel: SavedPanel): Boolean {
        val pick = readOthers().firstOrNull { it.toPanel()?.id == panel.id } ?: return false
        val token = pick.text("token") ?: return false
        val current = activeEntry()
        val currentId = current?.toPanel()?.id
        val list = readOthers().filter { val id = it.toPanel()?.id; id != panel.id && id != currentId } + listOfNotNull(current)
        val editor = prefs.edit()
        writeOthers(editor, list)
        editor.putString(KEY_URL, panel.baseUrl)
            .putString(KEY_TOKEN, token)
            .putString(KEY_MODE, panel.mode.name)
            .remove(KEY_TOKEN_BG)
        if (panel.username != null) editor.putString(KEY_USER, panel.username) else editor.remove(KEY_USER)
        return editor.commit()
    }

    /** Forgets one of the other panels (its token is deleted from this phone). */
    fun removeOther(panel: SavedPanel) {
        val editor = prefs.edit()
        writeOthers(editor, readOthers().filter { it.toPanel()?.id != panel.id })
        editor.commit()
    }

    /** True when [baseUrl] is still used by the active panel or one of the others. */
    fun usesAddress(baseUrl: String): Boolean =
        load()?.baseUrl == baseUrl || others().any { it.baseUrl == baseUrl }

    /** Re-encrypts the other panels' tokens when the app lock is turned on ([seal]) or off (vault open). */
    private fun resealOthers(seal: Boolean) {
        val list = readOthers()
        if (list.isEmpty()) return
        val next = list.map { entry ->
            val stored = entry.text("token") ?: return@map entry
            val plain = decrypt(stored) ?: return@map entry
            val token = (if (LockVault.isSealed(plain)) vault.open(plain) else plain) ?: return@map entry
            val again = encrypt(if (seal) wrap(token) else token)
            JsonObject(entry + ("token" to JsonPrimitive(again)))
        }
        val editor = prefs.edit()
        writeOthers(editor, next)
        editor.commit()
    }

    /** Forgets the token but keeps the address and username to prefill the sign-in form. */
    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_BG).remove(KEY_MODE).commit()
    }

    /** Forgets everything, including the address and username ("Forgot PIN?"). */
    fun wipe() {
        prefs.edit().clear().commit()
    }

    /** StrongBox for a new key when the phone has one; an existing key is kept (see [KeystoreAes]). */
    private fun key(): SecretKey = KeystoreAes.key(KEY_ALIAS)

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(sealed)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val (iv, sealed) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(iv)))
        String(cipher.doFinal(unb64(sealed)), Charsets.UTF_8)
    }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val KEY_ALIAS = "crafty_remote_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_BG = "token_alerts"
        const val KEY_MODE = "auth_mode"
        const val KEY_USER = "username"
        const val KEY_PANELS = "panels"
    }
}
