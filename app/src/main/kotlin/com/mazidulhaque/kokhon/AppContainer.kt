package com.mazidulhaque.kokhon

import android.content.Context
import java.time.Clock

/** Holds app-wide singletons. Repositories and schedulers are added here as they are built. */
class AppContainer(
    @Suppress("unused") private val context: Context,
) {
    val clock: Clock = Clock.systemDefaultZone()
}
