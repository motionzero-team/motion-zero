package com.motionzero.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.motionzero.app.core.TripReport
import com.motionzero.app.ui.MotionZeroTheme
import com.motionzero.app.ui.TripReportScreen
import kotlinx.coroutines.launch

/**
 * 도착 후 트립 리포트. manifest 에서 landscape 로 지정해 이 화면만 앱 영역 전체(창 2개)를 쓴다.
 * 내비 옆 창에서 열리면 Pleos 홈 화면이 "창을 넓혀 달라"는 안내를 먼저 띄우고, 사용자가 한 번 누르면 넓어진다.
 * (앱이 창 크기를 직접 바꾸는 API 는 없다.) 앱이 내비 뒤에 있을 때 도착하면 시스템이 화면 실행을 막으므로,
 * 서비스가 음성으로 알리고 사용자가 앱 아이콘을 누르면 MainActivity 가 이 화면을 연다.
 *
 * 닫을 때 내비게이션을 앞으로 부르는 일은 이 화면이 그 순간 직접 한다(결과 코드로 미루면 늦게 전달되어 엉뚱한 때 내비로 넘어간다).
 * - 확인·뒤로: 내비로 돌아간다.
 * - 다시 출발(기어 D·R, 주행 규제): 이미 본 리포트이므로 [ReportRelay.current] 가 비워져 스스로 닫힌다. 화면에 보이고 있었을 때만 내비를 부른다.
 * - manifest 에서 singleTop: 서비스와 MainActivity 가 거의 동시에 띄워도 한 화면만 남는다.
 */
class TripReportActivity : ComponentActivity() {
    private var report: TripReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shown = ReportRelay.current.value
        val state = MotionZeroController.get(this).ui.value
        if (shown == null || state.engine.tripActive || state.restricted) {
            // 리포트가 이미 사라졌거나 주행 중이다. 앱이 내비 뒤에 있을 때 막힌 실행 기록이 태스크에 남아 있다가
            // 사용자가 아이콘을 누를 때 만들어지는 경우다. 사용자는 앱(증상 버튼)을 열려는 것이므로 홈 화면을 보여 준다.
            // 아직 보지 않은 리포트는 다시 정차하면 MainActivity 가 띄우도록 표시를 되돌린다.
            if (shown != null && ReportRelay.launched === shown) ReportRelay.launched = null
            if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        report = shown
        ReportRelay.opened = shown
        MotionZeroService.cancelArrival(this)
        onBackPressedDispatcher.addCallback(this) { closeToNavigation() }
        setContent {
            MotionZeroTheme { TripReportScreen(shown, ::closeToNavigation) }
        }
        lifecycleScope.launch {
            ReportRelay.current.collect {
                if (it !== shown && !isFinishing) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) openNavigation()
                    finish()
                }
            }
        }
    }

    private fun closeToNavigation() {
        openNavigation()
        finish()
        // onDestroy 까지 기다리지 않고 바로 비운다. 그 사이 아래의 MainActivity 가 다시 보이면서 같은 리포트를 또 띄우지 않게 한다.
        // finish() 뒤에 비워야 위 수집기가 isFinishing 을 보고 내비를 한 번 더 부르지 않는다.
        report?.let { ReportRelay.current.compareAndSet(it, null) }
    }

    override fun onDestroy() {
        if (isFinishing) report?.let { ReportRelay.current.compareAndSet(it, null) }
        super.onDestroy()
    }
}
