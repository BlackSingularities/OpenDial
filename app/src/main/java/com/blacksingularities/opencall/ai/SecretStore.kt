package com.blacksingularities.opencall.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Klucze API szyfrowane kluczem z Android Keystore. Szyfrogram leży w osobnym pliku wyłączonym
 * z kopii zapasowej; po przywróceniu na innym telefonie odczyt po prostu zwraca pusty napis.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun get(name: String): String {
        val stored = prefs.getString(name, null) ?: return ""
        return runCatching {
            val (iv, data) = stored.split(':', limit = 2)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /** Pusta wartość usuwa wpis. Zwraca false, gdy szyfrowanie się nie udało – klucz nie jest wtedy zapisany. */
    fun put(name: String, value: String): Boolean {
        if (value.isBlank()) {
            prefs.edit().remove(name).apply()
            return true
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val data = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            prefs.edit().putString(name, "$iv:$data").apply()
        }.isSuccess
    }

    private companion object {
        const val FILE = "opendial_secrets"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "opendial_ai_keys"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
