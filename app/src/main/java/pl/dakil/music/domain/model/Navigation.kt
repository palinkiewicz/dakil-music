package pl.dakil.music.domain.model

/** A surface of the app whose navigation entries the user can customize. */
enum class NavComponent { BOTTOM_BAR, LIBRARY_TABS, MORE_SCREEN }

/**
 * Every navigation destination/action that can be placed on a customizable surface.
 * The enum *name* is the stable persistence key — do not rename members.
 */
enum class NavItem {
    NOW_PLAYING,
    LIBRARY,
    LISTENING_HISTORY,
    STATISTICS,
    REFRESH_LIBRARY,
    SETTINGS,
    BACKUP,
    ABOUT,
    ALBUMS,
    ARTISTS,
    GENRES,
    PLAYLISTS,
    FAVOURITES,
    ALL_SONGS,
    MORE,
}

/** One row in a component: an item plus whether it is currently shown. */
data class NavEntry(val item: NavItem, val enabled: Boolean)

/**
 * The full per-component navigation configuration. Always holds an ordered list for
 * every component (defaulting via [NavDefaults] when a component has never been saved).
 */
data class NavConfig(val byComponent: Map<NavComponent, List<NavEntry>>) {

    fun entries(component: NavComponent): List<NavEntry> =
        byComponent[component] ?: NavDefaults.defaultEntries(component)

    /** The enabled items of [component] in their configured order. */
    fun enabled(component: NavComponent): List<NavItem> =
        entries(component).filter { it.enabled }.map { it.item }

    fun withComponent(component: NavComponent, entries: List<NavEntry>): NavConfig =
        copy(byComponent = byComponent + (component to entries))

    companion object {
        val DEFAULT = NavConfig(
            NavComponent.entries.associateWith { NavDefaults.defaultEntries(it) },
        )
    }
}

/** Static catalog of which items each component allows and how it looks by default. */
object NavDefaults {

    /** Maximum number of enabled entries allowed in the bottom bar. */
    const val MAX_BOTTOM_BAR = 5

    /** Items that must always remain reachable (at least one enabled instance across all components). */
    val LOCKED_ITEMS = setOf(NavItem.SETTINGS, NavItem.NOW_PLAYING)

    private val LIBRARY_ITEMS = listOf(
        NavItem.ALBUMS,
        NavItem.ARTISTS,
        NavItem.GENRES,
        NavItem.PLAYLISTS,
        NavItem.FAVOURITES,
        NavItem.ALL_SONGS,
    )

    // Catalog order shared by the bottom bar and the More screen.
    private val SHARED_ITEMS = listOf(
        NavItem.NOW_PLAYING,
        NavItem.LIBRARY,
        NavItem.LISTENING_HISTORY,
        NavItem.STATISTICS,
        NavItem.REFRESH_LIBRARY,
        NavItem.SETTINGS,
        NavItem.BACKUP,
        NavItem.ABOUT,
        NavItem.ALBUMS,
        NavItem.ARTISTS,
        NavItem.GENRES,
        NavItem.PLAYLISTS,
        NavItem.FAVOURITES,
        NavItem.ALL_SONGS,
    )

    fun availableItems(component: NavComponent): List<NavItem> = when (component) {
        NavComponent.BOTTOM_BAR -> SHARED_ITEMS + NavItem.MORE
        NavComponent.MORE_SCREEN -> SHARED_ITEMS
        NavComponent.LIBRARY_TABS -> LIBRARY_ITEMS
    }

    private val DEFAULT_ENABLED = mapOf(
        NavComponent.BOTTOM_BAR to listOf(NavItem.NOW_PLAYING, NavItem.LIBRARY, NavItem.MORE),
        NavComponent.MORE_SCREEN to listOf(
            NavItem.LISTENING_HISTORY,
            NavItem.STATISTICS,
            NavItem.REFRESH_LIBRARY,
            NavItem.SETTINGS,
            NavItem.BACKUP,
            NavItem.ABOUT,
        ),
        NavComponent.LIBRARY_TABS to listOf(
            NavItem.ALBUMS,
            NavItem.ARTISTS,
            NavItem.GENRES,
            NavItem.PLAYLISTS,
        ),
    )

    /** Whether an item defaults to enabled when it first appears in a component. */
    fun defaultEnabled(component: NavComponent, item: NavItem): Boolean =
        item in DEFAULT_ENABLED.getValue(component)

    /** Enabled items first (in their order), then the remaining available items, disabled. */
    fun defaultEntries(component: NavComponent): List<NavEntry> {
        val enabled = DEFAULT_ENABLED.getValue(component)
        val rest = availableItems(component).filter { it !in enabled }
        return enabled.map { NavEntry(it, true) } + rest.map { NavEntry(it, false) }
    }

    /**
     * Reconciles a persisted (possibly stale) ordered list with the current catalog:
     * drops unknown/disallowed items and appends any newly-introduced item at its default state.
     */
    fun reconcile(component: NavComponent, stored: List<NavEntry>): List<NavEntry> {
        val allowed = availableItems(component)
        val valid = stored.filter { it.item in allowed }.distinctBy { it.item }
        val present = valid.mapTo(HashSet()) { it.item }
        val appended = allowed
            .filter { it !in present }
            .map { NavEntry(it, defaultEnabled(component, it)) }
        return valid + appended
    }
}

/**
 * Pure cross-component rules that decide which entries the user may not switch off,
 * so the UI can gray them out and the ViewModel can keep the config valid.
 */
object NavRules {

    /**
     * Items in [component] that must stay ON (rendered as a checked, disabled switch):
     * the last reachable Settings / Now Playing, plus More when it is the only path to Settings.
     */
    fun lockedItems(config: NavConfig, component: NavComponent): Set<NavItem> {
        val locked = mutableSetOf<NavItem>()

        for (item in NavDefaults.LOCKED_ITEMS) {
            val componentsEnabling = NavComponent.entries.filter { item in config.enabled(it) }
            if (componentsEnabling.size == 1 && componentsEnabling.first() == component) {
                locked += item
            }
        }

        // If Settings lives only in the More screen, More itself must stay on the bottom bar.
        if (component == NavComponent.BOTTOM_BAR &&
            NavItem.SETTINGS in config.enabled(NavComponent.MORE_SCREEN) &&
            NavItem.SETTINGS !in config.enabled(NavComponent.BOTTOM_BAR)
        ) {
            locked += NavItem.MORE
        }

        return locked
    }
}
