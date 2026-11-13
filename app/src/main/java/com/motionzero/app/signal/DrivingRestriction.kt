package com.motionzero.app.signal

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 주행 규제(CarUxRestrictions) 구독.
 * 플랫폼이 "주행 중 최적화 필요(requiresDistractionOptimization)"를 알리면 화면을 주행 모드로 줄인다
 * (설정·체험·기록을 숨기고 예고 카드·좌석·증상 버튼만 남김).
 * 차량이 아닌 기기에서는 android.car 가 없으므로 조용히 아무것도 하지 않는다.
 */
class DrivingRestriction(context: Context, private val onChange: (Boolean) -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        try {
            car = Car.createCar(appContext, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) attach(c) else detach()
            }
        } catch (t: Throwable) {
            // android.car 미탑재(일반 휴대폰 등): 항상 정차 화면
            car = null
        }
    }

    fun stop() {
        detach()
        runCatching { car?.disconnect() }
        car = null
    }

    private fun attach(c: Car) {
        try {
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            m.registerListener { publish(it) }
            publish(m.currentCarUxRestrictions)
        } catch (t: Throwable) {
            manager = null
        }
    }

    private fun detach() {
        runCatching { manager?.unregisterListener() }
        manager = null
    }

    private fun publish(r: CarUxRestrictions?) {
        val restricted = r?.isRequiresDistractionOptimization ?: false
        main.post { onChange(restricted) }
    }
}
