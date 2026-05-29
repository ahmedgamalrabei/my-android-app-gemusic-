package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PulseAudioSynth
import com.example.data.MusicRepository
import com.example.data.Playlist
import com.example.data.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class RepeatMode {
    NONE, ONE, ALL
}

enum class MusicTheme(val titleAr: String) {
    GOLDEN_ORANGE("جي ميوزك الذهبي"),
    WARM_BRONZE("البرونزي الدافئ"),
    SUNBURST_YELLOW("الأصفر الذهبي"),
    COZY_AMBER("العنبر الداكن")
}

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    private var mediaPlayer: MediaPlayer? = null
    private val synthPlayer = PulseAudioSynth()

    // Database Flows
    val allSongs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state flows
    private val _currentPlaylistId = MutableStateFlow<Int?>(null)
    val currentPlaylistId: StateFlow<Int?> = _currentPlaylistId

    val playlistSongs: StateFlow<List<Song>> = _currentPlaylistId
        .flatMapLatest { id ->
            if (id != null) repository.getSongsInPlaylist(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Filtered songs
    val filteredSongs: StateFlow<List<Song>> = combine(allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active playback states
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    // Playback modifiers
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled

    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode

    // Sound FX / Virtual Equalizer (5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz)
    private val _equalizerBands = MutableStateFlow(floatArrayOf(0f, 0f, 0f, 0f, 0f))
    val equalizerBands: StateFlow<FloatArray> = _equalizerBands

    private val _reverbPreset = MutableStateFlow("ردهة كبيرة")
    val reverbPreset: StateFlow<String> = _reverbPreset

    // Sleep Timer state (minutes remaining, null if inactive)
    private val _sleepTimerRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerRemaining: StateFlow<Int?> = _sleepTimerRemaining

    // Theme selector
    private val _selectedTheme = MutableStateFlow(MusicTheme.GOLDEN_ORANGE)
    val selectedTheme: StateFlow<MusicTheme> = _selectedTheme

    // Playback progression tracking loop
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    // Live visualizer data
    val visualizerData: StateFlow<Float> = synthPlayer.visualizerFlow

    // Active playing list depending on view context
    private var currentQueue: List<Song> = emptyList()

    init {
        viewModelScope.launch {
            repository.initializeBuiltInSongs()
            // Pull the list immediately to form queue
            allSongs.collectLatest { songs ->
                if (currentQueue.isEmpty() && songs.isNotEmpty()) {
                    currentQueue = songs
                    if (_currentSong.value == null) {
                        _currentSong.value = songs.firstOrNull()
                    }
                }
            }
        }
        startProgressTracker()
    }

    fun findSongById(id: String): Song? {
        return allSongs.value.find { it.id == id }
    }

    fun scanLocalFiles() {
        viewModelScope.launch {
            repository.scanDeviceFiles()
            Toast.makeText(getApplication(), "تم تحديث مكتبة الأغاني من ذاكرة الجهاز بنجاح!", Toast.LENGTH_LONG).show()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTheme(theme: MusicTheme) {
        _selectedTheme.value = theme
    }

    // --- Audio Control interface ---
    fun selectAndPlay(song: Song, queueContext: List<Song>) {
        _currentSong.value = song
        currentQueue = if (queueContext.isNotEmpty()) queueContext else allSongs.value
        playSong(song)
    }

    private fun playSong(song: Song) {
        stopAllPlayers()
        _currentPositionMs.value = 0L

        if (song.isBuiltIn) {
            // Use procedurally generated synth engine
            synthPlayer.updateEqualizerGains(_equalizerBands.value)
            synthPlayer.start(song.id, 0L)
            _isPlaying.value = true
        } else {
            // Local device song
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(song.path)
                    prepare()
                    start()
                    setOnCompletionListener {
                        onSongCompleted()
                    }
                }
                _isPlaying.value = true
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error playing local audio: ${e.message}")
                Toast.makeText(getApplication(), "غير قادر على تشغيل هذا الملف الصوتي", Toast.LENGTH_SHORT).show()
                _isPlaying.value = false
                nextTrack()
            }
        }
    }

    private fun onSongCompleted() {
        viewModelScope.launch(Dispatchers.Main) {
            nextTrack()
        }
    }

    fun togglePlayPause() {
        val song = _currentSong.value ?: return

        if (_isPlaying.value) {
            pausePlayback()
        } else {
            resumePlayback(song)
        }
    }

    private fun pausePlayback() {
        _isPlaying.value = false
        if (mediaPlayer != null) {
            mediaPlayer?.pause()
        } else {
            synthPlayer.pause()
        }
    }

    private fun resumePlayback(song: Song) {
        _isPlaying.value = true
        if (song.isBuiltIn) {
            synthPlayer.resume()
        } else {
            if (mediaPlayer != null) {
                mediaPlayer?.start()
            } else {
                playSong(song)
            }
        }
    }

    fun nextTrack() {
        val song = _currentSong.value ?: return
        if (currentQueue.isEmpty()) return

        var nextIndex: Int
        if (_isShuffleEnabled.value) {
            nextIndex = (0 until currentQueue.size).random()
        } else {
            val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
            nextIndex = currentIndex + 1
            if (nextIndex >= currentQueue.size) {
                nextIndex = if (_repeatMode.value == RepeatMode.ALL) 0 else currentQueue.size - 1
            }
        }

        if (nextIndex in currentQueue.indices) {
            selectAndPlay(currentQueue[nextIndex], currentQueue)
        }
    }

    fun previousTrack() {
        val song = _currentSong.value ?: return
        if (currentQueue.isEmpty()) return

        var prevIndex: Int
        if (_isShuffleEnabled.value) {
            prevIndex = (0 until currentQueue.size).random()
        } else {
            val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
            prevIndex = currentIndex - 1
            if (prevIndex < 0) {
                prevIndex = if (_repeatMode.value == RepeatMode.ALL) currentQueue.size - 1 else 0
            }
        }

        if (prevIndex in currentQueue.indices) {
            selectAndPlay(currentQueue[prevIndex], currentQueue)
        }
    }

    fun seekTo(positionMs: Long) {
        val song = _currentSong.value ?: return
        _currentPositionMs.value = positionMs
        if (song.isBuiltIn) {
            synthPlayer.seekTo(positionMs)
        } else {
            mediaPlayer?.seekTo(positionMs.toInt())
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
            // also update selected song favorite status
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = _currentSong.value?.copy(isFavorite = !song.isFavorite)
            }
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    // --- Equalizer & Reverb controls ---
    fun updateEqualizerBand(bandIndex: Int, value: Float) {
        val updated = _equalizerBands.value.copyOf()
        if (bandIndex in updated.indices) {
            updated[bandIndex] = value
            _equalizerBands.value = updated
            // Apply to active synth player immediately
            synthPlayer.updateEqualizerGains(updated)
        }
    }

    fun updateReverbPreset(preset: String) {
        _reverbPreset.value = preset
        // Simple filter impact emulation depending on selected reverb type
        val customEQ = when (preset) {
            "عادي" -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
            "ردهة كبيرة" -> floatArrayOf(6f, 3f, -2f, -4f, -6f) // boosted bass, cut treble
            "ردهة صغيرة" -> floatArrayOf(3f, 1f, 0f, -1f, -2f)
            "مسرح" -> floatArrayOf(2f, 4f, 6f, 2f, -1f) // boosted mid-range vocal focus
            "مغارة" -> floatArrayOf(8f, 5f, -4f, -6f, -10f)
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        }
        _equalizerBands.value = customEQ
        synthPlayer.updateEqualizerGains(customEQ)
    }

    // --- Sleep Timer ---
    fun setSleepTimer(minutes: Int?) {
        _sleepTimerRemaining.value = minutes
        sleepTimerJob?.cancel()

        if (minutes != null) {
            sleepTimerJob = viewModelScope.launch {
                var currentMin = minutes
                while (currentMin > 0) {
                    delay(60000L) // wait 1 minute
                    currentMin--
                    _sleepTimerRemaining.value = currentMin
                }
                // Sleep timer ended! Pause audio
                pausePlayback()
                _sleepTimerRemaining.value = null
                Log.d("MusicViewModel", "Sleep timer ended. Playback paused.")
            }
        }
    }

    // --- Custom Playlists DB controls ---
    fun createAndAddPlaylist(name: String, initialSong: Song? = null) {
        viewModelScope.launch {
            val pId = repository.createPlaylist(name)
            if (initialSong != null) {
                repository.addSongToPlaylist(pId, initialSong.id)
            }
        }
    }

    fun addSongToPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    // --- Internal Helpers ---
    private fun startProgressTracker() {
        progressJob = viewModelScope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    val song = _currentSong.value
                    if (song != null) {
                        if (song.isBuiltIn) {
                            val currentPos = synthPlayer.getPositionMs()
                            _currentPositionMs.value = currentPos
                            if (currentPos >= song.duration) {
                                // Loop single track or play next
                                if (_repeatMode.value == RepeatMode.ONE) {
                                    seekTo(0)
                                } else {
                                    nextTrack()
                                }
                            }
                        } else {
                            mediaPlayer?.let { player ->
                                if (player.isPlaying) {
                                    _currentPositionMs.value = player.currentPosition.toLong()
                                }
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopAllPlayers() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        synthPlayer.stop()
        _isPlaying.value = false
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        stopAllPlayers()
    }
}
