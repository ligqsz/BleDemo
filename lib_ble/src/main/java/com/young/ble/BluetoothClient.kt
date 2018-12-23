package com.young.ble

import com.young.ble.been.BleDevice
import io.reactivex.Observable
import java.util.*

/**
 * the interface of bluetooth api
 * @auth young
 * @date 2018 2018/12/22 10:32
 */
const val TAG = "BLE"

interface BluetoothClient {
    fun search(millis: Int, cancel: Boolean): Observable<BleDevice>

    fun stopSearch()

    fun connect(mac: String): Observable<String>

    fun disconnect(mac: String)

    fun write(mac: String, service: UUID, characteristic: UUID, values: ByteArray): Observable<String>

    fun <D> registerNotify(
        mac: String,
        service: UUID,
        characteristic: UUID,
        onSuccess: (D) -> Unit,
        onFail: (String) -> Unit
    ): Observable<String>

    fun clean(mac: String)

    fun cleanAll()

    fun openBluetooth()

    fun closeBluetooth()
}