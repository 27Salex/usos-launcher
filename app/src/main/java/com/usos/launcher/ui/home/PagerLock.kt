package com.usos.launcher.ui.home

/** Regla pura del bloqueo del pager (Widgets | Inicio | Noticias). Un solo sitio, con test. */
object PagerLock {
    /**
     * El deslizamiento lateral solo se bloquea por la edición de inicio, la edición de widgets (y solo mientras el
     * pager está en la página de Widgets: fuera de ella un flag olvidado nunca puede bloquearlo) o la búsqueda.
     */
    fun scrollEnabled(editing: Boolean, widgetsEditing: Boolean, searchOpen: Boolean, currentPage: Int): Boolean =
        !editing && !(widgetsEditing && currentPage == WIDGETS_PAGE) && !searchOpen

    /** La edición de widgets solo es válida en la página de Widgets. */
    fun widgetsEditingValid(widgetsEditing: Boolean, settledPage: Int): Boolean = widgetsEditing && settledPage == WIDGETS_PAGE

    /** ¿Hay que reasentar el pager? Quieto, sin gesto en curso, y a medio camino entre páginas. */
    fun needsSettle(scrolling: Boolean, offsetFraction: Float): Boolean = !scrolling && offsetFraction != 0f

    const val WIDGETS_PAGE = 0
    const val HOME_PAGE = 1
}
