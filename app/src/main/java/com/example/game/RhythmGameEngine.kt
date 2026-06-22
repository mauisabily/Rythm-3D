package com.example.game

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sound.ToneGenerator
import com.example.sound.InstrumentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// Representation of a dynamic rhythmic beat note block
data class RhythmNote(
    val id: String = UUID.randomUUID().toString(),
    val lane: Int,            // 0..6 representing notes A, B, C, D, E, F, G
    val hitTimeMs: Long,      // Absolute offset in ms from song start when note reaches hit zone
    var isHit: Boolean = false,
    var isMissed: Boolean = false,
    var scoreValue: String = "", // "PERFECT", "GOOD", "MISS"
    var glowIntensity: Float = 0.0f,
    var fadeAlpha: Float = 1.0f,
    val frequencyHz: Double? = null,
    val chordFrequencies: DoubleArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RhythmNote) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

// Song tracks
data class RhythmSong(
    val title: String,
    val artist: String,
    val bpm: Int,
    val difficulty: String,
    val noteDurationMs: Long, // Duration of the track
    val notes: List<Pair<Int, Long>>, // Pair of (LaneIndex, TimeOffsetMs)
    val isKidsSong: Boolean = false,
    val kidsLaneCount: Int = 7,
    val kidsTheme: String = "" // "balloon", "dino", "unicorn"
)

class RhythmGameViewModel : ViewModel() {
    val toneGenerator = ToneGenerator()

    // Game stats
    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo

    private val _maxCombo = MutableStateFlow(0)
    val maxCombo: StateFlow<Int> = _maxCombo

    private val _songProgress = MutableStateFlow(0.0f)
    val songProgress: StateFlow<Float> = _songProgress

    private val _elapsedTimeMs = MutableStateFlow(0L)
    val elapsedTimeMs: StateFlow<Long> = _elapsedTimeMs

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _selectedSongName = MutableStateFlow("Synthwave Odyssey")
    val selectedSongName: StateFlow<String> = _selectedSongName

    private val _isKidsArena = MutableStateFlow(false)
    val isKidsArena: StateFlow<Boolean> = _isKidsArena

    private val _currentInstrument = MutableStateFlow(InstrumentType.PIANO)
    val currentInstrument: StateFlow<InstrumentType> = _currentInstrument

    fun changeInstrument(instrument: InstrumentType) {
        _currentInstrument.value = instrument
        toneGenerator.currentInstrument = instrument
    }

    private val _scrollSpeedMultiplier = MutableStateFlow(3) // Default 3x slower for wonderful human reactions!
    val scrollSpeedMultiplier: StateFlow<Int> = _scrollSpeedMultiplier

    fun setScrollSpeedMultiplier(multiplier: Int) {
        _scrollSpeedMultiplier.value = multiplier.coerceIn(1, 5)
    }

    fun setKidsArena(enabled: Boolean) {
        _isKidsArena.value = enabled
        val songsList = if (enabled) getKidsPresetSongs() else getPresetSongs()
        selectSong(songsList[0].title)
    }

    private val _gameRating = MutableStateFlow("") // Displays PERFECT!, GOOD!, or MISS! temporarily
    val gameRating: StateFlow<String> = _gameRating

    // Active live notes rendered in the interactive 3D track
    val activeNotes = mutableStateListOf<RhythmNote>()

    // Recording custom tracks (for music enthusiasts!)
    val isRecording = mutableStateOf(false)
    val recordedNotesList = mutableStateListOf<Pair<Int, Long>>()
    var recordingStartTime = 0L

    // Tracks hit/flash triggers for lane buttons
    val laneFeedback = mutableStateListOf(0L, 0L, 0L, 0L, 0L, 0L, 0L)
    val laneHitStatus = mutableStateListOf("", "", "", "", "", "", "") // "PERFECT", "GOOD", "MISS"
    var kidsMelodyIndex = 0

    private var gameLoopJob: Job? = null
    var currentSong: RhythmSong = getPresetSongs()[0]
        private set

    // Setup track presets
    fun selectSong(songTitle: String) {
        val preset = (getPresetSongs() + getKidsPresetSongs()).find { it.title == songTitle } ?: getPresetSongs()[0]
        currentSong = preset
        _selectedSongName.value = preset.title
        resetGame()
    }

