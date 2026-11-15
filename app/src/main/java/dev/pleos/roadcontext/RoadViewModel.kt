package dev.pleos.roadcontext

import android.app.Application
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import dev.pleos.roadcontext.data.*
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import dev.pleos.roadcontext.core.*
import dev.pleos.roadcontext.integration.*
import org.json.JSONObject

/** UI owns no matching rules. Synthetic travel always runs through the same pure engine. */
class RoadViewModel(application: Application, savedStateHandle: SavedStateHandle = SavedStateHandle()) : AndroidViewModel(application) {
 private val permissionGate = PermissionRequestGate(savedStateHandle)
 var ui by mutableStateOf(RoadUiState(setup=permissionGate.pending,permissionRequestPending=permissionGate.pending))
  private set
 private val engine = GuidanceEngine(syntheticSegment())
 private val writer=BoundedDiagnosticWriter(application.filesDir,onFailure={Log.w("RoadContext","Diagnostic write failed",it)})
 private val catalog=try { CatalogLoader().load(application.assets.open("catalog.json").bufferedReader().use {it.readText()},System.currentTimeMillis()) }
  catch(e:Exception) { CatalogLoadResult.Rejected(listOf("catalog_unavailable")) }
 private val liveGuidance=LiveGuidanceCoordinator(catalog)
 private var sdkReplay:SdkRoadReplaySession?=null
 private var lastReplayDecision:String?=null
 private val handler=Handler(Looper.getMainLooper())
 private val locationContext=SdkLocationContext()
 internal val currentRouteGeneration:Long get()=locationContext.routeGeneration
 private var liveInputAvailable=true
 private var locationUnsupported=false
 private val ticker=object:Runnable {
  override fun run() {
   if(!started) return
   if(!ui.simulation && !ui.setup) {
    val replay=sdkReplay
    if(replay!=null) presentSdkReplay(replay.gate.tick(SystemClock.elapsedRealtime()), "tick")
    else presentLive(liveGuidance.tick(SystemClock.elapsedRealtime(),System.currentTimeMillis()))
   }
   handler.postDelayed(this,1000)
  }
 }
 private var reader: PleosNavigationReader? = null
 private var demoIndex = -1
 private var demoTime = 0L
 private val positions = listOf(0.0,100.0,200.0,300.0,450.0,600.0,750.0,900.0)
 private var started = false

