package com.young.ble.v2

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.young.ble.TAG
import java.util.concurrent.ConcurrentHashMap

/**
 * 蓝牙初始化类
 * @auth young
 * @date 2018 2018/12/22 10:42
 */
@Suppress("unused")
class BluetoothLeInitialization(private val context: Context) {
    companion object {
        @JvmStatic
        private val mBluetoothWorker: Handler by lazy {
            val thread = HandlerThread("bluetooth worker")
            thread.start()
            Handler(thread.looper)
        }
    }

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothManager: BluetoothManager? = null
    private val mGattConnectorMap: MutableMap<String, BluetoothLeConnector> by lazy {
        ConcurrentHashMap<String, BluetoothLeConnector>(0)
    }
    private var mBluetoothSearcher: BluetoothLeSearcher? = null

    /**
     * 初始化BluetoothAdapter
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    fun initialize(): Boolean {
        if (mBluetoothManager == null) {
            mBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }

        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = mBluetoothManager?.adapter
            if (mBluetoothAdapter == null) {
                Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
                return false
            }
        }

        return mBluetoothAdapter!!.isEnabled || mBluetoothAdapter!!.enable()
    }

    fun getBluetoothSearcher(): BluetoothLeSearcher? {
        if (mBluetoothSearcher == null) {
            synchronized(BluetoothLeInitialization::class) {
                if (mBluetoothSearcher == null) {
                    if (mBluetoothAdapter == null) {
                        Log.e(
                            TAG,
                            "cannot create BluetoothLeSearcher instance because not " + "initialize, please call initialize() method"
                        )
                        return null
                    }
                }
                mBluetoothSearcher = BluetoothLeSearcher(context, mBluetoothAdapter, mBluetoothWorker)
            }
        }
        return mBluetoothSearcher
    }

    fun getBluetoothLeConnector(mac: String): BluetoothLeConnector {
        var result: BluetoothLeConnector? = mGattConnectorMap[mac]!!
        if (result != null) {
            return result
        }
        result = BluetoothLeConnector(context, mBluetoothAdapter, mac, mBluetoothWorker)
        mGattConnectorMap[mac] = result
        return result
    }

    fun cleanConnector(mac: String) {
        val result = mGattConnectorMap[mac]
        result?.run {
            mGattConnectorMap.remove(mac)
            disconnect()
            mOnConnectListener = null
        }
    }

    /**
     * 在不在需要连接蓝牙设备的时候，
     * 或者生命周期暂停的时候调用这一个方法
     */
    fun cleanAllConnector() {
        mGattConnectorMap.keys.forEach {
            cleanConnector(it)
        }
    }
}