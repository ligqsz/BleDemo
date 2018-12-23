package com.young.ble.v2

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.util.Log
import com.young.ble.TAG
import com.young.ble.utils.BleUtils
import java.lang.Exception
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @auth young
 * @date 2018 2018/12/22 10:53
 */
@Suppress("unused")
class BluetoothLeSearcher(
    private val context: Context,
    private val mBluetoothAdapter: BluetoothAdapter?,
    private val mHandler: Handler
) {
    private val mAlertHandler: Handler by lazy {
        val thread = HandlerThread("bluetooth searcher handler")
        thread.start()
        Handler(thread.looper)
    }
    private var mScanCallback: ScanCallback? = null
    private val mScanning = AtomicBoolean(false)

    private fun wrapCallback(callback: ScanCallback): ScanCallback {

        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                callback.onScanResult(callbackType, result)
            }

            override fun onScanFailed(errorCode: Int) {
                callback.onScanFailed(errorCode)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                callback.onBatchScanResults(results)
            }
        }
    }

    /**
     * 指定开始扫描蓝牙服务. 如果一个扫描服务正在运行,
     * 马上停止当前的扫描服务, 只进行新的扫描服务.
     */
    fun scanLeDevice(
        scanMillis: Int,
        callback: ScanCallback
    ) {

        Handler(Looper.getMainLooper()).post {
            BleUtils.requestPermission(context as Activity, Manifest.permission.ACCESS_COARSE_LOCATION, {
                runOn(Runnable {
                    if (it) scan(callback, scanMillis)
                    else callback.onScanFailed(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)
                })

            }, {
                runOn(Runnable {
                    Log.e(TAG, "Cannot have location permission")
                    callback.onScanFailed(SCAN_FAILED_INTERNAL_ERROR)
                })
            })
        }
    }

    private fun scan(callback: ScanCallback, scanMillis: Int) {
        if (mScanning.get()) {
            stopScan()
        }

        mScanCallback = wrapCallback(callback)

        // Stops scanning after a pre-defined scan period.
        // 预先定义停止蓝牙扫描的时间（因为蓝牙扫描需要消耗较多的电量）
        mAlertHandler.removeCallbacksAndMessages(null)
        mAlertHandler.postDelayed({ stopScanLeDevice() }, scanMillis.toLong())

        mScanning.set(true)

        mBluetoothAdapter?.bluetoothLeScanner?.startScan(callback)
    }

    fun stopScan() {
        if (mScanning.get()) {

            mScanning.set(false)
            mAlertHandler.removeCallbacksAndMessages(null)
            mBluetoothAdapter?.bluetoothLeScanner?.stopScan(mScanCallback)

            mScanCallback = null
        }
    }

    fun stopScanLeDevice() {
        runOn(Runnable { stopScan() })
    }

    private fun runOn(runnable: Runnable) {
        mHandler.post(runnable)
    }

    fun isScanning(): Boolean {
        return mScanning.get()
    }
}