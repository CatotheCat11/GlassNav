package com.cato.glassmapscompanion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


class BluetoothMapsService(private val mHandler: Handler, private val mAdapter: BluetoothAdapter, private val context: Context) {
    companion object {
        const val TAG: String = "BluetoothMapsService"
        const val NAME: String = "GlassMapsBluetooth"
        const val MESSAGE_CONNECT: Int = -1
        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_ERROR: Int = 2
        private var mConnectThread: ConnectThread? = null
        private var mConnectedThread: ConnectedThread? = null
        val MY_UUID: UUID = UUID.fromString("684ebeac-9863-4145-8e66-efb89c816434")
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) @RequiresPermission(
            Manifest.permission.BLUETOOTH_CONNECT
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
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
                    val writeErrorMsg = mHandler.obtainMessage(MESSAGE_ERROR)
                    val bundle = Bundle().apply {
                        putString("error", "Couldn't connect to Glass")
                    }
                    writeErrorMsg.data = bundle
                    mHandler.sendMessage(writeErrorMsg)
                    return
                }

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                Log.i(TAG, "Bluetooth connected to device: ${socket.remoteDevice.name}")
                val connectMsg = mHandler.obtainMessage(MESSAGE_CONNECT)
                mHandler.sendMessage(connectMsg)
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
                val readMsg = mHandler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer)
                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                Log.i(TAG, "Sending data: ${bytes.size} bytes")
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = mHandler.obtainMessage(MESSAGE_ERROR)
                val bundle = Bundle().apply {
                    putString("error", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                mHandler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = mHandler.obtainMessage(
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