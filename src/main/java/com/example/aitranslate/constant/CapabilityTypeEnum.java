package com.example.aitranslate.constant;

/**
 * LLM capability types supported by the translation service.
 *
 * Used to:
 *   1. Route MQ messages to the correct prompt template in ModelCallServiceImpl
 *   2. Build capability-specific cache keys in TranslateUtil.buildCacheKey()
 *   3. Select the correct batch size in TranslateCoreServiceImpl.getBatchSize()
 */
public enum CapabilityTypeEnum {

    /** Translate text from source locale to target locale. */
    TRANSLATE,

    /** Check and correct grammar in source text. */
    CHECK_GRAMMAR,

    /**
     * Translate PlantUML diagram source.
     * Sends the full UML to the LLM with an instruction to translate
     * human-readable labels while preserving PlantUML syntax.
     */
    PLANTUML_TRANSLATE
}
