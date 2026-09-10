package xyz.cdr.builderlauncher.clock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import xyz.cdr.builderlauncher.R

object ClockSoundPlayer {
    private const val PREVIEW_MS = 5_000L
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var playing = false
    @Volatile private var mode = Mode.IDLE
    private var player: MediaPlayer? = null
    private var audio: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fadeStartedAt = 0L
    private val previewStop = Runnable { stopPreview() }
    private val fadeTick = object : Runnable {
        override fun run() {
            val current = synchronized(lock) { player }
            if (!playing || current == null) return
            val gain = Clock.fadeGain(System.currentTimeMillis() - fadeStartedAt)
            runCatching { current.setVolume(gain, gain) }
            if (gain < 1f) main.postDelayed(this, 80)
        }
    }

    val alerting: Boolean get() = playing && mode == Mode.ALERT

    fun preview(context: Context, sound: ClockSound) {
        start(
            context = context.applicationContext,
            sound = sound,
            loop = false,
            fade = false,
            vibrate = false,
            preview = true,
        )
    }

    fun startAlert(context: Context, sound: ClockSound) {
        start(
            context = context.applicationContext,
            sound = sound,
            loop = true,
            fade = true,
            vibrate = true,
            preview = false,
        )
    }

    fun stopPreview() {
        synchronized(lock) {
            if (mode == Mode.PREVIEW) haltLocked()
        }
    }

    fun stop() {
        synchronized(lock) { haltLocked() }
    }

    private fun start(
        context: Context,
        sound: ClockSound,
        loop: Boolean,
        fade: Boolean,
        vibrate: Boolean,
        preview: Boolean,
    ) {
        synchronized(lock) {
            if (!preview && mode == Mode.ALERT && playing) return
            haltLocked()
            val resId = rawId(sound) ?: return
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!preview) runCatching { ensureAlarmAudible(am) }
            val next = runCatching { buildPlayer(context, resId, loop, preview) }.getOrNull() ?: return
            requestFocus(am, preview)
            if (!preview) runCatching { holdWake(context) }
            if (vibrate) buzz(context)
            playing = true
            mode = if (preview) Mode.PREVIEW else Mode.ALERT
            player = next
            audio = am
            val startGain = if (fade) Clock.FLOOR_GAIN else 1f
            runCatching { next.setVolume(startGain, startGain) }
            runCatching { next.start() }
            if (fade) {
                fadeStartedAt = System.currentTimeMillis()
                main.post(fadeTick)
            }
            if (preview) {
                main.postDelayed(previewStop, PREVIEW_MS)
            }
        }
    }

    private fun buildPlayer(context: Context, resId: Int, loop: Boolean, preview: Boolean): MediaPlayer {
        val attrs = audioAttrs(preview)
        return MediaPlayer().apply {
            setAudioAttributes(attrs)
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            val afd = context.resources.openRawResourceFd(resId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            isLooping = loop
            setOnCompletionListener {
                if (!loop) main.post { stop() }
            }
            setOnErrorListener { _, _, _ ->
                main.post { stop() }
                true
            }
            prepare()
        }
    }

    private fun audioAttrs(preview: Boolean): AudioAttributes {
        val builder = AudioAttributes.Builder()
        if (Clock.useMediaStream(preview)) {
            builder.setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        } else {
            builder.setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
        }
        return builder.build()
    }

    private fun ensureAlarmAudible(am: AudioManager) {
        val stream = AudioManager.STREAM_ALARM
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        if (am.getStreamVolume(stream) <= 0) {
            am.setStreamVolume(stream, (max / 2).coerceAtLeast(1), 0)
        }
    }

    private fun rawId(sound: ClockSound): Int? = when (sound) {
        ClockSound.PULSE -> R.raw.clock_pulse
        ClockSound.CHIME -> R.raw.clock_chime
        ClockSound.BELL -> R.raw.clock_bell
        ClockSound.ORTHODOX -> R.raw.clock_orthodox
        ClockSound.HUM -> R.raw.clock_hum
        ClockSound.OFF -> null
    }

    private fun requestFocus(am: AudioManager, preview: Boolean) {
        val built = audioAttrs(preview)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(built)
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { am.requestAudioFocus(req) }
        focusRequest = req
    }

    private fun buzz(context: Context) {
        val vib = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (!vib.hasVibrator()) return
        runCatching {
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 650), 0))
        }
        vibrator = vib
    }

    private fun holdWake(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "builderlauncher:clock").apply {
            setReferenceCounted(false)
            acquire(30 * 60_000L)
        }
    }

    private fun haltLocked() {
        playing = false
        mode = Mode.IDLE
        main.removeCallbacks(fadeTick)
        main.removeCallbacks(previewStop)
        val current = player
        player = null
        runCatching { current?.stop() }
        runCatching { current?.release() }
        runCatching { vibrator?.cancel() }
        vibrator = null
        val am = audio
        val req = focusRequest
        if (am != null && req != null) {
            runCatching { am.abandonAudioFocusRequest(req) }
        }
        audio = null
        focusRequest = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private enum class Mode { IDLE, PREVIEW, ALERT }
}
