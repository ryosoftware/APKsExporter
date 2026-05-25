package com.ryosoftware.apks_exporter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryosoftware.apks_exporter.main_activity.InstallAppTask
import com.ryosoftware.utilities.PermissionUtilities
import com.ryosoftware.utilities.StatusBarUtilities
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    var contextMenuItem by remember { mutableStateOf<AppItem?>(null) }
    var showInstallDescription by remember { mutableStateOf(false) }
    var showNoFolderDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }

    val saveFolderUri = ApplicationPreferences.get<String?>(ApplicationPreferences.SAVE_FOLDER_KEY, null)
    val autoBackupEnabled = ApplicationPreferences.get(
        ApplicationPreferences.AUTO_BACKUP_APPS_KEY,
        ApplicationPreferences.AUTO_BACKUP_APPS_DEFAULT
    )

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        pendingInstallUri?.let { uri ->
            if (PermissionUtilities.permissionGranted(activity, Manifest.permission.INSTALL_PACKAGES)) {
                isInstalling = true
                doInstallApp(activity, uri) { isInstalling = false }
                pendingInstallUri = null
            }
        }
    }

    val apkFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (PermissionUtilities.permissionGranted(activity, Manifest.permission.INSTALL_PACKAGES)) {
                isInstalling = true
                doInstallApp(activity, uri) { isInstalling = false }
            } else {
                pendingInstallUri = uri
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${activity.packageName}".toUri()
                )
                installPermissionLauncher.launch(intent)
            }
        }
    }

    StatusBarUtilities.setColor(activity, MaterialTheme.colorScheme.primary.toArgb())

    val scrollBehavior = if (!viewModel.isSearching && !viewModel.isSelecting) {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),

        topBar = {
            when {
                viewModel.isSearching -> SearchTopBar(
                    query = viewModel.searchText ?: "",
                    onQueryChange = { viewModel.updateSearch(it) },
                    onClose = { viewModel.clearSearch() }
                )
                viewModel.isSelecting -> SelectionTopBar(
                    selectedCount = viewModel.selectedItems.size,
                    onSaveApps = {
                        val items = viewModel.selectedItems.toList()
                        if (saveFolderUri == null) {
                            showNoFolderDialog = true
                        } else if (!DocumentFile.fromTreeUri(activity, saveFolderUri.toUri())!!.canWrite()) {
                            activity.contentResolver.takePersistableUriPermission(
                                saveFolderUri.toUri(),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } else {
                            isSaving = true
                            doSaveApps(activity, items, scope) { isSaving = false }
                        }
                    },
                    onMarkAsBacked = {
                        for (item in viewModel.selectedItems) {
                            if (item.isAppUpdated) item.setBackupDone()
                        }
                        viewModel.cancelSelection()
                        viewModel.loadData()
                    },
                    onCancel = { viewModel.cancelSelection() }
                )
                else -> DefaultTopBar(
                    onSearch = { viewModel.startSearch() },
                    onSettings = onNavigateToSettings,
                    onInstallApp = {
                        val doNotShow = ApplicationPreferences.get(
                            DO_NOT_SHOW_INSTALL_APP_FILE_PICKER_DESCRIPTION_KEY, false
                        )
                        if (doNotShow) {
                            apkFilePickerLauncher.launch(arrayOf(
                                "application/vnd.android.package-archive",
                                "application/zip"
                            ))
                        } else {
                            showInstallDescription = true
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    hasItems = viewModel.items.isNotEmpty()
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                viewModel.isLoading && viewModel.items.isEmpty() -> LoadingState()
                viewModel.filteredItems.isEmpty() && !viewModel.isLoading -> EmptyState()
                else -> AppList(
                    items = viewModel.filteredItems,
                    viewModel = viewModel,
                    onContextMenuChange = { contextMenuItem = it }
                )
            }

            if (isSaving || isInstalling) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showInstallDescription) {
        InstallDescriptionDialog(
            onAccept = {
                showInstallDescription = false
                apkFilePickerLauncher.launch(arrayOf(
                    "application/vnd.android.package-archive",
                    "application/zip"
                ))
            },
            onDismiss = { showInstallDescription = false }
        )
    }

    if (contextMenuItem != null) {
        val item = contextMenuItem!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val hasLaunchIntent = activity.packageManager.getLaunchIntentForPackage(item.packageName) != null
        val canUninstall = (!item.isSystemApp) || item.isUpdatedSystemApp

        ModalBottomSheet(
            onDismissRequest = { contextMenuItem = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.icon != null) {
                        Image(
                            painter = rememberDrawablePainter(item.icon),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.sym_def_app_icon),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Text(
                    text = item.appLabel,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                HorizontalDivider()

                Text(
                    text = pluralStringResource(R.plurals.count_apks_extended, item.apkCount, item.apkCount, sizeToStr(item.apkSize)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)
                )
                AppContextMenuItem(
                    icon = { Icon(painterResource(R.drawable.ic_actionbar_save_apps), contentDescription = null) },
                    text = { Text(pluralStringResource(R.plurals.save_app_package, item.apkCount, item.apkCount)) },
                    onClick = {
                        contextMenuItem = null
                        scope.launch {
                            val folder = ApplicationPreferences.get<String?>(ApplicationPreferences.SAVE_FOLDER_KEY, null)
                            if (folder != null && DocumentFile.fromTreeUri(activity, folder.toUri())!!.canWrite()) {
                                doSaveApps(activity, listOf(item), scope) {}
                            }
                        }
                    }
                )
                AppContextMenuItem(
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    text = { Text(pluralStringResource(R.plurals.share_app_package, item.apkCount, item.apkCount)) },
                    onClick = {
                        contextMenuItem = null
                        item.shareApk(activity)
                    }
                )
                if (canUninstall && (item.packageName != LocalContext.current.packageName)) {
                    AppContextMenuItem(
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        text = {
                            Text(if (item.isUpdatedSystemApp) stringResource(R.string.uninstall_updates)
                            else stringResource(R.string.uninstall_app))
                        },
                        onClick = {
                            contextMenuItem = null
                            item.uninstallApp(activity)
                        },
                    )
                }
                AppContextMenuItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    text = { Text(stringResource(R.string.app_info)) },
                    onClick = {
                        contextMenuItem = null
                        val intent = Intent(activity, AppDetailActivity::class.java).apply {
                            putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, item.packageName)
                        }
                        activity.startActivity(intent)
                    }
                )
                if (hasLaunchIntent && (item.packageName != LocalContext.current.packageName)) {
                    AppContextMenuItem(
                        icon = { Icon(painterResource(R.drawable.ic_open_app), contentDescription = null) },
                        text = { Text(stringResource(R.string.open_app)) },
                        onClick = {
                            contextMenuItem = null
                            item.openApp(activity)
                        }
                    )
                }
                if (autoBackupEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                contextMenuItem = null
                                item.canAutomaticallyBackup(!item.canAutomaticallyBackup())
                                scope.launch { viewModel.loadData() }
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.can_automatically_backup))
                        Switch(
                            checked = item.canAutomaticallyBackup(),
                            onCheckedChange = {
                                contextMenuItem = null
                                item.canAutomaticallyBackup(!item.canAutomaticallyBackup())
                            }
                        )
                    }
                }
            }
        }
    }
    if (showNoFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNoFolderDialog = false },
            title = { Text(stringResource(R.string.information)) },
            text = { Text(stringResource(R.string.no_save_folder_selected)) },
            confirmButton = {
                TextButton(onClick = { showNoFolderDialog = false }) {
                    Text(stringResource(R.string.accept_button))
                }
            }
        )
    }
}

