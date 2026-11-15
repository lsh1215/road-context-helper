package dev.pleos.roadcontext

import android.os.Bundle
import android.util.Log
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.LaunchedEffect
import dev.pleos.roadcontext.integration.CarUxRestrictionSource
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
 private lateinit var model:RoadViewModel
 private var restrictions:CarUxRestrictionSource?=null
 private var replayRequested=false
 private var galleryRequested=false
 private val locationPermission="pleos.car.permission.FUSED_LOCATION"
 private val permissionRequest=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
  model.permissionResult(granted)
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  replayRequested=BuildConfig.DEBUG && intent.getBooleanExtra("sdk_road_replay",false)
  galleryRequested=intent.getBooleanExtra("cluster_demo",false)
  model=ViewModelProvider(this)[RoadViewModel::class.java]
  restrictions=CarUxRestrictionSource(this) { policy ->
   Log.i("RoadContextUx",policy.toString())
   model.setRestrictions(policy.known,policy.requiresDistractionOptimization ?: true,policy.activeRestrictions ?: -1)
   if(galleryRequested) {
    if(model.ui.clusterDemo || model.clusterDemo(intent.getStringExtra("road_profile"))) galleryRequested=false
   } else if(replayRequested && model.sdkRoadReplay(intent.getStringExtra("road_profile") ?: "yanghwa")) replayRequested=false
  }
  setContent {
   val state=model.ui
   LaunchedEffect(state.playing,state.clusterDemo) {
    while(model.ui.playing && !model.ui.clusterDemo) {delay(3000);model.next()}
   }
   RoadScreen(state,model::demo,{
    if(model.ui.setup) model.requestLocationPermission { permissionRequest.launch(locationPermission) }
    else model.live()
   },model::togglePlay,model::next,model::reset,model::theme,{model.sdkRoadReplay()},{model.clusterDemo()})
  }
 }
 override fun onStart() {super.onStart();restrictions?.start();model.start()}
 override fun onStop() {model.stop();restrictions?.close();super.onStop()}
 override fun onDestroy() {restrictions?.close();super.onDestroy()}
}
