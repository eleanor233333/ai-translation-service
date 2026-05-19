package com.example.aitranslate.message;

import com.example.aitranslate.impl.AesEncryptServiceImpl;
import com.example.aitranslate.model.message.DocMessage;
import com.example.aitranslate.model.message.ModelCallMessage;
import com.example.aitranslate.model.message.ResultMessage;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MQ producer implementation.
 *
 * All sensitive text content is AES-256 encrypted before being placed
 * on the queue. This ensures that message payloads are opaque to any
 * intermediate broker infrastructure.
 *
 * SofaMQ-specific producer annotations (@MessageProducer, @Messaging) are
 * omitted in this demo — in production these bind the producer to the
 * correct topic/group configuration in the broker.
 *
 * Delay messages (sendMessage(DocMessage, int delayMs)) are used in the
 * CHECK_RESULT stage to give the LLM consumer time to write results to
 * cache before the checker fires.
 */
@Component
public class TranslateProducerImpl implements TranslateProducer {

    @Autowired
    private AesEncryptServiceImpl aesEncryptService;

    // In production: injected SofaMQ Producer beans per topic/group.
    // Omitted here — replace with your MQ client of choice (RabbitMQ, SQS, etc.)

    @Override
    public void sendMessage(ModelCallMessage message) {
        // encrypt source texts before enqueue
        List<String> encrypted = aesEncryptService.encryptForStringList(message.getSourceTextList());
        message.setSourceTextList(encrypted);

        // enqueue to model-call topic
        // aiTranslateMessageProducer.send(message);
        throw new UnsupportedOperationException("MQ producer not wired in demo — replace with your broker client");
    }

    @Override
    public void sendMessage(DocMessage message) {
        sendMessage(message, 0);
    }

    @Override
    public void sendMessage(DocMessage message, int delayMilliseconds) {
        // delayMilliseconds > 0: schedule delayed delivery (used in CHECK_RESULT stage)
        // aiTranslateDocMessageProducer.send(message, delayMilliseconds);
        throw new UnsupportedOperationException("MQ producer not wired in demo — replace with your broker client");
    }

    @Override
    public void sendMessage(ResultMessage message) {
        // encrypt all result fields before enqueue
        if (CollectionUtils.isNotEmpty(message.getSourceTextList())) {
            message.setSourceTextList(
                    aesEncryptService.encryptForStringList(message.getSourceTextList()));
        }
        if (CollectionUtils.isNotEmpty(message.getSuccessList())) {
            message.setSuccessList(
                    aesEncryptService.encryptForMapList(message.getSuccessList()));
        }
        if (CollectionUtils.isNotEmpty(message.getSuccessKeyList())) {
            message.setSuccessKeyList(
                    aesEncryptService.encryptForStringList(message.getSuccessKeyList()));
        }
        if (CollectionUtils.isNotEmpty(message.getSuccessValueList())) {
            message.setSuccessValueList(
                    aesEncryptService.encryptForStringList(message.getSuccessValueList()));
        }
        if (CollectionUtils.isNotEmpty(message.getFailedList())) {
            message.setFailedList(
                    aesEncryptService.encryptForStringList(message.getFailedList()));
        }

        // aiTranslateResultMessageProducer.send(message);
        throw new UnsupportedOperationException("MQ producer not wired in demo — replace with your broker client");
    }
}
