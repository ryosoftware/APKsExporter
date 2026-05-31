package com.ryosoftware.apks_exporter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences"
)

object ApplicationPreferences {
    const val IS_FIRST_APP_LAUNCH_TIME = "is-first-app-launch-time"
    const val SHOW_SYSTEM_PACKAGES_KEY = "show-system-packages"
    var SHOW_SYSTEM_PACKAGES_DEFAULT = false
    const val SHOW_SYSTEM_PACKAGES_ONLY_IF_UPDATED_KEY = "show-system-packages-only-if-updated"
    var SHOW_SYSTEM_PACKAGES_ONLY_IF_UPDATED_DEFAULT = false
    const val SAVE_AS_ZIP_FILE_KEY = "save-as-zip-file"
    var SAVE_AS_ZIP_FILE_DEFAULT = false
    const val DO_NOT_COMPRESS_SINGLE_FILES_KEY = "do-not-compress-single-files"
    var DO_NOT_COMPRESS_SINGLE_FILES_DEFAULT = false
    const val USE_APP_LABEL_KEY = "use-app-label"
    var USE_APP_LABEL_DEFAULT = false
    const val USE_LONG_VERSION_NUMBER_KEY = "use-long-version-number"
    var USE_LONG_VERSION_NUMBER_DEFAULT = false
    const val SORT_UPDATED_APPS_FIRST_KEY = "sort-updated-apps-first"
    var SORT_UPDATED_APPS_FIRST_DEFAULT = false
    const val SAVE_FOLDER_KEY = "save-folder"
    const val AUTO_BACKUP_APPS_KEY = "auto-backup-apps"
    var AUTO_BACKUP_APPS_DEFAULT = false
    const val THEME_KEY = "theme"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val THEME_SYSTEM = "system"
    val THEME_VALUES = arrayOf(THEME_DARK, THEME_LIGHT, THEME_SYSTEM)
    var THEME_DEFAULT = THEME_SYSTEM
    const val BLACK_BACKGROUND_KEY = "black-background"
    var BLACK_BACKGROUND_DEFAULT = false
    const val USE_SYSTEM_ACCENT_COLOR_KEY = "system-accent-color"
    var USE_SYSTEM_ACCENT_COLOR_DEFAULT = false
    const val SEED_BACKUP_DATA_FOR_ALL_APPS = "seed-backup-data-for-all-apps"
    const val LAST_AUTO_BACKUP_TIME_KEY = "last-auto-backup-time"
    const val NEXT_AUTO_BACKUP_TIME_KEY = "next-auto-backup-time"

    const val LAST_BACKED_APP_PACKAGES_KEY = "last-backed-app-packages"
    const val LAST_BACKUP_ERRORS_COUNT_KEY = "last-backup-errors-count"

    private const val VERSION_KEY = "version"
    private const val VERSION_VALUE = 1.1f

    @PublishedApi
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @PublishedApi
    internal var dataStore: DataStore<Preferences>? = null

    private fun getContext(): Context = Main.instance

    private fun upgrade(edit: MutablePreferences, fromVersion: Float) {
    }

    fun initialize() {
        dataStore = getContext().appDataStore
        initializeConstants()
        scope.launch {
            val currentVersion = dataStore!!.data.first()[floatPreferencesKey(VERSION_KEY)] ?: 0f
            if (currentVersion != VERSION_VALUE) {
                if (currentVersion == 0f) {
                    val oldPrefs = getContext().getSharedPreferences(
                        "${getContext().packageName}_preferences", Context.MODE_PRIVATE
                    )
                    if (oldPrefs.all.isNotEmpty()) {
                        dataStore!!.edit { edit ->
                            oldPrefs.all.forEach { (key, value) ->
                                when (value) {
                                    is Boolean -> edit[booleanPreferencesKey(key)] = value
                                    is Int -> edit[intPreferencesKey(key)] = value
                                    is Long -> edit[longPreferencesKey(key)] = value
                                    is String -> edit[stringPreferencesKey(key)] = value
                                    is Set<*> -> edit[stringSetPreferencesKey(key)] = value as Set<String>
                                    is Float -> edit[floatPreferencesKey(key)] = value
                                }
                            }
                            upgrade(edit, currentVersion)
                            edit[floatPreferencesKey(VERSION_KEY)] = VERSION_VALUE
                        }
                        return@launch
                    }
                }
                dataStore!!.edit { edit ->
                    if (currentVersion < VERSION_VALUE) upgrade(edit, currentVersion)
                    edit[floatPreferencesKey(VERSION_KEY)] = VERSION_VALUE
                }
            }
        }
    }

