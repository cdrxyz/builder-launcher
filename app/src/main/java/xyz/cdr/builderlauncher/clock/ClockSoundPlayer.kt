package xyz.cdr.builderlauncher.clock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object ClockSoundPlayer {
    private val lock = Any()
    @Volatile private var playing = false
    @Volatile private var mode = Mode.IDLE
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var audio: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

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
            val pcm = ClockTone.pcm(sound)
            if (pcm.isEmpty()) return
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val media = Clock.useMediaStream(am.getStreamVolume(AudioManager.STREAM_ALARM), preview)
            val attrs = attributes(media)
            val next = runCatching { buildTrack(attrs, pcm.size) }.getOrNull() ?: return
            requestFocus(am, attrs)
            if (!preview) runCatching { holdWake(context) }
            if (vibrate) buzz(context)
            playing = true
            mode = if (preview) Mode.PREVIEW else Mode.ALERT
            track = next
            audio = am
            runCatching { next.play() }
            val startedAt = System.currentTimeMillis()
            worker = Thread {
                try {
                    writeLoop(next, pcm, loop, fade, startedAt)
                } finally {
                    synchronized(lock) {
                        if (track === next) haltLocked()
                    }
                }
            }.also { it.start() }
        }
    }

    private fun writeLoop(
        next: AudioTrack,
        pcm: ShortArray,
        loop: Boolean,
        fade: Boolean,
        startedAt: Long,
    ) {
        val chunk = 1_024
        while (playing) {
            var offset = 0
            while (playing && offset < pcm.size) {
                val gain = if (fade) Clock.fadeGain(System.currentTimeMillis() - startedAt) else 1f
                runCatching { next.setVolume(gain.coerceIn(0f, 1f)) }
                val n = (pcm.size - offset).coerceAtMost(chunk)
                val written = runCatching { next.write(pcm, offset, n) }.getOrDefault(-1)
                when {
                    written < 0 -> return
                    written == 0 -> Thread.sleep(10)
                    else -> offset += written
                }
            }
            if (!loop) return
        }
    }

    private fun attributes(media: Boolean): AudioAttributes {
        val builder = AudioAttributes.Builder()
            .setUsage(if (media) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        if (!media) builder.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
        return builder.build()
    }

    private fun buildTrack(attrs: AudioAttributes, pcmShorts: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setSampleRate(ClockTone.SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val min = AudioTrack.getMinBufferSize(
            ClockTone.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2_048)
        return AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(min.coerceAtMost((pcmShorts * 2).coerceAtLeast(min)))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun requestFocus(am: AudioManager, attrs: AudioAttributes) {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
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
        val current = track
        worker = null
        track = null
        runCatching { current?.pause() }
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
