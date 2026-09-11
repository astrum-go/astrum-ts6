package br.app.astrum.ts6.app.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.app.astrum.ts6.protocol.Ts3IdentityCodec
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.identityDataStore by preferencesDataStore(name = "ts3_identity")

class IdentityVault(private val context: Context) {
    suspend fun getOrCreate(): String {
        val stored = context.identityDataStore.data.first()[encryptedIdentityKey]
        if (stored != null) return decrypt(stored)

        val generated = Ts3IdentityCodec.generate()
        context.identityDataStore.edit { preferences ->
            preferences[encryptedIdentityKey] = encrypt(generated)
        }
        return generated
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(
            payloadVersion,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(":")
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == payloadVersion) { "Unsupported identity payload" }

        val cipher = Cipher.getInstance(transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        val encryptedIdentityKey = stringPreferencesKey("encrypted_identity")
        const val payloadVersion = "v1"
        const val androidKeyStore = "AndroidKeyStore"
        const val keyAlias = "ts3_mobile_identity_v1"
        const val transformation = "AES/GCM/NoPadding"
    }
}

