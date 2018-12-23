package com.young.ble.utils

import android.app.Activity
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable

object BleUtils {
    private val disposables: CompositeDisposable = CompositeDisposable()
    @JvmStatic
    fun requestPermission(
        act: Activity,
        permission: String,
        success: (Boolean) -> Unit = {},
        fail: (T: Throwable) -> Unit = {},
        complete: () -> Unit = {}
    ) {
        disposables.add(
            RxPermissions(act)
                .request(permission)
                .subscribe(success, fail, complete)
        )
    }

    @JvmStatic
    fun requestPermissions(
        act: Activity,
        vararg permissions: String,
        success: (Boolean) -> Unit = {}, fail: (T: Throwable) -> Unit = {},
        complete: () -> Unit = {}
    ) {
        val observableList: MutableList<Observable<Boolean>> = mutableListOf()
        for (permission in permissions) {
            observableList.add(RxPermissions(act).requestEach(permission)
                .map { it.granted })
        }
        disposables.add(
            Observable.concat(observableList)
                .subscribe(success, fail, complete)
        )
    }
}