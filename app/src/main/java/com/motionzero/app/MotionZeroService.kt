package com.motionzero.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.motionzero.app.core.Severity
import com.motionzero.app.core.TripReport
import com.motionzero.app.ui.distanceLabel
import com.motionzero.app.ui.durationLabel
import com.motionzero.app.ui.symptomTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 화면 없이 동작하게 하는 포그라운드 서비스.
 *
 * 주행 중에는 내비가 앱 영역 전체를 쓰고, 멀미 제로는 이 서비스 안에서 빛·소리로만 알린다.
 * - 도착하면 리포트 화면을 띄운다. 앱이 내비 뒤에 있어 시스템이 화면 실행을 막으면(백그라운드 실행 제한)
 *   음성으로 알리고, 사용자가 앱 아이콘을 누르면 리포트가 열린다.
 * - 상태 알림의 증상 버튼·"리포트 보기" 알림은 일반 Android Automotive 용 보조 수단이다(API 33 이상에서는 알림을 허용해야 보인다).
 *   Pleos Connect 는 시스템·플랫폼 서명 앱의 알림만 화면에 띄워(에뮬레이터 실측) 여기서는 보이지 않는다.
 */
class MotionZeroService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: MotionZeroController
    private lateinit var nm: NotificationManager

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        createChannels()
        // 포그라운드 전환을 가장 먼저 한다(SDK 연결보다 앞). 프로세스가 회수된 뒤 시스템이 다시 살릴 때처럼
        // 백그라운드에서 전환이 거절되면(ForegroundServiceStartNotAllowedException 등) 조용히 멈춘다. 앱을 열면 다시 시작된다.
        val first = statusNotification("멀미 제로가 준비 중이에요")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(STATUS_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(STATUS_ID, first)
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "포그라운드로 전환하지 못해 서비스를 멈춤", e)
            stopSelf()
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "포그라운드로 전환하지 못해 서비스를 멈춤", e)
            stopSelf()
            return
        }
        controller = MotionZeroController.get(this)
        scope.launch {
            controller.ui.map(::statusText).distinctUntilChanged().collect { nm.notify(STATUS_ID, statusNotification(it)) }
        }
        scope.launch { ReportRelay.current.collect(::onReport) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::controller.isInitialized) return START_NOT_STICKY
        if (intent?.action == ACTION_SYMPTOM) {
            val severity = intent.getStringExtra(EXTRA_SEVERITY)?.let { runCatching { Severity.valueOf(it) }.getOrNull() }
            if (severity != null) controller.reportSymptom(severity)
        }
        // 시스템이 프로세스를 회수해도 다시 살아나 다음 주행을 계속 살핀다.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun onReport(report: TripReport?) {
        if (report == null) {
            nm.cancel(ARRIVAL_ID)
            return
        }
        if (report === ReportRelay.launched) return
        ReportRelay.launched = report
        startReportScreen()
        // 앱이 내비 뒤에 있으면 시스템이 화면 실행을 막는다(백그라운드 실행 제한). 잠시 뒤에도 열리지 않았으면
        // 음성으로 알리고, 앱을 열면 리포트가 뜬다. Pleos 는 일반 앱의 알림을 화면에 띄우지 않으므로 알림은 보조 수단이다.
        delay(REPORT_FALLBACK_MS)
        if (ReportRelay.current.value !== report || ReportRelay.opened === report) return
        if (ReportRelay.uiStarted) {
            // 기다리는 사이 사용자가 앱을 열었다. 앱 창이 보이므로 이제는 바로 띄울 수 있다(리포트 화면은 singleTop).
            startReportScreen()
            return
        }
        ReportRelay.launched = null
        controller.announce("도착했어요. 멀미 제로를 열면 오늘 주행 리포트를 볼 수 있어요.")
        nm.notify(ARRIVAL_ID, arrivalNotification(report))
    }

    private fun startReportScreen() {
        try {
            startActivity(Intent(this, TripReportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.i(TAG, "리포트 화면을 바로 띄우지 못함", e)
        }
    }

    private fun createChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "멀미 케어 상태", NotificationManager.IMPORTANCE_LOW).apply {
                description = "멀미 제로가 살피는 중임을 알리고, 증상 버튼을 둡니다."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ARRIVAL, "도착 리포트", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "도착하면 트립 리포트를 볼 수 있다고 알립니다."
            },
        )
    }

    private fun statusNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_motionzero)
            .setContentTitle("멀미 제로")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(activityIntent(Intent(this, MainActivity::class.java), REQ_OPEN))
            .apply { Severity.entries.forEach { addAction(0, symptomTitle(it), symptomIntent(it)) } }
            .build()

    private fun arrivalNotification(report: TripReport): Notification =
        NotificationCompat.Builder(this, CHANNEL_ARRIVAL)
            .setSmallIcon(R.drawable.ic_stat_motionzero)
            .setContentTitle("도착했어요 · 트립 리포트")
            .setContentText("${durationLabel(report.durationMs)} · ${distanceLabel(report.distanceM)} · 미리 알림 ${report.totalCues}회")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(activityIntent(Intent(this, TripReportActivity::class.java), REQ_REPORT))
            .build()

    private fun activityIntent(intent: Intent, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        this, requestCode, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun symptomIntent(severity: Severity): PendingIntent = PendingIntent.getService(
        this, REQ_SYMPTOM + severity.ordinal,
        Intent(this, MotionZeroService::class.java).setAction(ACTION_SYMPTOM).putExtra(EXTRA_SEVERITY, severity.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 상태 알림 한 줄. 자주 바뀌지 않는 값만 써서 알림이 매초 갱신되지 않게 한다. */
    private fun statusText(s: UiState): String {
        val e = s.engine
        return when {
            !e.careEnabled -> "멀미 케어가 꺼져 있어요"
            s.comfort != null && (e.tripActive || s.demoRunning) -> "${s.comfort.title} · 도움 ${s.comfort.items.size}가지"
            s.demoRunning -> "체험 주행 중 · 미리 알림 ${e.cueCount}회"
            e.tripActive -> "주행 중 · 회전·제동을 빛과 소리로 미리 알려요 · ${e.cueCount}회"
            else -> "정차 중 · 출발하면 알아서 시작해요"
        }
    }

    companion object {
        private const val TAG = "MotionZero"
        private const val CHANNEL_STATUS = "care_status"
        private const val CHANNEL_ARRIVAL = "arrival_report"
        private const val STATUS_ID = 1
        private const val ARRIVAL_ID = 2
        private const val REQ_OPEN = 10
        private const val REQ_REPORT = 11
        private const val REQ_SYMPTOM = 20
        private const val REPORT_FALLBACK_MS = 1_500L
        const val ACTION_SYMPTOM = "com.motionzero.app.action.SYMPTOM"
        const val EXTRA_SEVERITY = "severity"

        /** 앱을 열 때 부른다. 이미 실행 중이면 아무 일도 하지 않는다. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MotionZeroService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "포그라운드 서비스를 시작하지 못함", e)
            }
        }

        /** 리포트 화면이 열리면 "리포트 보기" 알림을 지운다. */
        fun cancelArrival(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(ARRIVAL_ID)
        }
    }
}
