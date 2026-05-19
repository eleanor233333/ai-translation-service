-- AI Translation Service — Database Schema

-- Main translation task table
-- One row per document translation request (Yuque .lake or Excel .xlsx).
-- Status transitions: PENDING → PROCESSING → DONE | FAILED
CREATE TABLE translate_task (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    uuid        VARCHAR(64)     NOT NULL COMMENT 'public task identifier returned to caller',
    status      VARCHAR(64)     NOT NULL COMMENT 'PENDING | PROCESSING | DONE | FAILED',
    url         TEXT                     COMMENT 'download URL for translated document, populated on DONE',
    content     TEXT                     COMMENT 'original document content',
    user_id     VARCHAR(64)              COMMENT 'requesting user identifier',
    gmt_create  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY  uk_uuid          (uuid),
    INDEX       idx_uuid_user_id (uuid, user_id)  COMMENT 'used for task ownership validation and URL retrieval'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Sub-task table
-- One row per translatable text node within a document.
-- A document is split into N nodes by the NodeParserHandler;
-- each node becomes a row here and is translated independently via SofaMQ.
-- Status transitions: PENDING → SUCCESS | FAILED
-- FINAL_CHECK stage queries all rows for a task_id to determine if
-- the parent task is complete.
CREATE TABLE translate_task_text (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    task_id          VARCHAR(64) NOT NULL COMMENT 'uuid of the parent translate_task',
    status           VARCHAR(64) NOT NULL COMMENT 'PENDING | SUCCESS | FAILED',
    original_text    TEXT                 COMMENT 'source text extracted from the document node',
    translated_text  TEXT                 COMMENT 'LLM translation result, populated on SUCCESS',
    gmt_create       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_task_id (task_id)           COMMENT 'fetch all sub-tasks for a parent task (FINAL_CHECK stage)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
