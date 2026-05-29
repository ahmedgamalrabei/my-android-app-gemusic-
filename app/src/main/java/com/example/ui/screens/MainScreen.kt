package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.Playlist
import com.example.data.Song
import com.example.ui.components.*
import com.example.ui.theme.GMusicTheme
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.example.ui.viewmodel.MusicTheme
import com.example.ui.viewmodel.MusicViewModel
import com.example.AdManager

enum class CategoryTab(val titleAr: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SONGS("جميع الأغاني", Icons.Default.MusicNote),
    ARTISTS("الفنانين", Icons.Default.Person),
    ALBUMS("الألبومات", Icons.Default.Album),
    FOLDERS("المجلدات", Icons.Default.Folder),
    PLAYLISTS("قوائم التشغيل", Icons.AutoMirrored.Default.PlaylistPlay),
    FAVORITES("المفضلات", Icons.Default.Favorite)
}

enum class GroupType {
    NONE, ARTIST, ALBUM, FOLDER, PLAYLIST
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    
    // Observed ViewModel States
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val isShuffle by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val reverbPreset by viewModel.reverbPreset.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val currentTheme by viewModel.selectedTheme.collectAsState()
    val visualizerValue by viewModel.visualizerData.collectAsState()

    // Navigation and Filtering Scope
    var activeTab by remember { mutableStateOf(CategoryTab.SONGS) }
    var selectedGroupType by remember { mutableStateOf(GroupType.NONE) }
    var selectedGroupName by remember { mutableStateOf("") }
    var selectedGroupPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // Dialog & UI Sheets visibility flags
    var isPlayerDetailVisible by remember { mutableStateOf(false) }
    var isEqVisible by remember { mutableStateOf(false) }
    var isTimerVisible by remember { mutableStateOf(false) }
    var isThemeVisible by remember { mutableStateOf(false) }
    var isPlaylistAddVisible by remember { mutableStateOf(false) }
    var activeSongForPlaylistAdd by remember { mutableStateOf<Song?>(null) }

    // Interstitial Ad setup on song click count
    val activity = context as? android.app.Activity
    var songClickCount by remember { mutableStateOf(0) }

    val onSongPlayWithAd: (Song, List<Song>) -> Unit = { song, queue ->
        songClickCount++
        if (songClickCount % 3 == 0) {
            activity?.let { act ->
                AdManager.showInterstitial(act) {
                    viewModel.selectAndPlay(song, queue)
                }
            } ?: viewModel.selectAndPlay(song, queue)
        } else {
            viewModel.selectAndPlay(song, queue)
        }
    }

