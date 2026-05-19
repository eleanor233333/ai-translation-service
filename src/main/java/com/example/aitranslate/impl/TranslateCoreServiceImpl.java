package com.example.aitranslate.impl;

import com.example.aitranslate.constant.CapabilityTypeEnum;
import com.example.aitranslate.message.TranslateProducer;
import com.example.aitranslate.model.AITranslateHandleResult;
import com.example.aitranslate.model.message.ModelCallMessage;
import com.example.aitranslate.utils.TranslateUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core async translation orchestration.
 *
 * Implements a cache-first, MQ-backed, polling-based translation flow:
 *
 *   1. Check Redis cache for all input texts.
 *   2. Return immediately if all texts are cached.
 *   3. For uncached texts: partition into batches, encrypt, enqueue to SofaMQ.
 *   4. SofaMQ consumer calls LLM and writes results back to Redis (see ModelCallServiceImpl).
 *   5. Poll Redis at intervals until all pending texts are resolved or timeout is reached.
 *   6. Merge cached + newly resolved into success list; unresolved into failed list.
 *
 * Dual cache key strategy:
 *   - Successful translations stored under a normal key with a long TTL.
 *   - Failed translations stored under a FAILED key with a short TTL.
 *     On the next poll, a FAILED key hit moves the text immediately to failedList
 *     without waiting for another LLM attempt.
 */
@Component
public class TranslateCoreServiceImpl {

    @Autowired
    private CacheServiceImpl cacheService;

    @Autowired
    private TranslateProducer translateProducer;

    // Configurable via application properties
    @Value("${translate.poll.max-retries:20}")
    private int maxRetries;

    @Value("${translate.poll.interval-ms:500}")
    private int sleepIntervalMs;

    @Value("${translate.batch.translate-size:20}")
    private int translateBatchSize;

    @Value("${translate.batch.grammar-size:10}")
    private int grammarBatchSize;

    // ── Public entry points ──────────────────────────────────────────────────

    public AITranslateHandleResult processTranslate(List<String> sourceStrings,
                                                     String sourceLocale,
                                                     String targetLocale,
                                                     boolean isLoadTest) {
        return processCoreFlow(CapabilityTypeEnum.TRANSLATE,
                sourceStrings, sourceLocale, targetLocale, isLoadTest);
    }

    public AITranslateHandleResult processCheckGrammar(List<String> sourceStrings,
                                                        String sourceLocale,
                                                        boolean isLoadTest) {
        return processCoreFlow(CapabilityTypeEnum.CHECK_GRAMMAR,
                sourceStrings, sourceLocale, null, isLoadTest);
    }

    // ── Core flow ────────────────────────────────────────────────────────────

    private AITranslateHandleResult processCoreFlow(CapabilityTypeEnum capability,
                                                     List<String> rawSourceStrings,
                                                     String sourceLocale,
                                                     String targetLocale,
                                                     boolean isLoadTest) {
        // Step 1: deduplicate, trim, filter blank
        List<String> sourceStrings = formatSourceStrings(capability, rawSourceStrings);

        // Step 2: cache-first lookup
        AITranslateHandleResult firstCacheResult =
                getCachedResult(capability, sourceLocale, targetLocale, sourceStrings, isLoadTest, true);

        List<Map<String, String>> succeedResult = new ArrayList<>(firstCacheResult.getSuccessList());

        // Step 3: compute uncached list
        List<String> toBeTranslated =
                getToBeTranslatedList(sourceStrings, firstCacheResult.getSuccessKeyList());

        if (CollectionUtils.isEmpty(toBeTranslated)) {
            // all cached — return immediately
            return AITranslateHandleResult.of(AITranslateHandleResult::new, succeedResult, Collections.emptyList());
        }

        // Step 4: partition into batches and enqueue to SofaMQ
        sendBatches(capability, sourceLocale, targetLocale, toBeTranslated, isLoadTest);

        // Step 5: poll Redis until all pending texts resolve or timeout
        List<String> failedResult = new ArrayList<>();
        List<String> pending = new ArrayList<>(toBeTranslated);
        int attempts = 0;

        while (CollectionUtils.isNotEmpty(pending) && attempts < maxRetries) {
            attempts++;
            sleep(sleepIntervalMs);

            AITranslateHandleResult pollResult =
                    getCachedResult(capability, sourceLocale, targetLocale, pending, isLoadTest, false);

            succeedResult.addAll(pollResult.getSuccessList());
            failedResult.addAll(pollResult.getFailedList());

            // remove resolved (success + failed) from pending
            List<String> resolved = new ArrayList<>(pollResult.getSuccessKeyList());
            resolved.addAll(pollResult.getFailedList());
            pending = pending.stream()
                    .filter(s -> !resolved.contains(s))
                    .collect(Collectors.toList());
        }

        // Step 6: anything still pending after timeout → failed
        if (CollectionUtils.isNotEmpty(pending)) {
            failedResult.addAll(pending);
        }

        return AITranslateHandleResult.of(AITranslateHandleResult::new, succeedResult, failedResult);
    }

