package com.xlines.app

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

class MainActivity:ComponentActivity() {
 private var lib by mutableStateOf(Library.empty())
 private var selected by mutableIntStateOf(0)
 private var message by mutableStateOf("")
 private var pending by mutableStateOf<JSONObject?>(null)
 private var libraryVersion by mutableIntStateOf(0)
 private val doc:JSONObject? get(){libraryVersion;return Library.doc(lib,lib.optInt("selected",0))}
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)runCatching {
  contentResolver.openInputStream(uri)!!.use{input->
   val out=java.io.ByteArrayOutputStream();val bytes=ByteArray(4096)
   while(true){val n=input.read(bytes);if(n<0)break;require(out.size()+n<=Transfer.LIMIT){"Файл слишком большой"};out.write(bytes,0,n)}
   importText(out.toString("UTF-8"))
  }
 }.onFailure{message=it.message?:"Ошибка импорта"}}
 private val scanner=registerForActivityResult(ScanContract()){result->result.contents?.let{runCatching{importText(it)}.onFailure{e->message=e.message?:"Неверный QR"}}}
 private val permission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->if(result.resultCode==RESULT_OK)startTunnel()else message="Разрешение не выдано"}
 private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  runCatching{Secrets(this).load()?.let{lib=Library.parse(it)}}.onFailure{message="Не удалось открыть сохранённые подключения: ${it.message}"}
  intent.dataString?.let{runCatching{importText(it)}.onFailure{e->message=e.message?:"Ошибка ссылки"}}
  if(Build.VERSION.SDK_INT>=33)notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
  setContent{Screen()}
 }
 private fun persist(){Secrets(this).save(lib.toString());libraryVersion++}
 private fun importText(text:String){
  require(!TunnelState.active){"Сначала отключите соединение"};val value=Transfer.parse(text)
  if(Library.find(lib,value)>=0){val copy=JSONObject(lib.toString());Library.merge(copy,value,null);Secrets(this).save(copy.toString());lib=copy;selected=0;libraryVersion++;message="Подключение обновлено"}
  else pending=value
 }
 private fun finishImport(name:String){
  val value=pending?:return;pending=null
  runCatching{val copy=JSONObject(lib.toString());Library.merge(copy,value,name);Secrets(this).save(copy.toString());lib=copy;selected=0;libraryVersion++;message="Подключение добавлено"}.onFailure{message=it.message?:"Не удалось сохранить"}
 }
 private fun selectServer(index:Int){if(TunnelState.active){message="Сначала отключите соединение";return};val copy=JSONObject(lib.toString());copy.put("selected",index);runCatching{Secrets(this).save(copy.toString());lib=copy;selected=0;libraryVersion++}.onFailure{message=it.message?:"Ошибка"}}
 private fun renameServer(index:Int,name:String){val copy=JSONObject(lib.toString());Library.rename(copy,index,name);runCatching{Secrets(this).save(copy.toString());lib=copy;libraryVersion++}.onFailure{message=it.message?:"Ошибка"}}
 private fun deleteServer(index:Int){if(TunnelState.active){message="Сначала отключите соединение";return};val copy=JSONObject(lib.toString());Library.remove(copy,index);runCatching{Secrets(this).save(copy.toString());lib=copy;selected=0;libraryVersion++}.onFailure{message=it.message?:"Ошибка"}}
 private fun startTunnel(){startForegroundService(Intent(this,XLinesService::class.java).putExtra("index",selected).putExtra("server",lib.optInt("selected",0)))}
 private fun toggle(){if(TunnelState.active){startService(Intent(this,XLinesService::class.java).setAction("STOP"));return};if(doc==null){message="Профилей нет. Сканируйте QR или импортируйте .xlines";return};val intent=VpnService.prepare(this);if(intent==null)startTunnel()else permission.launch(intent)}
 @Composable private fun Screen(){
  val accent=Color(0xFF0DDFB9);val bg=Color(0xFF08171C);val card=Color(0xFF12272D)
  var expanded by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)}
  var sheet by remember{mutableStateOf(false)};var addChoice by remember{mutableStateOf(false)};var renaming by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf(false)}
  val servers=lib.getJSONArray("servers");val current=lib.optInt("selected",0);val hasServers=servers.length()>0
  MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=bg,surface=card)){
   Surface(Modifier.fillMaxSize(),color=bg){Box(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp).statusBarsPadding().navigationBarsPadding(),horizontalAlignment=Alignment.CenterHorizontally){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){DiamondIcon(accent,26.dp);Spacer(Modifier.width(8.dp));Text("XLines",fontSize=23.sp,fontWeight=FontWeight.Bold,color=accent)};TextButton(onClick={settings=true}){GearIcon(accent,28.dp)}}
    Spacer(Modifier.height(24.dp))
    Text(if(doc!=null)"VLESS + REALITY" else "Импортируйте подключение",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFFF0F6F6))
    Spacer(Modifier.height(38.dp))
    Button(onClick={toggle()},shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF0B2C28)),modifier=Modifier.size(150.dp).border(3.dp,accent,CircleShape)){PowerIcon(accent,76.dp)}
    Spacer(Modifier.height(22.dp));Text(TunnelState.status,fontSize=23.sp,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(10.dp));Text("Внешний IP: ${TunnelState.ip}",color=Color(0xFFB1C4CD))
    Spacer(Modifier.height(38.dp))
    Row(Modifier.fillMaxWidth().background(card,RoundedCornerShape(12.dp)).padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Row(verticalAlignment=Alignment.CenterVertically){ArrowIcon(true,accent);Spacer(Modifier.width(6.dp));Text("${TunnelState.down} Мбит/с")};Row(verticalAlignment=Alignment.CenterVertically){ArrowIcon(false,accent);Spacer(Modifier.width(6.dp));Text("${TunnelState.up} Мбит/с")}}
    Spacer(Modifier.height(22.dp));Button(onClick={scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false))},enabled=!TunnelState.active,modifier=Modifier.fillMaxWidth()){Text("Сканировать QR")}
    OutlinedButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!TunnelState.active,modifier=Modifier.fillMaxWidth()){Text("Импорт файла .xlines")}
    if(message.isNotEmpty())Text(message,color=accent,fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
    Text("Профиль хранится зашифрованным на устройстве",fontSize=11.sp,color=Color.Gray,modifier=Modifier.padding(top=12.dp))
    Spacer(Modifier.height(if(sheet)330.dp else 120.dp))
   }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xFF1F1F22),RoundedCornerShape(topStart=16.dp,topEnd=16.dp)).navigationBarsPadding().padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){
     Column(Modifier.fillMaxWidth().clickable{sheet=!sheet},horizontalAlignment=Alignment.CenterHorizontally){
      Box(Modifier.width(36.dp).height(4.dp).background(Color(0xFF5A5A60),RoundedCornerShape(2.dp)));Spacer(Modifier.height(8.dp))
      Row(verticalAlignment=Alignment.CenterVertically){Text(if(hasServers)Library.name(lib,current) else "Нет серверов",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(6.dp));ChevronIcon(sheet,Color(0xFFB1C4CD),18.dp)}
      Text(if(hasServers)(if(TunnelState.active)"• " else "")+Library.host(doc!!)+" · "+(doc!!.getJSONArray("connections").let{a->(0 until a.length()).joinToString(" · "){i->Transfer.protocol(a.getJSONObject(i)).substringBefore(" +")}}) else "Добавьте сервер или профиль",fontSize=12.sp,color=Color(0xFF9A9AA0))
     }
     if(sheet){
      Spacer(Modifier.height(10.dp))
      for(i in 0 until servers.length()){
       val d=servers.getJSONObject(i).getJSONObject("doc")
       Row(Modifier.fillMaxWidth().padding(vertical=3.dp).background(Color(0xFF2A2A2E),RoundedCornerShape(9.dp)).border(1.dp,if(i==current)accent else Color(0xFF2A2A2E),RoundedCornerShape(9.dp)).clickable{selectServer(i);sheet=false}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(servers.getJSONObject(i).getString("name"),fontWeight=FontWeight.SemiBold);Text(Library.host(d),fontSize=11.sp,color=Color(0xFF9A9AA0))}
        if(i==current)CheckIcon(accent,20.dp)
       }
      }
      OutlinedButton(onClick={sheet=false;addChoice=true},enabled=!TunnelState.active,modifier=Modifier.fillMaxWidth().padding(top=6.dp)){Text("+ Добавить сервер",color=accent)}
      if(hasServers)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){TextButton(onClick={renaming=true},enabled=!TunnelState.active){Text("Переименовать")};TextButton(onClick={deleting=true},enabled=!TunnelState.active){Text("Удалить")}}
     }
    }
   }
   if(addChoice)AlertDialog(onDismissRequest={addChoice=false},title={Text("Добавить сервер")},text={Text("Отсканируйте QR или откройте файл .xlines, который вам передали.")},
    confirmButton={TextButton(onClick={addChoice=false;scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false))}){Text("Сканировать QR")}},
    dismissButton={TextButton(onClick={addChoice=false;picker.launch(arrayOf("*/*"))}){Text("Файл .xlines")}})
   pending?.let{p->var name by remember(p){mutableStateOf(p.getString("name"))}
    AlertDialog(onDismissRequest={finishImport(p.getString("name"))},title={Text("Название подключения")},text={Column{Text(Library.host(p)+"\nТак сервер будет называться в списке.");OutlinedTextField(value=name,onValueChange={name=it.take(60)},singleLine=true)}},
     confirmButton={TextButton(onClick={finishImport(name)}){Text("Сохранить")}},dismissButton={TextButton(onClick={finishImport(p.getString("name"))}){Text("Пропустить")}})}
   if(renaming&&hasServers){var name by remember{mutableStateOf(Library.name(lib,current))}
    AlertDialog(onDismissRequest={renaming=false},title={Text("Название подключения")},text={OutlinedTextField(value=name,onValueChange={name=it.take(60)},singleLine=true)},
     confirmButton={TextButton(onClick={renaming=false;renameServer(current,name)}){Text("Сохранить")}},dismissButton={TextButton(onClick={renaming=false}){Text("Отмена")}})}
   if(deleting&&hasServers)AlertDialog(onDismissRequest={deleting=false},title={Text("Удалить сервер?")},text={Text("«"+Library.name(lib,current)+"» будет убран из приложения. Сам сервер не меняется.")},
    confirmButton={TextButton(onClick={deleting=false;deleteServer(current)}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=false}){Text("Отмена")}})
   }
   if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Настройки и диагностика")},text={Text("Полный туннель через системную службу Android. Смена протокола доступна после отключения.\n\n${TunnelState.diagnostic.ifEmpty{"Ошибок текущего сеанса нет"}}\n\nДля защиты от обхода туннеля используйте системную настройку Android «Блокировать соединения без туннеля» после проверки устройства.")},confirmButton={TextButton(onClick={settings=false}){Text("Закрыть")}})
  }
 }
}
