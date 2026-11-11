package com.xlines.app
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class LibraryTest {
 private fun resource(name:String)=javaClass.classLoader!!.getResource(name)!!.readText()
 private fun doc()=Transfer.parse(resource("golden.xlines"))
 @Test fun oldSingleDocumentBecomesFirstServer(){
  val lib=Library.parse(doc().toString());assertEquals(1,Library.count(lib));assertEquals(0,lib.getInt("selected"));assertNotNull(Library.doc(lib,0))
 }
 @Test fun sameServerMergesDifferentServerAppends(){
  val lib=Library.empty();val first=Library.merge(lib,doc(),"Spain");assertTrue(first.second);assertEquals("Spain",Library.name(lib,0))
  val again=Library.merge(lib,doc(),null);assertFalse(again.second);assertEquals(1,Library.count(lib))
  val other=doc();other.put("name","Other");val third=Library.merge(lib,other,"Finland");assertTrue(third.second);assertEquals(2,Library.count(lib));assertEquals(1,lib.getInt("selected"))
 }
 @Test fun renameRemoveAndRoundTrip(){
  val lib=Library.empty();Library.merge(lib,doc(),"A");val other=doc();other.put("name","B");Library.merge(lib,other,"B")
  Library.rename(lib,0,"Испания");val back=Library.parse(lib.toString());assertEquals("Испания",Library.name(back,0))
  Library.remove(back,0);assertEquals(1,Library.count(back));assertEquals("B",Library.name(back,0));assertEquals(0,back.getInt("selected"))
  Library.remove(back,0);assertEquals(0,Library.count(back));assertNull(Library.doc(back,0))
 }
 @Test(expected=IllegalArgumentException::class) fun invalidDocumentIsRejected(){
  val lib=Library.empty();Library.merge(lib,doc(),"A");lib.getJSONArray("servers").getJSONObject(0).getJSONObject("doc").getJSONArray("connections").getJSONObject(0).getJSONObject("outbound").getJSONObject("tls").put("insecure",true)
  Library.parse(lib.toString())
 }
}
