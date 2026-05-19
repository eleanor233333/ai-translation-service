package com.example.aitranslate.utils;

import com.example.aitranslate.constant.CapabilityTypeEnum;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for cache key construction, result parsing, and batch formatting.
 */
public class TranslateUtil {

    private static final String KEY_SEPARATOR = "@";
    private static final String FAILED        = "FAILED";

    /**
     * Partitions a flat string list into sub-batches of the given size.
     * Used before MQ dispatch to control per-message payload size.
     */
    public static List<List<String>> formatRequestList(List<String> strings, int batchSize) {
        return Lists.partition(strings, batchSize);
    }

    /**
     * Builds a deterministic cache key for a translation or grammar check result.
     *
     * Key structure: capability@sourceLocale@targetLocale@SHA-256(text)
     *
     * SHA-256 is used so that the key length is bounded regardless of input size,
     * and so that the original text is not stored in the key.
     */
    public static String buildCacheKey(CapabilityTypeEnum capability,
                                        String sourceLocale,
                                        String targetLocale,
                                        String text) {
        return Joiner.on(KEY_SEPARATOR)
                .useForNull("NULL")
                .join(capability.name(), sourceLocale, targetLocale, DigestUtils.sha256Hex(text));
    }

    /**
     * Builds a cache key for load test traffic.
     * Appends a traceId so load test results are stored in isolated cache slots
     * and do not pollute the production cache.
     */
    public static String buildCacheKey(CapabilityTypeEnum capability,
                                        String sourceLocale,
                                        String targetLocale,
                                        String text,
                                        boolean isLoadTest) {
        String key = buildCacheKey(capability, sourceLocale, targetLocale, text);
        if (isLoadTest) {
            // append a unique identifier to isolate load test cache slots
            key = Joiner.on(KEY_SEPARATOR).useForNull("NULL")
                    .join(key, "LOAD_TEST", System.nanoTime());
        }
        return key;
    }

    /**
     * Builds the failed-sentinel cache key for a text.
     * Stored with a short TTL when the LLM call fails, so subsequent requests
     * for the same text are immediately marked as failed without retrying.
     */
    public static String buildFailedCacheKey(CapabilityTypeEnum capability,
                                              String sourceLocale,
                                              String targetLocale,
                                              String text) {
        String normalKey = buildCacheKey(capability, sourceLocale, targetLocale, text);
        return Joiner.on(KEY_SEPARATOR).useForNull("NULL").join(normalKey, FAILED);
    }

    /**
     * Parses the raw string returned by the LLM into a list of translated segments.
     *
     * LLMs frequently wrap JSON output in markdown code fences (```json ... ```)
     * even when instructed not to. This method strips those fences before parsing.
     * Returns an empty list (not an exception) if parsing fails, so the caller
     * can treat the result as a failed translation.
     */
    public static List<String> parseResultContent(String resultContent) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isBlank(resultContent)) {
            return result;
        }

        // strip markdown fences if present
        if (resultContent.startsWith("```")) {
            resultContent = resultContent
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
        }

        try {
            List<String> segments = com.alibaba.fastjson.JSON.parseArray(resultContent, String.class);
            if (segments == null) {
                return result;
            }
            for (String segment : segments) {
                if (StringUtils.isNotBlank(segment) && !"\n".equals(segment)) {
                    result.add(segment);
                }
            }
        } catch (Exception e) {
            return result;
        }

        return result;
    }

    /**
     * Returns true if the given string contains HTML tags.
     */
    public static boolean isValidHTML(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        return !Jsoup.parse(html).text().equals(html);
    }
}
