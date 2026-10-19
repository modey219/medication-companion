package com.medcompanion.app.domain.assistant

/**
 * Sends one conversation turn to the assistant backend.
 *
 * Implementations never throw for a network or server failure: they answer with
 * [AssistantResult.Failed] so the screen can show a calm message and offer a
 * retry (AGENTS.md > AI Assistant: "never crash on network errors").
 *
 * Cancellation is the one exception: a cancelled call must stay cancelled.
 */
interface AssistantChatClient {
    suspend fun send(request: AssistantRequest): AssistantResult
}
