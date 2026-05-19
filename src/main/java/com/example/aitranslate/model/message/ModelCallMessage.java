package com.example.aitranslate.model.message;

import com.example.aitranslate.constant.CapabilityTypeEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MQ message payload for a single LLM model call batch.
 *
 * Produced by TranslateCoreServiceImpl and consumed by TranslateConsumerImpl.
 * The sourceTextList is AES-256 encrypted before enqueue and decrypted
 * by the consumer before the LLM call.
 */
@Getter
@Setter
@ToString
public class ModelCallMessage implements Serializable {

    private static final long serialVersionUID = 2396445138548701776L;

    /** Determines which prompt template and parser to use. */
    private CapabilityTypeEnum capability;

    private String sourceLocale;

    private String targetLocale;

    /** Encrypted text list — decrypted by consumer before processing. */
    private List<String> sourceTextList;

    /** Arbitrary key-value context passed through the pipeline. */
    private Map<String, String> extendInfo;

    /**
     * When true, cache key is appended with a traceId to isolate
     * load test traffic from production cache slots.
     */
    private boolean loadTestMode = false;

    /** Batch sequence number within a single translate request (0, 1, 2 ...). */
    private int batchNo = 0;
}
