package com.kalima.backend.assistant.service;

import java.util.function.Consumer;

/** Receives incremental LLM text deltas during streaming generation. */
@FunctionalInterface
public interface LlmStreamConsumer extends Consumer<String> {
}
