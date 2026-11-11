package com.xlines.app
import org.junit.Test
import org.junit.Assert.*
class ConnStateTest {
 @Test fun statesFollowServiceStatus(){
  assertEquals(ConnState.IDLE,connState("Не подключено",false))
  assertEquals(ConnState.CONNECTING,connState("Подключение…",true))
  assertEquals(ConnState.CONNECTING,connState("Туннель запущен; проверка связи…",true))
  assertEquals(ConnState.CONNECTED,connState("Подключено",true))
  assertEquals(ConnState.ERROR,connState("Туннель запущен · проверьте интернет",true))
  assertEquals(ConnState.ERROR,connState("Ошибка подключения",false))
 }
 @Test fun onlyFirstPhaseBlocksTaps(){
  assertTrue(tapBlocked("Подключение…",true))
  assertFalse(tapBlocked("Подключено",true))
  assertFalse(tapBlocked("Туннель запущен; проверка связи…",true))
  assertFalse(tapBlocked("Не подключено",false))
 }
 @Test fun successMessagesAreSeparatedFromErrors(){
  assertTrue(isSuccessMessage("Подключение добавлено"))
  assertTrue(isSuccessMessage("Подключение обновлено"))
  assertFalse(isSuccessMessage("Сначала отключите соединение"))
  assertFalse(isSuccessMessage("Разрешение не выдано"))
 }
}
