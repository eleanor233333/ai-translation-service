package com.example.aitranslate.message;

import com.example.aitranslate.constant.DocMessageType;
import com.example.aitranslate.impl.AesEncryptServiceImpl;
import com.example.aitranslate.impl.ModelCallServiceImpl;
import com.example.aitranslate.model.message.DocMessage;
import com.example.aitranslate.model.message.ModelCallMessage;
import com.example.aitranslate.model.message.ResultMessage;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MQ consumer — routes incoming messages to the correct handler.
 *
 * Three message types are consumed:
 *
 * 1. ModelCallMessage (translate / grammar / PlantUML batch)
 *    → decrypt payload → ModelCallServiceImpl.modelCallAndStoreCache()
 *
 * 2. DocMessage (document pipeline stage transitions)
 *    → switch on DocMessageType → route to the correct pipeline stage handler
 *    This is the driver of the 5-stage document translation state machine:
 *
 *      CREATE_DOC_TRANSLATE_TASK
 *          Parse the document into TranslateNode list.
 *          Persist each node as a sub-task row in translate_task_text.
 *          Emit one DOC_TRANSLATE_EXECUTE_TASK message per sub-task.
 *
 *      DOC_TRANSLATE_EXECUTE_TASK
 *          Translate one sub-task node via TranslateCoreService.
 *          Emit DOC_TRANSLATE_CHECK_RESULT_TASK (with delay) to verify result.
 *
 *      DOC_TRANSLATE_CHECK_RESULT_TASK
 *          Check that the translation result is stored in cache.
 *          SofaMQ auto-retries this message if result is not yet available.
 *          On success: update sub-task status to SUCCESS.
 *          Emit DOC_TRANSLATE_FINAL_CHECK_TASK.
 *
 *      DOC_TRANSLATE_FINAL_CHECK_TASK
 *          Check whether ALL sub-tasks for the parent task have reached
 *          a terminal state (SUCCESS or FAIL).
 *          If yes: emit DOC_TRANSLATE_GENERATE_RESULT_TASK.
 *          If no: wait for remaining sub-tasks.
 *
 *      DOC_TRANSLATE_GENERATE_RESULT_TASK
 *          Merge all translated nodes back into the original document.
 *          Upload result to S3/OSS.
 *          Update translate_task.url and status to SUCCESS.
 *
 * 3. ResultMessage (translation results from LLM consumer)
 *    → decrypt all encrypted fields → handleTranslateResultMsg()
 *
 * SofaMQ-specific consumer annotations (@MessageConsumer, @Messaging) are
 * omitted in this demo. In production these bind the consumer to the
 * correct topic/group and handle retry/dead-letter configuration.
 */
@Component
public class TranslateConsumerImpl {

    @Autowired
    private ModelCallServiceImpl modelCallService;

    @Autowired
    private AesEncryptServiceImpl aesEncryptService;

    // In production: injected AITranslateServiceImpl handles doc pipeline stages.
    // Omitted here as AITranslateServiceImpl is not included in this demo.

    // ── ModelCallMessage consumer ─────────────────────────────────────────────

    /**
     * Handles a batch translation / grammar check message.
     * Decrypts the source text list before passing to the model call service.
     */
    public void consumeModelCallMessage(ModelCallMessage message, int retryTimes) {
        List<String> decrypted = aesEncryptService.decryptForStringList(message.getSourceTextList());
        message.setSourceTextList(decrypted);
        modelCallService.modelCallAndStoreCache(message, retryTimes);
    }

    // ── DocMessage consumer ───────────────────────────────────────────────────

    /**
     * Routes a document pipeline message to the correct stage handler.
     *
     * The switch on DocMessageType is the central dispatch for the 5-stage
     * state machine. Each case calls the corresponding method on
     * AITranslateServiceImpl (omitted in demo — see stub comment).
     */
    public void consumeDocMessage(DocMessage message, int retryTimes) {
        String parentTaskUuid = message.getParentTaskUuid();
        Map<String, Object> extendInfo = message.getExtendInfo();

        switch (message.getMessageType()) {

            case CREATE_DOC_TRANSLATE_TASK:
                // parse document → persist sub-tasks → emit EXECUTE messages
                // aiTranslateService.handleBatchDocTranslate(parentTaskUuid, retryTimes);
                throw new UnsupportedOperationException("AITranslateServiceImpl not included in demo");

            case DOC_TRANSLATE_EXECUTE_TASK: {
                Long subTaskId = parseLong(extendInfo.get("subTaskId"));
                // translate one node sub-task
                // aiTranslateService.handleDocTranslateSubTask(parentTaskUuid, subTaskId, retryTimes);
                throw new UnsupportedOperationException("AITranslateServiceImpl not included in demo");
            }

            case DOC_TRANSLATE_CHECK_RESULT_TASK: {
                Long subTaskId = parseLong(extendInfo.get("subTaskId"));
                // verify result in cache — SofaMQ retries if not yet available
                // aiTranslateService.handleDocTranslateCheckResult(parentTaskUuid, subTaskId, retryTimes);
                throw new UnsupportedOperationException("AITranslateServiceImpl not included in demo");
            }

            case DOC_TRANSLATE_FINAL_CHECK_TASK:
                // check if all sub-tasks are done → emit GENERATE if yes
                // aiTranslateService.checkTaskFinalStatus(parentTaskUuid, retryTimes);
                throw new UnsupportedOperationException("AITranslateServiceImpl not included in demo");

            case DOC_TRANSLATE_GENERATE_RESULT_TASK:
                // merge nodes → upload to S3/OSS → update task URL
                // aiTranslateService.generateFinalTranslatedDoc(parentTaskUuid, retryTimes);
                throw new UnsupportedOperationException("AITranslateServiceImpl not included in demo");

            default:
                // unknown message type — log and drop
                break;
        }
    }

    // ── ResultMessage consumer ────────────────────────────────────────────────

    /**
     * Handles a result message carrying translation output back from the
     * LLM consumer. Decrypts all encrypted list fields before processing.
     */
    public void consumeResultMessage(ResultMessage message) {
        if (CollectionUtils.isNotEmpty(message.getSourceTextList())) {
            message.setSourceTextList(
                    aesEncryptService.decryptForStringList(message.getSourceTextList()));
        }
        if (CollectionUtils.isNotEmpty(message.getSuccessList())) {
            message.setSuccessList(
                    aesEncryptService.decryptForMapList(message.getSuccessList()));
        }
        if (CollectionUtils.isNotEmpty(message.getSuccessKeyList())) {
            message.setSuccessKeyList(
                    aesEncryptService.decryptForStringList(message.getSuccessKeyList()));
        }
        if (CollectionUtils.isNotEmpty(message.getSuccessValueList())) {
            message.setSuccessValueList(
                    aesEncryptService.decryptForStringList(message.getSuccessValueList()));
        }
        if (CollectionUtils.isNotEmpty(message.getFailedList())) {
            message.setFailedList(
                    aesEncryptService.decryptForStringList(message.getFailedList()));
        }

        // aiTranslateService.handleTranslateResultMsg(message);
        throw new UnsupportedOperationException("AITranslateServiceImpl not included in demo");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Long parseLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
