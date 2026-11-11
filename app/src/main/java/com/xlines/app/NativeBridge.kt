package com.xlines.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import java.lang.reflect.Proxy
import java.net.NetworkInterface

/** Adapter for the pinned libbox v1.14.4 Go binding; unsupported platform features fail explicitly. */
class NativeBridge(private val service:XLinesService) {
 private val prefix="io.nekohasekai.libbox."
 private var server:Any?=null
 private var tunnel:ParcelFileDescriptor?=null
 private var callback:ConnectivityManager.NetworkCallback?=null
 private var listener:Any?=null
 private val cm=service.getSystemService(ConnectivityManager::class.java)
 private fun type(name:String)=Class.forName(prefix+name)
 private fun call(target:Any,name:String,vararg args:Any?):Any? {
  val method=target.javaClass.methods.firstOrNull { it.name.equals(name,true) && it.parameterCount==args.size }?:throw UnsupportedOperationException(name)
  method.isAccessible=true;return method.invoke(target,*args)
 }
 private fun setter(target:Any,name:String,value:Any) {call(target,"set$name",value)}
 private fun static(name:String,vararg args:Any?):Any? = type("Libbox").methods.first{it.name.equals(name,true)&&it.parameterCount==args.size}.invoke(null,*args)
 private fun proxy(name:String,fn:(String,Array<out Any?>)->Any?):Any {
  val cls=type(name)
  return Proxy.newProxyInstance(cls.classLoader,arrayOf(cls)){ p,m,a -> when(m.name){"toString"->"XLines $name";"hashCode"->System.identityHashCode(p);"equals"->p===a?.firstOrNull();else->fn(m.name,a?:emptyArray())} }
 }
 private fun iterator(name:String,values:List<Any>):Any {
  var at=0
  return proxy(name){method,_->when(method){"hasNext"->at<values.size;"next"->values[at++];else->throw UnsupportedOperationException(method)}}
 }
 private fun items(iter:Any?):List<Any> {
  if(iter==null)return emptyList()
  val out=mutableListOf<Any>();while(call(iter,"hasNext")==true)call(iter,"next")?.let{out.add(it)}
  return out
 }
 private fun underlying():Network?=cm.allNetworks.firstOrNull { n->cm.getNetworkCapabilities(n)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}==true }
 private fun update() {
  val net=underlying();val link=net?.let{cm.getLinkProperties(it)};val name=link?.interfaceName?:""
  val index=if(name.isEmpty())-1 else NetworkInterface.getByName(name)?.index?:-1
  listener?.let{call(it,"updateDefaultInterface",name,index,false,false)}
  service.setUnderlyingNetworks(net?.let{arrayOf(it)})
 }
 private fun interfaces():Any {
  val networks=cm.allNetworks.mapNotNull{ n->cm.getLinkProperties(n)?.let{it.interfaceName to (n to it)}}.toMap()
  val values=java.util.Collections.list(NetworkInterface.getNetworkInterfaces()).mapNotNull { n ->
   if(n.interfaceAddresses.any{it.address.hostAddress=="172.28.0.1"})return@mapNotNull null
   val obj=type("NetworkInterface").getDeclaredConstructor().newInstance()
   setter(obj,"Index",n.index);setter(obj,"MTU",n.mtu);setter(obj,"Name",n.name)
   setter(obj,"Addresses",iterator("StringIterator",n.interfaceAddresses.mapNotNull{a->a.address.hostAddress?.substringBefore('%')?.let{"$it/${a.networkPrefixLength}"}}))
   var flags=0;if(n.isUp)flags=flags or 1 or 64;if(n.isLoopback)flags=flags or 8;if(n.supportsMulticast())flags=flags or 4096
   setter(obj,"Flags",flags)
   val pair=networks[n.name];val caps=pair?.let{cm.getNetworkCapabilities(it.first)}
   val kind=when{caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true->0;caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)==true->1;caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)==true->2;else->3}
   setter(obj,"Type",kind);setter(obj,"DNSServer",iterator("StringIterator",pair?.second?.dnsServers?.mapNotNull{it.hostAddress}?:emptyList()))
   setter(obj,"Gateway",iterator("StringIterator",pair?.second?.routes?.mapNotNull{it.gateway?.hostAddress}?:emptyList()))
   setter(obj,"Metered",false);obj
  }
  return iterator("NetworkInterfaceIterator",values)
 }
 private fun openTun(options:Any):Int {
  val builder=service.Builder().setSession("XLines").setMtu((call(options,"getMTU") as Number).toInt()).setBlocking(false)
  for(name in listOf("getInet4Address","getInet6Address"))for(p in items(call(options,name)))builder.addAddress(call(p,"address") as String,(call(p,"prefix") as Number).toInt())
  for(ip in items(call(options,"getDNSServerAddress")))builder.addDnsServer(ip as String)
  builder.addRoute("0.0.0.0",0);builder.addRoute("::",0)
  tunnel=builder.establish()?:throw IllegalStateException("Разрешение не выдано")
  return tunnel!!.fd
 }
 fun start(config:String) {
  val setup=type("SetupOptions").getDeclaredConstructor().newInstance()
  setter(setup,"BasePath",service.filesDir.absolutePath);setter(setup,"WorkingPath",service.filesDir.absolutePath);setter(setup,"TempPath",service.cacheDir.absolutePath)
  setter(setup,"LogMaxLines",300L);setter(setup,"FixAndroidStack",true);static("setup",setup)
  val platform=proxy("PlatformInterface"){method,args->when(method){
   "localDNSTransport"->null
   "usePlatformAutoDetectInterfaceControl"->true
   "autoDetectInterfaceControl"->{val fd=(args[0] as Number).toInt();require(service.protect(fd)){"Не удалось исключить сокет ядра из VPN"};null}
   "openTun"->openTun(args[0]!!)
   "startDefaultInterfaceMonitor"->{listener=args[0];update();val cb=object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){update()};override fun onLost(network:Network){update()};override fun onLinkPropertiesChanged(network:Network,properties:android.net.LinkProperties){update()}};callback=cb;cm.registerDefaultNetworkCallback(cb);null}
   "closeDefaultInterfaceMonitor"->{callback?.let{runCatching{cm.unregisterNetworkCallback(it)}};callback=null;listener=null;null}
   "getInterfaces"->interfaces()
   "underNetworkExtension","usePlatformShell","usePlatformBridge"->false
   "includeAllNetworks"->true
   "readWIFIState"->null
   "clearDNSCache","sendNotification","cancelNotification","registerMyInterface","closeNeighborMonitor"->null
   "startNeighborMonitor"->{args[0]?.let{call(it,"updateNeighborTable",iterator("NeighborEntryIterator",emptyList()))};null}
   "tailscaleHostname"->"xlines-android"
   else->throw UnsupportedOperationException("Не поддерживается Android platform: $method")
  }}
  val handler=proxy("CommandServerHandler"){method,_->when(method){
   "serviceStop"->{service.requestStop();null}
   "serviceReload"->null
   "getSystemProxyStatus"->type("SystemProxyStatus").getDeclaredConstructor().newInstance()
   "setSystemProxyEnabled","writeDebugMessage"->null
   else->throw UnsupportedOperationException(method)
  }}
  server=static("newCommandServer",handler,platform)
  call(server!!,"start")
  val override=type("OverrideOptions").getDeclaredConstructor().newInstance()
  call(server!!,"startOrReloadService",config,override)
 }
 fun close() {
  server?.let{runCatching{call(it,"closeService")};runCatching{call(it,"close")}};server=null
  callback?.let{runCatching{cm.unregisterNetworkCallback(it)}};callback=null;listener=null
  tunnel?.close();tunnel=null
 }
}