 fun start() {
  if(!started) { handler.postDelayed(ticker,1000);locationContext.refreshClockOffset() }
  started=true
  liveGuidance.setAvailable(true);liveInputAvailable=true
  val granted=hasLocationPermission()
  // A grant may arrive while stopped or through settings. Reconcile from the real platform;
  // DENIED alone must not release an outstanding OS dialog waiting for the user's response.
  if(granted && permissionGate.pending) { permissionGate.complete(::showPermissionPending);ui=ui.copy(setup=false) }
  if(!ui.simulation && !granted) {
   ui=ui.copy(setup=true,phase="처음 사용하기",title="주변 도로를 알려드리려면",message="차량 위치 사용을 허용해 주세요.",detail="다음 시스템 화면의 허용은 위치 읽기 권한입니다. 차량을 제어하거나 위치를 서버로 전송하지 않습니다.")
   return
  }
  if(!ui.simulation && reader == null) {
   ui=ui.copy(setup=false)
   locationUnsupported=false
   reader=PleosNavigationReader(getApplication()) { consume(it) }
   reader?.start()
  }
 }
 private fun hasLocationPermission() = getApplication<Application>().checkSelfPermission("pleos.car.permission.FUSED_LOCATION") == android.content.pm.PackageManager.PERMISSION_GRANTED
 private fun showPermissionPending(pending:Boolean) { ui=ui.copy(permissionRequestPending=pending) }
 fun requestLocationPermission(launch:()->Unit):Boolean {
  if(permissionGate.pending || !ui.setup || !ui.settingsAllowed || ui.simulation) return false
  if(hasLocationPermission()) { permissionResult(true);return false }
  return try { permissionGate.launch(::showPermissionPending,launch) }
  catch(failure:Exception) {
   // Gate already released ownership. Keep the existing setup controls available for retry.
   ui=ui.copy(phase="위치 권한 필요",title="권한 요청을 열지 못했습니다",message="잠시 후 다시 시도해 주세요.",detail="위치 사용 허용 버튼으로 다시 요청할 수 있습니다.")
   Log.w("RoadContext","Permission request launch failed",failure)
   false
  }
 }
 fun permissionResult(granted:Boolean) {
  val wasPending=permissionGate.complete(::showPermissionPending)
  // An old result must not replace an explicitly selected synthetic scene or restart an already
  // reconciled live reader. Setup without pending remains valid for the already-granted shortcut.
  if(ui.simulation || (!wasPending && !ui.setup)) return
  if(granted) { ui=ui.copy(setup=false);reader?.close();reader=null;if(started && !ui.simulation) start() }
  else ui=ui.copy(phase="위치 권한 필요",title="위치 정보 사용이 필요합니다",message="주변 도로 구조를 안내하기 위한 권한입니다.",detail="정차 중 앱 권한 설정에서 허용할 수 있습니다.")
 }
 fun stop() {
  started=false
  handler.removeCallbacks(ticker)
  ui=ui.copy(playing=false)
  sdkReplay?.let { it.reset();ui=ui.copy(sdkReplay=it.visual(it.gate.tick(SystemClock.elapsedRealtime())));lastReplayDecision=null }
  // A paused synthetic scene has no freshness claim. Live guidance must be reacquired.
  if(!ui.simulation) clearPresentation()
  liveGuidance.setAvailable(false)
  reader?.close();reader=null
 }
 fun setRestrictions(known:Boolean, requiresDO:Boolean, flags:Int) {
  val allow = known && !requiresDO && flags == 0
  ui=ui.copy(settingsAllowed=allow,playing=ui.playing && allow)
 }
 fun demo() {
  if(!ui.settingsAllowed || permissionGate.pending) return
  sdkReplay=null
  ui=ui.copy(sdkReplay=null,clusterDemo=false)
  reader?.close(); reader=null
  engine.reset(InputSource.REPLAY_SYNTHETIC); demoIndex=-1;demoTime=0
  ui=ui.copy(setup=false,inputSource=DiagramSource.REPLAY_SYNTHETIC,diagramModel=null,simulation=true,playing=false,sourceLabel="가상 구간 시연",phase="시연 준비",title="진입로를 미리 살펴보세요",message="차로가 이어지는 구간과 합류 지점을 설명합니다.",detail="실제 장소가 아닌 가상 도로입니다",showDiagram=false,distance=null)
 }
 fun live() {
  if(!ui.settingsAllowed || permissionGate.pending) return
  sdkReplay=null
  engine.reset(InputSource.REAL_SDK)
  ui=RoadUiState(inputSource=DiagramSource.REAL_SDK,settingsAllowed=ui.settingsAllowed,dark=ui.dark)
  locationContext.resetLiveSession()
  if(started) start()
 }
 fun clusterDemo(profile:String? = null):Boolean {
  if(!BuildConfig.DEBUG || !ui.settingsAllowed || permissionGate.pending) return false
  sdkReplay=null;lastReplayDecision=null
  reader?.close();reader=null
  engine.reset(InputSource.REPLAY_SYNTHETIC)
  ui=RoadUiState(settingsAllowed=ui.settingsAllowed,dark=ui.dark,simulation=true,
   playing=true,clusterDemo=true,clusterDemoProfile=profile,inputSource=DiagramSource.REPLAY_SYNTHETIC,
   sourceLabel="가상 도로 시연",phase="도로 상황 데모")
  return true
 }
 fun sdkRoadReplay(profile:String = sdkReplay?.profile ?: "yanghwa"):Boolean {
  if(!BuildConfig.DEBUG || !isEmulator() || !ui.settingsAllowed || permissionGate.pending || !hasLocationPermission()) return false
  val session=runCatching { SdkRoadReplaySession(getApplication(), profile) }.getOrElse {
   Log.e("RoadContext","GPS replay reference unavailable",it);return false
  }
  reader?.close();reader=null
  sdkReplay=session;lastReplayDecision=null
  locationContext.refreshClockOffset()
  ui=RoadUiState(settingsAllowed=ui.settingsAllowed,dark=ui.dark,inputSource=DiagramSource.REAL_SDK,
   sourceLabel="실도로 GPS 시험 · Pleos SDK",sdkReplay=session.visual(session.gate.tick(SystemClock.elapsedRealtime())))
  record("gps_replay_session",JSONObject().put("route",session.road.id).put("inputOrigin","EMULATOR_GPS")
   .put("sdkProvider","FUSED_LOCATION").put("productionCatalog",false))
  if(started) start()
  return true
 }
 private fun isEmulator()=android.os.Build.HARDWARE in listOf("ranchu","goldfish") || android.os.Build.FINGERPRINT.startsWith("generic")
 private fun presentSdkReplay(result:SdkRouteReplayResult, trigger:String) {
  val replay=sdkReplay ?: return
  ui=ui.copy(sdkReplay=replay.visual(result),showDiagram=false,diagramModel=null,playing=false,
   phase=when(result.state) { SdkRouteReplayState.GUIDANCE -> "본선 연결 구간 접근";SdkRouteReplayState.PASSED -> "구간 통과";else -> "위치 입력 대기" },distance=null)
  val visual=ui.sdkReplay!!
  val endProgress=if (visual.laneEndFromMeters!=null && visual.laneEndToMeters!=null && visual.tunnelExitMeters!=null)
   visual.alongMeters?.let { LaneEndZone(visual.laneEndFromMeters,visual.laneEndToMeters,visual.tunnelExitMeters).at(it) } else null
  val secondProgress=if(visual.secondLaneEndFromMeters!=null && visual.secondLaneEndToMeters!=null && visual.tunnelExitMeters!=null)
   visual.alongMeters?.let { LaneEndZone(visual.secondLaneEndFromMeters,visual.secondLaneEndToMeters,visual.tunnelExitMeters).at(it) } else null
  val stateKey="${result.state}:${result.visible}"
  if(trigger=="sdk" || stateKey!=lastReplayDecision) record("gps_replay_decision",JSONObject()
   .put("state",result.state.name).put("visible",result.visible).put("trigger",trigger)
   .put("route",replay.road.id).put("receivedCount",replay.count)
   .put("alongMeters",visual.alongMeters).put("latitude",replay.lastPoint?.latitude)
   .put("laneEndNearMeters",endProgress?.nearMeters).put("laneEndFarMeters",endProgress?.farMeters)
   .put("secondLaneEndNearMeters",secondProgress?.nearMeters).put("secondLaneEndFarMeters",secondProgress?.farMeters)
   .put("secondLaneEndPhase",secondProgress?.phase?.name).put("secondLaneEndLabel",secondProgress?.label)
   .put("laneEndPhase",endProgress?.phase?.name).put("laneEndLabel",endProgress?.label)
   .put("longitude",replay.lastPoint?.longitude).put("inputOrigin","EMULATOR_GPS_VIA_PLEOS_FUSED"))
  lastReplayDecision=stateKey
 }
 fun togglePlay() { if(ui.simulation && ui.settingsAllowed) ui=ui.copy(playing=!ui.playing) }
 fun theme() { if(ui.settingsAllowed) ui=ui.copy(dark=!ui.dark) }
 fun reset() { if(ui.settingsAllowed) demo() }
 fun next() {
  if(!ui.settingsAllowed || !ui.simulation) return
  demoIndex++
  if(demoIndex>=positions.size) {ui=ui.copy(playing=false);return}
  demoTime+=3000
  val d=engine.update(ProjectedSample(positions[demoIndex],0.0,90.0,demoTime,InputSource.REPLAY_SYNTHETIC,0,"synthetic-route"),demoTime,System.currentTimeMillis())
  record("decision",JSONObject().put("source",d.source.name).put("state",d.state.name).put("reason",d.reason.name).put("previewCount",d.previewCount).put("alongMeters",positions[demoIndex]))
  val title=when(d.state) {
   GuidanceState.PREVIEW -> "진입 차로는\n앞까지 이어집니다"
   GuidanceState.APPROACHING -> "앞에서 차로가\n합쳐집니다"
   GuidanceState.PASSED -> "합류 구간을 지났습니다"
   GuidanceState.SUPPRESSED -> "구조 안내를 기다립니다"
   else -> "조금 뒤, 진입로를 만납니다"
  }
  ui=ui.copy(title=title,phase=when(d.state) {
   GuidanceState.PREVIEW -> "미리 보는 도로 구조"
   GuidanceState.APPROACHING -> "합류 구간 접근"
   GuidanceState.PASSED -> "구간 통과"
   else -> "안내 대기"
  },message=when(d.state) {
   GuidanceState.PREVIEW -> "진입 차로가 끝부분에서 본선과 연결됩니다."
   GuidanceState.APPROACHING -> "진입 차로가 끝에서 본선과 연결됩니다."
   GuidanceState.PASSED -> "다음 지원 구간에서 다시 안내합니다."
   else -> "지원 구간에 가까워지면 구조를 보여드립니다."
  },detail=if(d.displayable) "도로 구조 설명이며, 현재 차로와 합류 가능 여부는 판단하지 않습니다." else "가상 도로의 입력으로 동작하는 시연입니다.",
   showDiagram=d.displayable,distance=d.distanceToMergeMeters?.takeIf {it>0}?.let {"약 ${it.toInt()}m 앞"},playing=ui.playing && demoIndex<positions.lastIndex)
 }
 internal fun consume(event:SourceEvent) {
  val data=JSONObject().put("source","REAL_SDK").put("eventType",event.javaClass.simpleName)
  when(event) {
   is SourceEvent.Location -> if(handleLocation(event,data)) return
   is SourceEvent.Turns -> {
    val array=org.json.JSONArray()
    event.items.forEach {array.put(JSONObject().put("type",it.type).put("distance",it.rawDistance).put("description",it.description))}
    data.put("items",array).put("generation",event.observedRouteGeneration).put("routeAssociationVerified",event.routeAssociationVerified)
   }
   is SourceEvent.Connection -> handleConnection(event,data)
   is SourceEvent.Failure -> if(handleFailure(event,data)) return
   is SourceEvent.RouteBoundary -> handleRouteBoundary(event,data)
   is SourceEvent.SubscriptionCount -> data.put("count",event.count)
   is SourceEvent.RouteState -> data.put("routing",event.routing).put("mode",event.mode)
  }
  record("sdk",data)
 }

