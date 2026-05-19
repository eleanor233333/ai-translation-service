package com.example.aitranslate.model;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Abstract base class for translation result payloads.
 *
 * Holds the success and failure lists from a translate or grammar check call.
 * Provides multiple views of the success data:
 *   - successList       : full list of {id, content, type, cacheKey} maps
 *   - successKeyList    : just the original source texts (for set-difference logic)
 *   - successValueList  : just the translated values
 *   - successKeyValueMap: source → translated map for O(1) lookup
 *   - cachedKeySet      : subset of keys whose result came from cache (not LLM)
 *
 * The static factory method of() constructs and populates all views in one call,
 * keeping the polling loop in TranslateCoreServiceImpl clean.
 */
@Getter
@Setter
public abstract class AITranslateResultPayload {

    private List<Map<String, String>> successList;
    private List<String> successKeyList   = new ArrayList<>();
    private List<String> successValueList = new ArrayList<>();
    private Set<String> cachedKeySet      = new HashSet<>();
    private List<String> failedList;
    private Map<String, String> successKeyValueMap = new HashMap<>();

    public static <T extends AITranslateResultPayload> T of(
            Supplier<T> constructor,
            List<Map<String, String>> successList,
            List<String> failedList) {

        T result = constructor.get();
        result.setFailedList(failedList);

        if (CollectionUtils.isNotEmpty(successList)) {
            result.setSuccessList(successList);

            result.setSuccessKeyList(
                    successList.stream()
                            .map(m -> m.get("id"))
                            .toList()
            );

            result.setSuccessValueList(
                    successList.stream()
                            .map(m -> m.get("content"))
                            .toList()
            );

            // track which results came from cache vs fresh LLM call
            result.setCachedKeySet(
                    successList.stream()
                            .filter(m -> StringUtils.equals("cache", m.get("type")))
                            .map(m -> m.get("id"))
                            .collect(Collectors.toSet())
            );

            result.setSuccessKeyValueMap(
                    successList.stream()
                            .collect(Collectors.toMap(
                                    m -> m.get("id"),
                                    m -> m.get("content")
                            ))
            );
        } else {
            result.setSuccessList(new ArrayList<>());
            result.setSuccessKeyList(new ArrayList<>());
            result.setSuccessValueList(new ArrayList<>());
            result.setCachedKeySet(new HashSet<>());
            result.setSuccessKeyValueMap(new HashMap<>());
        }

        return result;
    }
}
