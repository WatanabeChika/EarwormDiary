package com.example.earwormdiary

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.earwormdiary.data.local.CategoryStorage
import com.example.earwormdiary.data.local.RecordStorage
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.ui.screens.CalendarScreen
import com.example.earwormdiary.ui.screens.CategoryManagementScreen
import com.example.earwormdiary.ui.screens.DataBackupScreen
import com.example.earwormdiary.ui.screens.LibrarySettingsScreen
import com.example.earwormdiary.ui.screens.SettingsMenuScreen
import com.example.earwormdiary.ui.screens.SongSelectionView
import com.example.earwormdiary.ui.screens.TodayScreen
import java.time.LocalDate
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import coil.imageLoader
import coil.request.ImageRequest
import com.example.earwormdiary.ui.components.bitmapCache
import com.example.earwormdiary.ui.components.loadLocalAudioCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppEntry()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEntry() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()

    val initialRecords = remember { RecordStorage.loadRecords(context) }
    val records = remember { mutableStateMapOf<LocalDate, DailyRecord>().apply { putAll(initialRecords) } }

    val imageLoader = context.imageLoader
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val currentMonth = YearMonth.now()

                val thisMonthRecords = records.filterKeys { date ->
                    YearMonth.from(date) == currentMonth
                }

                thisMonthRecords.values.forEach { record ->
                    val song = record.song
                    if (song.isNone || song.isText) return@forEach

                    val isNetwork = song.albumArtUri.toString().startsWith("http")

                    if (isNetwork) {
                        val request = ImageRequest.Builder(context)
                            .data(song.albumArtUri.toString())
                            .build()
                        imageLoader.enqueue(request)
                    } else {
                        val cacheKey = song.uri.toString()
                        if (bitmapCache.get(cacheKey) == null) {
                            val loadedBitmap = loadLocalAudioCover(context, song.uri)
                            if (loadedBitmap != null) {
                                bitmapCache.put(cacheKey, loadedBitmap)
                            }
                        }
                    }
                }
            }
        }
    }

    fun save() {
        RecordStorage.saveRecords(context, records)
    }

    var calendarSelectedDate by remember { mutableStateOf(LocalDate.now()) }

    val prefs = remember { context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE) }
    val savedUris = remember {
        prefs.getStringSet("folder_uris", emptySet())?.map { it.toUri() } ?: emptyList()
    }
    var folderUris by remember { mutableStateOf(savedUris) }

    fun updateFolders(newUris: List<Uri>) {
        folderUris = newUris
        prefs.edit { putStringSet("folder_uris", newUris.map { it.toString() }.toSet()) }
    }

    var categories by remember { mutableStateOf(CategoryStorage.loadCategories(context)) }

    fun updateCategories(newCategories: List<Category>) {
        categories = newCategories
        CategoryStorage.saveCategories(context, newCategories)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route
    val showBottomBar = currentDestination == "today" || currentDestination == "calendar"

    val topBarTitle = when {
        currentDestination == "today" -> "今日旋律"
        currentDestination == "calendar" -> "耳虫日历"
        currentDestination == "settings" -> "设置"
        currentDestination == "settings/library" -> "音乐库管理"
        currentDestination == "settings/category" -> "类别管理"
        currentDestination == "settings/backup" -> "数据备份与恢复"
        currentDestination?.startsWith("selection") == true -> "选择歌曲"
        else -> "Daily Music"
    }

    Scaffold(
        topBar = {
            if (currentDestination?.startsWith("selection") != true) {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    ),
                    navigationIcon = {
                        // 如果是设置及其子页面，显示返回箭头
                        if (currentDestination == "settings/library" ||
                            currentDestination == "settings/backup" ||
                            currentDestination == "settings/category") {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        } else if (currentDestination == "settings") {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = {
                        if (showBottomBar) {
                            IconButton(onClick = { navController.navigateSingle("settings") }) {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                CustomAnimatedBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { route -> navController.navigateSingle(route) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(innerPadding)
        ) {
            // ================== 今日页面 (Today) ==================
            composable(
                route = "today",
                enterTransition = {
                    when (initialState.destination.route) {
                        "calendar" -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                        else -> EnterTransition.None
                    }
                },
                exitTransition = {
                    when (targetState.destination.route) {
                        "calendar" -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                        else -> ExitTransition.None
                    }
                }
            ) {
                Box(modifier = Modifier.fillMaxSize().onHorizontalSwipe(
                    onSwipeLeft = { navController.navigateSingle("calendar") }
                )) {
                    TodayScreen(
                        records = records,
                        categories = categories,
                        onNavigateToSearch = {
                            val today = LocalDate.now().toString()
                            navController.navigateSingle("selection/$today")
                        },
                        onRemoveRecord = {
                            records.remove(LocalDate.now())
                            save()
                        },
                        onUpdateRecord = { updatedRecord ->
                            records[updatedRecord.date] = updatedRecord
                            save()
                        }
                    )
                }
            }

            // ================== 日历页面 (Calendar) ==================
            composable(
                route = "calendar",
                enterTransition = {
                    when (initialState.destination.route) {
                        "today" -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                        else -> EnterTransition.None
                    }
                },
                exitTransition = {
                    when (targetState.destination.route) {
                        "today" -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                        else -> ExitTransition.None
                    }
                }
            ) {
                Box(modifier = Modifier.fillMaxSize().onHorizontalSwipe(
                    onSwipeRight = { navController.navigateSingle("today") }
                )) {
                    CalendarScreen(
                        records = records,
                        categories = categories,
                        selectedDate = calendarSelectedDate,
                        onDateSelected = { calendarSelectedDate = it },
                        onDayClick = { date -> navController.navigateSingle("selection/$date") },
                        onRemoveRecord = { date ->
                            records.remove(date)
                            save()
                        },
                        onCopyRecord = { sourceDate, targetDate ->
                            val sourceRecord = records[sourceDate]
                            if (sourceRecord != null) {
                                records[targetDate] = sourceRecord.copy(date = targetDate)
                                save()
                            }
                        },
                        onUpdateRecord = { updatedRecord ->
                            records[updatedRecord.date] = updatedRecord
                            save()
                        }
                    )
                }
            }

            // ================== 设置主菜单 ==================
            composable(
                route = "settings",
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                },
                exitTransition = {
                    val targetRoute = targetState.destination.route
                    if (targetRoute?.startsWith("settings/") == true) {
                        ExitTransition.None
                    } else {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                    }
                },
                popEnterTransition = {
                    val initialRoute = initialState.destination.route
                    if (initialRoute?.startsWith("settings/") == true) {
                        EnterTransition.None
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                    }
                }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .onHorizontalSwipe(onSwipeRight = { navController.popBackStack() }),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsMenuScreen(
                        onNavigateToLibrary = { navController.navigate("settings/library") },
                        onNavigateToCategory = { navController.navigate("settings/category") },
                        onNavigateToBackup = { navController.navigate("settings/backup") }
                    )
                }
            }

            // ================== 设置/音乐库 (子菜单) ==================
            composable(
                route = "settings/library",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    )
                }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(elevation = 16.dp)
                        .onHorizontalSwipe(onSwipeRight = { navController.popBackStack() }),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LibrarySettingsScreen(
                        folderUris = folderUris,
                        records = records,
                        onAddFolder = { uri ->
                            if (!folderUris.contains(uri)) updateFolders(
                                folderUris + uri
                            )
                        },
                        onRemoveFolder = { uri -> updateFolders(folderUris - uri) },
                        onRecordsUpdated = { updatedRecords ->
                            records.clear()
                            records.putAll(updatedRecords)
                            save()
                        }
                    )
                }
            }

            // ================== 设置/导出 (子菜单) ==================
            composable(
                route = "settings/backup",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    )
                }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(elevation = 16.dp)
                        .onHorizontalSwipe(onSwipeRight = { navController.popBackStack() }),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DataBackupScreen(
                        records = records,
                        categories = categories,
                        onImportRecords = { newRecords ->
                            // 合并数据：将导入的记录 put 到现有 map 中
                            records.putAll(newRecords)
                            save()
                        },
                        onCategoriesChanged = { newCategories ->
                            updateCategories(newCategories)
                        }
                    )
                }
            }

            // ================== 设置/类别管理 ==================
            composable(
                route = "settings/category",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    )
                }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(elevation = 16.dp)
                        .onHorizontalSwipe(onSwipeRight = { navController.popBackStack() }),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CategoryManagementScreen(
                        categories = categories,
                        onCategoriesChanged = { updateCategories(it) }
                    )
                }
            }

            // ================== 检索页面 (Selection) ==================
            composable(
                route = "selection/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(350))
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(350))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(350))
                }
            ) { backStackEntry ->
                val dateString = backStackEntry.arguments?.getString("date") ?: LocalDate.now().toString()
                val targetDate = LocalDate.parse(dateString)

                PullToDismissContainer(
                    onDismiss = { navController.popBackStack() }
                ) {
                    SongSelectionView(
                        targetDate = targetDate,
                        folderUris = folderUris,
                        onSongSelected = { song ->
                            records[targetDate] = DailyRecord(targetDate, song)
                            save()
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomAnimatedBottomBar(
    currentDestination: String?,
    onNavigate: (String) -> Unit
) {
    val selectedIndex = if (currentDestination == "calendar") 1 else 0
    val items = listOf(
        Triple("today", "今日", Icons.Default.Home),
        Triple("calendar", "日历", Icons.Default.DateRange)
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val targetBias = if (selectedIndex == 0) -1f else 1f
            val animatedBias by animateFloatAsState(
                targetValue = targetBias,
                animationSpec = tween(durationMillis = 300),
                label = "bias"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .align(BiasAlignment(animatedBias, 0f))
                    .padding(vertical = 12.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, (route, label, icon) ->
                    val isSelected = selectedIndex == index
                    val contentColor = if (isSelected)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onNavigate(route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

fun NavController.navigateSingle(route: String) {
    if (this.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        val currentRoute = this.currentDestination?.route
        if (currentRoute != route) {
            if (route == "today" || route == "calendar") {
                this.navigate(route) {
                    popUpTo("today") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            } else {
                this.navigate(route)
            }
        }
    }
}

fun Modifier.onHorizontalSwipe(
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    threshold: Float = 50f
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull()

            if (change != null && change.changedToDown()) {
                var dragTotalX = 0f
                var dragTotalY = 0f
                var isHorizontalLocked = false
                var isVerticalLocked = false

                do {
                    val moveEvent = awaitPointerEvent()
                    val moveChange = moveEvent.changes.firstOrNull() ?: break

                    if (moveChange.positionChange() != Offset.Zero) {
                        val delta = moveChange.positionChange()
                        dragTotalX += delta.x
                        dragTotalY += delta.y

                        if (!isHorizontalLocked && !isVerticalLocked) {
                            if (dragTotalX.absoluteValue > dragTotalY.absoluteValue + 5f) {
                                isHorizontalLocked = true
                            } else if (dragTotalY.absoluteValue > dragTotalX.absoluteValue + 5f) {
                                isVerticalLocked = true
                            }
                        }

                        if (isHorizontalLocked) {
                            moveChange.consume()
                        }
                    }
                } while (moveEvent.changes.any { it.pressed })

                if (isHorizontalLocked) {
                    if (dragTotalX > threshold && onSwipeRight != null) {
                        onSwipeRight()
                    } else if (dragTotalX < -threshold && onSwipeLeft != null) {
                        onSwipeLeft()
                    }
                }
            }
        }
    }
}

@Composable
fun PullToDismissContainer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 80.dp.toPx() }

    var isDismissed by remember { mutableStateOf(false) }

    fun performDismiss() {
        if (!isDismissed) {
            isDismissed = true
            onDismiss()
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (offsetY > 0) {
                    val newOffset = (offsetY + available.y).coerceAtLeast(0f)
                    val consumed = offsetY - newOffset
                    offsetY = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == UserInput && available.y > 0) {
                    offsetY += available.y
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offsetY > dismissThreshold) {
                    performDismiss()
                } else {
                    offsetY = 0f
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    val releaseListenerModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) {
                    if (offsetY > dismissThreshold) {
                        performDismiss()
                    } else if (offsetY > 0) {
                        offsetY = 0f
                    }
                }
            }
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = tween(durationMillis = 300),
        label = "offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .then(releaseListenerModifier)
            .offset { IntOffset(0, animatedOffset.roundToInt()) }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}