    private fun initializeConstants() {
        val context = getContext()
        SHOW_SYSTEM_PACKAGES_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.show_system_apps_default))
        SHOW_SYSTEM_PACKAGES_ONLY_IF_UPDATED_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.show_system_apps_only_if_updated_default))
        SAVE_AS_ZIP_FILE_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.save_as_zip_file_default))
        DO_NOT_COMPRESS_SINGLE_FILES_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.do_not_compress_single_files_default))
        USE_APP_LABEL_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.use_app_label_default))
        USE_LONG_VERSION_NUMBER_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.use_long_version_number_default))
        SORT_UPDATED_APPS_FIRST_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.sort_updated_apps_first_default))
        AUTO_BACKUP_APPS_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.auto_backup_apps_default))
        val themeDefault = context.getString(R.string.theme_default)
        if (themeDefault in THEME_VALUES) THEME_DEFAULT = themeDefault
        BLACK_BACKGROUND_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.black_background_default))
        USE_SYSTEM_ACCENT_COLOR_DEFAULT = java.lang.Boolean.parseBoolean(context.getString(R.string.use_system_accent_color_default))
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> get(name: String, defaultValue: T): T {
        val key = when (T::class) {
            Boolean::class -> booleanPreferencesKey(name)
            Int::class -> intPreferencesKey(name)
            Long::class -> longPreferencesKey(name)
            String::class -> stringPreferencesKey(name)
            Float::class -> floatPreferencesKey(name)
            Set::class -> stringSetPreferencesKey(name)
            else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
        }
        return runBlocking {
            (dataStore!!.data.first()[key as Preferences.Key<T>] ?: defaultValue)
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> observe(name: String, defaultValue: T): Flow<T> {
        val key = when (T::class) {
            Boolean::class -> booleanPreferencesKey(name)
            Int::class -> intPreferencesKey(name)
            Long::class -> longPreferencesKey(name)
            String::class -> stringPreferencesKey(name)
            Float::class -> floatPreferencesKey(name)
            Set::class -> stringSetPreferencesKey(name)
            else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
        }
        return dataStore!!.data.map { it[key as Preferences.Key<T>] ?: defaultValue }
    }

    fun removePackagePreferences(packageName: String) {
        scope.launch {
            dataStore!!.edit { prefs ->
                val keysToRemove = mutableListOf<Preferences.Key<*>>()
                for (key in prefs.asMap().keys) {
                    for (suffix in MainService.APP_SUFFIXES) {
                        if (key.name == "$packageName-$suffix") {
                            keysToRemove.add(key)
                        }
                    }
                }
                for (key in keysToRemove) {
                    @Suppress("UNCHECKED_CAST")
                    prefs.remove(key as Preferences.Key<Any>)
                }
            }
        }
    }

    fun cleanupOrphanedPreferences(installedPackages: Set<String>) {
        scope.launch {
            dataStore!!.edit { prefs ->
                val keysToRemove = mutableListOf<Preferences.Key<*>>()
                for (key in prefs.asMap().keys) {
                    for (suffix in MainService.APP_SUFFIXES) {
                        if (key.name.endsWith("-$suffix")) {
                            val packageName = key.name.removeSuffix("-$suffix")
                            if (packageName !in installedPackages) {
                                keysToRemove.add(key)
                            }
                        }
                    }
                }
                for (key in keysToRemove) {
                    @Suppress("UNCHECKED_CAST")
                    prefs.remove(key as Preferences.Key<Any>)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> put(name: String, value: T) {
        scope.launch {
            val key = when (T::class) {
                Boolean::class -> booleanPreferencesKey(name)
                Int::class -> intPreferencesKey(name)
                Long::class -> longPreferencesKey(name)
                String::class -> stringPreferencesKey(name)
                Float::class -> floatPreferencesKey(name)
                Set::class -> stringSetPreferencesKey(name)
                else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
            }
            dataStore!!.edit { prefs ->
                prefs[key as Preferences.Key<T>] = value
            }
        }
    }

    fun delete(name: String) {
        scope.launch {
            dataStore!!.edit { prefs ->
                prefs.remove(stringPreferencesKey(name))
            }
        }
    }

    fun delete(names: Array<String>) {
        scope.launch {
            dataStore!!.edit { prefs ->
                for (name in names) {
                    prefs.remove(stringPreferencesKey(name))
                }
            }
        }
    }
}
