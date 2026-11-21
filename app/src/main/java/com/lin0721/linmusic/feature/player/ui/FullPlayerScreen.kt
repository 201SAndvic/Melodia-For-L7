package com.lin0721.linmusic.feature.player.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentFloorScreen
import com.lin0721.linmusic.core.comment.ui.CommentFullScreen
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet
import com.lin0721.linmusic.core.player.rememberQueueItemCoverUrl
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.layout.LocalMelodiaWindow
import com.lin0721.linmusic.core.ui.theme.FallbackBackdropPalette
import com.lin0721.linmusic.core.ui.theme.PaletteMemoryCache
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// 双栏播放页：封面尺寸上限、左栏在封面之外额外的横向量（歌名/歌词左右留白）
private val PlayerTwoPaneCoverLimit = 420.dp
private val PlayerTwoPanePaneExtra = 112.dp
// 双栏播放页左栏封面之上的顶部标题栏行高度（图标 32 + 上下 16/44 留白），右栏首项按它对齐封面顶部
private val PlayerTwoPaneHeaderHeight = 92.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    isPlayerOpen: Boolean,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit = {},
    onDragClose: (Float, Float) -> Unit = { _, _ -> }
) {
    if (currentTrack == null) return

    val context = LocalContext.current
    val viewModel: PlayerViewModel = koinViewModel()
    val songDetailState by viewModel.songDetailState.collectAsStateWithLifecycle()
    val songDetail = songDetailState.songDetail
    // 大播放按钮专用：弱网缓冲期间也要立刻显示"暂停中"图标，不能等音频真正流出的 isPlaying；
    // 歌词区/顶栏/队列等其他地方仍按严格的 isPlaying 判断，不受影响
    val playWhenReady by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val playContext by viewModel.playerManager.playContext.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val activeQuality by viewModel.activeQuality.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val playMode by viewModel.playerManager.playMode.collectAsStateWithLifecycle()
    val queue by viewModel.playerManager.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by viewModel.playerManager.currentIndex.collectAsStateWithLifecycle()
    val currentQueueItem by viewModel.playerManager.currentQueueItem.collectAsStateWithLifecycle()
    val previousQueueItem by viewModel.playerManager.previousQueueItem.collectAsStateWithLifecycle()
    val nextQueueItem by viewModel.playerManager.nextQueueItem.collectAsStateWithLifecycle()
    var showQueueSheet by remember { mutableStateOf(false) }
    var isLyricsFullScreen by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    // 播放页信息卡片顺序/显隐编辑弹窗
    var showCardEditorSheet by remember { mutableStateOf(false) }
    val cardLayout by viewModel.fullPlayerCardLayout.collectAsStateWithLifecycle()
    val optimizations = com.lin0721.linmusic.core.ui.layout.LocalPerformanceOptimizations.current
    val suspendCoveredPlayer = optimizations.enabled(com.lin0721.linmusic.core.preferences.PerformanceOptimization.SUSPEND_COVERED_PLAYER)
    val effectiveCardLayout = remember(optimizations, cardLayout) { optimizations.filterCards(cardLayout) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCommentFloor by remember { mutableStateOf(false) }
    val composerState by viewModel.composerState.collectAsStateWithLifecycle()
    val floorState by viewModel.floorState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showOutputDeviceSheet by remember { mutableStateOf(false) }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }
    val connectedDevice = rememberCurrentOutputDevice()

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            ToastManager.showToast(message)
        }
    }

    BackHandler(enabled = showQueueSheet) {
        showQueueSheet = false
    }

    BackHandler(enabled = isLyricsFullScreen) {
        isLyricsFullScreen = false
    }

    BackHandler(enabled = showMoreOptionsSheet) {
        showMoreOptionsSheet = false
    }

    BackHandler(enabled = showTimerSheet) {
        showTimerSheet = false
    }

    BackHandler(enabled = showCommentsSheet) {
        showCommentsSheet = false
    }

    BackHandler(enabled = showCommentFloor) {
        showCommentFloor = false
    }

    BackHandler(enabled = showOutputDeviceSheet) {
        showOutputDeviceSheet = false
    }

    BackHandler(enabled = showDownloadQualitySheet) {
        showDownloadQualitySheet = false
    }

    BackHandler(enabled = showCardEditorSheet) {
        showCardEditorSheet = false
    }

    if (showCardEditorSheet) {
        FullPlayerCardEditorSheet(
            layout = cardLayout,
            onLayoutChange = viewModel::saveFullPlayerCardLayout,
            onDismiss = { showCardEditorSheet = false }
        )
    }

    // 全屏歌词逐字滚动需要更密的进度回调
    val fastPositionToken = remember { Any() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val currentView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(isLyricsFullScreen, isPlaying, optimizations, viewModel, lifecycle) {
        fun updateFastClock() {
            viewModel.playerManager.setFastPositionUpdates(fastPositionToken,
                isLyricsFullScreen && isPlaying && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> updateFastClock() }
        lifecycle.addObserver(observer)
        updateFastClock()
        com.lin0721.linmusic.core.log.PerformanceMonitor.mark("player_ui",
            "display=${currentView.display?.displayId} lyrics=$isLyricsFullScreen playing=$isPlaying optimizations=${optimizations.enabledOptions.joinToString { it.key }} intervalMs=${viewModel.playerManager.positionUpdateInterval.value}")
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.playerManager.setFastPositionUpdates(fastPositionToken, false)
        }
    }

    val title = currentTrack.mediaMetadata.title?.toString() ?: ""
    val artist = currentTrack.mediaMetadata.artist?.toString() ?: ""
    val rawCoverUrl = currentTrack.mediaMetadata.artworkUri?.toString()
        ?.replace("?param=300y300", "").orEmpty()
    val fallbackCoverUrl = currentQueueItem?.let {
        rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri).replace("?param=300y300", "")
    }.orEmpty()
    val coverUrl = rawCoverUrl.ifBlank { fallbackCoverUrl }
    val paletteCacheKey = remember(currentTrack.mediaId, coverUrl) {
        PaletteMemoryCache.key(currentTrack.mediaId, coverUrl)
    }
    var colorPalette by remember(paletteCacheKey) {
        mutableStateOf(PaletteMemoryCache.get(paletteCacheKey) ?: FallbackBackdropPalette)
    }
    val colors = rememberFullPlayerColors(colorPalette)
    // 预览封面解析
    val previousCoverUrl = previousQueueItem?.let {
        rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri).replace("?param=300y300", "")
    }
    val nextCoverUrl = nextQueueItem?.let {
        rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri).replace("?param=300y300", "")
    }

    // 歌名/歌手要跟封面一起冻结：队列已经先切过去、currentTrack 还没跟上时，
    // 直接用实时值会让封面下方的文字在滑动/切歌过程中先于封面硬跳
    val previousKeyStr = previousQueueItem?.songId?.toString()
    val nextKeyStr = nextQueueItem?.songId?.toString()
    var displayedTitle by remember { mutableStateOf(title) }
    var displayedArtist by remember { mutableStateOf(artist) }
    if ((previousKeyStr != null && previousKeyStr == currentTrack.mediaId) ||
        (nextKeyStr != null && nextKeyStr == currentTrack.mediaId)
    ) {
        // 队列已经先切过去，曲目数据还没跟上，先保留原文字，等 currentTrack 落地后再刷新
    } else {
        displayedTitle = title
        displayedArtist = artist
    }

    fun shareCurrentSong() {
        val shareText = "《$title》- $artist https://music.163.com/song?id=${currentTrack.mediaId}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "分享歌曲"))
    }

    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scrollMetrics = rememberFullPlayerScrollMetrics(listState)

    // 手势状态同时被内联逻辑和嵌套滚动连接读写，持有 MutableState 本体便于透传
    val offsetYState = remember { mutableStateOf(0f) }
    var offsetY by offsetYState
    val isScrollGestureActiveState = remember { mutableStateOf(false) }
    var isScrollGestureActive by isScrollGestureActiveState
    val isGestureStartedAtTopState = remember { mutableStateOf(true) }
    var isGestureStartedAtTop by isGestureStartedAtTopState

    LaunchedEffect(isPlayerOpen) {
        if (isPlayerOpen) {
            offsetY = 0f
            isScrollGestureActive = false
            isGestureStartedAtTop = true
            isLyricsFullScreen = false
        }
    }

    var screenHeightPx by remember { mutableStateOf(0f) }
    // A restored/open player has the same silhouette as a newly opened one.
    // Do not depend on a non-zero animation offset to retain the top corners.
    val topCornerRadius = 24.dp
    // 横屏宽窗口（车机副屏、平板横屏）走「封面在左、控件与详情在右」的双栏布局
    val window = LocalMelodiaWindow.current
    val useTwoPane = window.useTwoPanePlayer
    // 左栏封面按可用高度留出顶部标题栏/歌名/单行歌词的余量，避免竖屏那套「整屏大方块」搬过来
    val leftPaneCoverSize = remember(window) {
        if (window.useTwoPanePlayer) {
            // 副屏短高下给底部进度、上一首/下一首和快捷操作预留空间。
            (window.height - 365.dp).coerceIn(150.dp, 230.dp)
        } else {
            minOf(window.height * 0.46f, PlayerTwoPaneCoverLimit).coerceAtLeast(160.dp)
        }
    }
    val leftPaneWidth = remember(leftPaneCoverSize) {
        (leftPaneCoverSize + PlayerTwoPanePaneExtra).coerceIn(320.dp, 640.dp)
    }
    val coroutineScope = rememberCoroutineScope()
    var dragReleaseJob by remember { mutableStateOf<Job?>(null) }

    // 松手后决定关闭播放器还是回弹，从列表中途开始的手势要求更严
    fun handleDragRelease(velocity: Float = 0f) {
        dragReleaseJob?.cancel()
        dragReleaseJob = coroutineScope.launch {
            val shouldClose = if (isGestureStartedAtTop) {
                offsetY > screenHeightPx * 0.10f || velocity > 450f
            } else {
                offsetY > screenHeightPx * 0.20f
            }

            if (offsetY > 0f && shouldClose) {
                val finalOffset = offsetY
                offsetY = 0f
                onDragClose(finalOffset, velocity)
            } else {
                animate(
                    initialValue = offsetY,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ ->
                    offsetY = value.coerceAtLeast(0f)
                }
            }
        }
    }

    val nestedScrollConnection = rememberFullPlayerNestedScrollConnection(
        listState = listState,
        offsetYState = offsetYState,
        isScrollGestureActiveState = isScrollGestureActiveState,
        isGestureStartedAtTopState = isGestureStartedAtTopState,
        onDragRelease = { handleDragRelease(velocity = it) }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenHeightPx = it.height.toFloat() }
            .nestedScroll(nestedScrollConnection)
            .graphicsLayer {
                translationY = offsetY
            }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            .background(Color.Black)
    ) {
        // While low-resource lyrics fully cover this screen, no hidden cover/card animations.
        // Navigation state and palette remain owned above this branch for a clean return.
        if (!suspendCoveredPlayer || !isLyricsFullScreen) {
        PlayerBackdrop(
            base = colors.base,
            mode = BackdropMode.Collapsed,
            // 双栏下封面始终停在左栏顶端，背景不用跟着右侧列表做视差位移
            translationYProvider = { if (useTwoPane) 0f else scrollMetrics.backgroundTranslationY }
        )

        val onPaletteExtracted: (PlayerBackdropPalette) -> Unit = {
            colorPalette = it
            PaletteMemoryCache.put(paletteCacheKey, it)
        }
        val onArtistInfoClick: () -> Unit = {
            songDetail?.ar?.firstOrNull()?.id?.let { id ->
                onArtistClick(id)
            }
        }

        if (!useTwoPane) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().haze(hazeState),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            ) {
                fullPlayerPlaybackSection(
                    songState = songDetailState,
                    colors = colors,
                    coverUrl = coverUrl,
                    previousCoverUrl = previousCoverUrl,
                    nextCoverUrl = nextCoverUrl,
                    onSwipeToPrevious = viewModel.playerManager::skipToPrevious,
                    onCancelSwipe = viewModel.playerManager::cancelPendingSkip,
                    currentKey = currentTrack.mediaId,
                    previousKey = previousQueueItem?.songId?.toString(),
                    nextKey = nextQueueItem?.songId?.toString(),
                    title = displayedTitle,
                    artist = displayedArtist,
                    playContext = playContext,
                    currentLyricIndex = currentLyricIndex,
                    isPlaying = isPlaying,
                    playWhenReady = playWhenReady,
                    currentPositionProvider = currentPositionProvider,
                    duration = duration,
                    playMode = playMode,
                    onClose = onClose,
                    onPaletteExtracted = onPaletteExtracted,
                    onMoreClick = { showMoreOptionsSheet = true },
                    onToggleLike = viewModel::toggleLike,
                    onArtistClick = onArtistInfoClick,
                    onSeek = onSeek,
                    onTogglePlay = onTogglePlay,
                    onPlayNext = viewModel.playerManager::playNext,
                    onPlayPrevious = viewModel.playerManager::playPrevious,
                    onToggleShuffle = viewModel.playerManager::toggleShuffle,
                    onToggleRepeat = viewModel.playerManager::toggleRepeat,
                    onDisableRoaming = { viewModel.playerManager.disableRoaming() },
                    onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
                    onOutputDeviceClick = { showOutputDeviceSheet = true },
                    onQueueClick = { showQueueSheet = true },
                    onShareClick = { shareCurrentSong() },
                    connectedDevice = connectedDevice,
                    paletteCacheKey = paletteCacheKey
                )

                fullPlayerInfoSection(
                    songState = songDetailState,
                    colors = colors,
                    commentsState = commentsState,
                    currentLyricIndex = currentLyricIndex,
                    cardLayout = effectiveCardLayout,
                    onOpenFullScreenLyrics = { isLyricsFullScreen = true },
                    onCommentsClick = { showCommentsSheet = true },
                    onRetryComments = viewModel::retryComments,
                    onFollowArtistClick = { artistId -> viewModel.toggleArtistFollow(artistId) },
                    onArtistClick = onArtistClick,
                    onEditCardsClick = { showCardEditorSheet = true },
                    onAlbumClick = onAlbumClick,
                    onSelectArtist = { index -> viewModel.selectArtist(index) }
                )
            }
        } else {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(hazeState)
            ) {
                // 左栏：封面、歌曲信息和固定在底部的一体化播放控件，不再通过下滑才能看到控件。
                Column(
                    modifier = Modifier
                        .width(leftPaneWidth)
                        .fillMaxHeight()
                        // 给副屏底部手势/导航区域留出呼吸空间，避免控件贴在最下沿。
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                if (useTwoPane) 28.dp else 0.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(statusBarTop))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FullPlayerCoverArt(
                            coverUrl = coverUrl,
                            title = displayedTitle,
                            playContext = playContext,
                            onClose = onClose,
                            onPaletteExtracted = onPaletteExtracted,
                            onMoreClick = { showMoreOptionsSheet = true },
                            previousCoverUrl = previousCoverUrl,
                            nextCoverUrl = nextCoverUrl,
                            onSwipeToPrevious = viewModel.playerManager::skipToPrevious,
                            onSwipeToNext = viewModel.playerManager::playNext,
                            currentKey = currentTrack.mediaId,
                            previousKey = previousQueueItem?.songId?.toString(),
                            nextKey = nextQueueItem?.songId?.toString(),
                            onCancelSwipe = viewModel.playerManager::cancelPendingSkip,
                            coverSize = leftPaneCoverSize,
                            paletteCacheKey = paletteCacheKey
                        )
                        SongInfo(
                            title = displayedTitle,
                            artist = displayedArtist,
                            isLiked = songDetailState.isLiked,
                            onToggleLike = viewModel::toggleLike,
                            onArtistClick = onArtistInfoClick
                        )
                        MiniLyricLine(
                            lyrics = songDetailState.lyrics,
                            currentLyricIndex = currentLyricIndex,
                            isPlaying = isPlaying
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f).heightIn(min = 0.dp))
                    FullPlayerControlContent(
                        songState = songDetailState,
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        onSeek = onSeek,
                        playWhenReady = playWhenReady,
                        onTogglePlay = onTogglePlay,
                        onPlayNext = viewModel.playerManager::playNext,
                        onPlayPrevious = viewModel.playerManager::playPrevious,
                        onToggleShuffle = viewModel.playerManager::toggleShuffle,
                        onToggleRepeat = viewModel.playerManager::toggleRepeat,
                        playMode = playMode,
                        playContext = playContext,
                        onDisableRoaming = { viewModel.playerManager.disableRoaming() },
                        onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
                        onOutputDeviceClick = { showOutputDeviceSheet = true },
                        onQueueClick = { showQueueSheet = true },
                        onShareClick = { shareCurrentSong() },
                        connectedDevice = connectedDevice,
                        compact = true
                    )
                }

                // 右栏只展示信息卡片，播放控件与封面同列放在左栏。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopStart
                ) {
                    LazyColumn(
                        state = listState,
                        // 从分栏边界开始；左侧控件行自带 8dp 右内距，正好匹配它距屏幕左侧的 8dp。
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            // 与左栏封面顶部对齐：顶部标题栏行（图标 + 上下留白）的高度
                            top = statusBarTop + PlayerTwoPaneHeaderHeight,
                            bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    ) {
                        fullPlayerInfoSection(
                            songState = songDetailState,
                            colors = colors,
                            commentsState = commentsState,
                            currentLyricIndex = currentLyricIndex,
                            cardLayout = effectiveCardLayout,
                            onOpenFullScreenLyrics = { isLyricsFullScreen = true },
                            onCommentsClick = { showCommentsSheet = true },
                            onRetryComments = viewModel::retryComments,
                            onFollowArtistClick = { artistId -> viewModel.toggleArtistFollow(artistId) },
                            onArtistClick = onArtistClick,
                            onEditCardsClick = { showCardEditorSheet = true },
                            onAlbumClick = onAlbumClick,
                            onSelectArtist = { index -> viewModel.selectArtist(index) }
                        )
                    }
                }
            }
        }

        FullPlayerTopBar(
            onClose = onClose,
            title = title,
            artist = artist,
            showTitle = scrollMetrics.showTitleInBar,
            isPlaying = isPlaying,
            onTogglePlay = onTogglePlay,
            isLiked = songDetailState.isLiked,
            onToggleLike = viewModel::toggleLike,
            backgroundColor = colors.base,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            onArtistClick = {
                songDetail?.ar?.firstOrNull()?.id?.let { id ->
                    onArtistClick(id)
                }
            },
            modifier = Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                },
                onDragStarted = {
                    isGestureStartedAtTop = true
                },
                onDragStopped = { velocity ->
                    handleDragRelease(velocity = velocity)
                }
            )
        )

        }
        FullPlayerLyricsOverlay(
            visible = isLyricsFullScreen,
            songState = songDetailState,
            colors = colors,
            currentLyricIndex = currentLyricIndex,
            title = title,
            artist = artist,
            hazeState = hazeState,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            playMode = playMode,
            onSeek = { timeMs ->
                viewModel.seekToTime(timeMs)
            },
            onClose = { isLyricsFullScreen = false },
            onTogglePlay = onTogglePlay,
            onPlayNext = viewModel.playerManager::playNext,
            onPlayPrevious = viewModel.playerManager::playPrevious,
            onToggleShuffle = viewModel.playerManager::toggleShuffle,
            onToggleRepeat = viewModel.playerManager::toggleRepeat,
            onMoreClick = { showMoreOptionsSheet = true }
        )

        FullPlayerSheets(
            songState = songDetailState,
            showQueueSheet = showQueueSheet,
            showMoreOptionsSheet = showMoreOptionsSheet,
            collectSongId = collectSongId,
            collectState = collectState,
            showTimerSheet = showTimerSheet,
            showOutputDeviceSheet = showOutputDeviceSheet,
            queue = queue,
            currentQueueIndex = currentQueueIndex,
            playMode = playMode,
            playContext = playContext,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            sleepTimerRemaining = sleepTimerRemaining,
            activeQuality = activeQuality,
            onPlayAtIndex = { viewModel.playerManager.playAtIndex(it) },
            onRemoveAtIndex = { viewModel.playerManager.removeFromQueue(it) },
            onMoveQueueItem = { from, to -> viewModel.playerManager.moveInQueue(from, to) },
            onToggleShuffle = viewModel.playerManager::toggleShuffle,
            onClearQueue = {
                viewModel.clearQueue()
                showQueueSheet = false
            },
            onDisableRoaming = { viewModel.playerManager.disableRoaming() },
            onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
            onQueueDismiss = { showQueueSheet = false },
            onToggleLike = viewModel::toggleLike,
            onAlbumClick = {
                val albumId = songDetail?.al?.id ?: 0L
                if (albumId > 0L) {
                    showMoreOptionsSheet = false
                    onAlbumClick(albumId)
                } else {
                    ToastManager.showToast("未找到专辑信息")
                }
            },
            onArtistClick = {
                val artistId = songDetail?.ar?.firstOrNull()?.id ?: 0L
                if (artistId > 0L) {
                    showMoreOptionsSheet = false
                    onArtistClick(artistId)
                } else {
                    ToastManager.showToast("未找到歌手信息")
                }
            },
            onShowTimerClick = {
                showTimerSheet = true
            },
            onQualitySelected = viewModel::updateQuality,
            onToggleIntelligence = { checked ->
                if (checked) {
                    val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                    viewModel.startIntelligenceMode(songId, title, artist, coverUrl)
                } else {
                    viewModel.playerManager.disableIntelligence()
                }
            },
            onStartSimilarRoaming = {
                val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                viewModel.startSimilarSongsRoaming(songId, title, artist, coverUrl)
            },
            onInsertSimilarSongs = {
                val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                viewModel.insertSimilarSongs(songId)
            },
            onCollectClick = {
                val songId = currentTrack.mediaId.toLongOrNull()
                if (songId != null) {
                    collectSongId = songId
                    viewModel.prepareCollectDialog(songId)
                }
            },
            onShareClick = { shareCurrentSong() },
            onDownloadClick = { showDownloadQualitySheet = true },
            onSaveCollection = { songId, items -> viewModel.savePlaylistCollection(songId, items) },
            onSaveNewCollection = { name, songId -> viewModel.createPlaylistAndAddSong(name, songId) },
            onCollectDismiss = { collectSongId = null },
            onMoreOptionsDismiss = { showMoreOptionsSheet = false },
            onSetTimer = { minutes ->
                viewModel.setSleepTimer(minutes)
                showTimerSheet = false
            },
            onTimerDismiss = { showTimerSheet = false },
            onOutputDeviceSelected = { deviceId -> viewModel.playerManager.setPreferredAudioDevice(deviceId) },
            onOutputDeviceDismiss = { showOutputDeviceSheet = false }
        )

        AnimatedVisibility(
            visible = showCommentsSheet,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            CommentFullScreen(
                commentsState = commentsState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentsSheet = false },
                onSortChange = viewModel::changeCommentSort,
                onLikeComment = viewModel::likeComment,
                onUserClick = { uid ->
                    showCommentsSheet = false
                    onNavigateToProfile(uid)
                },
                onExpandFloor = { comment ->
                    showCommentFloor = true
                    viewModel.openCommentFloor(comment)
                },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onSubmitComment = { content, target ->
                    if (target != null) {
                        viewModel.submitCommentReply(target.commentId, content)
                    } else {
                        viewModel.submitComment(content)
                    }
                },
                onRequireLogin = { ToastManager.showToast("请先登录账号") },
                onLoadMore = viewModel::loadMoreComments,
                onRetry = { viewModel.retryComments() }
            )
        }

        AnimatedVisibility(
            visible = showCommentFloor,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            CommentFloorScreen(
                floorState = floorState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentFloor = false },
                onLoadMore = viewModel::loadMoreCommentFloor,
                onSubmitReply = { parentCommentId, content ->
                    viewModel.submitCommentReply(parentCommentId, content)
                },
                onRequireLogin = { ToastManager.showToast("请先登录账号") },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onLikeClick = { comment -> viewModel.likeComment(comment) },
                onRetry = { (floorState as? CommentFloorState.Success)?.ownerComment?.let(viewModel::openCommentFloor) },
                onUserClick = onNavigateToProfile
            )
        }

        if (showDownloadQualitySheet) {
            DownloadQualityPickerSheet(
                songId = currentTrack.mediaId.toLongOrNull(),
                maxDownloadLevel = songDetail?.privilege?.dlLevel,
                onQualitySelected = { level ->
                    val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                    val artistNames = songDetail?.ar?.joinToString("/") { it.name }?.takeIf { it.isNotBlank() } ?: artist
                    val albumName = songDetail?.al?.name ?: ""
                    val albumYear = com.lin0721.linmusic.core.download.yearFromEpochMillis(songDetail?.publishTime ?: 0)
                    viewModel.downloadCurrentSong(songId, title, artistNames, albumName, coverUrl, albumYear, level)
                    showDownloadQualitySheet = false
                },
                onDismiss = { showDownloadQualitySheet = false }
            )
        }
    }
}
