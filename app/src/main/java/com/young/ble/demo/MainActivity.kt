package com.young.ble.demo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.widget.Toast
import com.young.ble.BluetoothClient
import com.young.ble.impl.BluetoothClientV2
import com.young.ble.v2.BluetoothLeInitialization
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val bluetoothClient: BluetoothClient by lazy {
        BluetoothClientV2(BluetoothLeInitialization(this))
    }

    private val dataList: MutableList<String> by lazy {
        mutableListOf<String>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bluetoothClient.openBluetooth()
        rv_devices.layoutManager = LinearLayoutManager(this)
        val deviceAdapter = DevideAdapter(this, dataList)
        rv_devices.adapter = deviceAdapter
        btn_scan.setOnClickListener {
            dataList.clear()
            bluetoothClient.search(5000, true)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ device ->
                    dataList.add(device.deviceName!! + "-----${device.mac}")
                    deviceAdapter.notifyDataSetChanged()
                }) { e ->
                    showToast(e.message!!)
                }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

}
