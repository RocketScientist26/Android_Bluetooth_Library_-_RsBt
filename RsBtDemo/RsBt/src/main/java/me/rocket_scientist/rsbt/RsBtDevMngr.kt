package me.rocket_scientist.rsbt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.lang.reflect.Method
import kotlin.system.exitProcess

open class RsBtDevMngr(private val activity: AppCompatActivity){
    enum class STAT {
        ENABLE_PERMISSION_RQ,
        TURNED_ON,
        TURNED_OFF,
        DEVICES_UPDATED,
        OK,
        FAIL
    }

    //Private
    private lateinit var perm_rq: ActivityResultLauncher<Intent>
    private fun checkPermissions(): STAT {
        if(Build.VERSION.SDK_INT < 31){
            if ((ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) || (ActivityCompat.checkSelfPermission((activity as Context), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)) {
                //Bluetooth usage permission not allowed, request
                return STAT.ENABLE_PERMISSION_RQ
            }
        }else{
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                //Bluetooth usage permission not allowed, request
                return STAT.ENABLE_PERMISSION_RQ
            }
        }
        return STAT.OK
    }
    private fun enableRq() {
        val bluetoothManager: BluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as (BluetoothManager)

        //Check for permissions
        if(checkPermissions() == STAT.ENABLE_PERMISSION_RQ){
            if(Build.VERSION.SDK_INT < 31){
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH), STAT.ENABLE_PERMISSION_RQ.ordinal)
            }else{
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), STAT.ENABLE_PERMISSION_RQ.ordinal)
            }
            //Permission requested
            return
        }
        //Check if bluetooth is enabled
        if (!bluetoothManager.adapter.isEnabled){
            //Enable requested
            perm_rq.launch(Intent(ACTION_REQUEST_ENABLE))
            return
        }

        //All fine, BT is turned on
        rsBtMngrMessage(STAT.TURNED_ON.ordinal)
    }

    //System broadcast receiver
    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(EXTRA_STATE, ERROR)
                val prev_state = intent.getIntExtra(EXTRA_PREVIOUS_STATE, ERROR)
                if ((state == STATE_TURNING_OFF) && ((prev_state == STATE_ON) || (prev_state == STATE_TURNING_ON))) {
                    rsBtMngrMessage(STAT.TURNED_OFF.ordinal)
                    enableRq()
                }else if(state == ERROR){
                    exit()
                }
            }else if(intent?.action == ACTION_BOND_STATE_CHANGED) {
                rsBtMngrMessage(STAT.DEVICES_UPDATED.ordinal)
            }
        }
    }

    fun init(){
        val bluetoothManager: BluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as (BluetoothManager)

        //Check if Bluetooth available
        if (bluetoothManager.adapter == null) {
            //Device doesn't support Bluetooth
            //!TBD toast message HERE, exit by clicking EXIT
            exit()
        }
        val filter = IntentFilter()
        filter.addAction(ACTION_STATE_CHANGED)
        filter.addAction(ACTION_BOND_STATE_CHANGED)
        activity.registerReceiver(broadCastReceiver, filter)

        //Register bluetooth enable callback
        perm_rq = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                exit()
            }else{
                rsBtMngrMessage(STAT.TURNED_ON.ordinal)
            }
        }
        enableRq()
    }

    //OnDestroy
    fun destroy() {
        activity.unregisterReceiver(broadCastReceiver)
    }

    //Public functions
    fun unpair(device: BluetoothDevice): STAT{
        try {
            device::class.java.getMethod("removeBond").invoke(device)
        } catch (e: Exception) {
            return STAT.FAIL
        }
        return STAT.OK
    }
    fun getDevices(): Set<BluetoothDevice?>? {
        @SuppressLint("MissingPermission")
        if (checkPermissions() == STAT.OK) {
            val bluetoothManager: BluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as (BluetoothManager)
            return bluetoothManager.adapter.bondedDevices
        }
        return null
    }
    fun exit() {
        activity.unregisterReceiver(broadCastReceiver)
        activity.finish()
        exitProcess(0)
    }

    //Overridable
    open fun rsBtMngrMessage(message: Int){
    }
}