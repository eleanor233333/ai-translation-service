package com.example.aitranslate.constant;

/**
 * Status enum for the parent translation task (translate_task table).
 *
 * Transitions:
 *   CREATING → EXECUTING → RESULT_GENERATING → SUCCESS
 *                                            → FAILED
 */
public enum TaskStatusEnum {

    /** Task record created, document not yet parsed into sub-tasks. */
    CREATING,

    /** Sub-tasks are being translated. */
    EXECUTING,

    /** All sub-tasks complete, generating result document. */
    RESULT_GENERATING,

    FAILED,

    SUCCESS
}
