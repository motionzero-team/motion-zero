package com.motionzero.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.motionzero.app.ui.MainScreen
import com.motionzero.app.ui.MotionZeroTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 정차 중 설정·체험 화면. 엔진은 [MotionZeroController] 가 프로세스 단위로 들고 있고, [MotionZeroService] 가 화면 없이도 살아 있게 한다.
 *
 * 주행 중에는 내비가 앱 영역 전체를 쓰고 멀미 제로는 화면 없이 빛·소리로만 알린다.
 * - 주행이 시작될 때(기어 D·체험 주행) 이 화면이 앱 영역 전체를 덮고 있으면 화면을 내비게이션에 돌려준다.
 * - 주행 중 증상을 신고하려고 앱을 열면(GNB 아이콘), 신고 5초 뒤 저절로 내비로 돌아간다. "내비로 돌아가기" 버튼도 있다.
 * - 내비 옆 창(1:1)에 둔 경우에는 화면을 그대로 둔다.
 */
class MainActivity : ComponentActivity() {
    private val controller by lazy { MotionZeroController.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱을 adb 등 다른 인텐트로 처음 띄운 뒤 아래쪽 앱 아이콘으로 다시 열면, 시스템이 같은 태스크 위에 화면을 하나 더 쌓는다.
        // 이미 떠 있는 태스크를 앞으로 가져온 것뿐이므로 새 화면은 바로 닫아 아래 화면(홈 또는 리포트)을 그대로 보여 준다.
        if (!isTaskRoot && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        // 화면이 없어도 계속 살피도록 포그라운드 서비스를 띄운다. Pleos 는 일반 앱 알림을 화면에 띄우지 않으므로
        // 알림 권한은 따로 묻지 않는다(서비스 동작에는 필요 없다. 일반 AAOS 에서 알림 버튼을 쓰려면 설정에서 알림을 허용한다).
        MotionZeroService.start(this)
        setContent {
            MotionZeroTheme { MainScreen(controller, onShowNavigation = ::openNavigation) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 앱이 보일 때 아직 보지 않은 리포트를 연다. 주행 중(기어 D·R, 주행 규제)에는 열지 않고, 다시 정차하면 연다.
                // 앱이 내비 뒤에 있을 때는 서비스가 띄운다(같은 표시로 한 번만).
                val driving = controller.ui.map { it.engine.tripActive || it.restricted }.distinctUntilChanged()
                combine(ReportRelay.current, driving) { report, moving -> report.takeUnless { moving } }.collect { report ->
                    if (report != null && report !== ReportRelay.launched && report !== ReportRelay.opened) {
                        ReportRelay.launched = report
                        startActivity(Intent(this@MainActivity, TripReportActivity::class.java))
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var wasActive = controller.ui.value.engine.tripActive
                controller.ui.map { it.engine.tripActive }.distinctUntilChanged().collect { active ->
                    // 앱 영역 전체를 덮은 채로 출발하면 내비를 앞으로. 내비 옆 창에 둔 경우는 그대로 둔다.
                    if (active && !wasActive && coversNavigation()) openNavigation()
                    wasActive = active
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // 화면을 연 뒤 사용자가 새로 신고한 증상만 본다(첫 값은 건너뛴다). 더 신고하면 5초를 다시 센다.
                controller.ui.map { it.symptomSeq }.distinctUntilChanged().drop(1).collectLatest {
                    if (!drivingAndCovering()) return@collectLatest
                    delay(RETURN_AFTER_SYMPTOM_MS)
                    if (drivingAndCovering()) openNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ReportRelay.onScreenStarted()
    }

    override fun onStop() {
        ReportRelay.onScreenStopped()
        super.onStop()
    }

    private fun drivingAndCovering(): Boolean {
        val s = controller.ui.value
        return (s.engine.tripActive || s.demoRunning) && coversNavigation()
    }

    /** 앱 영역 2칸을 써서 내비를 가리고 있는지. 옆 창 1칸은 약 841dp. */
    private fun coversNavigation() = resources.configuration.screenWidthDp >= WIDE_WINDOW_DP

    private companion object {
        const val WIDE_WINDOW_DP = 1100
        const val RETURN_AFTER_SYMPTOM_MS = 5_000L
    }
}
