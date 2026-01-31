package com.cato.glassmaps;

import static android.content.ContentValues.TAG;

import static java.lang.Math.abs;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Rotation;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.util.List;


import okhttp3.OkHttpClient;

public class MainActivity extends Activity/*TODO compass: implements SensorEventListener*/{
    private MapView mapView;
    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;
    private ImageView navArrow;
    private SensorManager sensorManager;
    private List<ScanResult> wifiAccessPoints;
    static OkHttpClient client = null;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private boolean lastAccelerometerSet = false;
    private boolean lastMagnetometerSet = false;
    private float[] rotation = new float[9];
    private float[] orientation = new float[3];
    private Location lastLocation = null;

    private static final int MAP_OFFSET_X = 0;    // pixels to offset horizontally
    private static final int MAP_OFFSET_Y = 80; // pixels to offset vertically (negative moves up)

    private static final int GPS_TIMEOUT_MS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CustomTrust customTrust = new CustomTrust(getApplicationContext());
        client = customTrust.getClient();

        /*
         * Before you make any calls on the mapsforge library, you need to initialize the
         * AndroidGraphicFactory. Behind the scenes, this initialization process gathers a bit of
         * information on your device, such as the screen resolution, that allows mapsforge to
         * automatically adapt the rendering for the device.
         * If you forget this step, your app will crash. You can place this code, like in the
         * Samples app, in the Android Application class. This ensures it is created before any
         * specific activity. But it can also be created in the onCreate() method in your activity.
         */
        AndroidGraphicFactory.createInstance(getApplication());

        /*
         * A MapView is an Android View (or ViewGroup) that displays a mapsforge map. You can have
         * multiple MapViews in your app or even a single Activity. Have a look at the mapviewer.xml
         * on how to create a MapView using the Android XML Layout definitions.
         */
        setContentView(R.layout.mapviewer);
        mapView = findViewById(R.id.mapView);
        navArrow = findViewById(R.id.navArrow);

