package com.pr0gramm.app.services

import androidx.annotation.ColorRes
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Settings
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.tryEnumValueOf

/**
 * A little service to get theme stuff.
 */
object ThemeHelper {
    var theme = Themes.ORANGE
        private set

    val accentColor: Int
        @ColorRes get() = theme.accentColor

    val primaryColor: Int
        @ColorRes get() = theme.primaryColor

    val primaryColorDark: Int
        @ColorRes get() = theme.primaryColorDark

    init {
        doInBackground {
            // update theme every time the value changes.
            Settings.changes { themeName }.collect { updateTheme() }
        }
    }

    /**
     * Updates the current theme from settings.
     */
    fun updateTheme() {
        val name = Settings.themeName
        Logger("ThemeHelper").debug { "Current Theme is $name" }
        theme = tryEnumValueOf<Themes>(name) ?: Themes.ORANGE
    }

    /**
     * Sets the current theme to the given value and stores it in the settings.
     */
    fun updateTheme(theme: Themes) {
        Settings.edit {
            putString("pref_theme", theme.name)
        }
    }
}
