package pl.dakil.music.domain.model

/** How the user removes a song from the play queue in the Now Playing screen. */
enum class QueueRemoveMode {
    /** Long-press a row to open a menu with a remove action. */
    MENU,

    /** An always-visible "x" button to the left of the cover art. */
    BUTTON,

    /** Swipe a row left or right to remove it. */
    SWIPE,
}
