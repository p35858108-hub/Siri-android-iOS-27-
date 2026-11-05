package com.assistant.siri.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_keys")

/** Se lanza cuando no se puede cifrar o guardar una clave de forma segura. */
class SecureStorageException(cause: Throwable) : Exception("No se pudo cifrar la clave", cause)

/**
 * Guarda las claves de API cifradas con AES-256-GCM.
 * La clave de cifrado vive en Android Keystore y no se puede extraer.
 * Cada texto cifrado queda ligado al id del proveedor (datos asociados de GCM).
 */
class SecureKeyStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.secureDataStore

    private val keyLock = Any()
    private var cachedKey: SecretKey? = null

    /** Todas las claves descifradas, por id de proveedor. Una clave ilegible se devuelve vacía. */
    val keys: Flow<Map<String, String>> = dataStore.safeData
        .map { prefs ->
            val result = mutableMapOf<String, String>()
            for ((key, value) in prefs.asMap()) {
                val name = key.name
                if (name.startsWith(KEY_PREFIX) && value is String) {
                    val providerId = name.removePrefix(KEY_PREFIX)
                    result[providerId] = decrypt(providerId, value)
                }
            }
            result.toMap()
        }
        .flowOn(Dispatchers.IO)

    /** Guarda la clave. Si está vacía, la elimina. Puede lanzar [SecureStorageException]. */
    suspend fun save(providerId: String, apiKey: String) {
        val plain = apiKey.trim()
        if (plain.isEmpty()) {
            delete(providerId)
            return
        }
        val encrypted = withContext(Dispatchers.IO) {
            try {
                encrypt(providerId, plain)
            } catch (e: GeneralSecurityException) {
                throw SecureStorageException(e)
            } catch (e: ProviderException) {
                throw SecureStorageException(e)
            }
        }
        dataStore.edit { prefs -> prefs[prefKey(providerId)] = encrypted }
    }

    /** Devuelve la clave descifrada, o una cadena vacía si no existe o no se puede leer. */
    suspend fun get(providerId: String): String {
        val stored = dataStore.safeData.first()[prefKey(providerId)] ?: return ""
        return withContext(Dispatchers.IO) { decrypt(providerId, stored) }
    }

    suspend fun delete(providerId: String) {
        dataStore.edit { prefs -> prefs.remove(prefKey(providerId)) }
    }

    // ---------- Cifrado ----------

    private fun prefKey(providerId: String) = stringPreferencesKey(KEY_PREFIX + providerId)

    private fun encrypt(providerId: String, plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(providerId: String, stored: String): String {
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            if (bytes.size <= IV_SIZE_BYTES) {
                ""
            } else {
                val iv = bytes.copyOfRange(0, IV_SIZE_BYTES)
                val encrypted = bytes.copyOfRange(IV_SIZE_BYTES, bytes.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
                cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            }
        } catch (e: GeneralSecurityException) {
            ""
        } catch (e: ProviderException) {
            ""
        } catch (e: IllegalArgumentException) {
            ""
        }
    }

    private fun secretKey(): SecretKey = synchronized(keyLock) {
        cachedKey?.let { return it }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        val key = existing ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
        cachedKey = key
        key
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ai_assistant_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_PREFIX = "api_key_"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