    private fun resetGame() {
        _score.value = 0
        _combo.value = 0
        _maxCombo.value = 0
        _elapsedTimeMs.value = 0L
        _songProgress.value = 0.0f
        _isPlaying.value = false
        _gameRating.value = ""
        activeNotes.clear()
        kidsMelodyIndex = 0
        
        // Spawn all notes from the pre-configured layout inside active list representing ABCDEFG lanes
        currentSong.notes.forEachIndexed { idx, pair ->
            val noteFreq = getMelodyFrequency(currentSong.title, idx, pair.first)
            val chord = getMelodyChord(currentSong.title, idx)
            activeNotes.add(
                RhythmNote(
                    lane = pair.first,
                    hitTimeMs = pair.second,
                    frequencyHz = noteFreq,
                    chordFrequencies = chord
                )
            )
        }

        for (i in 0..6) {
            laneFeedback[i] = 0L
            laneHitStatus[i] = ""
        }
    }

    fun startPlaying() {
        if (_isPlaying.value) return
        resetGame()
        _isPlaying.value = true
        val startTime = System.currentTimeMillis()

        gameLoopJob = viewModelScope.launch {
            while (_isPlaying.value) {
                val elapsed = System.currentTimeMillis() - startTime
                _elapsedTimeMs.value = elapsed
                _songProgress.value = (elapsed.toFloat() / currentSong.noteDurationMs).coerceIn(0.0f, 1.0f)

                // Detect past notes that the user completely missed (exceeded late hit threshold)
                activeNotes.forEach { note ->
                    val missThreshold = if (currentSong.isKidsSong) 2500 else 200
                    if (!note.isHit && !note.isMissed && (elapsed - note.hitTimeMs) > missThreshold) {
                        note.isMissed = true
                        note.scoreValue = "MISS"
                        if (!currentSong.isKidsSong) {
                            _combo.value = 0 // Break combo
                            _gameRating.value = "MISS"
                            triggerRatingFade()
                        }
                        // Immediate clean fade out for missed note
                        viewModelScope.launch {
                            var opacity = 1.0f
                            while (opacity > 0.0f) {
                                note.fadeAlpha = opacity
                                opacity -= 0.15f
                                delay(16)
                            }
                        }
                    }
                }

                // Check for song completion
                if (elapsed >= currentSong.noteDurationMs) {
                    _isPlaying.value = false
                }

                delay(16) // ~60fps checking cadence
            }
        }
    }

    fun stopPlaying() {
        _isPlaying.value = false
        gameLoopJob?.cancel()
    }

    fun startRecordingCustom() {
        recordedNotesList.clear()
        isRecording.value = true
        recordingStartTime = System.currentTimeMillis()
        _isPlaying.value = false
    }

    fun stopRecordingCustom() {
        isRecording.value = false
        if (recordedNotesList.isNotEmpty()) {
            // Build custom song dynamically
            val maxDur = (recordedNotesList.maxOfOrNull { it.second } ?: 1000L) + 1500L
            val customSong = RhythmSong(
                title = "My Recording Preset",
                artist = "Personal Music Synth",
                bpm = 110,
                difficulty = "Custom",
                noteDurationMs = maxDur,
                notes = recordedNotesList.toList()
            )
            // Register as the active track
            currentSong = customSong
            _selectedSongName.value = customSong.title
            resetGame()
        }
    }

