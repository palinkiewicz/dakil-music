package pl.dakil.music.domain.model

/** A selectable color theme for the app. [DAKILS_MUSIC] is the branded default. */
enum class AppColorTheme { DAKILS_MUSIC, DYNAMIC, OCEAN, LAVENDER, SUNSET, ROSE, TEAL }

/** Whether the app follows the system dark mode setting or overrides it. */
enum class DarkThemeOption { FOLLOW_SYSTEM, LIGHT, DARK }
