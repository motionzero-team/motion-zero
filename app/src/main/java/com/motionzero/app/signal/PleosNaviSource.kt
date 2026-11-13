package com.motionzero.app.signal

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.motionzero.app.core.CautionSignal
import com.motionzero.app.core.CautionType
import com.motionzero.app.core.Signal
import com.motionzero.app.core.SpeedEstimator
import com.motionzero.app.core.TbtSignal
import com.motionzero.app.core.TurnType
import ai.pleos.playground.navi.constants.TurnType as NaviTurnType

/**
 * NaviHelper 이벤트를 core 신호로 바꾼다.
 * - onTBTInfo 첫 항목(다음 기동) → TbtSignal
 * - onCautionPointInfo 중 가장 가까운 과속방지턱·내리막 → CautionSignal
 * - 위치 변화·회전 안내 남은 거리 변화 → SpeedEstimator → SpeedSignal (Vehicle SDK에 차속 API가 없음)
 */
class PleosNaviSource(
    context: Context,
    private val sink: (Signal) -> Unit,
    private val onStatus: (String) -> Unit,
) : NaviHelperEventListener {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val speed = SpeedEstimator()
    private var helper: NaviHelper? = null
    private var connection = "연결 중…"
    private var navigation: String? = null
    private var tbtReceived = false

    private val locationPoll = object : Runnable {
        override fun run() {
            try {
                helper?.getCurrentLocationInfo()
            } catch (e: Exception) {
                setConnection("위치 요청 실패: ${e.reason()}")
                return
            }
            main.postDelayed(this, LOCATION_POLL_MS)
        }
    }

    fun start() {
        var created: NaviHelper? = null
        try {
            val h = NaviHelper(appContext).also { created = it }
            h.initialize(
                {
                    main.post {
                        setConnection("연결됨")
                        runCatching { h.getTBTInfo() }
                        main.removeCallbacks(locationPoll)
                        main.post(locationPoll)
                    }
                },
                { t -> main.post { setConnection("연결 실패: ${t.reason()}") } },
            )
            h.addListener(this)
            helper = h
        } catch (e: Exception) {
            runCatching { created?.release() }
            setConnection("연결 실패: ${e.reason()}")
        }
    }

    fun stop() {
        main.removeCallbacks(locationPoll)
        helper?.let { h ->
            runCatching { h.removeListener(this) }
            runCatching { h.release() }
        }
        helper = null
    }

    private fun setConnection(text: String) {
        connection = text
        publishStatus()
    }

    private fun publishStatus() {
        val parts = listOfNotNull(connection, navigation, if (tbtReceived) "회전 안내 수신" else null)
        onStatus(parts.joinToString(" · "))
    }

    private fun post(block: (Long) -> Unit) {
        main.post { block(SystemClock.elapsedRealtime()) }
    }

    override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
        val next = tbtInfoList.firstOrNull() ?: return
        post { now ->
            val type = next.type.toCore()
            speed.onTbt(now, type, next.distance)?.let(sink)
            sink(TbtSignal(now, type, next.distance))
            if (!tbtReceived) {
                tbtReceived = true
                publishStatus()
            }
        }
    }

    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
        val nearest = nearestCaution(cautionPointList) ?: return
        post { now -> sink(CautionSignal(now, nearest.first, nearest.second)) }
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
        val lat = currentLocationInfo.latitude
        val lon = currentLocationInfo.longitude
        post { now -> speed.onLocation(now, lat, lon)?.let(sink) }
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        main.post {
            navigation = when (status) {
                NavigationStatus.ROUTE_DRIVE -> "경로 안내 중"
                NavigationStatus.FREE_DRIVE -> "자유 주행"
                NavigationStatus.INITIALIZING -> "내비 준비 중"
                else -> "안내 꺼짐"
            }
            publishStatus()
        }
    }

    override fun onError(errorCode: NaviErrorCode) {
        main.post { setConnection("오류: $errorCode") }
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        main.post { runCatching { helper?.getTBTInfo() } }
    }

    @Deprecated("Deprecated by NaviHelperEventListener")
    override fun onRouteEnded() = Unit
    override fun onRoutingCompleted() = Unit
    override fun onReRouteEnded() = Unit
    override fun onRouteOptionChanged(routeOption: RouteOption) = Unit
    override fun onRouteCancelled() = Unit
    override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = Unit
    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = Unit
    override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = Unit
    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = Unit
    override fun onDestinationInfo(destinationInfo: DestinationInfo) = Unit
    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = Unit
    override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) = Unit
    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) = Unit
    override fun onCurrentRoadSpeedLimit(speedLimit: Int) = Unit
    override fun onCameraAlert(cameraAlertList: List<CameraAlert>) = Unit
    override fun onDrivingInfo(drivingInfo: DrivingInfo) = Unit
    override fun onBookMarkChanged() = Unit
    override fun onDestinationChanged() = Unit
    override fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>) = Unit
    override fun onTripInfo(tripInfo: TripInfo) = Unit

    private companion object {
        const val LOCATION_POLL_MS = 1_000L
    }
}

/** 내비 회전 종류 → core 회전 종류(예상 가속도 표). */
internal fun NaviTurnType.toCore(): TurnType = when (this) {
    NaviTurnType.TURN_LEFT -> TurnType.LEFT
    NaviTurnType.TURN_RIGHT -> TurnType.RIGHT
    NaviTurnType.TURN_SHARP_L -> TurnType.SHARP_LEFT
    NaviTurnType.TURN_SHARP_R -> TurnType.SHARP_RIGHT
    NaviTurnType.TURN_UTURN -> TurnType.U_TURN
    NaviTurnType.TURN_ARRIVE -> TurnType.DESTINATION
    else -> when {
        name.startsWith("TURN_ENTER_ROUNDABOUT") -> TurnType.ROUNDABOUT
        // 차로 유지·IC 진출입·지하/고가차도 진입 등 좌우 분기는 완만한 회전으로 본다.
        name.endsWith("_L") -> TurnType.SLIGHT_LEFT
        name.endsWith("_R") -> TurnType.SLIGHT_RIGHT
        else -> TurnType.STRAIGHT
    }
}

internal fun CautionPointType.toCore(): CautionType = when (this) {
    CautionPointType.SPEED_BUMP -> CautionType.SPEED_BUMP
    CautionPointType.DOWNHILL_CAUTION_AREA -> CautionType.DOWNHILL
    else -> CautionType.OTHER
}

/** 앞에 남은 과속방지턱·내리막 중 가장 가까운 것(종류, 남은 거리 m). */
internal fun nearestCaution(points: List<CautionPoint>): Pair<CautionType, Double>? =
    points.asSequence()
        .map { it.type.toCore() to it.remainDistance.toDouble() }
        .filter { it.first != CautionType.OTHER && it.second >= 0 }
        .minByOrNull { it.second }
