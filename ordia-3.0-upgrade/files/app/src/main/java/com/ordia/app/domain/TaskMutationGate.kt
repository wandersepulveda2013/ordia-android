package com.ordia.app.domain

import kotlinx.coroutines.sync.Mutex

/** Serializes task state transitions shared by the UI and notification actions. */
object TaskMutationGate {
    val mutex = Mutex()
}
