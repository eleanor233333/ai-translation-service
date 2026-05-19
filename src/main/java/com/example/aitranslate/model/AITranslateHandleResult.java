package com.example.aitranslate.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The result object returned by TranslateCoreServiceImpl after a translate
 * or grammar check call completes (or times out).
 *
 * Extends AITranslateResultPayload which provides:
 *   - successList / successKeyList / successValueList / successKeyValueMap
 *   - cachedKeySet  (which results were cache hits vs fresh LLM calls)
 *   - failedList    (texts that failed or timed out)
 *
 * Usage in TranslateCoreServiceImpl:
 *   AITranslateHandleResult result =
 *       AITranslateHandleResult.of(AITranslateHandleResult::new, succeedList, failedList);
 */
@Getter
@Setter
@ToString
public class AITranslateHandleResult extends AITranslateResultPayload {
}
