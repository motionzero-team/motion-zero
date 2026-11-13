package com.motionzero.app.cue

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.motionzero.app.signal.reason
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 음성 안내(구간 예보·증상 대응 문구 전용).
 * Pleos TTS(ON_DEVICE)를 먼저 쓰고, 초기화·재생에 실패하면 Android TextToSpeech(한국어)로 넘어간다.
 */
class VoiceGuide(context: Context, private val log: (String) -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var fellBack = false
    private var android: AndroidTts? = null
    private var androidReady = false
    private var lastText: String? = null
    private var utteranceId = 0

    fun start() {
        android = AndroidTts(appContext) { status ->
            main.post {
                androidReady = status == AndroidTts.SUCCESS
                if (androidReady) runCatching { android?.language = Locale.KOREA }
            }
        }
        try {
            val tts = PleosTts(appContext, Mode.ON_DEVICE)
            // 이벤트 리스너는 SDK 내부 엔진이 만들어진 뒤(초기화 성공 후)에만 등록할 수 있다.
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        try {
                            tts.addEventListener(object : EventListener {
                                override fun onStart() = Unit
                                override fun onReady() = Unit
                                override fun onDone() = Unit
                                override fun onStop() = Unit
                                override fun onUpdatedRms(rms: Double) = Unit
                                override fun onError(errorType: ErrorType) {
                                    main.post { fallBack("재생 오류 $errorType", replay = true) }
                                }
                            })
                            runCatching { tts.setLocale(Locale.KOREA) }
                            pleosReady = true
                            log("음성 · Pleos TTS 사용")
                        } catch (e: Exception) {
                            fallBack(e.reason(), replay = false)
                        }
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    main.post { fallBack("초기화 실패 $errorType", replay = false) }
                }
            })
            pleos = tts
        } catch (e: Exception) {
            fallBack(e.reason(), replay = false)
        }
    }

    fun say(text: String) {
        lastText = text
        val tts = pleos
        if (pleosReady && tts != null) {
            try {
                tts.speak(text, true)
                return
            } catch (e: Exception) {
                fallBack(e.reason(), replay = false)
            }
        }
        speakAndroid(text)
    }

    fun release() {
        pleos?.let { runCatching { it.stop() }; runCatching { it.release() } }
        android?.let { runCatching { it.stop() }; runCatching { it.shutdown() } }
        pleos = null
        android = null
    }

    private fun fallBack(reason: String, replay: Boolean) {
        if (fellBack) return
        fellBack = true
        val wasReady = pleosReady
        pleosReady = false
        log("음성 · Pleos TTS 사용 불가($reason) → Android TTS")
        pleos?.let { runCatching { it.release() } }
        pleos = null
        if (replay && wasReady) lastText?.let(::speakAndroid)
    }

    private fun speakAndroid(text: String) {
        val tts = android
        if (!androidReady || tts == null) {
            log("음성 · 출력 불가(TTS 준비 안 됨): $text")
            return
        }
        tts.speak(text, AndroidTts.QUEUE_ADD, null, "motionzero-${++utteranceId}")
    }
}
