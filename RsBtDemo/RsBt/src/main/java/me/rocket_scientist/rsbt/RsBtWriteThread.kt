package me.rocket_scientist.rsbt

import android.bluetooth.BluetoothSocket
import android.os.Handler
import java.io.IOException

class RsBtWriteThread(private val handler: Handler, private val socket: BluetoothSocket) : Thread() {
    enum class STAT {
        MESSAGE_WRITTEN, WRITING_MESSAGE_FAILED, STREAM_BUSY, MESSAGE_SCHEDULED
    }
    private var data: String = ""
    private var write_rq = false

    private fun sendMessage(data: STAT, string: String){
        val message = handler.obtainMessage(data.ordinal, string)
        handler.sendMessage(message)
    }

    override fun run(){
        while(true) {
            if (write_rq) {
                try {
                    socket.outputStream?.write(data.toByteArray())
                } catch (e: IOException) {
                    write_rq = false
                    sendMessage(STAT.WRITING_MESSAGE_FAILED, data)
                }
                if(write_rq){
                    sendMessage(STAT.MESSAGE_WRITTEN, data)
                }
                write_rq = false
            }
        }
    }

    fun sendString(string: String) {
        if(!write_rq){
            data = string
            write_rq = true
            sendMessage(STAT.MESSAGE_SCHEDULED, data)
            return
        }
        sendMessage(STAT.STREAM_BUSY, string)
    }
}
