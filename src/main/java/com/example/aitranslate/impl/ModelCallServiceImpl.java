package com.example.aitranslate.impl;

import com.example.aitranslate.model.message.ModelCallMessage;
import org.springframework.stereotype.Component;

/**
 * LLM model call dispatcher.
 *
 * Receives a ModelCallMessage from the SofaMQ consumer, builds a prompt
 * via PromptUtil based on the capability type (TRANSLATE / CHECK_GRAMMAR /
 * PLANTUML_TRANSLATE), calls the Qwen model gateway, parses the response
 * via TranslateUtil.parseResultContent(), and writes results back to Redis
 * via CacheServiceImpl.
 *
 * Implementation provided by team infrastructure.
 */
@Component
public class ModelCallServiceImpl {

    /**
     * Executes the LLM call for the given message and stores results in cache.
     *
     * @param message    decrypted model call message from SofaMQ consumer
     * @param retryTimes number of times this message has been retried
     */
    public void modelCallAndStoreCache(ModelCallMessage message, int retryTimes) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }
}
