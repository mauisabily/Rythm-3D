package com.example.sound

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class InstrumentType {
    PIANO,
    GUITAR,
    SYNTH
}

class ToneGenerator {
    private val sampleRate = 22050 // Optimized sample rate for mobile performance and low overhead
    private val scope = CoroutineScope(Dispatchers.Default)

    var currentInstrument = InstrumentType.PIANO

    // ABCDEFG frequencies
    private val noteFrequencies = doubleArrayOf(
        440.00, // A4
        493.88, // B4
        523.25, // C5
        587.33, // D5
        659.25, // E5
        698.46, // F5
        783.99  // G5
    )

    fun playNoteIndex(index: Int, durationMs: Int = 200) {
        if (index < 0 || index >= noteFrequencies.size) return
        val frequency = noteFrequencies[index]
        playTone(frequency, durationMs)
    }

    fun playChord(frequencies: DoubleArray, durationMs: Int) {
        scope.launch {
            try {
                val numSamples = (durationMs * sampleRate) / 1000
                val buffer = ShortArray(numSamples)
                
                // For guitar, we simulate a fast strum delay between vocalized strings (approx 16ms = 350 samples at 22050Hz)
                val strumDelaySamples = if (currentInstrument == InstrumentType.GUITAR) 350 else 0
                
                for (i in 0 until numSamples) {
                    var signalSum = 0.0
                    var activeVoices = 0
                    
                    for (voiceIdx in frequencies.indices) {
                        val freq = frequencies[voiceIdx]
                        val delay = voiceIdx * strumDelaySamples
                        if (i >= delay) {
                            activeVoices++
                            val t = (i - delay).toDouble() / sampleRate
                            
                            // Soft attack envelope to prevent click + organic acoustic roll-off Decay
                            val envelope = when (currentInstrument) {
                                InstrumentType.PIANO -> {
                                    val attack = (t / 0.015).coerceAtMost(1.0)
                                    attack * Math.exp(-t * 2.2)
                                }
                                InstrumentType.GUITAR -> {
                                    val attack = (t / 0.006).coerceAtMost(1.0)
                                    attack * Math.exp(-t * 3.0)
                                }
                                else -> { // SYNTH
                                    when {
                                        i < 200 -> i.toDouble() / 200.0
                                        i > numSamples - 1000 -> (numSamples - i).toDouble() / 1000.0
                                        else -> 1.0
                                    }
                                }
                            }
                            
                            // High-fidelity harmonics/overtones
                            val voiceSignal = when (currentInstrument) {
                                InstrumentType.PIANO -> {
                                    val h1 = Math.sin(2.0 * Math.PI * freq * t) * 1.0
                                    val h2 = Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.5 * Math.exp(-t * 1.0)
                                    val h3 = Math.sin(2.0 * Math.PI * (freq * 3.0) * t) * 0.25 * Math.exp(-t * 2.0)
                                    val h4 = Math.sin(2.0 * Math.PI * (freq * 4.0) * t) * 0.12 * Math.exp(-t * 3.0)
                                    (h1 + h2 + h3 + h4) / 1.87
                                }
                                InstrumentType.GUITAR -> {
                                    val h1 = Math.sin(2.0 * Math.PI * freq * t) * 1.0
                                    val h2 = Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.4 * Math.exp(-t * 2.0)
                                    val h3 = Math.sin(2.0 * Math.PI * (freq * 3.0) * t) * 0.52 * Math.exp(-t * 1.0)
                                    val h4 = Math.sin(2.0 * Math.PI * (freq * 4.0) * t) * 0.16 * Math.exp(-t * 4.0)
                                    (h1 + h2 + h3 + h4) / 2.08
                                }
                                else -> { // SYNTH
                                    (Math.sin(2.0 * Math.PI * freq * t) * 0.85 + Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.15)
                                }
                            }
                            
                            signalSum += voiceSignal * envelope
                        }
                    }
                    
                    if (activeVoices > 0) {
                        val averageSignal = signalSum / (frequencies.size.toDouble().coerceAtLeast(1.0) * 0.9)
                        buffer[i] = (averageSignal * Short.MAX_VALUE * 0.6).toInt().toShort()
                    } else {
                        buffer[i] = 0
                    }
                }

                // Create static track for immediate playback at low latency
                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC
                )

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                // Efficiently dispose track resources after playback
                scope.launch {
                    try {
                        delay(durationMs.toLong() + 50)
                        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack.stop()
                        }
                        audioTrack.release()
                    } catch (e: Exception) {
                        Log.e("ToneGenerator", "Error cleaning up AudioTrack", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("ToneGenerator", "Error synthesizing chord", e)
            }
        }
    }

    fun playChordIndex(index: Int, durationMs: Int = 280) {
        // Octave-lowered richer full harmonic major/minor/diminished chords for kid and standard play:
        val chordFrequencies = when (index) {
            0 -> doubleArrayOf(220.00, 261.63, 329.63, 440.00) // Am (octave root + triad root + 3rd + 5th)
            1 -> doubleArrayOf(246.94, 293.66, 349.23, 493.88) // Bdim
            2 -> doubleArrayOf(261.63, 329.63, 392.00, 523.25) // C Major
            3 -> doubleArrayOf(293.66, 349.23, 440.00, 587.33) // Dm
            4 -> doubleArrayOf(329.63, 392.00, 493.88, 659.25) // Em
            5 -> doubleArrayOf(349.23, 440.00, 523.25, 698.46) // F Major
            6 -> doubleArrayOf(392.00, 493.88, 587.33, 783.99) // G Major
            else -> doubleArrayOf(261.63, 329.63, 392.00, 523.25)
        }
        playChord(chordFrequencies, durationMs)
    }

    fun playTone(frequency: Double, durationMs: Int) {
        scope.launch {
            try {
                val numSamples = (durationMs * sampleRate) / 1000
                val buffer = ShortArray(numSamples)
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    
                    // Attack-Decay-Sustain-Release style visual/audio envelopes
                    val envelope = when (currentInstrument) {
                        InstrumentType.PIANO -> {
                            val attack = (t / 0.012).coerceAtMost(1.0)
                            attack * Math.exp(-t * 2.5)
                        }
                        InstrumentType.GUITAR -> {
                            val attack = (t / 0.005).coerceAtMost(1.0)
                            attack * Math.exp(-t * 3.5)
                        }
                        else -> { // Retro flat ADSR sweep
                            when {
                                i < 200 -> i.toDouble() / 200.0
                                i > numSamples - 1000 -> (numSamples - i).toDouble() / 1000.0
                                else -> 1.0
                            }
                        }
                    }

                    // Harmonic overtones based on instrument type
                    val signal = when (currentInstrument) {
                        InstrumentType.PIANO -> {
                            val h1 = Math.sin(2.0 * Math.PI * frequency * t) * 1.0
                            val h2 = Math.sin(2.0 * Math.PI * (frequency * 2.0) * t) * 0.5 * Math.exp(-t * 1.0)
                            val h3 = Math.sin(2.0 * Math.PI * (frequency * 3.0) * t) * 0.25 * Math.exp(-t * 2.0)
                            val h4 = Math.sin(2.0 * Math.PI * (frequency * 4.0) * t) * 0.12 * Math.exp(-t * 3.0)
                            (h1 + h2 + h3 + h4) / 1.87
                        }
                        InstrumentType.GUITAR -> {
                            val h1 = Math.sin(2.0 * Math.PI * frequency * t) * 1.0
                            val h2 = Math.sin(2.0 * Math.PI * (frequency * 2.0) * t) * 0.4 * Math.exp(-t * 2.0)
                            val h3 = Math.sin(2.0 * Math.PI * (frequency * 3.0) * t) * 0.52 * Math.exp(-t * 1.0)
                            val h4 = Math.sin(2.0 * Math.PI * (frequency * 4.0) * t) * 0.16 * Math.exp(-t * 4.0)
                            (h1 + h2 + h3 + h4) / 2.08
                        }
                        else -> { // Retro Synth
                            (Math.sin(2.0 * Math.PI * frequency * t) * 0.85 + Math.sin(2.0 * Math.PI * (frequency * 2.0) * t) * 0.15)
                        }
                    }
                    
                    buffer[i] = (signal * Short.MAX_VALUE * envelope * 0.45).toInt().toShort()
                }

                // Create static track for immediate playback at low latency
                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC
                )

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                // Efficiently dispose track resources after playback
                scope.launch {
                    try {
                        delay(durationMs.toLong() + 50)
                        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack.stop()
                        }
                        audioTrack.release()
                    } catch (e: Exception) {
                        Log.e("ToneGenerator", "Error cleaning up AudioTrack", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("ToneGenerator", "Error synthesizing tone", e)
            }
        }
    }
}
