package com.example.aitranslate.model.message;

import com.example.aitranslate.constant.DocMessageType;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * MQ message payload for the 5-stage document translation pipeline.
 *
 * Each stage transition emits a DocMessage with the appropriate messageType.
 * The consumer routes the message to the correct handler based on messageType
 * (see TranslateConsumerImpl.consumeDocMessage).
 *
 * extendInfo carries stage-specific parameters, e.g.:
 *   DOC_TRANSLATE_EXECUTE_TASK    → { "subTaskId": 123 }
 *   DOC_TRANSLATE_CHECK_RESULT_TASK → { "subTaskId": 123 }
 */
@Data
@ToString
public class DocMessage implements Serializable {

    /** Which stage of the pipeline this message triggers. */
    private DocMessageType messageType;

    /** Database ID of the parent translate_task record. */
    private Long parentTaskId;

    /** Public UUID of the parent task (used as the primary lookup key). */
    private String parentTaskUuid;

    /** Stage-specific parameters. */
    private Map<String, Object> extendInfo = new HashMap<>();

    private boolean loadTestMode = false;

    private int batchNo = 0;

    public void addExtendInfo(String key, Object value) {
        if (extendInfo == null) {
            extendInfo = new HashMap<>();
        }
        extendInfo.put(key, value);
    }
}
