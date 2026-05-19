package com.example.aitranslate.constant;

/**
 * Status enum for individual translation sub-tasks (translate_task_text table).
 *
 * Each sub-task corresponds to one TranslateNode extracted from the document.
 *
 * Transitions:
 *   CREATING → EXECUTING → SUCCESS
 *                        → PARTIAL_SUCCESS  (some segments failed)
 *                        → FAIL
 */
public enum TaskTextStatusEnum {

    CREATING,

    EXECUTING,

    SUCCESS,

    /** Translation completed but some text segments within the node failed. */
    PARTIAL_SUCCESS,

    FAIL
}
