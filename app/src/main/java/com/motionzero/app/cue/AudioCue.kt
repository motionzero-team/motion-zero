package com.motionzero.app.cue

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.motionzero.app.core.Cue
import com.motionzero.app.core.Direction
import com.motionzero.app.core.MotionKind
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 짧은 효과음 큐. 에셋 없이 AudioTrack 으로 사인파를 합성한다.
 * - 방향: 스테레오 팬(LEFT → 왼쪽, RIGHT → 오른쪽, CENTER → 양쪽)
 * - 종류: 회전·차선 변경·커브는 상승음, 감속·내리막은 하강음, 방지턱은 짧은 2연음
 * - 크기: Cue.intensity 에 비례
 * 재생 동안만 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 을 잡고, 끝나면 반납한다.
 */
class AudioCue(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var playing: AudioTrack? = null

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change < 0) runCatching { playing?.pause() }
        }
        .build()

    fun play(cue: Cue) {
        val pcm = synthesize(cue.motion.kind, cue.motion.direction, cue.intensity)
        if (executor.isShutdown) return
        executor.execute {
            try {
                playBlocking(pcm)
            } catch (e: Exception) {
                Log.w(TAG, "효과음 재생 실패", e)
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }

    private fun playBlocking(pcm: ShortArray) {
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            playing = track
            track.play()
            Thread.sleep(pcm.size / 2 * 1000L / SAMPLE_RATE + TAIL_MS)
            track.stop()
        } finally {
            playing = null
            track?.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    companion object {
        private const val TAG = "AudioCue"
        const val SAMPLE_RATE = 44_100
        private const val TAIL_MS = 40L
        private const val MAX_AMPLITUDE = 0.8
        private const val SWEEP_MS = 280
        private const val BLIP_MS = 80
        private const val BLIP_GAP_MS = 70
        private const val FADE_MS = 8
        private const val LOW_HZ = 520.0
        private const val HIGH_HZ = 1_040.0
        private const val BLIP_HZ = 900.0

        /** 16비트 스테레오 인터리브 PCM(L, R, L, R …). */
        fun synthesize(kind: MotionKind, direction: Direction, intensity: Double): ShortArray {
            val segments = when (kind) {
                MotionKind.BRAKE, MotionKind.DOWNHILL -> listOf(Tone(SWEEP_MS, HIGH_HZ, LOW_HZ))
                MotionKind.BUMP -> listOf(Tone(BLIP_MS, BLIP_HZ, BLIP_HZ), Tone(BLIP_GAP_MS, 0.0, 0.0), Tone(BLIP_MS, BLIP_HZ, BLIP_HZ))
                MotionKind.TURN, MotionKind.LANE_CHANGE, MotionKind.CURVE -> listOf(Tone(SWEEP_MS, LOW_HZ, HIGH_HZ))
            }
            val (leftGain, rightGain) = when (direction) {
                Direction.LEFT -> 1.0 to 0.0
                Direction.RIGHT -> 0.0 to 1.0
                Direction.CENTER -> 1.0 to 1.0
            }
            val amplitude = MAX_AMPLITUDE * intensity.coerceIn(0.0, 1.0) * Short.MAX_VALUE
            val frames = segments.sumOf { it.frames }
            val out = ShortArray(frames * 2)
            var i = 0
            for (tone in segments) {
                val fade = SAMPLE_RATE * FADE_MS / 1000
                var phase = 0.0
                for (n in 0 until tone.frames) {
                    val sample = if (tone.startHz <= 0.0) {
                        0.0
                    } else {
                        val hz = tone.startHz + (tone.endHz - tone.startHz) * n / tone.frames
                        phase += 2 * PI * hz / SAMPLE_RATE
                        val envelope = min(1.0, min(n, tone.frames - 1 - n).toDouble() / fade)
                        sin(phase) * envelope * amplitude
                    }
                    out[i++] = (sample * leftGain).toInt().toShort()
                    out[i++] = (sample * rightGain).toInt().toShort()
                }
            }
            return out
        }
    }

    private class Tone(durationMs: Int, val startHz: Double, val endHz: Double) {
        val frames = SAMPLE_RATE * durationMs / 1000
    }
}