 private fun handleLocation(event:SourceEvent.Location,data:JSONObject):Boolean {
  val p=event.sample
  data.put("routeId",p.routeId).put("currentRouteIndex",p.currentRouteIndex).put("provider",p.provider.name).put("latitude",p.latitude).put("longitude",p.longitude).put("heading",p.heading).put("measuredTime",p.measuredTimeMillis).put("receivedElapsedMillis",p.receivedElapsedMillis).put("confidence",p.confidence).put("selected",event.selectedForPosition)
  locationContext.observe(p)
  if(!event.selectedForPosition || ui.simulation) return false

  locationUnsupported=false
  if(!liveInputAvailable) {liveGuidance.setAvailable(true);liveInputAvailable=true}
  val now=SystemClock.elapsedRealtime();val wall=System.currentTimeMillis()
  val selected=locationContext.select(p,now,wall)
  val replay=sdkReplay
  if(replay!=null) {
   replay.count++;replay.lastPoint=selected.point;replay.lastHeading=p.heading
   val replayResult=replay.gate.update(SdkRouteReplaySample(selected.point,p.heading,
    if(selected.clockValidated) selected.measuredElapsedMs else null,p.receivedElapsedMillis),now)
   data.put("inputOrigin","EMULATOR_GPS").put("testRoute",replay.road.id)
   record("sdk",data)
   presentSdkReplay(replayResult,"sdk")
   return true
  }
  val result=liveGuidance.update(selected.liveInput,now,wall)
  presentLive(result)
  ui=ui.copy(diagnostic="${p.provider}: ${locationContext.locationCount} samples; moved=${locationContext.moved}; ${result.status}")
  return false
 }

