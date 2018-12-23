package com.young.ble.been

import android.os.Parcel
import android.os.Parcelable

/**
 * save the message of bluetooth device
 * @auth young
 * @date 2018 2018/12/22 10:32
 */
class BleDevice(var deviceName: String?, var mac: String?, var rssi: Int = 0) : Parcelable {
    constructor() : this("", "", 0)
    private constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readString(),
        parcel.readInt()
    )

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.run {
            val intArr = IntArray(1)
            writeIntArray(intArr)
            rssi = intArr[0]
            val strings = Array<String>(3) { "" }
            writeStringArray(strings)
            deviceName = strings[0]
            mac = strings[1]
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BleDevice> {
        override fun createFromParcel(parcel: Parcel): BleDevice {
            return BleDevice(parcel)
        }

        override fun newArray(size: Int): Array<BleDevice?> {
            return arrayOfNulls(size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val device = other as BleDevice

        return mac == device.mac
    }

    override fun hashCode(): Int {
        return mac.hashCode()
    }

    override fun toString(): String {
        return "BleDevice(deviceName=$deviceName, mac=$mac, rssi=$rssi)"
    }


}