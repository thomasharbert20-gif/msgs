package com.msgs.app.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import gmbridge.SessionStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the Google Messages pairing keys encrypted with AES-256-GCM. The key is
 * generated inside the Android Keystore: it can't be exported, isn't part of any
 * backup, and is deleted on unpair, which makes any leftover ciphertext useless.
 * The engine never writes the session anywhere else.
 */
class SessionVault(context: Context) : SessionStore {
    private val file = File(context.noBackupFilesDir, "session.enc")

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey? {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    override fun save(data: ByteArray?) {
        val plain = data ?: return
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        val enc = cipher.doFinal(plain)
        val iv = cipher.iv
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(VERSION)
            out.write(iv.size)
            out.write(iv)
            out.write(enc)
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            error("couldn't write the encrypted session")
        }
    }

    @Synchronized
    override fun load(): ByteArray? = try {
        if (!file.exists()) null else {
            val bytes = file.readBytes()
            require(bytes.size > 2 && bytes[0].toInt() == VERSION)
            val ivLen = bytes[1].toInt()
            val iv = bytes.copyOfRange(2, 2 + ivLen)
            val enc = bytes.copyOfRange(2 + ivLen, bytes.size)
            val k = key(create = false) ?: error("no key")
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
            cipher.doFinal(enc)
        }
    } catch (e: Exception) {
        // Unreadable or tampered: treat as not paired rather than crash.
        clear()
        null
    }

    @Synchronized
    override fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    fun exists(): Boolean = file.exists()

    private companion object {
        const val ALIAS = "msgs_session_key_v2"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val VERSION = 2
    }
}