    // Main interaction handler: When a user triggers touch pads A-G
    fun triggerLaneHit(laneIndex: Int) {
        // Record if custom recording mode is active
        if (isRecording.value) {
            val offset = System.currentTimeMillis() - recordingStartTime
            recordedNotesList.add(Pair(laneIndex, offset))
            toneGenerator.playNoteIndex(laneIndex, durationMs = 200)
            laneFeedback[laneIndex] = System.currentTimeMillis()
            return
        }

        laneFeedback[laneIndex] = System.currentTimeMillis()

        val isKids = currentSong.isKidsSong

        var playedMelodyNote = false
        if (_isPlaying.value) {
            val currentTrackTime = _elapsedTimeMs.value
            val missLimit = if (isKids) 600 else 240
            
            val targets = activeNotes.filter { it.lane == laneIndex && !it.isHit && !it.isMissed }
            val closestNote = targets.minByOrNull { Math.abs(it.hitTimeMs - currentTrackTime) }
            
            if (closestNote != null && Math.abs(closestNote.hitTimeMs - currentTrackTime) <= missLimit) {
                if (isKids) {
                    toneGenerator.playChordIndex(laneIndex, durationMs = 280)
                } else {
                    val noteFreq = closestNote.frequencyHz
                    val chord = closestNote.chordFrequencies
                    if (noteFreq != null) {
                        if (chord != null) {
                            toneGenerator.playChord(chord, durationMs = 280)
                        } else {
                            toneGenerator.playTone(noteFreq, durationMs = 280)
                        }
                    }
                }
                playedMelodyNote = true
            }
        }

        if (!playedMelodyNote) {
            if (isKids) {
                toneGenerator.playChordIndex(laneIndex, durationMs = 280)
            } else {
                toneGenerator.playNoteIndex(laneIndex, durationMs = 180)
            }
        }

        if (!_isPlaying.value) return

        val currentTrackTime = _elapsedTimeMs.value

        // Look for the closest note in this corresponding lane within standard hit window
        val targets = activeNotes.filter { it.lane == laneIndex && !it.isHit && !it.isMissed }
        if (targets.isEmpty()) return

        // Get the closest note to compare hit accuracy
        val closestNote = targets.minByOrNull { Math.abs(it.hitTimeMs - currentTrackTime) } ?: return
        val offset = Math.abs(closestNote.hitTimeMs - currentTrackTime)

        val perfectLimit = if (isKids) 250 else 80
        val goodLimit = if (isKids) 450 else 160
        val missLimit = if (isKids) 600 else 240

        if (offset <= perfectLimit) { // Perfect Hit! Glow intensifies
            closestNote.isHit = true
            closestNote.scoreValue = "PERFECT"
            closestNote.glowIntensity = 1.0f
            _score.value += 100 + (_combo.value * 5)
            _combo.value += 1
            if (_combo.value > _maxCombo.value) {
                _maxCombo.value = _combo.value
            }
            _gameRating.value = if (isKids) "SUPER! ⭐" else "PERFECT!"
            laneHitStatus[laneIndex] = "PERFECT"
            triggerRatingFade()

            // Flash neon glowing visual anim path
            viewModelScope.launch {
                var intensity = 1.0f
                while (intensity > 0.0f) {
                    closestNote.glowIntensity = intensity
                    closestNote.fadeAlpha = intensity
                    intensity -= 0.08f
                    delay(20)
                }
            }
        } else if (offset <= goodLimit) { // Good Hit! Nice glow
            closestNote.isHit = true
            closestNote.scoreValue = "GOOD"
            closestNote.glowIntensity = 0.5f
            _score.value += 50
            _combo.value += 1
            if (_combo.value > _maxCombo.value) {
                _maxCombo.value = _combo.value
            }
            _gameRating.value = if (isKids) "AWESOME! ✨" else "GOOD!"
            laneHitStatus[laneIndex] = "GOOD"
            triggerRatingFade()

            viewModelScope.launch {
                var intensity = 0.5f
                while (intensity > 0.0f) {
                    closestNote.glowIntensity = intensity
                    closestNote.fadeAlpha = intensity
                    intensity -= 0.08f
                    delay(20)
                }
            }
        } else if (offset <= missLimit) { // Bad/Early/Late Hit! Minimal glow
            closestNote.isHit = true
            closestNote.scoreValue = "MISS"
            closestNote.glowIntensity = 0.0f
            closestNote.fadeAlpha = 0.0f
            if (!isKids) {
                _combo.value = 0 // Break combo
                _gameRating.value = "MISS"
                laneHitStatus[laneIndex] = "MISS"
                triggerRatingFade()
            } else {
                // Kids mode generous bonus! Count as oopsy-great instead of severe miss
                _score.value += 20
                _combo.value += 1
                _gameRating.value = "GREAT! 🦖"
                laneHitStatus[laneIndex] = "GOOD"
                triggerRatingFade()
            }
        }
    }

    private fun triggerRatingFade() {
         // Auto clear feedback after short delay
         viewModelScope.launch {
             delay(600)
             _gameRating.value = ""
         }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaying()
    }

