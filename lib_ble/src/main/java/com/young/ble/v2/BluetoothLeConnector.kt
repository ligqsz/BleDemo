package com.young.ble.v2

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.young.ble.TAG
import io.reactivex.functions.Consumer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Service for managing connection and data co
 * hosted on a given Bluetooth LE device.
 * @auth young
 * @date 2018 2018/12/22 10:52
 */
@Suppress("unused")
class BluetoothLeConnector(
    private val context: Context,
    private val mBluetoothAdapter: BluetoothAdapter?,
    private val mBluetoothDeviceAddress: String,
    private val mWorkHandler: Handler
) {
    var mOnConnectListener: OnConnectListener? = null
    var mOnDataAvailableListener: OnDataAvailableListener? = null

    private var mBluetoothGatt: BluetoothGatt? = null
    private val mAlertHandler: Handler by lazy {
        val thread = HandlerThread("bluetooth alerter")
        thread.start()
        Handler(thread.looper)
    }

    private val mConnectStatus = AtomicInteger(BluetoothGatt.STATE_DISCONNECTED)

    private val mIsStartService = AtomicBoolean(false)

    private val mDisconnectTime = AtomicLong(SystemClock.elapsedRealtime())

    private val mConnectTime = AtomicLong(SystemClock.elapsedRealtime())

    /**
     * Implements callback methods for GATT events that the app cares about. For
     * example, connection change and services discovered.
     * GATT连接的各种监听回调方法
     */
    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt, status: Int,
            newState: Int
        ) {
            mWorkHandler.post(Runnable {
                Log.d(
                    TAG, "onConnectionStateChange: thread "
                            + Thread.currentThread() + " status " + newState
                )

                // 清空连接初始化的超时连接任务代码
                mAlertHandler.removeCallbacksAndMessages(null)

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val err = "Cannot connect device with error status: $status"
                    disconnectGatt()
                    Log.e(TAG, err)
                    mOnConnectListener?.onError(err)
                    mConnectStatus.set(BluetoothGatt.STATE_DISCONNECTED)
                    return@Runnable
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // setting connect status is connected
                    mConnectStatus.set(BluetoothGatt.STATE_CONNECTED)
                    mOnConnectListener?.onConnect()

                    // Attempts to discover services after successful connection.
                    mIsStartService.set(false)
                    if (!gatt.discoverServices()) {
                        val err = "discover service return false"
                        Log.e(TAG, err)
                        gatt.disconnect()
                        mOnConnectListener?.onError(err)
                        return@Runnable
                    }

                    // 解决连接 Service 过长的问题
                    // 有些手机第一次启动服务的时间大于 2s
                    mAlertHandler.postDelayed({
                        mWorkHandler.post {
                            if (!mIsStartService.get()) {
                                gatt.disconnect()
                            }
                        }
                    }, 3000L)

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                    if (!mIsStartService.get()) {
                        val err = "service not found force disconnect"
                        Log.e(TAG, err)
                        mOnConnectListener?.onError(err)
                    }

                    mOnConnectListener?.onDisconnect()
                    close()
                    mConnectStatus.set(BluetoothGatt.STATE_DISCONNECTED)
                }
            })
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            mWorkHandler.post {
                // 清空连接服务设置的超时回调
                mIsStartService.set(true)
                mAlertHandler.removeCallbacksAndMessages(null)

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "进入通道连接！！！！ in thread " + Thread.currentThread())
                    mOnConnectListener?.onServiceDiscover()
                } else {
                    val err = "onServicesDiscovered received: $status"
                    Log.e(TAG, err)
                    gatt.disconnect()
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {

            Log.d(
                TAG, "callback characteristic read status " + status
                        + " in thread " + Thread.currentThread()
            )
            if (status == BluetoothGatt.GATT_SUCCESS && mOnDataAvailableListener != null) {
                mOnDataAvailableListener?.onCharacteristicRead(
                    characteristic.value,
                    status
                )
            }

        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {

            Log.d(TAG, "callback characteristic change in thread " + Thread.currentThread())
            if (mOnDataAvailableListener != null) {
                mOnDataAvailableListener?.onCharacteristicChange(
                    characteristic.uuid, characteristic.value
                )
            }

        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "callback characteristic write in thread " + Thread.currentThread())
            if (mOnDataAvailableListener != null) {
                mOnDataAvailableListener?.onCharacteristicWrite(
                    characteristic.uuid, status
                )
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "callback descriptor write in thread " + Thread.currentThread())

            if (mOnDataAvailableListener != null) {
                mOnDataAvailableListener?.onDescriptorWrite(
                    descriptor.uuid, status
                )
            }
        }
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     */
    fun connect(callback: OnConnectListener) {
        mWorkHandler.post(Runnable {
            Log.d(TAG, "connect: in thread " + Thread.currentThread())

            if (mBluetoothAdapter == null) {
                val err = "BluetoothAdapter not initialized or unspecified address."
                Log.e(TAG, err)
                callback.onError(err)
                return@Runnable
            }

            val device = mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress)
            if (device == null) {
                val err = "Device not found. Unable to connect."
                Log.e(TAG, err)
                callback.onError(err)
                return@Runnable
            }

            // 避免自动硬件断开后又自动连接，导致 service 回调被调用
            // 这里有隐患，实践证明 close 方法是异步调用的且单例，
            // 这就是说当一个 gatt 被创建之后，调用之前的 gatt 可能会把当前的 gatt close掉.
            // 最终造成 gatt 泄漏问题.
            // 一个解决方案就是延长连接硬件的时间
            if (mConnectStatus.get() != BluetoothGatt.STATE_DISCONNECTED) {
                val err = "Device is connecting"
                Log.e(TAG, err)
                callback.onError(err)
                return@Runnable
            }

            // 检查完没有任何错误再设置回调，确保上一次没有完成的操作得以继续回调，而不是被新的回调覆盖
            mOnConnectListener = callback
            // We want to directly connect to the device, so we are setting the
            // autoConnect
            // parameter to false.
            Log.d(TAG, "Trying to create a new connection.")
            mConnectTime.set(SystemClock.elapsedRealtime())
            mBluetoothGatt = device.connectGatt(context, false, mGattCallback)
            if (mBluetoothGatt == null) {
                val err = "bluetooth is not open!"
                Log.e(TAG, err)
                callback.onError(err)
                return@Runnable
            }

            mConnectStatus.set(BluetoothGatt.STATE_CONNECTING)
            mIsStartService.set(false)

            // 开一个定时器，如果超出 20s 就强制断开连接
            // 这个定时器必须在连接上设备之后清掉
            mAlertHandler.removeCallbacksAndMessages(null)
            mAlertHandler.postDelayed({
                mWorkHandler.post {
                    if (mConnectStatus.get() == BluetoothGatt.STATE_CONNECTING) {
                        disconnectGatt()
                        val err = "connect timeout, cannot not connect device"
                        Log.e(TAG, err)
                        callback.onError(err)
                    }
                }
            }, 20000L)
        })
    }

    fun disconnect() {
        mWorkHandler.post {
            disconnectGatt()
        }
    }

    private fun callDataAvailableListenerError(err: String) {
        Log.e(TAG, err)
        if (mOnDataAvailableListener != null) {
            mOnDataAvailableListener?.onError(err)
        }
    }

    private fun checkChannelAndDo(
        service: UUID,
        characteristic: UUID?,
        action: Consumer<BluetoothGattCharacteristic>
    ) {

        if (mBluetoothAdapter == null || mBluetoothGatt == null
            || mConnectStatus.get() != BluetoothGatt.STATE_CONNECTED
        ) {
            callDataAvailableListenerError("should be connect first!")
            return
        }

        val serviceChanel = mBluetoothGatt?.getService(service)
        if (serviceChanel == null) {
            callDataAvailableListenerError("service is null")
            return
        }

        val gattCharacteristic = serviceChanel.getCharacteristic(characteristic)

        if (characteristic == null) {
            callDataAvailableListenerError("characteristic is null")
            return
        }

        try {
            action.accept(gattCharacteristic)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * 从蓝牙模块读取数据, 读取的数据将会异步回调到 {@link com.young.ble.v2.BluetoothLeConnector#getMOnDataAvailableListener}
     * 方法设置的监听中
     */
    fun readCharacteristic(service: UUID, characteristic: UUID) {
        mWorkHandler.post {
            Log.d(TAG, "in readCharacteristic")
            checkChannelAndDo(service, characteristic,
                Consumer { bluetoothGattCharacteristic ->
                    if (mBluetoothGatt?.readCharacteristic(bluetoothGattCharacteristic)!!) {

                        callDataAvailableListenerError("cannot start characteristic read")
                    }
                })
        }
    }

    /**
     * write something data to characteristic
     */
    fun writeCharacteristic(
        service: UUID,
        characteristic: UUID,
        values: ByteArray
    ) {
        mWorkHandler.post {
            Log.d(TAG, "writing characteristic in thread " + Thread.currentThread())

            checkChannelAndDo(service, characteristic,
                Consumer { bluetoothGattCharacteristic ->
                    bluetoothGattCharacteristic.value = values

                    if (!mBluetoothGatt?.writeCharacteristic(bluetoothGattCharacteristic)!!) {

                        callDataAvailableListenerError("cannot start characteristic write")
                    }
                })
        }
    }

    /**
     * 往特定的通道写入数据
     */
    fun writeCharacteristic(service: UUID, characteristic: UUID, values: String) {
        writeCharacteristic(service, characteristic, values.toByteArray())
    }

    /**
     * 设置获取特征值UUID通知
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification. False otherwise.
     */
    fun setCharacteristicNotification(
        service: UUID,
        characteristic: UUID,
        enabled: Boolean
    ) {

        mWorkHandler.post {
            checkChannelAndDo(service, characteristic,
                Consumer { gattCharacteristic ->
                    if (enabled) {
                        Log.i(TAG, "Enable Notification")
                        mBluetoothGatt?.setCharacteristicNotification(gattCharacteristic, true)
                        val descriptor = gattCharacteristic
                            .getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID)
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                        if (!mBluetoothGatt?.writeDescriptor(descriptor)!!) {
                            callDataAvailableListenerError("cannot open notification channel")
                        }
                    } else {
                        Log.i(TAG, "Disable Notification")
                        mBluetoothGatt?.setCharacteristicNotification(gattCharacteristic, false)
                        val descriptor = gattCharacteristic
                            .getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID)
                        descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

                        if (!mBluetoothGatt?.writeDescriptor(descriptor)!!) {
                            callDataAvailableListenerError("cannot close notification channel")
                        }
                    }
                })
        }
    }

    private fun disconnectGatt() {
        Log.d(TAG, "disconnect: in thread " + Thread.currentThread())

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized")
            return
        }

        if (mConnectStatus.get() == BluetoothGatt.STATE_DISCONNECTED) {
            close()
            return
        }

        mBluetoothGatt?.disconnect()

        // 确保 Gatt 一定会被 close
        if (mConnectStatus.get() == BluetoothGatt.STATE_CONNECTING) {
            mAlertHandler.removeCallbacksAndMessages(null)
            close()
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure
     * resources are released properly.
     */
    private fun close() {
        Log.d(TAG, "close: in thread " + Thread.currentThread())

        if (mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized")
            return
        }

        mDisconnectTime.set(SystemClock.elapsedRealtime())
        mBluetoothGatt?.close()
        mBluetoothGatt = null
        mConnectStatus.set(BluetoothGatt.STATE_DISCONNECTED)
    }

    companion object {
        val CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")!!
    }

    /**
     * 连接状态回调
     */
    interface OnConnectListener {
        fun onConnect()

        fun onDisconnect()

        fun onServiceDiscover()

        fun onError(msg: String)
    }

    /**
     * 读写回调接口
     */
    interface OnDataAvailableListener {
        fun onCharacteristicRead(values: ByteArray, status: Int)

        fun onCharacteristicChange(characteristic: UUID, values: ByteArray)

        fun onCharacteristicWrite(characteristic: UUID, status: Int)

        fun onDescriptorWrite(descriptor: UUID, status: Int)

        fun onError(msg: String)
    }
}