        // Initialize sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Initialize wake lock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GlassMaps::LocationWakeLock");
        // Get the .map file from res/raw and copy it to a temporary file
        try {

            openMap();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void openMap() {
        try {
            /*
             * We then make some simple adjustments, such as showing a scale bar and zoom controls.
             * Changed to false by cato for now
             */
            mapView.getMapScaleBar().setVisible(false);

            /*
             * To avoid redrawing all the tiles all the time, we need to set up a tile cache with an
             * utility method.
             */
            TileCache tileCache = AndroidUtil.createTileCache(this, "mapcache",
                    mapView.getModel().displayModel.getTileSize(), 1f,
                    mapView.getModel().frameBufferModel.getOverdrawFactor());

            /*
             * Now we need to set up the process of displaying a map. A map can have several layers,
             * stacked on top of each other. A layer can be a map or some visual elements, such as
             * markers. Here we only show a map based on a mapsforge map file. For this we need a
             * TileRendererLayer. A TileRendererLayer needs a TileCache to hold the generated map
             * tiles, a map file from which the tiles are generated and Rendertheme that defines the
             * appearance of the map.
             */
            //TODO: Make proper map downloader
            MapDataStore mapDataStore = new MapFile(Environment.getExternalStorageDirectory().getPath() + "/Map.map");
            TileRendererLayer tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
                    mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
            tileRendererLayer.setXmlRenderTheme(MapsforgeThemes.DARK);

            /*
             * On its own a tileRendererLayer does not know where to display the map, so we need to
             * associate it with our mapView.
             */
            mapView.getLayerManager().getLayers().add(tileRendererLayer);

            /*
             * The map also needs to know which area to display and at what zoom level.
             * Note: this map position is specific to Diriyah area.
             */
            mapView.getModel().displayModel.setBackgroundColor(0x00000000);
            mapView.setZoomLevel((byte) 16);
            // Request location updates
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            startLocationUpdates();
        } catch (Exception e) {
            /*
             * In case of map file errors avoid crash, but developers should handle these cases!
             */
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        /*
         * Whenever your activity exits, some cleanup operations have to be performed lest your app
         * runs out of memory.
         */
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        mapView.destroyAll();
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastGpsTimestamp = 0L;
    private final Runnable gpsTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (now - lastGpsTimestamp >= GPS_TIMEOUT_MS) {
                getNetworkLocationUpdates();
            }
        }
    };
    private void startLocationUpdates() {
        try {
            Log.d(TAG, "Starting location updates");

            // Acquire wake lock to prevent GPS from being shut down
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }

            // Request high accuracy GPS updates
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    0.5f,
                    locationListener,
                    Looper.getMainLooper() // Explicitly specify the looper
            );

            lastGpsTimestamp = System.currentTimeMillis();
            handler.removeCallbacks(gpsTimeoutRunnable);
            handler.postDelayed(gpsTimeoutRunnable, 5000);

            // Get last known location
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                updateMapPosition(lastKnownLocation);
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }
    }

    private void getNetworkLocationUpdates() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        BroadcastReceiver scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                //scanCompleted.complete(Unit)
            }
        };
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiManager.startScan();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        List<ScanResult> wifis = wifiManager.getScanResults();
        JSONObject jsonBody = new JSONObject();
        JSONArray wifiArray = new JSONArray();
        try {
            for (ScanResult result : wifis) {
                JSONObject wifiObj = new JSONObject();
                wifiObj.put("macAddress", result.BSSID);
                wifiObj.put("signalStrength", result.level);
                wifiArray.put(wifiObj);
            }
            jsonBody.put("wifiAccessPoints", wifiArray);
        } catch (JSONException e) {
            Log.e(TAG, "JSON Error: " + e);
        }
        if (wifis != wifiAccessPoints) {
            // Update only if there are changes
            wifiAccessPoints = wifis;
            HttpsUtils.makePostRequest(client, "https://api.beacondb.net/v1/geolocate", jsonBody, "POST", new HttpsUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Network location response: " + response);
                    Location networkLocation = new Location(LocationManager.NETWORK_PROVIDER);
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONObject locationObj = jsonResponse.getJSONObject("location");
                        double lat = locationObj.getDouble("lat");
                        double lng = locationObj.getDouble("lng");
                        double accuracy = jsonResponse.getDouble("accuracy");
                        networkLocation.setLatitude(lat);
                        networkLocation.setLongitude(lng);
                        networkLocation.setAccuracy((float) accuracy);
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON Parsing Error: " + e);
                    }
                    locationListener.onLocationChanged(networkLocation);
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Network location error: " + errorMessage);
                }
            });
        }

    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.d(TAG, "Location changed: " + location.toString());

            if (lastLocation != null) {
                // Calculate bearing between last and current location
                double latitude1 = Math.toRadians(lastLocation.getLatitude());
                double longitude1 = Math.toRadians(-lastLocation.getLongitude());
                double latitude2 = Math.toRadians(location.getLatitude());
                double longitude2 = Math.toRadians(-location.getLongitude());

                double dLong = longitude2 - longitude1;

                double dPhi = Math.log(Math.tan(latitude2 / 2.0 + Math.PI / 4.0) / Math.tan(latitude1 / 2.0 + Math.PI / 4.0));
                if (abs(dLong) > Math.PI)
                    if (dLong > 0.0)
                        dLong = -(2.0 * Math.PI - dLong);
                    else
                        dLong = (2.0 * Math.PI + dLong);

                float bearing = (float) ((Math.toDegrees(Math.atan2(dLong, dPhi)) + 360.0) % 360.0);
                Log.d(TAG, "Bearing: " + bearing);
                // Update map rotation to match movement direction
                mapView.getModel().mapViewPosition.setRotation(new Rotation(bearing, 0, 0));
            }

            lastGpsTimestamp = System.currentTimeMillis();
            handler.removeCallbacks(gpsTimeoutRunnable);
            handler.postDelayed(gpsTimeoutRunnable, 5000);

            lastLocation = location;
            updateMapPosition(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(TAG, "Status changed: " + provider + ", status: " + status);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.d(TAG, "Provider enabled: " + provider);
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                startLocationUpdates(); // Restart updates when GPS is enabled
            }
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(TAG, "Provider disabled: " + provider);
        }
    };

    /*@Override TODO compass
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
            lastAccelerometerSet = true;
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
            lastMagnetometerSet = true;
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            boolean success = SensorManager.getRotationMatrix(rotation, null, lastAccelerometer, lastMagnetometer);
            if (success) {
                SensorManager.getOrientation(rotation, orientation);
                float azimuth = (float) Math.toDegrees(orientation[0]);
                // Adjust arrow rotation based on compass heading
                navArrow.setRotation(-azimuth + mapView.getModel().mapViewPosition.getRotation().degrees);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Can be implemented if you want to show compass accuracy warnings
    }*/


    private void updateMapPosition(Location location) {
        LatLong position = new LatLong(location.getLatitude(), location.getLongitude());

        // Get current map rotation in radians
        float mapRotationRadians = (float) mapView.getModel().mapViewPosition.getRotation().radians;

        // Rotate the offset based on map rotation
        double rotatedOffsetX = MAP_OFFSET_X * Math.cos(mapRotationRadians) -
                MAP_OFFSET_Y * Math.sin(mapRotationRadians);
        double rotatedOffsetY = MAP_OFFSET_X * Math.sin(mapRotationRadians) +
                MAP_OFFSET_Y * Math.cos(mapRotationRadians);

        // Get the model and current zoom level
        byte zoomLevel = mapView.getModel().mapViewPosition.getZoomLevel();

        // Calculate meters per pixel at the current latitude and zoom level
        double metersPerPixel = 40075016.686 * Math.cos(Math.toRadians(location.getLatitude()))
                / Math.pow(2, zoomLevel + 8);

        // Convert rotated pixel offsets to meters
        double metersOffsetY = rotatedOffsetY * metersPerPixel;
        double metersOffsetX = rotatedOffsetX * metersPerPixel;

        // Convert meters to degrees (approximately)
        double latOffset = metersOffsetY / 111319.0;
        double lonOffset = metersOffsetX / (111319.0 * Math.cos(Math.toRadians(location.getLatitude())));

        // Create new position with offset
        LatLong offsetPosition = new LatLong(
                position.latitude + latOffset,
                position.longitude + lonOffset
        );

        // Set the center to the offset position
        mapView.setCenter(offsetPosition);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }

        // Register sensor listeners TODO compass
        /*sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener((SensorEventListener) this, magnetometer, SensorManager.SENSOR_DELAY_GAME);*/

        // Stop any existing updates before starting new ones
        locationManager.removeUpdates(locationListener);
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        // Unregister sensor listeners
        if (sensorManager != null && this instanceof SensorEventListener) {
            sensorManager.unregisterListener((SensorEventListener) this);
        }
        handler.removeCallbacks(gpsTimeoutRunnable);
    }
}