    // Permissions check
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var isPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionGranted = granted
        if (granted) {
            viewModel.scanLocalFiles()
        } else {
            Toast.makeText(context, "الرجاء منح إذن القراءة لعرض ملفات الموسيقى من الذاكرة", Toast.LENGTH_LONG).show()
        }
    }

    // Back handler helper to clear drilldown levels
    val canGoBack = selectedGroupType != GroupType.NONE
    val onBackPressed = {
        selectedGroupType = GroupType.NONE
        selectedGroupName = ""
        selectedGroupPlaylist = null
    }

    GMusicTheme(musicTheme = currentTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "جي ميوزك",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                        )
                    },
                    actions = {
                        // Launch Theme customization
                        IconButton(
                            onClick = { isThemeVisible = true },
                            modifier = Modifier.testTag("action_themes")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "تخصيص الثيم",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Rescan files
                        IconButton(
                            onClick = {
                                if (isPermissionGranted) {
                                    viewModel.scanLocalFiles()
                                } else {
                                    launcher.launch(readPermission)
                                }
                            },
                            modifier = Modifier.testTag("action_scan")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث الملفات",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search layout and Permission alert (if not granted)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        placeholder = { Text("ابحث عن أغنية، فنان، ألبوم...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("search_field"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )

                    if (!isPermissionGranted) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "المزامنة مع ذاكرة الهاتف",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "اعرض ملفات الـ MP3 والصوتيات الخاصة بك مباشرة داخل التطبيق",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Button(
                                    onClick = { launcher.launch(readPermission) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("سماح", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Bottom list rendering
                    if (selectedGroupType == GroupType.NONE) {
                        // Standard Category selection tabs row
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(CategoryTab.values()) { tab ->
                                val isSelected = activeTab == tab
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { activeTab = tab },
                                    label = { Text(tab.titleAr, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.background,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.background
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("tab_chip_${tab.name}")
                                )
                            }
                        }

                        // Content according to selected tab
                        Box(modifier = Modifier.weight(1f)) {
                            when (activeTab) {
                                CategoryTab.SONGS -> SongsListView(
                                    songs = filteredSongs,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    isShuffle = isShuffle,
                                    onSongSelect = { song -> onSongPlayWithAd(song, filteredSongs) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddPlaylist = {
                                        activeSongForPlaylistAdd = it
                                        isPlaylistAddVisible = true
                                    },
                                    onShuffleAll = {
                                        if (filteredSongs.isNotEmpty()) {
                                            if (!isShuffle) viewModel.toggleShuffle()
                                            onSongPlayWithAd(filteredSongs.random(), filteredSongs)
                                        }
                                    }
                                )
                                CategoryTab.ARTISTS -> GroupedGridView(
                                    itemsMap = filteredSongs.groupBy { it.artist },
                                    icon = Icons.Default.Person,
                                    onClick = { artistName ->
                                        selectedGroupType = GroupType.ARTIST
                                        selectedGroupName = artistName
                                    }
                                )
                                CategoryTab.ALBUMS -> GroupedGridView(
                                    itemsMap = filteredSongs.groupBy { it.album },
                                    icon = Icons.Default.Album,
                                    onClick = { albumName ->
                                        selectedGroupType = GroupType.ALBUM
                                        selectedGroupName = albumName
                                    }
                                )
                                CategoryTab.FOLDERS -> GroupedGridView(
                                    itemsMap = filteredSongs.groupBy { it.folder },
                                    icon = Icons.Default.Folder,
                                    onClick = { folderName ->
                                        selectedGroupType = GroupType.FOLDER
                                        selectedGroupName = folderName
                                    }
                                )
                                CategoryTab.PLAYLISTS -> PlaylistsGridView(
                                    playlists = playlists,
                                    onCreatePlaylist = { viewModel.createAndAddPlaylist(it) },
                                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                    onClick = { p ->
                                        selectedGroupType = GroupType.PLAYLIST
                                        selectedGroupPlaylist = p
                                        viewModel.addSongToPlaylist(p.id, "") // dummy touch to refresh collection
                                    }
                                )
                                CategoryTab.FAVORITES -> SongsListView(
                                    songs = favoriteSongs,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    isShuffle = isShuffle,
                                    onSongSelect = { song -> onSongPlayWithAd(song, favoriteSongs) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddPlaylist = {
                                        activeSongForPlaylistAdd = it
                                        isPlaylistAddVisible = true
                                    },
                                    onShuffleAll = {
                                        if (favoriteSongs.isNotEmpty()) {
                                            if (!isShuffle) viewModel.toggleShuffle()
                                            onSongPlayWithAd(favoriteSongs.random(), favoriteSongs)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        // DRILL DOWN SUB-VIEW list (when folder/artist/album/playlist is clicked)
                        val groupSongsList = when (selectedGroupType) {
                            GroupType.ARTIST -> allSongs.filter { it.artist == selectedGroupName }
                            GroupType.ALBUM -> allSongs.filter { it.album == selectedGroupName }
                            GroupType.FOLDER -> allSongs.filter { it.folder == selectedGroupName }
                            GroupType.PLAYLIST -> playlistSongs.filter { it.title.isNotBlank() } // strip dummy
                            else -> emptyList()
                        }

                        val titleLabel = when (selectedGroupType) {
                            GroupType.ARTIST -> "الفنان: $selectedGroupName"
                            GroupType.ALBUM -> "ألبوم: $selectedGroupName"
                            GroupType.FOLDER -> "مجلد: $selectedGroupName"
                            GroupType.PLAYLIST -> "قائمة: ${selectedGroupPlaylist?.name ?: ""}"
                            else -> ""
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBackPressed,
                                    modifier = Modifier.testTag("group_back_button")
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "عودة")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = titleLabel,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                SongsListView(
                                    songs = groupSongsList,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    isShuffle = isShuffle,
                                    onSongSelect = { song -> onSongPlayWithAd(song, groupSongsList) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddPlaylist = {
                                        activeSongForPlaylistAdd = it
                                        isPlaylistAddVisible = true
                                    },
                                    onShuffleAll = {
                                        if (groupSongsList.isNotEmpty()) {
                                            if (!isShuffle) viewModel.toggleShuffle()
                                            onSongPlayWithAd(groupSongsList.random(), groupSongsList)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    AdBanner()

                    // Spacer height for anchored footer player overlay and AdBanner separation
                    Spacer(modifier = Modifier.height(currentSong?.let { 74.dp } ?: 16.dp))
                }

                // Anchored Bottom Floating Mini controller
                currentSong?.let { song ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        MiniPlayer(
                            currentSong = song,
                            isPlaying = isPlaying,
                            progressMs = currentPositionMs,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onNext = { viewModel.nextTrack() },
                            onClick = { isPlayerDetailVisible = true }
                        )
                    }
                }
            }
        }

        // --- FULL DETAIL OVERLAYS & SHEETS COORDINATION ---

        PlayerDetailView(
            visible = isPlayerDetailVisible,
            song = currentSong,
            isPlaying = isPlaying,
            progressMs = currentPositionMs,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            visualizerValue = visualizerValue,
            sleepTimerMin = sleepTimerRemaining,
            onClose = { isPlayerDetailVisible = false },
            onTogglePlay = { viewModel.togglePlayPause() },
            onNext = { viewModel.nextTrack() },
            onPrev = { viewModel.previousTrack() },
            onSeek = { viewModel.seekTo(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeat = { viewModel.cycleRepeatMode() },
            onOpenEqualizer = { isEqVisible = true },
            onOpenTimer = { isTimerVisible = true },
            onOpenPlaylistAdd = {
                activeSongForPlaylistAdd = currentSong
                isPlaylistAddVisible = true
            }
        )

        EqualizerSheet(
            visible = isEqVisible,
            bands = equalizerBands,
            activePreset = reverbPreset,
            onBandChange = { idx, db -> viewModel.updateEqualizerBand(idx, db) },
            onPresetChange = { viewModel.updateReverbPreset(it) },
            onDismiss = { isEqVisible = false }
        )

        SleepTimerSheet(
            visible = isTimerVisible,
            remainingMin = sleepTimerRemaining,
            onTimerSelect = { viewModel.setSleepTimer(it) },
            onDismiss = { isTimerVisible = false }
        )

        ThemePickerSheet(
            visible = isThemeVisible,
            currentTheme = currentTheme,
            onThemeSelect = { viewModel.selectTheme(it) },
            onDismiss = { isThemeVisible = false }
        )

        PlaylistManagerSheet(
            visible = isPlaylistAddVisible,
            song = activeSongForPlaylistAdd,
            playlists = playlists,
            onCreatePlaylist = { viewModel.createAndAddPlaylist(it, activeSongForPlaylistAdd) },
            onAddSongToPlaylist = { pid, sid ->
                viewModel.addSongToPlaylist(pid, sid)
                isPlaylistAddVisible = false
                Toast.makeText(context, "تمت الإضافة للمجموعة بنجاح!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                activeSongForPlaylistAdd = null
                isPlaylistAddVisible = false
            }
        )
    }
}

// --- SUB SCREEN COMPONENTS RENDERERS ---

@Composable
fun SongsListView(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffle: Boolean,
    onSongSelect: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddPlaylist: (Song) -> Unit,
    onShuffleAll: () -> Unit
) {
    val context = LocalContext.current

    if (songs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MusicOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "لا توجد ملفات موسيقية متوفرة",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "قم بنقل ملفات صوتية للهاتف أو مزامنة الذاكرة من الأعلى لعرضها هنا",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // "خلط الكل" / Shuffle play banner at top
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onShuffleAll)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(vertical = 14.dp, horizontal = 20.dp)
                        .testTag("shuffle_all_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "خلط الكل وتكرار المسارات",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.background
                        )
                        Text(
                            text = "تشغيل كافة العناصر (${songs.size} ملف صوتي) بترتيب عشوائي ذكي",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            items(songs) { song ->
                val isActive = currentSong?.id == song.id
                val activeBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) activeBgColor else Color.Transparent)
                        .clickable { onSongSelect(song) }
                        .padding(10.dp)
                        .testTag("song_item_${song.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small thumbnail cover
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActive && isPlaying) Icons.Default.QueueMusic else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${song.artist} • ${song.album}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Actions block: Share & More
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleFavorite(song) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "تفضيل الأغنية",
                                tint = if (song.isFavorite) Color(0xFFFF2A55) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Share song logic via native sharing sheet
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "مشاركة الموسيقى ✨")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "اسمع الحين الأغنية الرائعة \"${song.title}\" للفنان المبدع \"${song.artist}\" عبر مشغل جي ميوزك HD المذهل!"
                                    )
                                }
                                context.startActivity(Intent.createChooser(intent, "شارك المسار الغنائي عبر:"))
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة المسار",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onAddPlaylist(song) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "إضافة لقائمة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupedGridView(
    itemsMap: Map<String, List<Song>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (String) -> Unit
) {
    if (itemsMap.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("قائمتك فارغة تماماً")
        }
    } else {
        LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(itemsMap.keys.toList()) { key ->
                val songs = itemsMap[key] ?: emptyList()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clickable { onClick(key) }
                        .testTag("group_card_$key"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = key,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${songs.size} ملف صوتي",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsGridView(
    playlists: List<Playlist>,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onClick: (Playlist) -> Unit
) {
    var isFormVisible by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick trigger button
        if (!isFormVisible) {
            Button(
                onClick = { isFormVisible = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(44.dp)
                    .testTag("playlist_create_standalone")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة قائمة مخصصة جديدة", fontWeight = FontWeight.Bold)
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("إنشاء قائمة جديدة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        placeholder = { Text("مثلاً: روقان المساء") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_playlist_input"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isFormVisible = false }) {
                            Text("رجوع")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (playlistNameInput.isNotBlank()) {
                                    onCreatePlaylist(playlistNameInput)
                                    playlistNameInput = ""
                                    isFormVisible = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("إنشاء")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "لم تقم بإنشاء أي قوائم تشغيل حتى الآن.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlists) { p ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .combinedClickable(
                                onClick = { onClick(p) },
                                onLongClick = { onDeletePlaylist(p) }
                            )
                            .testTag("playlist_card_${p.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeletePlaylist(p) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف القائمة",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = p.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "اضغط للاستعراض",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
