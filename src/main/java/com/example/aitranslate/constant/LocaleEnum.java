package com.example.aitranslate.constant;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Supported locales for translation and grammar checking.
 *
 * Each locale carries:
 *   code        — the locale string used in API requests and cache keys
 *   glossaryKey — the short key used to look up glossary terms for this locale
 *   regex       — a pattern used to detect whether a text segment is already
 *                 in this locale (used to skip unnecessary translation calls)
 *
 * Note on regex: for locales sharing the Latin alphabet (e.g. MS_MY),
 * the regex is set to "$^" (never matches) to disable auto-detection,
 * since Latin-script locales cannot be reliably distinguished from English
 * by character range alone.
 */
@Getter
public enum LocaleEnum {

    EN_US("en_US", "en",
            "^[A-Za-z0-9.,!?;:&^%$#@'\"()\\[\\]{}<>\\-_/\\\\ ]+$"),

    MS_MY("ms_MY", "my",
            "$^"),  // disabled: Malay uses Latin script, conflicts with EN detection

    ZH_CN("zh_CN", "cn",
            "^[\u4e00-\u9fa5]+$"),

    VI_VN("vi_VN", "vn",
            "^[\u0041-\u005A\u0061-\u007A\u00C0-\u00FF\u0100-\u017F\u0180-\u024F]+$");

    private final String code;
    private final String glossaryKey;
    private final String regex;

    LocaleEnum(String code, String glossaryKey, String regex) {
        this.code = code;
        this.glossaryKey = glossaryKey;
        this.regex = regex;
    }

    public static LocaleEnum getByCode(String code) {
        for (LocaleEnum value : LocaleEnum.values()) {
            if (StringUtils.equals(value.getCode(), code)) {
                return value;
            }
        }
        return null;
    }
}
