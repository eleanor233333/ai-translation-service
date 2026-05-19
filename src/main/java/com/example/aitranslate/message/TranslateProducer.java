package com.example.aitranslate.message;

import com.example.aitranslate.model.message.DocMessage;
import com.example.aitranslate.model.message.ModelCallMessage;
import com.example.aitranslate.model.message.ResultMessage;

/**
 * MQ producer interface for the translation pipeline.
 *
 * Three message channels:
 *   ModelCallMessage — text translation / grammar check batches → LLM consumer
 *   DocMessage       — document pipeline stage transitions
 *   ResultMessage    — translation results written back after LLM call
 */
public interface TranslateProducer {

    void sendMessage(ModelCallMessage message);

    void sendMessage(DocMessage message);

    void sendMessage(DocMessage message, int delayMilliseconds);

    void sendMessage(ResultMessage message);
}
