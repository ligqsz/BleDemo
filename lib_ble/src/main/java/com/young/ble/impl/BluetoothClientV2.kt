package com.young.ble.impl

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.text.TextUtils
import android.util.Log
import com.young.ble.BluetoothClient
import com.young.ble.TAG
import com.young.ble.been.BleDevice
import com.young.ble.exception.BluetoothException
import com.young.ble.v2.BluetoothLeConnector
import com.young.ble.v2.BluetoothLeInitialization
import io.reactivex.Observable
import java.util.*

/**
 * @auth young
 * @date 2018 2018/12/22 10:38
 */
class BluetoothClientV2(private val initClient: BluetoothLeInitialization) : BluetoothClient {
    override fun search(millis: Int, cancel: Boolean): Observable<BleDevice> {
        return Observable.create {
            val searcher = initClient.getBluetoothSearcher()
            if (searcher?.isScanning()!! && !cancel) {
                it.onError(BluetoothException("is searching now"))
                return@create
            }

            if (searcher.isScanning()) {
                stopSearch()
            }

            initClient.getBluetoothSearcher()?.scanLeDevice(millis, object : ScanCallback() {
                val addresses = mutableSetOf<String>()
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val deviceName = result?.device?.name ?: "UNKNOWN DEVICE"
                    val address = result?.device?.address
                    val rssi = result?.rssi ?: 0
                    val bleDevice = BleDevice(deviceName, address, rssi)
                    if (!TextUtils.isEmpty(address) && !addresses.contains(address)) {
                        addresses.add(address!!)
                        it.onNext(bleDevice)
                    }
                }

                override fun onScanFailed(errorCode: Int) = it.onError(BluetoothException(errorCode))

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    Log.d(TAG, "onBatchScanResults--size:${results?.size}")
                    it.onComplete()
                }
            })
        }
    }

    override fun stopSearch() {
        initClient.getBluetoothSearcher()?.stopScanLeDevice()
    }

    override fun connect(mac: String): Observable<String> {
        return Observable.create {
            val connector = initClient.getBluetoothLeConnector(mac)
            connector.mOnDataAvailableListener = object : BluetoothLeConnector.OnDataAvailableListener {
                override fun onCharacteristicRead(values: ByteArray, status: Int) {
                }

                override fun onCharacteristicChange(characteristic: UUID, values: ByteArray) {
                }

                override fun onCharacteristicWrite(characteristic: UUID, status: Int) {
                }

                override fun onDescriptorWrite(descriptor: UUID, status: Int) {
                }

                override fun onError(msg: String) {
                }

            }

            connector.connect(object : BluetoothLeConnector.OnConnectListener {
                override fun onConnect() {
                }

                override fun onDisconnect() {
                }

                override fun onServiceDiscover() {
                    it.onNext(mac)
                    it.onComplete()
                }

                override fun onError(msg: String) {
                    it.onError(BluetoothException(msg))
                }

            })
        }
    }

    override fun disconnect(mac: String) {
        initClient.getBluetoothLeConnector(mac).disconnect()
    }

    override fun write(mac: String, service: UUID, characteristic: UUID, values: ByteArray): Observable<String> {
        return Observable.create { e ->
            val connector = initClient.getBluetoothLeConnector(mac)

            val onConnectListener = connector.mOnDataAvailableListener

            connector.mOnDataAvailableListener = object : BluetoothLeConnector.OnDataAvailableListener {
                override fun onCharacteristicRead(values: ByteArray, status: Int) {
                    onConnectListener?.onCharacteristicRead(values, status)
                }

                override fun onCharacteristicChange(characteristic: UUID, values: ByteArray) {
                    onConnectListener?.onCharacteristicChange(characteristic, values)
                }

                override fun onCharacteristicWrite(characteristic: UUID, status: Int) {
                    e.onNext(mac)
                    e.onComplete()
                }

                override fun onDescriptorWrite(descriptor: UUID, status: Int) {
                    onConnectListener?.onDescriptorWrite(descriptor, status)
                }

                override fun onError(msg: String) {
                    e.onError(Exception(msg))
                }
            }
            connector.writeCharacteristic(service, characteristic, values)
        }
    }

    override fun <D> registerNotify(
        mac: String,
        service: UUID,
        characteristic: UUID,
        onSuccess: (D) -> Unit,
        onFail: (String) -> Unit
    ): Observable<String> {
        return Observable.create {}
    }

    override fun clean(mac: String) {
        initClient.cleanConnector(mac)
    }

    override fun cleanAll() {
        initClient.cleanAllConnector()
    }

    override fun openBluetooth() {
        initClient.initialize()
    }

    override fun closeBluetooth() {

    }
}