package com.medcompanion.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class. Hilt is the only place for app-wide singletons
 * (see AGENTS.md > Code conventions).
 */
@HiltAndroidApp
class MedCompanionApp : Application()
