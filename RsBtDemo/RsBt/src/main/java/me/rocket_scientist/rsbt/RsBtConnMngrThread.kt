package me.rocket_scientist.rsbt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import java.io.IOException

@SuppressLint("MissingPermission")
open class RsBtConnMngrThread(private val handler: Handler, private val device: BluetoothDevice, private val context: Context) : Thread() {
    val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(device.uuids[0].uuid)
    enum class STAT {
        CONNECTED, DISCONNECTED, SOCKET_CLOSE_FAIL
    }

    private fun sendMessage(data: STAT){
        val message = handler.obtainMessage(data.ordinal)
        handler.sendMessage(message)
    }

    //Connect
    override fun run() {
        //Cancel device discovery to speed up connection
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as (android.bluetooth.BluetoothManager)).adapter?.cancelDiscovery()

        //If no socket, disconnect
        try {
            socket.connect()
            sendMessage(STAT.CONNECTED)
        } catch (connectException: IOException) {
            disconnect()
        }
    }

    //Disconnect
    fun disconnect() {
        try {
            socket.close()
            sendMessage(STAT.DISCONNECTED)
        } catch (e: IOException) {
            //Could not close the client socket
            sendMessage(STAT.SOCKET_CLOSE_FAIL)
        }
    }

    fun currentDevice(): BluetoothDevice {
        return device
    }
    fun isConnected(): Boolean {
        if(socket.isConnected){
            return true
        }
        return false
    }
}
