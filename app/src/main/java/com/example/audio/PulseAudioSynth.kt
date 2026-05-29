package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

class PulseAudioSynth {
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val sampleRate = 22050
    private var isPlaying = false
    private var songId: String = ""
    private var currentPositionSamples = 0L

    // Live visualizer data: current amplitude level [0.0 to 1.0]
    private val _visualizerFlow = MutableStateFlow(0f)
    val visualizerFlow: StateFlow<Float> = _visualizerFlow

    // Equalizer gains for visual-audio integration [bands 1 to 5]
    private var lowGain = 1.0f  // 60Hz - 230Hz
    private var midGain = 1.0f  // 910Hz
    private var highGain = 1.0f // 3.6kHz - 14kHz

    fun updateEqualizerGains(bands: FloatArray) {
        if (bands.size >= 5) {
            // map dB [-12, +12] or similar to multiplier [0.1, 3.0]
            lowGain = Math.pow(10.0, (bands[0] + bands[1]).toDouble() / 40.0).toFloat().coerceIn(0.1f, 3.0f)
            midGain = Math.pow(10.0, bands[2].toDouble() / 20.0).toFloat().coerceIn(0.1f, 3.0f)
            highGain = Math.pow(10.0, (bands[3] + bands[4]).toDouble() / 40.0).toFloat().coerceIn(0.1f, 3.0f)
            Log.d("PulseAudioSynth", "EQ weights: Low=$lowGain Mid=$midGain High=$highGain")
        }
    }

    fun start(id: String, startPositionMs: Long) {
        stop()
        songId = id
        currentPositionSamples = (startPositionMs * sampleRate) / 1000L
        isPlaying = true

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(4096),
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("PulseAudioSynth", "Error building audio track: ${e.message}")
            return
        }

