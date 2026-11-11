package com.xlines.app

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.zip.Inflater

object Transfer {
 const val LIMIT = 262144
 fun parse(text: String): JSONObject {
  require(text.toByteArray().size <= LIMIT) { "Файл слишком большой" }
  val json = if (text.startsWith("xlines:")) {
   val uri = URI(text)
   require(uri.scheme == "xlines" && uri.host == "import" && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) { "Неверная ссылка" }
   val pairs = (uri.rawQuery ?: "").split("&")
   require(pairs.size == 1 && pairs[0].startsWith("data=")) { "Неверная ссылка" }
   val token = URLDecoder.decode(pairs[0].substring(5), "UTF-8")
   val compressed = Base64.getUrlDecoder().decode(token)
   val inflater = Inflater()
   try {
    inflater.setInput(compressed); val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
    while (!inflater.finished()) {
     val n = inflater.inflate(buffer)
     require(n > 0 && result.size() + n <= LIMIT) { "Повреждённая или слишком большая ссылка" }
     result.write(buffer, 0, n)
    }
    require(inflater.remaining == 0) { "Лишние данные" }
    result.toString("UTF-8")
   } finally { inflater.end() }
  } else text.removePrefix("\uFEFF")
  return validate(JSONObject(json))
 }
 fun validate(doc: JSONObject): JSONObject {
  require(doc.getString("format") == "xlines" && doc.opt("version") == 1) { "Нужен формат .xlines версии 1" }
  require(doc.getString("name").length in 1..100)
  // Only VLESS + REALITY is supported; transports of older profiles (Hysteria2) are dropped on import.
  val rawConnections = doc.getJSONArray("connections"); val kept = JSONArray()
  for (i in 0 until rawConnections.length()) { val c = rawConnections.getJSONObject(i); if (c.optJSONObject("outbound")?.optString("type") == "vless") kept.put(c) }
  require(kept.length() > 0) { "Поддерживается только VLESS + REALITY" }
  doc.put("connections", kept)
  val connections = doc.getJSONArray("connections"); require(connections.length() in 1..8)
  for (i in 0 until connections.length()) {
   val c = connections.getJSONObject(i); require(c.getString("name").length <= 150)
   val o = c.getJSONObject("outbound")
   val allowed = setOf("type","tag","server","server_port","uuid","flow","password","tls","packet_encoding")
   require(o.keys().asSequence().all { it in allowed }) { "Недопустимые параметры транспорта" }
   require(o.getString("type") == "vless" && o.getString("tag") == "proxy") { "Протокол пока не поддерживается" }
   val host = o.getString("server");require(host.isNotBlank() && host.length <= 64 && host.matches(Regex("[0-9a-fA-F:.]+"))) { "Нужен IP сервера" }
   if(host.contains(':')) java.net.InetAddress.getByName(host) else {
    val pieces=host.split('.');require(pieces.size==4 && pieces.all { p->p.isNotEmpty() && p.length<=3 && (p=="0"||!p.startsWith('0')) && (p.toIntOrNull()?:-1) in 0..255 })
   }
   val port=o.get("server_port");require(port is Int && port in 1..65535)
   val tls = o.getJSONObject("tls");require(tls.opt("enabled") == true && tls.getString("server_name").isNotEmpty())
   require(tls.keys().asSequence().all { it in setOf("enabled","server_name","utls","reality","certificate") }) { "Недопустимые TLS-поля" }
   if (o.getString("type") == "vless") {
    java.util.UUID.fromString(o.getString("uuid"));val r=tls.getJSONObject("reality")
    require(r.opt("enabled") == true && r.getString("public_key").length==43)
    require(r.keys().asSequence().all { it in setOf("enabled","public_key","short_id") })
    val sid=r.getString("short_id");require(sid.length<=16 && sid.length%2==0 && sid.matches(Regex("[0-9a-fA-F]*")))
   } else require(o.getString("password").length in 1..256)
   tls.optJSONObject("utls")?.let { u -> require(u.keys().asSequence().all { it in setOf("enabled","fingerprint") }) }
   tls.optJSONArray("certificate")?.let { cert -> require(cert.length()<=4);for(n in 0 until cert.length()) require(cert.getString(n).startsWith("-----BEGIN CERTIFICATE-----") && cert.getString(n).length<=16384) }
  }
  return doc
 }
 fun configuration(doc: JSONObject, index: Int): String {
  validate(doc);val all=doc.getJSONArray("connections");require(index in 0 until all.length())
  val outbound=JSONObject(all.getJSONObject(index).getJSONObject("outbound").toString())
  return JSONObject().apply {
   put("log",JSONObject().put("level","warn"))
   put("inbounds",JSONArray().put(JSONObject().put("type","tun").put("tag","tun-in").put("address",JSONArray().put("172.28.0.1/30").put("fdfe:dcba:9876::1/126")).put("mtu",1400).put("auto_route",true).put("strict_route",true).put("stack","mixed")))
   put("outbounds",JSONArray().put(outbound))
   put("dns",JSONObject().put("servers",JSONArray().put(JSONObject().put("type","https").put("tag","proxy-dns").put("server","1.1.1.1").put("detour","proxy"))).put("final","proxy-dns").put("strategy","ipv4_only").put("reverse_mapping",true))
   put("route",JSONObject().put("auto_detect_interface",true).put("default_domain_resolver","proxy-dns").put("final","proxy").put("rules",JSONArray().put(JSONObject().put("action","sniff")).put(JSONObject().put("protocol","dns").put("action","hijack-dns"))))
  }.toString()
 }
 fun protocol(connection: JSONObject):String = when(connection.getJSONObject("outbound").getString("type")){"vless"->"VLESS + REALITY";else->"Недоступно"}
}
