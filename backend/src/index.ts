import { createApp } from "./app";

/**
 * Worker entry point. The Durable-ish pieces (LLM client, KV store, clock,
 * logger) are injected by createApp so tests can substitute them.
 */
export default createApp();
