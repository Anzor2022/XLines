package com.xlines.app

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 private fun scanOptions()=ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false).setPrompt("Наведите камеру на QR-код")
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
  val accent=Palette.accent
  var settings by remember{mutableStateOf(false)}
  var sheet by remember{mutableStateOf(false)};var addChoice by remember{mutableStateOf(false)};var renaming by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf(false)}
  val servers=lib.getJSONArray("servers");val current=lib.optInt("selected",0);val hasServers=servers.length()>0
  val conn=connState(TunnelState.status,TunnelState.active)
  BackHandler(enabled=sheet){sheet=false}
  MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=Palette.bg,surface=Palette.card)){
   Surface(Modifier.fillMaxSize(),color=Palette.bg){
    @Composable fun Brand(){Row(verticalAlignment=Alignment.CenterVertically){DiamondIcon(accent,26.dp);Spacer(Modifier.width(8.dp));Text("XLines",fontSize=23.sp,fontWeight=FontWeight.Bold,color=accent)}}
    @Composable fun Gear(){TextButton(onClick={settings=true},modifier=Modifier.semantics{contentDescription="Настройки и диагностика"}){GearIcon(accent,28.dp)}}
    @Composable fun ServerList(){
     Column(Modifier.fillMaxWidth()){
      for(i in 0 until servers.length()){
       val d=servers.getJSONObject(i).getJSONObject("doc")
       Row(Modifier.fillMaxWidth().padding(vertical=3.dp).background(Palette.row,RoundedCornerShape(9.dp)).border(1.dp,if(i==current)accent else Palette.row,RoundedCornerShape(9.dp)).clickable(role=Role.Button){selectServer(i);sheet=false}.semantics{selected=(i==current)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(servers.getJSONObject(i).getString("name"),fontWeight=FontWeight.SemiBold);Text(Library.host(d),fontSize=12.sp,color=Palette.dim)}
        if(i==current)CheckIcon(accent,20.dp)
       }
      }
      OutlinedButton(onClick={sheet=false;addChoice=true},enabled=!TunnelState.active,border=BorderStroke(1.dp,if(!TunnelState.active)accent else Palette.dim),colors=ButtonDefaults.outlinedButtonColors(contentColor=accent),modifier=Modifier.fillMaxWidth().padding(top=6.dp)){Text("+ Добавить сервер")}
      if(hasServers)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){TextButton(onClick={renaming=true},enabled=!TunnelState.active){Text("Переименовать")};TextButton(onClick={deleting=true},enabled=!TunnelState.active){Text("Удалить",color=if(!TunnelState.active)Palette.error else Palette.dim)}}
     }
    }
    @Composable fun Controls(){
     Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
      Text(if(doc!=null)"VLESS + REALITY" else "Импортируйте подключение",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Palette.text)
      Spacer(Modifier.height(38.dp))
      val ring=when(conn){ConnState.CONNECTING->Palette.ringDim;ConnState.ERROR->Palette.error;else->accent}
      val fill=when(conn){ConnState.CONNECTED->accent;ConnState.ERROR->Palette.errorBg;else->Palette.powerBg}
      val icon=when(conn){ConnState.CONNECTED->Palette.onAccent;ConnState.ERROR->Palette.error;ConnState.CONNECTING->Palette.iconBusy;ConnState.IDLE->accent}
      Box(Modifier.size(150.dp),contentAlignment=Alignment.Center){
       Button(onClick={if(!tapBlocked(TunnelState.status,TunnelState.active))toggle()},shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=fill),contentPadding=PaddingValues(0.dp),modifier=Modifier.size(150.dp).border(3.dp,ring,CircleShape).semantics{contentDescription=if(TunnelState.active)"Отключить" else "Подключить";stateDescription=TunnelState.status}){PowerIcon(icon,76.dp)}
       if(conn==ConnState.CONNECTING)CircularProgressIndicator(Modifier.size(150.dp),color=Palette.ringLight,strokeWidth=3.dp)
      }
      Spacer(Modifier.height(22.dp));Text(TunnelState.status,fontSize=23.sp,fontWeight=FontWeight.Bold,color=if(conn==ConnState.ERROR)Palette.error else Palette.text,textAlign=TextAlign.Center,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
      Spacer(Modifier.height(10.dp));Text("Внешний IP: ${TunnelState.ip}",color=Palette.muted)
      Spacer(Modifier.height(38.dp))
      Row(Modifier.fillMaxWidth().background(Palette.card,RoundedCornerShape(12.dp)).padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Row(verticalAlignment=Alignment.CenterVertically){ArrowIcon(true,accent);Spacer(Modifier.width(6.dp));Text("${TunnelState.down} Мбит/с")};Row(verticalAlignment=Alignment.CenterVertically){ArrowIcon(false,accent);Spacer(Modifier.width(6.dp));Text("${TunnelState.up} Мбит/с")}}
      Spacer(Modifier.height(22.dp));Button(onClick={scanner.launch(scanOptions())},enabled=!TunnelState.active,modifier=Modifier.fillMaxWidth()){Text("Сканировать QR")}
      OutlinedButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!TunnelState.active,border=BorderStroke(1.dp,if(!TunnelState.active)accent else Palette.dim),colors=ButtonDefaults.outlinedButtonColors(contentColor=accent),modifier=Modifier.fillMaxWidth()){Text("Импорт файла .xlines")}
      if(message.isNotEmpty())Text(message,color=if(isSuccessMessage(message))accent else Palette.error,fontSize=13.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=12.dp).semantics{liveRegion=LiveRegionMode.Polite})
      Text("Профиль хранится зашифрованным на устройстве",fontSize=12.sp,color=Palette.dim,textAlign=TextAlign.Center,modifier=Modifier.padding(top=12.dp))
     }
    }
    BoxWithConstraints(Modifier.fillMaxSize()){
     if(maxWidth>=840.dp){
      Row(Modifier.fillMaxSize()){
       Column(Modifier.width(300.dp).fillMaxHeight().background(Palette.sheet).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(16.dp)){
        Brand();Spacer(Modifier.height(20.dp));Text("Серверы",fontSize=12.sp,color=Palette.dim);Spacer(Modifier.height(6.dp))
        if(!hasServers)Text("Нет серверов",color=Palette.dim)
        ServerList()
       }
       Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.TopCenter){
        Column(Modifier.widthIn(max=460.dp).fillMaxHeight().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
         Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){Gear()};Spacer(Modifier.height(16.dp));Controls()
        }
       }
      }
     } else {
      Box(Modifier.fillMaxSize()){
       Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp).statusBarsPadding().navigationBarsPadding(),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Brand()};Gear()}
        Spacer(Modifier.height(24.dp))
        Controls()
        Spacer(Modifier.height(if(sheet)330.dp else 120.dp))
       }
       Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Palette.sheet,RoundedCornerShape(topStart=16.dp,topEnd=16.dp)).navigationBarsPadding().padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Column(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel=if(sheet)"Скрыть список серверов" else "Показать список серверов"){sheet=!sheet},horizontalAlignment=Alignment.CenterHorizontally){
         Box(Modifier.width(36.dp).height(4.dp).background(Palette.handle,RoundedCornerShape(2.dp)));Spacer(Modifier.height(8.dp))
         Row(verticalAlignment=Alignment.CenterVertically){Text(if(hasServers)Library.name(lib,current) else "Нет серверов",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(6.dp));ChevronIcon(sheet,Palette.muted,18.dp)}
         Text(if(hasServers)(if(TunnelState.active)"• " else "")+Library.host(doc!!)+" · "+(doc!!.getJSONArray("connections").let{a->(0 until a.length()).joinToString(" · "){i->Transfer.protocol(a.getJSONObject(i)).substringBefore(" +")}}) else "Добавьте сервер или профиль",fontSize=12.sp,color=Palette.dim)
        }
        if(sheet){Spacer(Modifier.height(10.dp));ServerList()}
       }
      }
     }
    }
   if(addChoice)AlertDialog(onDismissRequest={addChoice=false},title={Text("Добавить сервер")},text={Text("Отсканируйте QR или откройте файл .xlines, который вам передали.")},
    confirmButton={TextButton(onClick={addChoice=false;scanner.launch(scanOptions())}){Text("Сканировать QR")}},
    dismissButton={TextButton(onClick={addChoice=false;picker.launch(arrayOf("*/*"))}){Text("Файл .xlines")}})
   pending?.let{p->var name by remember(p){mutableStateOf(p.getString("name"))}
    AlertDialog(onDismissRequest={finishImport(p.getString("name"))},title={Text("Название подключения")},text={Column{Text(Library.host(p)+"\nТак сервер будет называться в списке.");OutlinedTextField(value=name,onValueChange={name=it.take(60)},label={Text("Название")},singleLine=true)}},
     confirmButton={TextButton(onClick={finishImport(name)}){Text("Сохранить")}},dismissButton={TextButton(onClick={finishImport(p.getString("name"))}){Text("Пропустить")}})}
   if(renaming&&hasServers){var name by remember{mutableStateOf(Library.name(lib,current))}
    AlertDialog(onDismissRequest={renaming=false},title={Text("Название подключения")},text={OutlinedTextField(value=name,onValueChange={name=it.take(60)},label={Text("Название")},singleLine=true)},
     confirmButton={TextButton(onClick={renaming=false;renameServer(current,name)}){Text("Сохранить")}},dismissButton={TextButton(onClick={renaming=false}){Text("Отмена")}})}
   if(deleting&&hasServers)AlertDialog(onDismissRequest={deleting=false},title={Text("Удалить сервер?")},text={Text("«"+Library.name(lib,current)+"» будет убран из приложения. Сам сервер не меняется.")},
    confirmButton={TextButton(onClick={deleting=false;deleteServer(current)}){Text("Удалить",color=Palette.error)}},dismissButton={TextButton(onClick={deleting=false}){Text("Отмена")}})
   }
   if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Настройки и диагностика")},text={Text("Полный туннель через системную службу Android. Смена протокола доступна после отключения.\n\n${TunnelState.diagnostic.ifEmpty{"Ошибок текущего сеанса нет"}}\n\nДля защиты от обхода туннеля используйте системную настройку Android «Блокировать соединения без туннеля» после проверки устройства.")},confirmButton={TextButton(onClick={settings=false}){Text("Закрыть")}})
  }
 }
}
