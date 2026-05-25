package com.ryosoftware.apks_exporter

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.apks_exporter.MainService.getLastAppUpdateNotificationTimesKey
import com.ryosoftware.apks_exporter.MainService.getLastAppUpdateTimeKey
import com.ryosoftware.apks_exporter.MainService.getLastAppVersionNumberKey
import com.ryosoftware.apks_exporter.MainService.getLastBackupTimeKey
import com.ryosoftware.apks_exporter.MainService.getLastBackupVersionNumberKey
import com.ryosoftware.utilities.LogUtilities
import com.ryosoftware.utilities.PackageManagerUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _items = mutableStateListOf<AppItem>()
    val items: List<AppItem> get() = _items

    var isLoading by mutableStateOf(false)
        private set

    var isSelecting by mutableStateOf(false)
        private set

    private val _selectedItems = mutableStateListOf<AppItem>()
    val selectedItems: List<AppItem> get() = _selectedItems.toList()

    var isSearching by mutableStateOf(false)
        private set

    var searchText: String? by mutableStateOf(null)
        private set

    init {
        loadData()
    }

    fun loadData() {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch {
            delay(100)
            val loadedItems = withContext(Dispatchers.IO) { doLoad() }
            _items.clear()
            if (loadedItems != null) {
                _items.addAll(loadedItems)
                incrementSeenCounts(loadedItems)
                ApplicationPreferences.cleanupOrphanedPreferences(
                    loadedItems.map { it.packageName }.toSet()
                )
            }
            isLoading = false
        }
    }

    private fun doLoad(): List<AppItem>? {
        try {
            val context = getApplication<Main>()
            val packages = context.packageManager.getInstalledPackages(0)
            if (ApplicationPreferences.get(ApplicationPreferences.IS_FIRST_APP_LAUNCH_TIME, true) ||
                ApplicationPreferences.get(ApplicationPreferences.SEED_BACKUP_DATA_FOR_ALL_APPS, false)) {
                val now = System.currentTimeMillis()
                runBlocking {
                    ApplicationPreferences.dataStore!!.edit { prefs ->
                        for (pi in packages) {
                            prefs[longPreferencesKey(getLastAppVersionNumberKey(pi.packageName))] = pi.longVersionCode
                            prefs[longPreferencesKey(getLastAppUpdateTimeKey(pi.packageName))] = 0L
                            prefs[intPreferencesKey(getLastAppUpdateNotificationTimesKey(pi.packageName))] = Int.MAX_VALUE

                            MainService.setBackupDone(pi)
                        }
                        prefs[booleanPreferencesKey(ApplicationPreferences.IS_FIRST_APP_LAUNCH_TIME)] = false
                        prefs[booleanPreferencesKey(ApplicationPreferences.SEED_BACKUP_DATA_FOR_ALL_APPS)] = false
                    }
                }
            }
            val showSystemPackages = ApplicationPreferences.get(
                ApplicationPreferences.SHOW_SYSTEM_PACKAGES_KEY,
                ApplicationPreferences.SHOW_SYSTEM_PACKAGES_DEFAULT
            )
            val showSystemPackagesOnlyIfUpdated = ApplicationPreferences.get(
                ApplicationPreferences.SHOW_SYSTEM_PACKAGES_ONLY_IF_UPDATED_KEY,
                ApplicationPreferences.SHOW_SYSTEM_PACKAGES_ONLY_IF_UPDATED_DEFAULT
            )
            val sortUpdatedAppsFirst = ApplicationPreferences.get(
                ApplicationPreferences.SORT_UPDATED_APPS_FIRST_KEY,
                ApplicationPreferences.SORT_UPDATED_APPS_FIRST_DEFAULT
            )

            val result = mutableListOf<AppItem>()
            for (pi in packages) {
                if (pi.applicationInfo?.packageName == null) continue
                if (MainService.isSystemApplication(pi) &&
                    (!showSystemPackages || (!MainService.isSystemApplicationUpdated(pi) && showSystemPackagesOnlyIfUpdated))
                ) continue
                val item = AppItem(
                    packageInfo = pi,
                    icon = PackageManagerUtilities.getApplicationIcon(context, pi.packageName),
                    appLabel = PackageManagerUtilities.getApplicationLabel(context, pi.packageName, pi.packageName)
                )
                result.add(item)
            }

            result.sortWith(Comparator { left, right ->
                val leftBackupUpdated = left.isAppUpdated
                val rightBackupUpdated = right.isAppUpdated
                if (!sortUpdatedAppsFirst || leftBackupUpdated == rightBackupUpdated) {
                    left.appLabel.lowercase().compareTo(right.appLabel.lowercase())
                } else {
                    if (leftBackupUpdated) -1 else 1
                }
            })

            return result
        } catch (e: Exception) {
            LogUtilities.show(this, e)
            return null
        }
    }

    fun toggleSelection(item: AppItem) {
        if (_selectedItems.contains(item)) _selectedItems.remove(item)
        else _selectedItems.add(item)
        isSelecting = _selectedItems.isNotEmpty()
    }

    fun startSelection(item: AppItem) {
        _selectedItems.clear()
        _selectedItems.add(item)
        isSelecting = true
    }

    private fun incrementSeenCounts(items: List<AppItem>) {
        for (item in items) {
            if (item.isAppUpdated) {
                MainService.incrementAppUpdateNotificationCount(item.packageName)
            }
        }
    }

    fun cancelSelection() {
        _selectedItems.clear()
        isSelecting = false
    }

    fun startSearch() {
        isSearching = true
        searchText = ""
    }

    fun updateSearch(text: String) {
        searchText = text
    }

    fun clearSearch() {
        isSearching = false
        searchText = null
    }

    val filteredItems: List<AppItem>
        get() {
            val query = searchText?.lowercase() ?: return _items
            if (query.isEmpty()) return _items
            return _items.filter {
                it.appLabel.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
}