        synthJob = scope.launch {
            val bufferSize = 1024
            val shortBuffer = ShortArray(bufferSize)

            // Dynamic music presets mapping a list of midi notes for chords/melodies
            val notes = getNotesForSong(songId)
            val bpm = getBpmForSong(songId)
            val samplesPerBeat = (sampleRate * 60) / bpm

            while (isActive && isPlaying) {
                for (i in 0 until bufferSize) {
                    val totalSampleIndex = currentPositionSamples + i
                    val beatIndex = (totalSampleIndex / samplesPerBeat).toInt()
                    
                    // Bass oscillator (low rhythm notes)
                    val bassNoteIndex = (beatIndex / 2) % notes.bass.size
                    val bassFreq = midiToFreq(notes.bass[bassNoteIndex])
                    
                    // Lead oscillator (arpeggiated rapid notes)
                    val arpeggioSpeed = 4 // 4 notes per beat
                    val arpeggioIndex = (totalSampleIndex / (samplesPerBeat / arpeggioSpeed)).toInt()
                    val leadNoteIndex = arpeggioIndex % notes.lead.size
                    val leadFreq = midiToFreq(notes.lead[leadNoteIndex])

                    // Generate waves
                    val bassTime = totalSampleIndex.toDouble() / sampleRate
                    val leadTime = totalSampleIndex.toDouble() / sampleRate

                    // Sine wave for soft low frequencies (with EQ filter applied)
                    val bassWave = sin(2.0 * Math.PI * bassFreq * bassTime) * 0.45 * lowGain

                    // Triangle/square hybrid wave for crisp high lead frequencies (with EQ filter applied)
                    val leadPhase = (leadTime * leadFreq) % 1.0
                    val leadWave = (if (leadPhase < 0.5) leadPhase * 4.0 - 1.0 else 3.0 - leadPhase * 4.0) * 0.18 * midGain

                    // Decorative sparkle/noise for high frequencies
                    val sparklePhase = (leadTime * (leadFreq * 2.0)) % 1.0
                    val sparkleWave = sin(2.0 * Math.PI * (leadFreq * 2.0) * leadTime) * 0.08 * highGain

                    val combined = (bassWave + leadWave + sparkleWave).coerceIn(-1.0, 1.0)
                    shortBuffer[i] = (combined * 32767.0).toInt().toShort()
                }

                audioTrack?.write(shortBuffer, 0, bufferSize)
                currentPositionSamples += bufferSize

                // Feed visualizer stream
                var peak = 0.0
                for (s in shortBuffer) {
                    val abs = Math.abs(s.toInt()).toDouble()
                    if (abs > peak) peak = abs
                }
                val normalizedPeak = (peak / 32767.0).toFloat().coerceIn(0f, 1f)
                _visualizerFlow.emit(normalizedPeak)

                // Yield to allow UI thread to breathe
                delay(10)
            }
        }
    }

    fun pause() {
        isPlaying = false
        audioTrack?.pause()
    }

    fun resume() {
        if (!isPlaying && songId.isNotEmpty()) {
            isPlaying = true
            audioTrack?.play()
            start(songId, (currentPositionSamples * 1000L) / sampleRate)
        }
    }

    fun stop() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null
        _visualizerFlow.value = 0f
    }

    fun seekTo(positionMs: Long) {
        currentPositionSamples = (positionMs * sampleRate) / 1000L
        if (isPlaying) {
            start(songId, positionMs)
        }
    }

    fun getPositionMs(): Long {
        return (currentPositionSamples * 1000L) / sampleRate
    }

    private fun midiToFreq(note: Int): Double {
        return 440.0 * Math.pow(2.0, (note - 69).toDouble() / 12.0)
    }

    private class SongNotes(val bass: IntArray, val lead: IntArray)

    private fun getNotesForSong(id: String): SongNotes {
        return when (id) {
            "builtin_1" -> SongNotes(
                bass = intArrayOf(36, 40, 43, 41), // C2, E2, G2, F2
                lead = intArrayOf(60, 64, 67, 71, 72, 76, 79, 83) // C-major luxury arps
            )
            "builtin_2" -> SongNotes(
                bass = intArrayOf(41, 45, 48, 46), // F2, A2, C3, Bb2
                lead = intArrayOf(65, 69, 72, 74, 77, 81, 84, 86) // F-major colorful chords
            )
            "builtin_3" -> SongNotes(
                bass = intArrayOf(33, 37, 40, 38), // A1, C2, E2, D2
                lead = intArrayOf(57, 60, 64, 62, 69, 72, 76, 74) // A-minor sweet dream waves
            )
            "builtin_4" -> SongNotes(
                bass = intArrayOf(43, 47, 50, 48), // G2, B2, D3, C3
                lead = intArrayOf(67, 71, 74, 76, 79, 83, 86, 88) // G-major bright drops
            )
            "builtin_5" -> SongNotes(
                bass = intArrayOf(36, 38, 40, 41), // C2, D2, E2, F2
                lead = intArrayOf(60, 62, 64, 65, 67, 69, 71, 72) // Sailor moon upbeat
            )
            "builtin_6" -> SongNotes(
                bass = intArrayOf(33, 34, 38, 40), // A1, Bb1, D2, E2
                lead = intArrayOf(57, 58, 62, 64, 65, 69, 70, 74) // Japanese Pentatonic
            )
            "builtin_7" -> SongNotes(
                bass = intArrayOf(34, 34, 31, 31), // Bb1, G1 rhythms
                lead = intArrayOf(58, 60, 61, 63, 65, 67, 68, 70) // Black Suit HipHop
            )
            else -> SongNotes(
                bass = intArrayOf(36, 40, 43, 45),
                lead = intArrayOf(60, 64, 67, 69)
            )
        }
    }

    private fun getBpmForSong(id: String): Int {
        return when (id) {
            "builtin_1" -> 85
            "builtin_2" -> 118
            "builtin_3" -> 72
            "builtin_4" -> 110
            "builtin_5" -> 130
            "builtin_6" -> 78
            "builtin_7" -> 92
            else -> 100
        }
    }
}