    companion object {
        fun getPresetSongs(): List<RhythmSong> {
            return listOf(
                RhythmSong(
                    title = "Synthwave Odyssey",
                    artist = "Cyber Pulse",
                    bpm = 120,
                    difficulty = "Hard",
                    noteDurationMs = 25000,
                    notes = generateAutoNotes(120, 25000, complexity = 3)
                ),
                RhythmSong(
                    title = "Cosmic Midnight",
                    artist = "Stella Drift",
                    bpm = 95,
                    difficulty = "Medium",
                    noteDurationMs = 28000,
                    notes = generateAutoNotes(95, 28000, complexity = 2)
                ),
                RhythmSong(
                    title = "Acoustic Breeze",
                    artist = "Lofi Horizon",
                    bpm = 75,
                    difficulty = "Easy",
                    noteDurationMs = 30000,
                    notes = generateAutoNotes(75, 30000, complexity = 1)
                )
            )
        }

        fun getKidsPresetSongs(): List<RhythmSong> {
            return listOf(
                RhythmSong(
                    title = "⭐ Twinkle Twinkle",
                    artist = "Toddler Favorite",
                    bpm = 70,
                    difficulty = "Easy",
                    noteDurationMs = 46000,
                    notes = generateTwinkleNotes(),
                    isKidsSong = true,
                    kidsLaneCount = 7,
                    kidsTheme = "balloon"
                ),
                RhythmSong(
                    title = "🐑 Mary Had a Little Lamb",
                    artist = "Cute Melody",
                    bpm = 76,
                    difficulty = "Easy",
                    noteDurationMs = 30000,
                    notes = generateMaryNotes(),
                    isKidsSong = true,
                    kidsLaneCount = 7,
                    kidsTheme = "dino"
                ),
                RhythmSong(
                    title = "🛶 Row Your Boat",
                    artist = "Fast Pace",
                    bpm = 95,
                    difficulty = "Medium",
                    noteDurationMs = 32000,
                    notes = generateRowYourBoatNotes(),
                    isKidsSong = true,
                    kidsLaneCount = 7,
                    kidsTheme = "boat"
                )
            )
        }

        fun generateTwinkleNotes(): List<Pair<Int, Long>> {
            val melody = listOf(
                Pair(2, 261.63), Pair(2, 261.63), Pair(6, 392.00), Pair(6, 392.00), Pair(0, 440.00), Pair(0, 440.00), Pair(6, 392.00), Pair(-1, 0.0),
                Pair(5, 349.23), Pair(5, 349.23), Pair(4, 329.63), Pair(4, 329.63), Pair(3, 293.66), Pair(3, 293.66), Pair(2, 261.63), Pair(-1, 0.0),
                Pair(6, 392.00), Pair(6, 392.00), Pair(5, 349.23), Pair(5, 349.23), Pair(4, 329.63), Pair(4, 329.63), Pair(3, 293.66), Pair(-1, 0.0),
                Pair(6, 392.00), Pair(6, 392.00), Pair(5, 349.23), Pair(5, 349.23), Pair(4, 329.63), Pair(4, 329.63), Pair(3, 293.66), Pair(-1, 0.0),
                Pair(2, 261.63), Pair(2, 261.63), Pair(6, 392.00), Pair(6, 392.00), Pair(0, 440.00), Pair(0, 440.00), Pair(6, 392.00), Pair(-1, 0.0),
                Pair(5, 349.23), Pair(5, 349.23), Pair(4, 329.63), Pair(4, 329.63), Pair(3, 293.66), Pair(3, 293.66), Pair(2, 261.63)
            )
            
            val list = mutableListOf<Pair<Int, Long>>()
            val beatMs = 1000L // 1.0s slow rhythmic notes for kid reactions
            var currentTime = 2000L
            
            melody.forEach { item ->
                if (item.first != -1) {
                    list.add(Pair(item.first, currentTime))
                }
                currentTime += beatMs
            }
            return list
        }

        fun generateMaryNotes(): List<Pair<Int, Long>> {
            val melody = listOf(
                Pair(4, 329.63), Pair(3, 293.66), Pair(2, 261.63), Pair(3, 293.66), Pair(4, 329.63), Pair(4, 329.63), Pair(4, 329.63), Pair(-1, 0.0),
                Pair(3, 293.66), Pair(3, 293.66), Pair(3, 293.66), Pair(-1, 0.0),
                Pair(4, 329.63), Pair(6, 392.00), Pair(6, 392.00), Pair(-1, 0.0),
                Pair(4, 329.63), Pair(3, 293.66), Pair(2, 261.63), Pair(3, 293.66), Pair(4, 329.63), Pair(4, 329.63), Pair(4, 329.63), Pair(4, 329.63),
                Pair(3, 293.66), Pair(3, 293.66), Pair(4, 329.63), Pair(3, 293.66), Pair(2, 261.63)
            )
            
            val list = mutableListOf<Pair<Int, Long>>()
            val beatMs = 1000L
            var currentTime = 2000L
            
            melody.forEach { item ->
                if (item.first != -1) {
                    list.add(Pair(item.first, currentTime))
                }
                currentTime += beatMs
            }
            return list
        }

        fun generateRowYourBoatNotes(): List<Pair<Int, Long>> {
            val melody = listOf(
                Pair(2, 261.63), Pair(2, 261.63), Pair(2, 261.63), Pair(3, 293.66), Pair(4, 329.63), Pair(-1, 0.0),
                Pair(4, 329.63), Pair(3, 293.66), Pair(4, 329.63), Pair(5, 349.23), Pair(6, 392.00), Pair(-1, 0.0),
                Pair(2, 523.25), Pair(2, 523.25), Pair(2, 523.25), Pair(6, 392.00), Pair(6, 392.00), Pair(6, 392.00),
                Pair(4, 329.63), Pair(4, 329.63), Pair(4, 329.63), Pair(2, 261.63), Pair(2, 261.63), Pair(2, 261.63), Pair(-1, 0.0),
                Pair(6, 392.00), Pair(5, 349.23), Pair(4, 329.63), Pair(3, 293.66), Pair(2, 261.63)
            )
            
            val list = mutableListOf<Pair<Int, Long>>()
            val beatMs = 800L
            var currentTime = 2000L
            
            melody.forEach { item ->
                if (item.first != -1) {
                    list.add(Pair(item.first, currentTime))
                }
                currentTime += beatMs
            }
            return list
        }

        fun getMelodyFrequency(songTitle: String, noteIndex: Int, lane: Int): Double {
            return when {
                songTitle.contains("Twinkle") -> {
                    val pitches = listOf(
                        261.63, 261.63, 392.00, 392.00, 440.00, 440.00, 392.00,
                        349.23, 349.23, 329.63, 329.63, 293.66, 293.66, 261.63,
                        392.00, 392.00, 349.23, 349.23, 329.63, 329.63, 293.66,
                        392.00, 392.00, 349.23, 349.23, 329.63, 329.63, 293.66,
                        261.63, 261.63, 392.00, 392.00, 440.00, 440.00, 392.00,
                        349.23, 349.23, 329.63, 329.63, 293.66, 293.66, 261.63
                    )
                    pitches.getOrElse(noteIndex) { 261.63 }
                }
                songTitle.contains("Mary") || songTitle.contains("Lamb") -> {
                    val pitches = listOf(
                        329.63, 293.66, 261.63, 293.66, 329.63, 329.63, 329.63,
                        293.66, 293.66, 293.66,
                        329.63, 392.00, 392.00,
                        329.63, 293.66, 261.63, 293.66, 329.63, 329.63, 329.63, 329.63,
                        293.66, 293.66, 329.63, 293.66, 261.63
                    )
                    pitches.getOrElse(noteIndex) { 329.63 }
                }
                songTitle.contains("Row") || songTitle.contains("Boat") -> {
                    val pitches = listOf(
                        261.63, 261.63, 261.63, 293.66, 329.63,
                        329.63, 293.66, 329.63, 349.23, 392.00,
                        523.25, 523.25, 523.25, 392.00, 392.00, 392.00,
                        329.63, 329.63, 329.63, 261.63, 261.63, 261.63,
                        392.00, 349.23, 329.63, 293.66, 261.63
                    )
                    pitches.getOrElse(noteIndex) { 261.63 }
                }
                else -> {
                    val standardNoteFrequencies = doubleArrayOf(440.00, 493.88, 523.25, 587.33, 659.25, 698.46, 783.99)
                    standardNoteFrequencies.getOrElse(lane) { 440.00 }
                }
            }
        }

        fun getMelodyChord(songTitle: String, noteIndex: Int): DoubleArray? {
            val C_CHORD = doubleArrayOf(130.81, 261.63, 329.63, 392.00) // Rich C Major
            val F_CHORD = doubleArrayOf(174.61, 349.23, 440.00, 523.25) // Rich F Major
            val G_CHORD = doubleArrayOf(146.83, 293.66, 392.00, 493.88) // Rich G Major
            
            return when {
                songTitle.contains("Twinkle") -> {
                    val phrase = noteIndex / 7
                    when (phrase) {
                        0 -> C_CHORD
                        1 -> F_CHORD
                        2 -> G_CHORD
                        3 -> F_CHORD
                        4 -> C_CHORD
                        else -> C_CHORD
                    }
                }
                songTitle.contains("Mary") || songTitle.contains("Lamb") -> {
                    when {
                        noteIndex < 7 -> C_CHORD
                        noteIndex < 10 -> G_CHORD
                        noteIndex < 13 -> C_CHORD
                        else -> C_CHORD
                    }
                }
                songTitle.contains("Row") || songTitle.contains("Boat") -> {
                    when {
                        noteIndex < 5 -> C_CHORD
                        noteIndex < 10 -> G_CHORD
                        noteIndex < 16 -> C_CHORD
                        else -> G_CHORD
                    }
                }
                else -> null
            }
        }

        private fun generateKidsAutoNotes(bpm: Int, totalDurationMs: Long, maxLanes: Int): List<Pair<Int, Long>> {
            val list = mutableListOf<Pair<Int, Long>>()
            val beatIntervalMs = (60000 / bpm).toLong()
            var currentMs = 2000L // Extra padding so kids have time to get ready!

            while (currentMs < totalDurationMs - 2000) {
                val lane = (Math.random() * maxLanes).toInt()
                list.add(Pair(lane, currentMs))
                
                // Keep the intervals very long and stress-free (e.g., spacing by 2, 3 or 4 full beats)
                val beatStep = when (Math.random()) {
                    in 0.0..0.4 -> 2
                    in 0.4..0.8 -> 3
                    else -> 4
                }
                currentMs += beatIntervalMs * beatStep
            }
            return list.sortedBy { it.second }
        }

        // Generates an amazing rhythm track based on BMP and track time containing lanes 0-6 (ABCDEFG)
        private fun generateAutoNotes(bpm: Int, totalDurationMs: Long, complexity: Int): List<Pair<Int, Long>> {
            val list = mutableListOf<Pair<Int, Long>>()
            val beatIntervalMs = (60000 / bpm).toLong()
            var currentMs = 1500L // Padding start

            val lanes = intArrayOf(0, 1, 2, 3, 4, 5, 6) // A, B, C, D, E, F, G

            while (currentMs < totalDurationMs - 1500) {
                when (complexity) {
                    1 -> { // Simplistic Single Note Streams
                        val lane = (currentMs / beatIntervalMs % 7).toInt()
                        list.add(Pair(lane, currentMs))
                        currentMs += beatIntervalMs * (if (Math.random() > 0.3) 2 else 1)
                    }
                    2 -> { // Medium: Syncopated Single & Chord Steps
                        val mainLane = (currentMs / beatIntervalMs % 7).toInt()
                        list.add(Pair(mainLane, currentMs))
                        
                        // Occasionally add syncopated half-beats
                        if (Math.random() > 0.6) {
                            val offBeatLane = (mainLane + 2) % 7
                            list.add(Pair(offBeatLane, currentMs + beatIntervalMs / 2))
                        }
                        currentMs += beatIntervalMs
                    }
                    3 -> { // Hard Level: Multi-note columns, triplets & complex beats
                        val rType = Math.random()
                        if (rType < 0.4) {
                            // Single fast series
                            list.add(Pair((Math.random() * 7).toInt(), currentMs))
                            list.add(Pair((Math.random() * 7).toInt(), currentMs + (beatIntervalMs / 2)))
                        } else if (rType < 0.7) {
                            // Beautiful dual-lane chord hits (A+E, C+G, etc!)
                            val lane1 = (Math.random() * 4).toInt()
                            val lane2 = lane1 + 3
                            list.add(Pair(lane1, currentMs))
                            list.add(Pair(lane2, currentMs))
                        } else {
                            // Standard rhythmic tap
                            list.add(Pair((Math.random() * 7).toInt(), currentMs))
                        }
                        currentMs += beatIntervalMs * (if (Math.random() > 0.7) 1 else 1)
                    }
                }
            }
            return list.sortedBy { it.second }
        }
    }
}
