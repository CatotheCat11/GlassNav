package com.cato.glassmapscompanion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cato.glassmapscompanion.ui.theme.GlassMapsCompanionTheme
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class MainActivity : ComponentActivity() {
    val LOCATION_PERMISSION_REQUEST_CODE = 0
    var requestingLocationUpdates: Boolean = false
    private lateinit var locationListener: LocationListener
    private lateinit var client: OkHttpClient
    private var lastLocation: Location? = null
    var bluetoothHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothMapsService.MESSAGE_CONNECT -> {
                    // Get the message content from the handler message
                    Log.d(TAG, "Connected")
                    bluetoothConnected.value = true
                }
                BluetoothMapsService.MESSAGE_READ -> {
                    // Get the message content from the handler message
                    val readMessage = msg.obj as String
                    Log.d(TAG, "Received: $readMessage")
                }
                BluetoothMapsService.MESSAGE_ERROR -> {
                    // Get the message content from the handler message
                    if (msg.obj != null) {
                        val readMessage = msg.obj as String
                        Log.e(TAG, "Received: $readMessage")
                        bluetoothConnected.value = false
                    }
                }
            }
        }
    };
    var bluetoothConnected: MutableState<Boolean> = mutableStateOf(false) //FIXME: Forgets state when device rotated
    var searchResults: MutableList<LocationInfo> = mutableStateListOf()
    lateinit var bluetoothService: BluetoothMapsService
    val locationManager: LocationManager by lazy {
        getSystemService(LOCATION_SERVICE) as LocationManager
    }
    companion object {
        const val TAG: String = "MainActivity"
        lateinit var macAddress: String
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
                    Manifest.permission.ACCESS_COARSE_LOCATION
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
                    bluetoothService =
                        BluetoothMapsService(bluetoothHandler, bluetoothAdapter, this)
                    locationListener = LocationListener { location: Location ->
                        Log.i(TAG, "Location update: $location")
                        lastLocation = location
                        if (bluetoothConnected.value) {
                            /*val latBytes =
                                ByteBuffer.allocate(8).putDouble(location.latitude).array()
                            val lonBytes =
                                ByteBuffer.allocate(8).putDouble(location.longitude).array()
                            val altBytes =
                                ByteBuffer.allocate(8).putDouble(location.altitude).array()
                            val speedBytes = ByteBuffer.allocate(4).putFloat(location.speed).array()
                            val bearingBytes =
                                ByteBuffer.allocate(4).putFloat(location.bearing).array()
                            val locationData = ByteArray(32)
                            System.arraycopy(latBytes, 0, locationData, 0, 8)
                            System.arraycopy(lonBytes, 0, locationData, 8, 8)
                            System.arraycopy(altBytes, 0, locationData, 16, 8)
                            System.arraycopy(speedBytes, 0, locationData, 24, 4)
                            System.arraycopy(bearingBytes, 0, locationData, 28, 4)
                            bluetoothService.write(locationData)*/
                            val resultObj= JSONObject()
                            resultObj.put("type", "location")
                            resultObj.put("latitude", location.latitude)
                            resultObj.put("longitude", location.longitude)
                            resultObj.put("altitude", location.altitude)
                            resultObj.put("speed", location.speed)
                            resultObj.put("bearing", location.bearing)
                            bluetoothService.write(resultObj.toString().toByteArray())
                        }
                    }
                    setContent {
                        GlassMapsCompanionTheme {
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
                                    bluetoothMapsService = bluetoothService,
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
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun search(query: String) {
        if (lastLocation == null) {
            val toast: Toast = Toast.makeText(this, "Can't search, haven't obtained current location", Toast.LENGTH_LONG)
            toast.show()
            return
        }
        val viewBox =
            (lastLocation!!.longitude - 1.8).toString() + "," + (lastLocation!!.latitude - 1.8) + "," + (lastLocation!!.longitude + 1.8) + "," + (lastLocation!!.latitude + 1.8)
        val request: Request = Request.Builder()
            .header("User-Agent", "GlassMaps/1.0")
            .url("https://nominatim.openstreetmap.org/search?format=json&bounded=1&q=$query&viewbox=$viewBox")
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                searchResults.clear()
                val results: JSONArray = JSONArray(response.body.string())
                val lastLocationPoint: GeoPoint = GeoPoint(lastLocation!!.latitude, lastLocation!!.longitude)
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

    private fun distFrom(point1: GeoPoint, point2: GeoPoint): Float {
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
            bluetoothService.write(extras.getByteArray(key))
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (requestingLocationUpdates && hasLocationPermissions()) startLocationUpdates()
    }

    private fun startLocationUpdates() {
        Log.i(TAG, "Starting location updates")
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000,
            0.5f,
            locationListener,
            Looper.getMainLooper()
        )
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
    bluetoothMapsService: BluetoothMapsService?,
    bluetoothAdapter: BluetoothAdapter
) {
    // Controls expansion state of the search bar
    var expanded by rememberSaveable { mutableStateOf(false) }

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
                            expanded = false
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
                                    resultObj.put("type", "search")
                                    resultObj.put("name", result.name)
                                    resultObj.put("displayName", result.displayName)
                                    resultObj.put("latitude", result.location.latitude)
                                    resultObj.put("longitude", result.location.longitude)
                                    resultObj.put("distance", result.distance)
                                    bluetoothMapsService?.write(resultObj.toString().toByteArray())
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
                        bluetoothMapsService?.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
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
                                bluetoothMapsService?.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.address))
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
