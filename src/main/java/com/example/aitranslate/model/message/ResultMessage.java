package com.example.aitranslate.model.message;

import com.example.aitranslate.model.AITranslateResultPayload;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MQ message carrying translation results from the consumer back to the
 * result handler.
 *
 * All list fields (sourceTextList, successList, successKeyList,
 * successValueList, failedList) are AES-256 encrypted in transit and
 * decrypted by the consumer before processing.
 */
@Getter
@Setter
@ToString
public class ResultMessage extends AITranslateResultPayload implements Serializable {

    private String status;

    private String sourceLocale;

    private String targetLocale;

    /** Encrypted source texts — decrypted by consumer. */
    private List<String> sourceTextList;

    private Map<String, Object> extendInfo = new HashMap<>();

    private boolean loadTestMode = false;
}
