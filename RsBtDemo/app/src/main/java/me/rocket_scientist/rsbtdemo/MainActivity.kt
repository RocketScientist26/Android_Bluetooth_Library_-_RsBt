package me.rocket_scientist.rsbtdemo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import me.rocket_scientist.rsbt.RsBtConnMngrThread
import me.rocket_scientist.rsbt.RsBtDevMngr
import me.rocket_scientist.rsbt.RsBtReadThread
import me.rocket_scientist.rsbt.RsBtWriteThread


class MainActivity : AppCompatActivity() {
    //UI
    private lateinit var spinner_devices: Spinner
    private lateinit var textView_rx: TextView
    private lateinit var button_clear: Button
    private lateinit var textView_tx: TextView
    private lateinit var button_connect: Button
    private lateinit var button_send: Button

    private var conn_disconn_rq = false

    //Bluetooth write thread
    private lateinit var btth_w: RsBtWriteThread
    private inner class btWriteHandler : Handler(Looper.getMainLooper()){
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                RsBtWriteThread.STAT.MESSAGE_WRITTEN.ordinal -> {
                    //!UNUSED
                }
                RsBtWriteThread.STAT.MESSAGE_SCHEDULED.ordinal -> {
                    //!UNUSED
                }
                RsBtWriteThread.STAT.STREAM_BUSY.ordinal -> {
                    connectClicked()
                }
                RsBtWriteThread.STAT.WRITING_MESSAGE_FAILED.ordinal -> {
                    connectClicked()
                }
            }
        }
    }

    //Bluetooth read thread
    private lateinit var btth_r: RsBtReadThread
    private inner class btReadHandler : Handler(Looper.getMainLooper()){
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if(msg.what == RsBtReadThread.STAT.MESSAGE_READ_SUCCESSFULLY.ordinal) {
                val data = msg.obj.toString()
                runOnUiThread {
                    textView_rx.append(data + "\n")
                    uiEnableConditional()
                }
            }else if(msg.what == RsBtReadThread.STAT.NO_INPUT_STREAM.ordinal) {
                connectClicked()
            }
        }
    }

    //Bluetooth connection manager
    private lateinit var btconmngr: RsBtConnMngrThread
    private inner class btConnHandler : Handler(Looper.getMainLooper()){
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                RsBtConnMngrThread.STAT.CONNECTED.ordinal -> {
                    btth_r = RsBtReadThread(btReadHandler(), btconmngr.socket)
                    /*
                         After receiving every chunk of data, thread runs
                         timer with "rx_timeout_ms" milliseconds timeout.
                         If no more data will be received for this time,
                         "packet" reception will be completed.
                    */
                    btth_r.rx_timeout_ms = 80
                    btth_r.start()
                    btth_w = RsBtWriteThread(btWriteHandler(), btconmngr.socket)
                    btth_w.start()

                    conn_disconn_rq = false
                    uiEnableConditional()
                }
                RsBtConnMngrThread.STAT.DISCONNECTED.ordinal -> {
                    conn_disconn_rq = false
                    uiEnableConditional()
                }
                RsBtConnMngrThread.STAT.SOCKET_CLOSE_FAIL.ordinal -> {
                    //!UNUSED
                }
            }
        }
    }

    //Bluetooth Device Manager
    private var devices: Set<BluetoothDevice?>? = null
    private var btdevmngr = object : RsBtDevMngr(this) {
        override fun rsBtMngrMessage(message: Int) {
            when (message) {
                STAT.TURNED_ON.ordinal -> {
                    devices = getDevices()
                    updateDevices()
                }
                STAT.TURNED_OFF.ordinal -> {
                    devices = null
                    updateDevices()
                }
                STAT.DEVICES_UPDATED.ordinal -> {
                    devices = getDevices()
                    updateDevices()
                }
            }
        }
    }
    private var devices_names = mutableListOf<String>()

    //UI
    private fun updateDevices(){
        var i = 0
        if(devices != null){
            devices_names = mutableListOf()
            @SuppressLint("MissingPermission")
            while(i != devices!!.count()){
                devices_names.add(devices!!.elementAt(i)?.name.toString())
                i++
            }
        }else{
            devices_names.add(resources.getString(R.string.spinner_no_devices))
        }
        val aa = ArrayAdapter(this, android.R.layout.simple_spinner_item, devices_names)
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner_devices.adapter = aa
        uiEnableConditional()
    }
    private fun uiEnableConditional(){
        runOnUiThread {
            var isconnected = false
            if (::btconmngr.isInitialized) {
                if (btconmngr.isConnected()) {
                    isconnected = true
                }
            }

            if (conn_disconn_rq) {
                spinner_devices.isEnabled = false
                button_connect.isEnabled = false
                button_send.isEnabled = false
            } else {
                if (isconnected) {
                    button_connect.isEnabled = true
                    spinner_devices.isEnabled = false
                    button_send.isEnabled = true
                    button_connect.setText(R.string.button_connected)
                } else {
                    if (devices_names.isNotEmpty()) {
                        spinner_devices.isEnabled = true
                        button_connect.isEnabled = true
                    } else {
                        spinner_devices.isEnabled = false
                        button_connect.isEnabled = false
                    }
                    button_send.isEnabled = false
                    button_connect.setText(R.string.button_disconnected)
                }
            }
            button_clear.isEnabled = textView_rx.text.toString() != ""
        }
    }
    private fun clearClicked() {
        textView_rx.text = ""
        uiEnableConditional()
    }
    private fun sendClicked() {
        btth_w.sendString(textView_tx.text.toString())
    }
    private fun connectClicked() {
        conn_disconn_rq = true
        if(::btconmngr.isInitialized){
            if(btconmngr.isConnected()){
                uiEnableConditional()
                btconmngr.disconnect()
                return
            }
        }
        btconmngr = RsBtConnMngrThread(btConnHandler(), devices!!.elementAt(spinner_devices.selectedItemPosition)!!, this)
        btconmngr.start()

        runOnUiThread { button_connect.setText(R.string.button_connecting) }
        uiEnableConditional()
    }

    //App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //UI
        spinner_devices = findViewById(R.id.Spinner_Devices)
        button_connect = findViewById(R.id.Button_Connect)
        button_clear = findViewById(R.id.Button_Clear)
        button_send = findViewById(R.id.Button_Send)
        textView_rx = findViewById(R.id.TextView_Rx_Data)
        textView_tx = findViewById(R.id.EditText_Tx)

        //App
        btdevmngr.init()
        updateDevices()

        //Enable button clicks
        button_connect.setOnClickListener{ connectClicked() }
        button_clear.setOnClickListener{ clearClicked() }
        button_send.setOnClickListener{ sendClicked() }
    }
    override fun onDestroy() {
        super.onDestroy()
        btdevmngr.destroy()
    }
}