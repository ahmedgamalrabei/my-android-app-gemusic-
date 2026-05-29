package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Song
import com.example.ui.viewmodel.RepeatMode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PlayerDetailView(
    visible: Boolean,
    song: Song?,
    isPlaying: Boolean,
    progressMs: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    visualizerValue: Float, // from 0f to 1f
    sleepTimerMin: Int?,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenPlaylistAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val infiniteTransition = rememberInfiniteTransition(label = "player_details_rot")
    val diskAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label = "angle"
    )
    val animatedAngle = if (isPlaying) diskAngle else 0f

    // Soft bouncing scale effect on CD based on the real-time visualizer amplitude value
    val modelScale by animateFloatAsState(
        targetValue = 1f + (visualizerValue * 0.12f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "scale"
    )

    // Format minutes/seconds
    fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(400, easing = FastOutLinearInEasing)
        ) + fadeOut()
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary

        // Immersive glowing neon gradient background based on the current active song & theme
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Ambient neon halo circles in background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 3.2f)
                val baseRadius = size.width / 2.5f + (visualizerValue * 45f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.18f + (visualizerValue * 0.12f)),
                            secondaryColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.5f
                    ),
                    center = center,
                    radius = baseRadius * 1.5f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag("player_collapse_button")
                            .shadow(2.dp, CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "إغلاق",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "الأغنية المشغلة الآن",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = song.folder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    IconButton(
                        onClick = onOpenPlaylistAdd,
                        modifier = Modifier
                            .shadow(2.dp, CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "إضافة لقائمة التشغيل",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.4f))

                // Giant Glowing Disk Section with pulsating circular halo
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer(
                            scaleX = modelScale,
                            scaleY = modelScale
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsating light ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeRadius = 135.dp.toPx()
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.2f + (visualizerValue * 0.3f)),
                            radius = strokeRadius + (visualizerValue * 22f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                        // Decorative rotating audio waves inside the halo
                        val pointsCount = 30
                        val center = Offset(size.width / 2, size.height / 2)
                        for (i in 0 until pointsCount) {
                            val angle = (i * 2 * Math.PI / pointsCount) + (animatedAngle * Math.PI / 180f)
                            val innerDist = strokeRadius - 4.dp.toPx()
                            val barHeight = 8.dp.toPx() + (visualizerValue * 28.dp.toPx() * (i % 3 + 1) / 3f)
                            val startX = center.x + innerDist * cos(angle).toFloat()
                            val startY = center.y + innerDist * sin(angle).toFloat()
                            val endX = center.x + (innerDist + barHeight) * cos(angle).toFloat()
                            val endY = center.y + (innerDist + barHeight) * sin(angle).toFloat()
                            drawLine(
                                color = if (i % 2 == 0) primaryColor else secondaryColor,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }

                    // Vinyl vinyl container
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .shadow(16.dp, CircleShape)
                            .clip(CircleShape)
                            .rotate(animatedAngle)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF2E2042),
                                        Color(0xFF0C0714)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glossy sound lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = 95.dp.toPx(),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = 75.dp.toPx(),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = 55.dp.toPx(),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                        }

                        // Central core image placeholder (colorful abstract graphics)
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            primaryColor,
                                            secondaryColor,
                                            tertiaryColor
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Centered musical icon
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "ألبوم",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(54.dp)
                                    .shadow(4.dp, CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.4f))

                // Track and Artist Metadata Labels
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite toggle Button
                        IconButton(
                            onClick = { onToggleFavorite(song) },
                            modifier = Modifier
                                .testTag("favorite_button")
                                .shadow(2.dp, CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "تفضيل",
                                tint = if (song.isFavorite) Color(0xFFFF2A55) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Song titles
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Sleep timer view / indicator
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = onOpenTimer,
                                modifier = Modifier
                                    .shadow(2.dp, CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "مؤقت النوم",
                                    tint = if (sleepTimerMin != null) primaryColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (sleepTimerMin != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .background(primaryColor, CircleShape)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${sleepTimerMin}د",
                                        color = MaterialTheme.colorScheme.background,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.3f))

                // Progress state seekbar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressMs.toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..song.duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = primaryColor.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_progress_slider")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(progressMs),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = formatTime(song.duration),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.4f))

                // Primary Playback controllers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Loop Mode button
                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier.size(46.dp)
                    ) {
                        val icon = when (repeatMode) {
                            RepeatMode.NONE -> Icons.Default.TrendingFlat
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                        }
                        val tint = if (repeatMode != RepeatMode.NONE) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        Icon(icon, contentDescription = "تكرار", tint = tint)
                    }

                    // Prev track button
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(2.dp, CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "السابق",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Large central playing trigger button
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .testTag("player_main_play_pause")
                            .size(80.dp)
                            .shadow(8.dp, CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White,
                                        primaryColor
                                    )
                                ),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "تشغيل/إيقاف مؤقت",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Next track button
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(2.dp, CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "التالي",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Shuffle toggle Button
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(46.dp)
                    ) {
                        val tint = if (isShuffle) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "خلط",
                            tint = tint
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.4f))

                // Bottom shortcut action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenEqualizer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            contentColor = primaryColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("موازن الصوت HD", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onOpenTimer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            contentColor = secondaryColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مؤقت النوم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
