package com.example.runtraining.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import com.example.runtraining.util.Log

/**
 * Plays a short ~150 ms beep on the MEDIA stream with transient audio focus
 * per Spec FR-023b / FR-023c.
 *  - STREAM_MUSIC → respects media volume (works in silent ringer mode too).
 *  - AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK → music apps duck briefly instead of
 *    being interrupted; we release focus immediately after each beep.
 *
 * `release()` is safe to call from any thread; the underlying ToneGenerator
 * is also safe across threads for `startTone`.
 */
class TonePlayer(context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val toneGenerator: ToneGenerator =
        ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME)

    private val focusAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(focusAttributes)
            .setOnAudioFocusChangeListener { /* ignored — beeps are fire-and-forget */ }
            .setAcceptsDelayedFocusGain(false)
            .build()

    /** Play one short beep. Safe to call from any thread. */
    fun beep() {
        try {
            audioManager.requestAudioFocus(focusRequest)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
            // Release focus immediately so any underlying music un-ducks.
            audioManager.abandonAudioFocusRequest(focusRequest)
        } catch (t: Throwable) {
            Log.w("TonePlayer.beep failed", t)
        }
    }

    fun release() {
        runCatching { toneGenerator.release() }
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    companion object {
        private const val BEEP_VOLUME = 80           // 0..100
        private const val BEEP_DURATION_MS = 150
    }
}
