package com.example.aitranslate.constant;

/**
 * Message types that drive the 5-stage async document translation pipeline.
 *
 * Each stage is triggered by a SofaMQ (RabbitMQ-compatible) message of the
 * corresponding type. The consumer routes incoming messages to the correct
 * handler via a switch on this enum (see TranslateConsumerImpl).
 *
 * Pipeline flow:
 *
 *   CREATE_DOC_TRANSLATE_TASK
 *       → parse document into TranslateNode list, persist sub-tasks to DB,
 *         emit one DOC_TRANSLATE_EXECUTE_TASK message per sub-task
 *
 *   DOC_TRANSLATE_EXECUTE_TASK
 *       → translate one sub-task node via TranslateCoreService,
 *         emit DOC_TRANSLATE_CHECK_RESULT_TASK
 *
 *   DOC_TRANSLATE_CHECK_RESULT_TASK
 *       → verify sub-task translation result is stored in cache;
 *         SofaMQ auto-retries if result not yet available
 *
 *   DOC_TRANSLATE_FINAL_CHECK_TASK
 *       → check whether ALL sub-tasks for the parent task have reached
 *         a terminal state (SUCCESS or FAIL);
 *         emit DOC_TRANSLATE_GENERATE_RESULT_TASK when complete
 *
 *   DOC_TRANSLATE_GENERATE_RESULT_TASK
 *       → merge all translated nodes back into the original document,
 *         upload result to S3/OSS, update task URL in DB
 */
public enum DocMessageType {

    CREATE_DOC_TRANSLATE_TASK,

    DOC_TRANSLATE_EXECUTE_TASK,

    DOC_TRANSLATE_CHECK_RESULT_TASK,

    DOC_TRANSLATE_FINAL_CHECK_TASK,

    DOC_TRANSLATE_GENERATE_RESULT_TASK
}