@Composable
private fun AppContextMenuItem(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(16.dp))
        text()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onInstallApp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    hasItems: Boolean
) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = onInstallApp) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.install_app))
            }
            if (hasItems) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onSaveApps: () -> Unit,
    onMarkAsBacked: () -> Unit,
    onCancel: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        actions = {
            IconButton(onClick = onMarkAsBacked) {
                Icon(
                    painter = painterResource(R.drawable.ic_actionbar_accept),
                    contentDescription = null
                )
            }
            IconButton(onClick = onSaveApps) {
                Icon(
                    painter = painterResource(R.drawable.ic_actionbar_save_apps),
                    contentDescription = null
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onPrimary,
                    focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                    unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_no_apps_big),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_apps),
            color = Color.Gray,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun AppList(
    items: List<AppItem>,
    viewModel: MainViewModel,
    onContextMenuChange: (AppItem?) -> Unit
) {
    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()
    var lettersHeight by remember { mutableStateOf(0f) }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.packageName }
            ) { _, item ->
                AppCardWithSpacing(item, viewModel, onContextMenuChange)
            }
        }

        val letters = remember(items.toList()) {
            items.map { it.appLabel.first().uppercaseChar() }.distinct().sorted()
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(20.dp)
                .fillMaxHeight()
                .onSizeChanged { lettersHeight = it.height.toFloat() }
                .pointerInput(lettersHeight) {
                    detectTapGestures { offset ->
                        val index = if (lettersHeight > 0) {
                            (offset.y / lettersHeight * letters.size).toInt()
                                .coerceIn(0, letters.size - 1)
                        } else 0
                        val letter = letters[index]
                        val targetIndex = items.indexOfFirst {
                            it.appLabel.first().uppercaseChar() >= letter
                        }
                        if (targetIndex >= 0) {
                            listScope.launch { listState.animateScrollToItem(targetIndex) }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxHeight()
            ) {
                for (letter in letters) {
                    Text(
                        text = letter.toString(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCardWithSpacing(
    item: AppItem,
    viewModel: MainViewModel,
    onContextMenuChange: (AppItem?) -> Unit
) {
    val isSelected = viewModel.selectedItems.contains(item)
    AppCard(
        item = item,
        isSelected = isSelected,
        isSelecting = viewModel.isSelecting,
        onClick = {
            if (viewModel.isSelecting) {
                viewModel.toggleSelection(item)
            } else {
                onContextMenuChange(item)
            }
        },
        onLongClick = {
            if (!viewModel.isSelecting) {
                viewModel.startSelection(item)
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun StatusBadge(
    icon: Painter,
    containerColor: Color,
    tint: Color,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor.copy(alpha = 0.15f),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = containerColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = containerColor
            )
        }
    }
}

@Composable
private fun AppCard(
    item: AppItem,
    isSelected: Boolean,
    isSelecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val autoBackupEnabled = ApplicationPreferences.get(
        ApplicationPreferences.AUTO_BACKUP_APPS_KEY, false
    )
    val autoBackupFlow by item.observeCanAutomaticallyBackup().collectAsState(initial = item.canAutomaticallyBackup())
    var autoBackupApp by remember { mutableStateOf(autoBackupFlow) }
    LaunchedEffect(autoBackupFlow) { autoBackupApp = autoBackupFlow }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isSelecting) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .padding(end = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_checked),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.size(50.dp)) {
                    if (item.icon != null) {
                        Image(
                            painter = rememberDrawablePainter(item.icon),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.sym_def_app_icon),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (isSelecting) 0.dp else 12.dp)
            ) {
                Text(
                    text = item.appLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.packageName != item.appLabel) {
                    Text(
                        text = item.packageName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.versionName?.let {
                    Text(
                        text = stringResource(R.string.app_version_name_and_number, item.versionName?:"", item.versionCode),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.isAppUpdated && (!item.isAppBacked)) {
                        StatusBadge(
                            icon = painterResource(R.drawable.ic_app_updated),
                            containerColor = MaterialTheme.colorScheme.primary,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            label = stringResource(R.string.badge_app_recently_updated)
                        )
                    }
                    if (item.apkCount > 0) {
                        StatusBadge(
                            icon = painterResource(R.drawable.ic_split_apk),
                            containerColor = MaterialTheme.colorScheme.primary,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            label = pluralStringResource(R.plurals.count_apks, item.apkCount, item.apkCount, sizeToStr(item.apkSize))
                        )
                    }
                    if (autoBackupEnabled && (!autoBackupApp)) {
                        StatusBadge(
                            icon = painterResource(R.drawable.ic_auto_backup_off),
                            containerColor = MaterialTheme.colorScheme.error,
                            tint = MaterialTheme.colorScheme.onError,
                            label = stringResource(R.string.badge_autobackup_off),
                            onClick = {
                                item.canAutomaticallyBackup(true)
                                autoBackupApp = true
                            }
                        )
                    }
                    if (item.isSystemApp) {
                        StatusBadge(
                            icon = painterResource(R.drawable.ic_system_app),
                            containerColor = MaterialTheme.colorScheme.primary,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            label = stringResource(R.string.badge_system_app)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun sizeToStr(size: Long) : String {
    if (size >= 1_000_000_000) return stringResource(R.string.size_in_gb, size / 1_000_000_000.0)
    if (size >= 1_000_000) return stringResource(R.string.size_in_mb, size / 1_000_000.0)
    return stringResource(R.string.size_in_kb, size / 1000.0)
}
@Composable
private fun rememberDrawablePainter(drawable: Drawable): Painter {
    return remember(drawable) {
        BitmapPainter(drawable.toBitmap().asImageBitmap())
    }
}

@Composable
private fun InstallDescriptionDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    var notShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.install_app)) },
        text = {
            Column {
                Text(stringResource(R.string.install_app_description))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = notShowAgain, onCheckedChange = { notShowAgain = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.do_not_show_anymore))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (notShowAgain) {
                    ApplicationPreferences.put(
                        DO_NOT_SHOW_INSTALL_APP_FILE_PICKER_DESCRIPTION_KEY, true
                    )
                }
                onAccept()
            }) {
                Text(stringResource(R.string.accept_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

private fun doSaveApps(
    activity: Activity,
    items: List<AppItem>,
    scope: kotlinx.coroutines.CoroutineScope,
    onFinish: () -> Unit
) {
    scope.launch {
        var success = true
        for (item in items) {
            if (!MainService.doBackup(activity, item.packageInfo)) {
                success = false
                break
            }
        }
        onFinish()
        val context = Main.getInstance()
        Toast.makeText(
            context,
            if (success) R.string.operation_done else R.string.cant_complete_requested_operation,
            Toast.LENGTH_LONG
        ).show()
        context.sendBroadcast(Intent(ACTION_SAVE_TASKS_FINISHED))
    }
}

private const val ACTION_SAVE_TASKS_FINISHED = BuildConfig.APPLICATION_ID + ".SAVE_TASKS_FINISHED"

private const val DO_NOT_SHOW_INSTALL_APP_FILE_PICKER_DESCRIPTION_KEY = "do-not-show-install-app-file-picker-description";

private fun doInstallApp(
    activity: Activity,
    uri: android.net.Uri,
    onFinish: () -> Unit
) {
    InstallAppTask(uri, object : InstallAppTask.Listener {
        override fun onInstallAppTaskStarted() {}
        override fun onInstallAppTaskFinished(success: Boolean) {
            onFinish()
            val context = Main.getInstance()
            Toast.makeText(
                context,
                if (success) R.string.operation_done else R.string.cant_complete_requested_operation,
                Toast.LENGTH_LONG
            ).show()
        }
    }).execute()
}


