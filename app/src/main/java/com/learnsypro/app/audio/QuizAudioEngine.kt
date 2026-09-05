package com.learnsypro.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val Context.qpAudioDataStore by preferencesDataStore(name = "learnsy_qp_audio")
private val MUTED_KEY = booleanPreferencesKey("qp_muted")

/** Dạng sóng — tương đương osc.type trong Web Audio ('sine'|'triangle'|'sawtooth'|'square'). */
enum class WaveType { SINE, TRIANGLE, SAWTOOTH, SQUARE }

/** Một "note" trong chuỗi âm thanh — tương đương {f,d,t,v,delay,ramp} trong _sfxPlay bản gốc. */
data class SfxNote(
    val freqHz: Float,
    val durationSec: Float,
    val wave: WaveType = WaveType.SINE,
    val volume: Float = 1f,
    val delaySec: Float = 0f,
    val slideRamp: Boolean = false // tương đương ramp:'slide' — tần số trượt lên *1.08 trong suốt note
)

/**
 * ── QuizAudioEngine ──
 * Tương đương toàn bộ audio engine trong quiz-player.jsx (_AC, _sfxPlay,
 * _sfxClick/Correct/Wrong/Streak/Fanfare/Sad/Tick/TickUrgent/Nav/Submit,
 * _startBgmSynth).
 *
 * Web Audio API tổng hợp sóng âm trực tiếp qua OscillatorNode; Android
 * không có API tương đương built-in nên ta tự sinh waveform theo mẫu (PCM
 * 16-bit) rồi phát qua AudioTrack — cùng nguyên lý, tự do tùy biến tần số/
 * dạng sóng/độ dài/ramp giống hệt bản gốc, điều mà ToneGenerator (chỉ có
 * sẵn các tone DTMF cố định) không làm được.
 *
 * Trạng thái mute đồng bộ qua DataStore, tương đương localStorage qp_muted.
 */
class QuizAudioEngine(private val context: Context, private val scope: CoroutineScope) {

    private val sampleRate = 44100
    private var muted = false
    private var bgmTrack: AudioTrack? = null
    private var bgmPlaying = false

