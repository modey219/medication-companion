package com.medcompanion.app.core.di

import com.medcompanion.app.BuildConfig
import com.medcompanion.app.data.assistant.AssistantBaseUrl
import com.medcompanion.app.data.assistant.HttpAssistantChatClient
import com.medcompanion.app.domain.assistant.AssistantChatClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The assistant backend client (T10).
 *
 * The base URL comes from `BuildConfig.ASSISTANT_BASE_URL`, which is filled from
 * the `assistantBaseUrl` Gradle property, so a different proxy (local
 * `wrangler dev`, staging) needs no code change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {
    @Binds
    @Singleton
    abstract fun bindAssistantChatClient(impl: HttpAssistantChatClient): AssistantChatClient

    companion object {
        @Provides
        @Singleton
        @AssistantBaseUrl
        fun provideAssistantBaseUrl(): String = BuildConfig.ASSISTANT_BASE_URL
    }
}
