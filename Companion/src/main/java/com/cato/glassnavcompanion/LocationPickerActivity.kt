package com.cato.glassnavcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cato.glassnavcompanion.MainActivity.Companion.distFrom
import com.cato.glassnavcompanion.MainActivity.GeoPoint
import com.cato.glassnavcompanion.MainActivity.LocationInfo
import com.cato.glassnavcompanion.ui.theme.GlassNavCompanionTheme
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.toJson

val TAG = "LocationPickerActivity"
class LocationPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassNavCompanionTheme() {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationPickerView()
                }
            }
        }
    }
}

@Composable
fun LocationPickerView() {
    val camera =
        rememberCameraState(
            firstPosition =
                CameraPosition(
                    target = Position(latitude = 45.521, longitude = -122.675),
                    zoom = 13.0
                )
        )
    MaplibreMap(
        cameraState = camera,
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty")
    )
    LaunchedEffect(Unit) {
        if (BluetoothMapsService.lastLocation != null) {
            camera.position = CameraPosition(
                target = Position(
                    latitude = BluetoothMapsService.lastLocation!!.latitude,
                    longitude = BluetoothMapsService.lastLocation!!.longitude
                ),
                zoom = 15.0
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painterResource(R.drawable.nav_markers),
            contentDescription = "Selected location marker",
            modifier = Modifier.align(Alignment.Center).height(128.dp).fillMaxWidth()
        )
        Button(
            onClick = {
                val location = camera.position.target
                val request: Request = Request.Builder()
                    .header("User-Agent", "GlassNav/1.0")
                    .url("https://nominatim.openstreetmap.org/reverse?lat=${location.latitude}&lon=${location.longitude}&format=json")
                    .build()
                MainActivity.client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val result = JSONObject(response.body.string())
                        val resultObj= JSONObject()
                        resultObj.put("t", "s")
                        resultObj.put("n", result.getString("name"))
                        resultObj.put("dn", result.getString("display_name"))
                        resultObj.put("la", location.latitude)
                        resultObj.put("lo", location.longitude)
                        val lastLocationPoint = GeoPoint(BluetoothMapsService.lastLocation!!.latitude, BluetoothMapsService.lastLocation!!.longitude)
                        val selectedLocationPoint = GeoPoint(location.latitude, location.longitude)
                        resultObj.put("di", distFrom(lastLocationPoint, selectedLocationPoint))
                        BluetoothMapsService.write(resultObj.toString().toByteArray())
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Search request failed", e)
                    }
                })
            },
            modifier = Modifier.wrapContentSize().align(Alignment.BottomCenter).padding(24.dp)
        ) { Text("Send to Glass") }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GlassNavCompanionTheme() {
        LocationPickerView()
    }
}