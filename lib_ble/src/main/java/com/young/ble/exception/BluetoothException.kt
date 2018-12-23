package com.young.ble.exception

import android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
import android.util.SparseArray
import java.lang.Exception

/**
 * @auth young
 * @date 2018 2018/12/22 12:54
 */
class BluetoothException(msg: String) : Exception(msg) {
    constructor(errCode: Int) : this(getErrorString(errCode))

    init {
        map.put(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED")
    }

    companion object {
        val map: SparseArray<String> = SparseArray()
        fun getErrorString(errCode: Int): String {
            val get = map.get(errCode)
            return get ?: ""
        }
    }

}