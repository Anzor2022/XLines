package com.xlines.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object TunnelState {
 var status by mutableStateOf("Не подключено")
 var active by mutableStateOf(false)
 var ip by mutableStateOf("—")
 var down by mutableStateOf("—")
 var up by mutableStateOf("—")
 var diagnostic by mutableStateOf("")
}
class XLinesService:VpnService() {
 private val worker=Executors.newSingleThreadExecutor();private val main=Handler(Looper.getMainLooper())
 private var bridge:NativeBridge?=null
 @Volatile private var stopping=false
 @Volatile private var connection:HttpURLConnection?=null
 private val monitor=Executors.newSingleThreadScheduledExecutor()
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP"){requestStop();return START_NOT_STICKY}
  if(bridge!=null || TunnelState.active)return START_NOT_STICKY
  stopping=false;TunnelState.active=true;TunnelState.status="Подключение…"
  val manager=getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("proxy","XLines",NotificationManager.IMPORTANCE_LOW))
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,1,Intent(this,XLinesService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
  startForeground(1,Notification.Builder(this,"proxy").setSmallIcon(R.drawable.ic_xlines).setContentTitle("XLines").setContentText("Подключено").setContentIntent(open).addAction(Notification.Action.Builder(null,"Отключить",stop).build()).setOngoing(true).build())
  val index=intent?.getIntExtra("index",0)?:0;val server=intent?.getIntExtra("server",0)?:0
  worker.execute {
   try {
    val library=Library.parse(Secrets(this).load()?:error("Импортируйте подключение"));val doc=Library.doc(library,server)?:error("Сервер не выбран")
    bridge=NativeBridge(this);bridge!!.start(Transfer.configuration(doc,index))
    if(stopping)return@execute
    main.post{TunnelState.status="Туннель запущен; проверка связи…"}
    var oldRx=TrafficStats.getUidRxBytes(android.os.Process.myUid());var oldTx=TrafficStats.getUidTxBytes(android.os.Process.myUid());var oldTime=System.nanoTime()
    monitor.scheduleWithFixedDelay({
     if(stopping)return@scheduleWithFixedDelay
     try {
      val c=URL("https://api.ipify.org").openConnection() as HttpURLConnection;connection=c;c.connectTimeout=6000;c.readTimeout=6000
      val ip=c.inputStream.bufferedReader().use{it.readText()}.trim();require(ip.length<=64 && ip.contains(Regex("[0-9]")))
      if(!stopping)main.post{TunnelState.ip=ip;TunnelState.status="Подключено"};c.disconnect()
     } catch(e:Exception){if(!stopping)main.post{TunnelState.status="Туннель запущен · проверьте интернет";TunnelState.diagnostic=e.cause?.message?:e.message?:"Ошибка проверки"}}
     finally { connection?.disconnect();connection=null }
     val rx=TrafficStats.getUidRxBytes(android.os.Process.myUid());val tx=TrafficStats.getUidTxBytes(android.os.Process.myUid());val now=System.nanoTime();val seconds=(now-oldTime)/1e9
     if(!stopping)main.post{TunnelState.down="%.2f".format((rx-oldRx).coerceAtLeast(0)*8/seconds/1e6);TunnelState.up="%.2f".format((tx-oldTx).coerceAtLeast(0)*8/seconds/1e6)}
     oldRx=rx;oldTx=tx;oldTime=now
    },0,15,java.util.concurrent.TimeUnit.SECONDS)
   } catch(e:Exception){main.post{TunnelState.diagnostic=(e.cause?.message?:e.message?:e.toString());TunnelState.status="Ошибка подключения"};requestStop(true)}
  }
  return START_NOT_STICKY
 }
 fun requestStop(error:Boolean=false) {
  if(stopping)return
  stopping=true;connection?.disconnect();monitor.shutdownNow()
  worker.execute{bridge?.close();bridge=null;main.post{TunnelState.active=false;if(!error)TunnelState.status="Не подключено";TunnelState.ip="—";TunnelState.down="—";TunnelState.up="—";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}
 }
 override fun onRevoke(){requestStop()}
 override fun onDestroy(){if(!stopping)requestStop();worker.shutdown();super.onDestroy()}
}
