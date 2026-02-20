package com.cato.glassnavcompanion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cato.glassnavcompanion.ui.theme.GlassNavCompanionTheme
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class MainActivity : ComponentActivity() {
    val LOCATION_PERMISSION_REQUEST_CODE = 0
    var requestingLocationUpdates: Boolean = false

    var searchResults: MutableList<LocationInfo> = mutableStateListOf()
    val locationManager: LocationManager by lazy {
        getSystemService(LOCATION_SERVICE) as LocationManager
    }
    companion object {
        const val TAG: String = "MainActivity"
        lateinit var client: OkHttpClient
        lateinit var macAddress: String
        var bluetoothConnected: MutableState<Boolean> = mutableStateOf(false) //FIXME: Forgets state when device rotated
        fun distFrom(point1: GeoPoint, point2: GeoPoint): Float {

            val earthRadius = 6371000.0 //meters
            val dLat = Math.toRadians(point2.latitude - point1.latitude)
            val dLng = Math.toRadians(point2.longitude - point1.longitude)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(point1.latitude)) * cos(Math.toRadians(point2.latitude)) * sin(
                dLng / 2
            ) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return (earthRadius * c).toFloat()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        client = OkHttpClient()

        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermissions()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter == null) {
                    Log.e(TAG, "No bluetooth adapter found")
                    Toast.makeText(this, "No bluetooth adapter found", Toast.LENGTH_LONG).show();
                    finish()
                } else {
                    val intent = Intent(this, BluetoothMapsService::class.java)
                    ContextCompat.startForegroundService(this, intent)
                    setContent {
                        GlassNavCompanionTheme {
                            Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                                val state = remember { TextFieldState() }
                                MainView(
                                    textFieldState = state,
                                    onSearch = { query ->
                                        search(query)
                                    },
                                    searchResults = searchResults,
                                    connected = bluetoothConnected,
                                    modifier = Modifier.padding(padding),
                                    bluetoothAdapter = bluetoothAdapter
                                )
                            }
                        }
                    }
                    requestingLocationUpdates = true;
                }
            }
        }
    }

    fun hasLocationPermissions():Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun search(query: String) {
        if (BluetoothMapsService.lastLocation == null) {
            val toast: Toast = Toast.makeText(this, "Can't search, haven't obtained current location", Toast.LENGTH_LONG)
            toast.show()
            return
        }
        val viewBox =
            (BluetoothMapsService.lastLocation!!.longitude - 1.8).toString() + "," + (BluetoothMapsService.lastLocation!!.latitude - 1.8) + "," + (BluetoothMapsService.lastLocation!!.longitude + 1.8) + "," + (BluetoothMapsService.lastLocation!!.latitude + 1.8)
        val request: Request = Request.Builder()
            .header("User-Agent", "GlassNav/1.0")
            .url("https://nominatim.openstreetmap.org/search?format=json&bounded=1&q=$query&viewbox=$viewBox")
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                searchResults.clear()
                val results: JSONArray = JSONArray(response.body.string())
                val lastLocationPoint: GeoPoint = GeoPoint(BluetoothMapsService.lastLocation!!.latitude, BluetoothMapsService.lastLocation!!.longitude)
                for (i in 0..<results.length()) {
                    val result = results.getJSONObject(i)
                    if (!result.getString("name").isBlank()) {
                        val name = result.getString("name")
                        val displayName = result.getString("display_name")
                        val location: GeoPoint = GeoPoint( result.getDouble("lat"), result.getDouble("lon"))
                        val distance: Float = distFrom(location, lastLocationPoint)
                        val searchResult: LocationInfo =
                            LocationInfo(name, displayName, location, distance)
                        searchResults.add(searchResult)
                    }
                }
                runOnUiThread { searchResults.sortBy { it.distance } }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Search request failed", e)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun hasBluetoothPermissions():Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }
    fun sendKey(key: String, extras: Bundle) {
        if (extras.containsKey(key)) {
            Log.i(TAG, "Sending key $key")
            BluetoothMapsService.write(extras.getByteArray(key))
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate()
            } else {
                finish()
            }
        }
    }
    class LocationInfo(
        var name: String = "Unknown",
        var displayName: String = "Unknown",
        var location: GeoPoint,
        var distance: Float
    )

    class GeoPoint(
        var latitude: Double = 0.0,
        var longitude: Double = 0.0
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    searchResults: List<MainActivity.LocationInfo>,
    modifier: Modifier = Modifier,
    connected: MutableState<Boolean>,
    bluetoothAdapter: BluetoothAdapter
) {
    // Controls expansion state of the search bar
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier
            .fillMaxSize()
            .semantics { isTraversalGroup = true }
    ) {
        if (connected.value) {
            SearchBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .semantics { traversalIndex = 0f },
                inputField = {
                    SearchBarDefaults.InputField(
                        query = textFieldState.text.toString(),
                        onQueryChange = { textFieldState.edit { replace(0, length, it) } },
                        onSearch = {
                            onSearch(textFieldState.text.toString())
                        },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        placeholder = { Text("Search using Nominatim") }
                    )
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                // Display search results in a scrollable column
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("Pick from map") },
                        leadingContent = { Image(painter = painterResource(R.drawable.ic_pin_drop), contentDescription = "Pick from map")},
                        modifier = Modifier
                            .clickable {
                                val intent = Intent(context, LocationPickerActivity::class.java)
                                context.startActivity(intent)
                            }
                            .fillMaxWidth()
                    )
                    searchResults.forEach { result ->
                        ListItem(
                            headlineContent = { Text(result.name) },
                            supportingContent = {
                                Text(
                                    result.displayName + " · " + formatDistance(
                                        result.distance
                                    )
                                )
                            },
                            modifier = Modifier
                                .clickable {
                                    Log.i("MainActivity", "Clicked " + result.name)
                                    val resultObj= JSONObject()
                                    resultObj.put("t", "s")
                                    resultObj.put("n", result.name)
                                    resultObj.put("dn", result.displayName)
                                    resultObj.put("la", result.location.latitude)
                                    resultObj.put("lo", result.location.longitude)
                                    resultObj.put("di", result.distance)
                                    BluetoothMapsService.write(resultObj.toString().toByteArray())
                                }
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }

        Column(
            modifier
                .wrapContentSize()
                .align(Alignment.Center)
        ) {
            if (connected.value) {
                Text("Glass is connected", modifier = modifier.align(Alignment.CenterHorizontally))
                Button(
                    onClick = {
                        BluetoothMapsService.disconnect()
                    },
                    modifier = modifier.align(Alignment.CenterHorizontally)
                ) { Text("Disconnect") }
                Button(
                    onClick = {
                        BluetoothMapsService.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                            MainActivity.macAddress))
                    },
                    modifier = modifier.align(Alignment.CenterHorizontally)
                ) { Text("Reconnect") }
            } else {
                Text("Glass is disconnected", modifier = modifier.align(Alignment.CenterHorizontally))
                /*Button(
                    onClick = {
                        bluetoothMapsService?.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("macAddressHere"))
                    },
                    modifier.align(Alignment.CenterHorizontally)
                ) { Text("Connect") }*/
                for (device in bluetoothAdapter.bondedDevices) {
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text(device.address) },
                        leadingContent = {},
                        modifier = Modifier
                            .clickable {
                                BluetoothMapsService.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.address))
                                MainActivity.macAddress = device.address
                            }
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}
fun formatDistance(distance: Float): String {
    if (distance >= 1000) {
        return (distance / 1000).toString() + " km"
    } else {
        return "$distance m"
    }
}
