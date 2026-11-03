package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavConfig
import pl.dakil.music.domain.model.NavDefaults
import pl.dakil.music.domain.model.NavEntry
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.domain.model.NavItem.ABOUT
import pl.dakil.music.domain.model.NavItem.BACKUP
import pl.dakil.music.domain.model.NavItem.LIBRARY
import pl.dakil.music.domain.model.NavItem.LISTENING_HISTORY
import pl.dakil.music.domain.model.NavItem.MORE
import pl.dakil.music.domain.model.NavItem.NOW_PLAYING
import pl.dakil.music.domain.model.NavItem.REFRESH_LIBRARY
import pl.dakil.music.domain.model.NavItem.SETTINGS
import pl.dakil.music.domain.model.NavItem.STATISTICS

class NavLegacyDefaultsTest {

    private val legacyBar = listOf(NOW_PLAYING, LIBRARY, MORE)
    private val legacyMore = listOf(LISTENING_HISTORY, STATISTICS, REFRESH_LIBRARY, SETTINGS, BACKUP, ABOUT)

    private fun config(bar: List<NavItem>, more: List<NavItem>) = NavConfig.DEFAULT
        .withComponent(NavComponent.BOTTOM_BAR, entries(NavComponent.BOTTOM_BAR, bar))
        .withComponent(NavComponent.MORE_SCREEN, entries(NavComponent.MORE_SCREEN, more))

    private fun entries(component: NavComponent, enabled: List<NavItem>) =
        NavDefaults.reconcile(component, enabled.map { NavEntry(it, true) } +
            NavDefaults.availableItems(component).filter { it !in enabled }.map { NavEntry(it, false) })

    @Test
    fun oldDefaultsMoveToTheNewOnes() {
        val upgraded = NavDefaults.upgradeLegacyDefaults(config(legacyBar, legacyMore), miniPlayerShown = true)
        assertEquals(listOf(LIBRARY, STATISTICS, MORE), upgraded.enabled(NavComponent.BOTTOM_BAR))
        assertEquals(NavConfig.DEFAULT.enabled(NavComponent.MORE_SCREEN), upgraded.enabled(NavComponent.MORE_SCREEN))
    }

    @Test
    fun withoutTheMiniPlayerNowPlayingStaysAndSoDoesStatistics() {
        val old = config(legacyBar, legacyMore)
        assertEquals(old, NavDefaults.upgradeLegacyDefaults(old, miniPlayerShown = false))
    }

    @Test
    fun aCustomBarIsKeptAndMoreKeepsStatisticsItsOnlyPlace() {
        val old = config(listOf(LIBRARY, NOW_PLAYING, MORE), legacyMore)
        assertEquals(old, NavDefaults.upgradeLegacyDefaults(old, miniPlayerShown = true))
    }

    @Test
    fun aCustomMoreIsKept() {
        val more = listOf(SETTINGS, STATISTICS, ABOUT)
        val upgraded = NavDefaults.upgradeLegacyDefaults(config(legacyBar, more), miniPlayerShown = true)
        assertEquals(listOf(LIBRARY, STATISTICS, MORE), upgraded.enabled(NavComponent.BOTTOM_BAR))
        assertEquals(more, upgraded.enabled(NavComponent.MORE_SCREEN))
    }
}
