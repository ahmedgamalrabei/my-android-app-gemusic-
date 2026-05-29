package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    suspend fun initializeBuiltInSongs() = withContext(Dispatchers.IO) {
        val count = songDao.getBuiltInSongs().size
        if (count == 0) {
            val builtInTracks = listOf(
                Song(
                    id = "builtin_1",
                    title = "Golden Sky (السماء الذهبية)",
                    artist = "lucasfroom",
                    album = "ألبوم النبض الإلكتروني",
                    duration = 247000,
                    path = "builtin://golden_sky",
                    isFavorite = true,
                    mimeType = "audio/synthetic",
                    folder = "نغمة هادئة",
                    isBuiltIn = true
                ),
                Song(
                    id = "builtin_2",
                    title = "Colorful World (عالم ملون)",
                    artist = "Yasmine",
                    album = "ألبوم الألوان الرقيقة",
                    duration = 182000,
                    path = "builtin://colorful_world",
                    isFavorite = false,
                    mimeType = "audio/synthetic",
                    folder = "إيقاع حركي",
                    isBuiltIn = true
                ),
                Song(
                    id = "builtin_3",
                    title = "Sweet Dreams (أحلام سعيدة)",
                    artist = "GRABOTE",
                    album = "سينث الكونية",
                    duration = 215000,
                    path = "builtin://sweet_dreams",
                    isFavorite = false,
                    mimeType = "audio/synthetic",
                    folder = "نغمة هادئة",
                    isBuiltIn = true
                ),
                Song(
                    id = "builtin_4",
                    title = "Sunny Rain (مطر مشمس)",
                    artist = "Akin",
                    album = "أثير الطبيعة",
                    duration = 198000,
                    path = "builtin://sunny_rain",
                    isFavorite = true,
                    mimeType = "audio/synthetic",
                    folder = "إيقاع حركي",
                    isBuiltIn = true
                ),
                Song(
                    id = "builtin_5",
                    title = "Sailor Moon (بحارة القمر)",
                    artist = "Manga de Amigos",
                    album = "موسيقى الأنيمي",
                    duration = 154000,
                    path = "builtin://sailor_moon",
                    isFavorite = false,
                    mimeType = "audio/synthetic",
                    folder = "أنيمي وبوب",
                    isBuiltIn = true
                ),
                Song(
                    id = "builtin_6",
                    title = "Sakura (ساكورا)",
                    artist = "NZV Remix",
                    album = "ريتشموند جروف",
                    duration = 265000,
                    path = "builtin://sakura",
                    isFavorite = false,
                    mimeType = "audio/synthetic",
                    folder = "ثقافة يابانية",
                    isBuiltIn = true
                ),
                Song(
                    id = "builtin_7",
                    title = "Black Suit (البدلة السوداء)",
                    artist = "Den PhiSpon",
                    album = "إلكترو هيب هوب",
                    duration = 220000,
                    path = "builtin://black_suit",
                    isFavorite = false,
                    mimeType = "audio/synthetic",
                    folder = "إيقاع حركي",
                    isBuiltIn = true
                )
            )
            songDao.insertSongs(builtInTracks)
        }
    }

    suspend fun scanDeviceFiles() = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE
        )

        try {
            val cursor = resolver.query(uri, projection, selection, null, null)
            val songsToInsert = mutableListOf<Song>()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "غير معروف"
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "فنان غير معروف"
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "ألبوم غير معروف"
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)) ?: "audio/mpeg"

                    // Extract folder name from path
                    val folderName = try {
                        val parts = path.split("/")
                        if (parts.size > 2) parts[parts.size - 2] else "مجلد الموسيقى"
                    } catch (e: Exception) {
                        "مجلد الموسيقى"
                    }

                    songsToInsert.add(
                        Song(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            path = path,
                            isFavorite = false,
                            mimeType = mimeType,
                            folder = folderName,
                            isBuiltIn = false
                        )
                    )
                }
                cursor.close()
            }

            if (songsToInsert.isNotEmpty()) {
                // Clear old scanned songs so we don't duplicate
                songDao.clearScannedSongs()
                songDao.insertSongs(songsToInsert)
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error scanning device music files: ${e.message}")
        }
    }

    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) {
        songDao.updateSong(song.copy(isFavorite = !song.isFavorite))
    }

    suspend fun setFavorite(songId: String, isFav: Boolean) = withContext(Dispatchers.IO) {
        songDao.setSongFavorite(songId, isFav)
    }

    fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query)
    }

    fun getSongsInPlaylist(playlistId: Int): Flow<List<Song>> = playlistDao.getSongsInPlaylist(playlistId)

    suspend fun createPlaylist(name: String): Int = withContext(Dispatchers.IO) {
        playlistDao.createPlaylist(Playlist(name = name)).toInt()
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
}
