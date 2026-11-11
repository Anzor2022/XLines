package com.xlines.app

enum class ConnState { IDLE, CONNECTING, CONNECTED, ERROR }

/** Maps the service status text to one visual state of the power button. */
fun connState(status:String,active:Boolean):ConnState = when {
 status.startsWith("Ошибка") -> ConnState.ERROR
 !active -> ConnState.IDLE
 status=="Подключено" -> ConnState.CONNECTED
 status.startsWith("Туннель запущен · ") -> ConnState.ERROR
 else -> ConnState.CONNECTING
}

/** Only the first phase blocks taps; later phases may still be cancelled with the same button. */
fun tapBlocked(status:String,active:Boolean):Boolean = active && status=="Подключение…"

/** Messages that confirm something are shown in the accent colour, all others as errors. */
fun isSuccessMessage(message:String):Boolean = message.startsWith("Подключение добавлено") || message.startsWith("Подключение обновлено")