 private fun handleConnection(event:SourceEvent.Connection,data:JSONObject) {
  data.put("component",event.component).put("state",event.state)
  if(event.component=="FusedLocation" && event.state=="NO_LOCATION" && sdkReplay!=null) {
   sdkReplay?.reset();presentSdkReplay(SdkRouteReplayResult(SdkRouteReplayState.INVALID),"no_location")
  }
  if(event.component=="FusedLocation" && event.state=="UNSUPPORTED" && !ui.simulation) {
   locationUnsupported=true
   liveInputAvailable=false
   locationContext.clearNavigation()
   liveGuidance.setAvailable(false)
   if(sdkReplay!=null) {sdkReplay?.reset();presentSdkReplay(SdkRouteReplayResult(SdkRouteReplayState.INVALID),"unsupported")}
   else presentLive(liveGuidance.tick(SystemClock.elapsedRealtime(),System.currentTimeMillis()))
  }
 }

 private fun handleFailure(event:SourceEvent.Failure,data:JSONObject):Boolean {
  data.put("component",event.component).put("code",event.code).put("detail",event.detail)
  locationContext.clearNavigation()
  if(sdkReplay!=null) {
   if(event.component.startsWith("FusedLocation") || event.component=="FUSED_LOCATION") {
    sdkReplay?.reset();presentSdkReplay(SdkRouteReplayResult(SdkRouteReplayState.INVALID),"failure")
   }
   record("sdk",data)
   return true
  }
  if(!ui.simulation) {
   clearPresentation();liveGuidance.setAvailable(false);liveInputAvailable=false
   ui=ui.copy(phase="연결 확인 중",title="위치 정보를 기다립니다",message="연결이 확인되면 지원 구간을 살펴봅니다.",detail="정차 중 가상 구간 체험을 이용할 수 있습니다.")
  }
  return false
 }

 private fun handleRouteBoundary(event:SourceEvent.RouteBoundary,data:JSONObject) {
  val generation=locationContext.onRouteBoundary()
  liveGuidance.onRouteChanged(generation)
  if(!ui.simulation && sdkReplay==null) clearPresentation()
  data.put("generation",generation).put("readerGeneration",event.generation).put("reason",event.reason)
 }
 private fun record(event:String, data:JSONObject) {
  if(!BuildConfig.DEBUG) return
  val row=JSONObject().put("event",event).put("atEpochMs",System.currentTimeMillis()).put("atElapsedMs",SystemClock.elapsedRealtime()).put("data",data).toString()
  Log.i("RoadContext",row)
  writer.append(row)
 }
 private fun clearPresentation() {
  ui=RoadGuidancePresentation.clear(ui)
 }
 internal fun presentLive(result:LiveGuidanceResult) {
  if(ui.simulation || ui.setup || sdkReplay!=null) return
  ui=RoadGuidancePresentation.live(ui,result,locationUnsupported)
 }
 override fun onCleared() { stop();writer.close();super.onCleared() }
}
