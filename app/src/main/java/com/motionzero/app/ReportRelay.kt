package com.motionzero.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.motionzero.app.core.TripReport
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * 도착 리포트를 뷰모델 → [TripReportActivity] 로 넘기는 통로. TripReport 는 Parcelable 이 아니어서 같은 프로세스 안에서만 쓴다.
 * Activity 가 다시 만들어져도 값이 남아 있도록 프로세스 단위로 둔다.
 */
object ReportRelay {
    /**
     * 지금 보여 줄 리포트. 리포트 화면에서 확인하거나, 이미 본 리포트를 두고 다시 출발하면(기어 D·R, 주행 규제) null 이 되고
     * 열린 리포트 화면은 스스로 닫힌다. 아직 보지 않은 리포트는 새 주행이 1분을 넘길 때까지 남는다.
     */
    val current = MutableStateFlow<TripReport?>(null)

    /** 리포트 화면을 이미 띄운 리포트. MainActivity 가 다시 만들어져도 같은 리포트를 두 번 띄우지 않게 한다. */
    @Volatile
    var launched: TripReport? = null

    /** 리포트 화면이 실제로 열린 리포트. 앱이 내비 뒤에 있어 화면 실행이 막혔는지 판단하는 데 쓴다. */
    @Volatile
    var opened: TripReport? = null

    /** 화면에 보이는(onStart~onStop) MainActivity 수. 보이는 동안에는 시스템이 리포트 화면 실행을 막지 않는다. */
    private val startedScreens = AtomicInteger()

    val uiStarted: Boolean get() = startedScreens.get() > 0

    fun onScreenStarted() {
        startedScreens.incrementAndGet()
    }

    fun onScreenStopped() {
        startedScreens.decrementAndGet()
    }
}

/** 표준 지도 앱 인텐트로 내비게이션을 앞으로 부른다. Pleos Connect 에서는 기본 내비 앱이 받는다. */
fun Context.openNavigation() {
    val maps = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(maps)
    } catch (e: ActivityNotFoundException) {
        Log.i("MotionZero", "지도 앱이 없어 현재 화면에 머문다", e)
    }
}
