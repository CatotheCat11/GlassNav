package com.cato.glassnavcompanion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cato.glassnavcompanion.MainActivity.Companion.bluetoothConnected
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID


class BluetoothMapsService(): Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun connect(device: BluetoothDevice) {
            Log.d(TAG, "Connecting to device: ${device.name}")
            mConnectThread = instance?.ConnectThread(device)
            mConnectThread?.start()
        }

        fun disconnect() {
            mConnectThread?.cancel()
            mConnectThread = null;
            mConnectedThread?.cancel()
            mConnectedThread = null;
            bluetoothConnected.value = false
        }

        fun stop() {
            disconnect()
            instance?.stopSelf()
        }

        fun write(bytes: ByteArray?) {
            mConnectedThread?.write(bytes)
        }

        const val TAG: String = "BluetoothMapsService"
        const val NAME: String = "GlassNavBluetooth"
        const val MESSAGE_CONNECT: Int = -1
        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_ERROR: Int = 2
        private var mConnectThread: ConnectThread? = null
        private var mConnectedThread: ConnectedThread? = null
        private var mAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val MY_UUID: UUID = UUID.fromString("684ebeac-9863-4145-8e66-efb89c816434")
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1
        var lastLocation: Location? = null

        var instance: BluetoothMapsService? = null
    }

    val locationManager: LocationManager? by lazy {
        getSystemService(LOCATION_SERVICE) as? LocationManager
    }

    private var locationListener = LocationListener { location: Location ->
        Log.i(TAG, "Location update: $location")
        lastLocation = location
        if (bluetoothConnected.value) {
            Companion.write(locationToBytes(location))
        }
    }

    fun locationToBytes(location: Location): ByteArray {
        val latBytes =
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
        return locationData
    }


    override fun onCreate() {
        super.onCreate()
        instance = this;
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceTypes);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        startLocationUpdates()
        for (device in mAdapter?.bondedDevices ?: emptySet()) {
            if (device.name.contains("Glass", ignoreCase = true)) {
                Log.d(TAG, "Found paired Glass device: ${device.name}, attempting to connect")
                connect(device)
                break
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glass Companion Running")
            .setContentText("Streaming location to Google Glass")
            //.setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startLocationUpdates() {
        Log.i(TAG, "Starting location updates")
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000,
            0.5f,
            locationListener,
            Looper.getMainLooper()
        )
    }

    var bluetoothHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_CONNECT -> {
                    // Get the message content from the handler message
                    Log.d(TAG, "Connected")
                    bluetoothConnected.value = true
                    // Send last known location immediately upon connection
                    locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { write(locationToBytes(it)) }
                }
                MESSAGE_READ -> {
                    // Get the message content from the handler message
                    val readMessage = msg.obj as String
                    Log.d(TAG, "Received: $readMessage")
                }
                MESSAGE_ERROR -> {
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



    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) @RequiresPermission(
            Manifest.permission.BLUETOOTH_CONNECT
        ) {
            if (ContextCompat.checkSelfPermission(this@BluetoothMapsService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "BLUETOOTH_CONNECT permission granted")
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } else {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                null
            }
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            mAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                Log.i(TAG, "Attempting to connect to device: ${socket.remoteDevice.name}")
                try {
                    socket.connect()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not connect to the client socket", e)
                    // Send a failure message back to the activity.
                    val writeErrorMsg = bluetoothHandler.obtainMessage(MESSAGE_ERROR)
                    val bundle = Bundle().apply {
                        putString("error", "Couldn't connect to Glass")
                    }
                    writeErrorMsg.data = bundle
                    bluetoothHandler.sendMessage(writeErrorMsg)
                    return
                }

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                Log.i(TAG, "Bluetooth connected to device: ${socket.remoteDevice.name}")
                val connectMsg = bluetoothHandler.obtainMessage(MESSAGE_CONNECT)
                bluetoothHandler.sendMessage(connectMsg)
                mConnectedThread = ConnectedThread(socket)
                mConnectedThread?.start()
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    @Synchronized
    fun connect(device: BluetoothDevice?) {
        Log.d(TAG, "connect to: $device")

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device!!)
        mConnectThread!!.start()
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }

                // Send the obtained bytes to the UI activity.
                val readMsg = bluetoothHandler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer)
                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray?) {
            try {
                Log.i(TAG, "Sending data: ${bytes?.size} bytes")
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
                bluetoothConnected.value = false
                disconnect()
                // Send a failure message back to the activity.
                val writeErrorMsg = bluetoothHandler.obtainMessage(MESSAGE_ERROR)
                val bundle = Bundle().apply {
                    putString("error", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                bluetoothHandler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = bluetoothHandler.obtainMessage(
                MESSAGE_WRITE, -1, -1, mmBuffer)
            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray?) {
        // Create temporary object
        val r: ConnectedThread
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            r = mConnectedThread!!
        }
        // Perform the write unsynchronized
        r.write(out!!)
    }
}