    // ── Cache lookup ─────────────────────────────────────────────────────────

    /**
     * Checks Redis for each text in the list.
     *
     * For each text:
     *   - If a FAILED sentinel key exists → add to failedList (skip LLM retry)
     *   - If a success key exists → add to successList
     *   - Otherwise → leave in pending (will be dispatched to MQ)
     */
    public AITranslateHandleResult getCachedResult(CapabilityTypeEnum capability,
                                                    String sourceLocale,
                                                    String targetLocale,
                                                    List<String> texts,
                                                    boolean isLoadTest,
                                                    boolean tagAsCached) {
        List<Map<String, String>> succeedResult = new ArrayList<>();
        List<String> failedResult = new ArrayList<>();

        for (String text : texts) {
            // check failed sentinel key first
            String failedKey = TranslateUtil.buildFailedCacheKey(capability, sourceLocale, targetLocale, text);
            if (cacheService.isExistingKey(failedKey)) {
                failedResult.add(text);
                continue;
            }

            // check success key
            String cacheKey = TranslateUtil.buildCacheKey(capability, sourceLocale, targetLocale, text, isLoadTest);
            String cachedValue = cacheService.getCacheValue(cacheKey);
            if (StringUtils.isNotBlank(cachedValue)) {
                Map<String, String> entry = new HashMap<>();
                entry.put("id", text);
                entry.put("content", cachedValue);
                entry.put("type", tagAsCached ? "cache" : "new");
                entry.put("cacheKey", cacheKey);
                succeedResult.add(entry);
            }
        }

        return AITranslateHandleResult.of(AITranslateHandleResult::new, succeedResult, failedResult);
    }

    // ── MQ dispatch ──────────────────────────────────────────────────────────

    private void sendBatches(CapabilityTypeEnum capability,
                              String sourceLocale,
                              String targetLocale,
                              List<String> texts,
                              boolean isLoadTest) {
        int batchSize = getBatchSize(capability);
        List<List<String>> batches = TranslateUtil.formatRequestList(texts, batchSize);

        for (int i = 0; i < batches.size(); i++) {
            ModelCallMessage message = new ModelCallMessage();
            message.setCapability(capability);
            message.setSourceLocale(sourceLocale);
            message.setTargetLocale(targetLocale);
            message.setSourceTextList(batches.get(i));
            message.setBatchNo(i);
            message.setLoadTestMode(isLoadTest);
            translateProducer.sendMessage(message);
        }
    }

    /**
     * Batch size is tuned per capability type.
     * Grammar check benefits from smaller batches for accuracy;
     * translation can handle larger batches efficiently.
     */
    private int getBatchSize(CapabilityTypeEnum capability) {
        return switch (capability) {
            case TRANSLATE -> translateBatchSize;
            case CHECK_GRAMMAR -> grammarBatchSize;
            case PLANTUML_TRANSLATE -> translateBatchSize;
            default -> 1;
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<String> formatSourceStrings(CapabilityTypeEnum capability, List<String> sourceStrings) {
        return sourceStrings.stream()
                .distinct()
                .filter(StringUtils::isNotBlank)
                .map(s -> capability == CapabilityTypeEnum.CHECK_GRAMMAR ? s.trim() : s)
                .toList();
    }

    private List<String> getToBeTranslatedList(List<String> sourceStrings, List<String> successKeyList) {
        if (CollectionUtils.isEmpty(successKeyList)) {
            return sourceStrings;
        }
        return sourceStrings.stream()
                .filter(s -> !successKeyList.contains(s))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
