package com.xlines.app
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Secrets(private val context:Context) {
 private fun key():SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (store.getKey("xlines-profile-v1",null) as? SecretKey)?.let{return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder("xlines-profile-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun save(text:String) {
  Library.parse(text)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
  val tmp=java.io.File(context.filesDir,"profile.new");tmp.writeBytes(cipher.iv+cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
  require(tmp.renameTo(java.io.File(context.filesDir,"profile.enc"))) {"Не удалось сохранить профиль"}
 }
 fun load():String? {
  val file=java.io.File(context.filesDir,"profile.enc");if(!file.exists())return null
  val data=file.readBytes();require(data.size in 29..(Library.LIMIT+128))
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12)))
  return String(cipher.doFinal(data.copyOfRange(12,data.size)),Charsets.UTF_8)
 }
}
