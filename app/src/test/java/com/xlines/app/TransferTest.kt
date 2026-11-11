package com.xlines.app
import org.junit.Test
import org.junit.Assert.*
class TransferTest {
 private fun resource(name:String)=javaClass.classLoader!!.getResource(name)!!.readText()
 @Test fun pythonFileAndUriAgree(){
  val file=Transfer.parse(resource("golden.xlines"));val qr=Transfer.parse(resource("golden-uri.txt"))
  assertEquals(file.getString("name"),qr.getString("name"))
  assertEquals(file.getJSONArray("connections").getJSONObject(0).getJSONObject("outbound").getString("uuid"),qr.getJSONArray("connections").getJSONObject(0).getJSONObject("outbound").getString("uuid"))
  assertTrue(Transfer.configuration(file,0).contains("proxy-dns"))
 }
 @Test fun noWebImports(){assertTrue(runCatching{Transfer.parse("https://example.org/profile")}.isFailure)}
 @Test fun onlyVlessKept(){val d=Transfer.parse(resource("golden-uri.txt"));val a=d.getJSONArray("connections");for(i in 0 until a.length())assertEquals("vless",a.getJSONObject(i).getJSONObject("outbound").getString("type"))}
 @Test(expected=IllegalArgumentException::class) fun noInsecureTls(){val d=Transfer.parse(resource("golden.xlines"));d.getJSONArray("connections").getJSONObject(0).getJSONObject("outbound").getJSONObject("tls").put("insecure",true);Transfer.validate(d)}
}
