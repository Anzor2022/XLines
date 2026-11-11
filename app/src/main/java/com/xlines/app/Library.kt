package com.xlines.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Several named servers on one device. Each entry keeps one validated .xlines document. */
object Library {
 const val MAX_SERVERS = 20
 const val LIMIT = 4_000_000

 fun empty(): JSONObject = JSONObject().put("servers", JSONArray()).put("selected", 0)

 fun entry(name: String, doc: JSONObject): JSONObject =
  JSONObject().put("id", UUID.randomUUID().toString()).put("name", name.trim().take(60).ifEmpty { host(doc) }).put("doc", doc)

 fun host(doc: JSONObject): String =
  doc.getJSONArray("connections").getJSONObject(0).getJSONObject("outbound").getString("server")

 fun type(connection: JSONObject): String = connection.getJSONObject("outbound").getString("type")

 fun count(lib: JSONObject): Int = lib.getJSONArray("servers").length()

 fun doc(lib: JSONObject, index: Int): JSONObject? {
  val all = lib.getJSONArray("servers")
  return if (index in 0 until all.length()) all.getJSONObject(index).getJSONObject("doc") else null
 }

 fun name(lib: JSONObject, index: Int): String = lib.getJSONArray("servers").getJSONObject(index).getString("name")

 /** Reads stored text. Old installs stored one bare .xlines document: it becomes the first server. */
 fun parse(text: String): JSONObject {
  require(text.length <= LIMIT) { "Хранилище слишком большое" }
  val root = JSONObject(text)
  if (root.optString("format") == "xlines") {
   val doc = Transfer.validate(root)
   return empty().apply { getJSONArray("servers").put(entry(doc.getString("name"), doc)) }
  }
  val all = root.getJSONArray("servers"); require(all.length() <= MAX_SERVERS) { "Слишком много серверов" }
  for (i in 0 until all.length()) {
   val item = all.getJSONObject(i); require(item.getString("name").length in 1..60)
   Transfer.validate(item.getJSONObject("doc"))
  }
  val selected = root.optInt("selected", 0)
  root.put("selected", if (all.length() == 0) 0 else selected.coerceIn(0, all.length() - 1))
  return root
 }

 /** Same server IP and same document name are one server: connections of the same protocol are replaced. */
 fun find(lib: JSONObject, doc: JSONObject): Int {
  val all = lib.getJSONArray("servers")
  for (i in 0 until all.length()) {
   val d = all.getJSONObject(i).getJSONObject("doc")
   if (host(d) == host(doc) && d.getString("name") == doc.getString("name")) return i
  }
  return -1
 }

 /** Returns the index of the server and true when a new one was appended (the caller asks for its name). */
 fun merge(lib: JSONObject, doc: JSONObject, name: String?): Pair<Int, Boolean> {
  Transfer.validate(doc)
  val all = lib.getJSONArray("servers"); val found = find(lib, doc)
  if (found >= 0) {
   val old = all.getJSONObject(found).getJSONObject("doc"); val oldConnections = old.getJSONArray("connections")
   val incoming = doc.getJSONArray("connections"); val kinds = (0 until incoming.length()).map { type(incoming.getJSONObject(it)) }.toSet()
   val merged = JSONArray()
   for (i in 0 until oldConnections.length()) if (type(oldConnections.getJSONObject(i)) !in kinds) merged.put(oldConnections.getJSONObject(i))
   for (i in 0 until incoming.length()) merged.put(incoming.getJSONObject(i))
   val copy = JSONObject(old.toString()); copy.put("connections", merged); Transfer.validate(copy)
   all.getJSONObject(found).put("doc", copy)
   lib.put("selected", found); return Pair(found, false)
  }
  require(all.length() < MAX_SERVERS) { "Слишком много серверов" }
  all.put(entry(name ?: doc.getString("name"), doc))
  lib.put("selected", all.length() - 1); return Pair(all.length() - 1, true)
 }

 fun rename(lib: JSONObject, index: Int, name: String) {
  val value = name.trim().take(60); if (value.isNotEmpty()) lib.getJSONArray("servers").getJSONObject(index).put("name", value)
 }

 fun remove(lib: JSONObject, index: Int) {
  val all = lib.getJSONArray("servers"); val rest = JSONArray()
  for (i in 0 until all.length()) if (i != index) rest.put(all.getJSONObject(i))
  lib.put("servers", rest); lib.put("selected", if (rest.length() == 0) 0 else lib.optInt("selected", 0).coerceIn(0, rest.length() - 1))
 }
}
