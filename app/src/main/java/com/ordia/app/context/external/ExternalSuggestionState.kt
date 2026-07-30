package com.ordia.app.context.external

/**
 * Estados del ciclo de vida de una sugerencia externa.
 *
 * PENDING -> DISPLAYED -> RESOLVED
 *         -> DISPLAYED -> IGNORED
 *         -> DISPLAYED -> POSTPONED -> DISPLAYED
 *         -> DISPLAYED -> EDITING -> DISPLAYED
 *         -> EXPIRED
 */
enum class ExternalSuggestionState {
    /** En cola, aún no mostrada al usuario. */
    PENDING,

    /** Actualmente visible al usuario. */
    DISPLAYED,

    /** El usuario está editando los detalles. */
    EDITING,

    /** El usuario pospuso la decisión. */
    POSTPONED,

    /** Resuelta: tarea/evento/nota creado. */
    RESOLVED,

    /** Expirada sin acción del usuario. */
    EXPIRED,

    /** El usuario ignoró explícitamente. */
    IGNORED
}