    init {
        scope.launch {
            muted = context.qpAudioDataStore.data.first()[MUTED_KEY] ?: false
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        scope.launch { context.qpAudioDataStore.edit { it[MUTED_KEY] = value } }
        if (value) stopBgm()
    }

    fun isMuted(): Boolean = muted

    // ═══════════════ Public SFX API — tương đương các hàm _sfx* ═══════════════

    fun playClick() = play(
        listOf(
            SfxNote(880f, 0.07f, WaveType.TRIANGLE, 0.7f),
            SfxNote(1100f, 0.05f, WaveType.SINE, 0.4f, delaySec = 0.04f)
        )
    )

    fun playCorrect() = play(
        listOf(
            SfxNote(523f, 0.10f, WaveType.TRIANGLE, 0.8f),
            SfxNote(659f, 0.10f, WaveType.TRIANGLE, 0.8f, delaySec = 0.09f),
            SfxNote(784f, 0.14f, WaveType.TRIANGLE, 0.9f, delaySec = 0.18f),
            SfxNote(1047f, 0.18f, WaveType.SINE, 0.6f, delaySec = 0.28f)
        ),
        masterVol = 0.28f
    )

    fun playWrong() = play(
        listOf(
            SfxNote(300f, 0.10f, WaveType.SAWTOOTH, 0.5f),
            SfxNote(220f, 0.18f, WaveType.SAWTOOTH, 0.6f, delaySec = 0.08f),
            SfxNote(160f, 0.25f, WaveType.TRIANGLE, 0.4f, delaySec = 0.18f)
        ),
        masterVol = 0.24f
    )

    /** n = độ dài streak hiện tại (>=3) — cao độ và tốc độ tăng dần theo streak, giống bản gốc. */
    fun playStreak(n: Int) {
        val base = 523f + min(n - 3, 5) * 40f
        val spd = maxOf(0.045f, 0.08f - (n - 3) * 0.005f)
        play(
            listOf(
                SfxNote(base, 0.09f, WaveType.TRIANGLE, 0.7f),
                SfxNote(base * 1.25f, 0.09f, WaveType.TRIANGLE, 0.7f, delaySec = spd),
                SfxNote(base * 1.5f, 0.09f, WaveType.TRIANGLE, 0.8f, delaySec = spd * 2),
                SfxNote(base * 2f, 0.14f, WaveType.SINE, 0.6f, delaySec = spd * 3)
            ),
            masterVol = 0.26f
        )
    }

    fun playFanfare() = play(
        listOf(
            SfxNote(523f, 0.12f, WaveType.TRIANGLE, 0.9f),
            SfxNote(659f, 0.12f, WaveType.TRIANGLE, 0.9f, delaySec = 0.11f),
            SfxNote(784f, 0.12f, WaveType.TRIANGLE, 0.9f, delaySec = 0.22f),
            SfxNote(1047f, 0.18f, WaveType.SINE, 1.0f, delaySec = 0.33f),
            SfxNote(784f, 0.09f, WaveType.TRIANGLE, 0.7f, delaySec = 0.52f),
            SfxNote(1047f, 0.09f, WaveType.SINE, 0.8f, delaySec = 0.62f),
            SfxNote(1319f, 0.26f, WaveType.SINE, 1.0f, delaySec = 0.72f, slideRamp = true),
            SfxNote(659f, 0.26f, WaveType.TRIANGLE, 0.4f, delaySec = 0.72f) // harmony
        ),
        masterVol = 0.30f
    )

    fun playSad() = play(
        listOf(
            SfxNote(440f, 0.14f, WaveType.SAWTOOTH, 0.6f),
            SfxNote(370f, 0.18f, WaveType.SAWTOOTH, 0.7f, delaySec = 0.12f),
            SfxNote(294f, 0.24f, WaveType.TRIANGLE, 0.5f, delaySec = 0.26f),
            SfxNote(220f, 0.30f, WaveType.TRIANGLE, 0.4f, delaySec = 0.44f)
        ),
        masterVol = 0.26f
    )

    fun playTick() = play(listOf(SfxNote(1200f, 0.04f, WaveType.SQUARE, 0.35f)), masterVol = 0.18f)

    fun playTickUrgent() = play(
        listOf(
            SfxNote(1400f, 0.05f, WaveType.SQUARE, 0.5f),
            SfxNote(1600f, 0.04f, WaveType.SQUARE, 0.4f, delaySec = 0.06f)
        ),
        masterVol = 0.22f
    )

    fun playNav() = play(listOf(SfxNote(660f, 0.06f, WaveType.SINE, 0.5f)), masterVol = 0.15f)

    fun playSubmit() = play(
        listOf(
            SfxNote(440f, 0.08f, WaveType.TRIANGLE, 0.6f),
            SfxNote(550f, 0.10f, WaveType.TRIANGLE, 0.7f, delaySec = 0.07f),
            SfxNote(660f, 0.12f, WaveType.SINE, 0.8f, delaySec = 0.15f)
        ),
        masterVol = 0.24f
    )

    // ═══════════════ BGM — tiếng mưa nền chill, loop ═══════════════

    /**
     * Tương đương _startBgmSynth: white-noise loop qua bandpass+lowpass filter,
     * mô phỏng tiếng mưa nhẹ nhàng, không dùng oscillator nên tránh rít/hú.
     * Android không có BiquadFilterNode built-in đơn giản, nên ta áp dụng bộ
     * lọc IIR đơn giản (1-pole low-pass + band-pass xấp xỉ) trực tiếp lên
     * buffer nhiễu trắng trước khi phát qua AudioTrack ở chế độ loop.
     */
    fun startBgm() {
        if (muted || bgmPlaying) return
        scope.launch(Dispatchers.Default) {
            val durationSec = 2f
            val numSamples = (sampleRate * durationSec).toInt()
            val noise = FloatArray(numSamples) { Random.nextFloat() * 2f - 1f }

            // Low-pass 1-pole đơn giản (cắt tần cao) — tương đương lowpass 3500Hz
            var lpPrev = 0f
            val lpAlpha = 0.25f
            for (i in noise.indices) {
                lpPrev += lpAlpha * (noise[i] - lpPrev)
                noise[i] = lpPrev
            }

            // Band-pass xấp xỉ bằng cách trừ đi bản low-pass thứ 2 chậm hơn (giữ dải giữa ~1200Hz)
            var bpPrev = 0f
            val bpAlpha = 0.06f
            val filtered = FloatArray(numSamples)
            for (i in noise.indices) {
                bpPrev += bpAlpha * (noise[i] - bpPrev)
                filtered[i] = (noise[i] - bpPrev) * 0.85f
            }

            val vol = 0.05f // _BGM_VOL
            val pcm = ShortArray(numSamples) { i ->
                (filtered[i] * vol * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val track = buildAudioTrack(numSamples * 2)
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, numSamples, -1) // loop vô hạn
            bgmTrack = track
            bgmPlaying = true
            track.play()
        }
    }

    fun stopBgm() {
        bgmPlaying = false
        bgmTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        bgmTrack = null
    }

    // ═══════════════ Core synth engine ═══════════════

    /** Tương đương _sfxPlay(notes, masterVol) — tổng hợp và phát chuỗi note. */
    private fun play(notes: List<SfxNote>, masterVol: Float = DEFAULT_VOL) {
        if (muted) return
        scope.launch(Dispatchers.Default) {
            try {
                val totalDurationSec = notes.maxOf { it.delaySec + it.durationSec } + 0.05f
                val totalSamples = (sampleRate * totalDurationSec).toInt()
                val mix = FloatArray(totalSamples)

                notes.forEach { note -> renderNoteInto(mix, note, masterVol) }

                val pcm = ShortArray(totalSamples) { i ->
                    (mix[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                }

                val track = buildAudioTrack(pcm.size * 2)
                track.write(pcm, 0, pcm.size)
                track.play()
                // Giải phóng track sau khi phát xong (one-shot, không loop)
                kotlinx.coroutines.delay((totalDurationSec * 1000).toLong() + 100)
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Silent — tương đương try/catch rỗng trong bản gốc, không làm gián đoạn app
            }
        }
    }

    /** Sinh waveform cho 1 note và cộng dồn (mix) vào buffer chung. */
    private fun renderNoteInto(mix: FloatArray, note: SfxNote, masterVol: Float) {
        val startSample = (note.delaySec * sampleRate).toInt()
        val durationSamples = (note.durationSec * sampleRate).toInt()
        val attackSamples = (0.008f * sampleRate).toInt().coerceAtLeast(1) // linearRamp 8ms attack

        for (i in 0 until durationSamples) {
            val idx = startSample + i
            if (idx >= mix.size) break

            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / durationSamples

            // Tần số trượt nếu slideRamp (tương đương ramp:'slide' → freq * 1.08 tuyến tính)
            val freq = if (note.slideRamp) note.freqHz * (1f + 0.08f * progress) else note.freqHz

            val phase = 2f * PI.toFloat() * freq * t
            val raw = when (note.wave) {
                WaveType.SINE -> sin(phase)
                WaveType.TRIANGLE -> (2f / PI.toFloat()) * kotlin.math.asin(sin(phase))
                WaveType.SAWTOOTH -> 2f * (t * freq - kotlin.math.floor(0.5f + t * freq))
                WaveType.SQUARE -> if (sin(phase) >= 0f) 1f else -1f
            }

            // Envelope: attack tuyến tính 8ms rồi exponential decay về cuối note
            val envelope = when {
                i < attackSamples -> i.toFloat() / attackSamples
                else -> {
                    val decayProgress = (i - attackSamples).toFloat() / (durationSamples - attackSamples).coerceAtLeast(1)
                    kotlin.math.exp(-decayProgress * 5f) // xấp xỉ exponentialRampToValueAtTime(0.0001,...)
                }
            }

            mix[idx] += raw * envelope * note.volume * masterVol
        }
    }

    private fun buildAudioTrack(bufferSizeBytes: Int): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(bufferSizeBytes, minBufferSize),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    companion object {
        private const val DEFAULT_VOL = 0.22f
    }
}
