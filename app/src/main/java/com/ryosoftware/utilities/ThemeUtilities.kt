package com.ryosoftware.utilities

import android.content.Context
import android.content.res.Configuration

object ThemeUtilities {
    fun isDarkThemeActive(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
