package me.rocket_scientist.rsbt

import android.bluetooth.BluetoothSocket
import android.os.Handler
import java.io.IOException
import java.util.*
import kotlin.concurrent.schedule

class RsBtReadThread(private val handler: Handler, private val socket: BluetoothSocket) : Thread() {
    enum class STAT {
        MESSAGE_READ_SUCCESSFULLY, NO_INPUT_STREAM
    }
    var rx_timeout_ms: Long = 80
    private var rx_timer = Timer()
    private var collecting = false
    private var rx_data = ""

    private fun sendOnTimeout(string: String){
        rx_timer = Timer()
        rx_timer.schedule(rx_timeout_ms) {
            val message = handler.obtainMessage(STAT.MESSAGE_READ_SUCCESSFULLY.ordinal, string)
            message.sendToTarget()
            collecting = false
            rx_data = ""
        }
    }

    //Read
    override fun run() {
        var length = 0
        val data = ByteArray(4096)
        while (true) {
            length = try {
                socket.inputStream!!.read(data)
            } catch (e: IOException) {
                //Input stream disconnected
                val message = handler.obtainMessage(STAT.NO_INPUT_STREAM.ordinal)
                message.sendToTarget()
                break
            }

            if(length > 0) {
                //Convert to string
                val bytes = ByteArray(length)
                var i = 0
                while (i != length) {
                    bytes[i] = data[i]
                    i++
                }

                rx_timer.cancel()
                if(!collecting){
                    collecting = true
                    rx_data = ""
                    rx_data += String(bytes)
                    sendOnTimeout(rx_data)
                }else{
                    rx_data += String(bytes)
                    sendOnTimeout(rx_data)
                }
            }
        }
    }

}
