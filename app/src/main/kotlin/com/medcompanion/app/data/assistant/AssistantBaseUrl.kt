package com.medcompanion.app.data.assistant

import javax.inject.Qualifier

/**
 * The assistant backend base URL, injected so tests and local builds can point
 * at another proxy. It is set with the `assistantBaseUrl` Gradle property and
 * defaults to a placeholder (see `BuildConfig.ASSISTANT_BASE_URL`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AssistantBaseUrl
