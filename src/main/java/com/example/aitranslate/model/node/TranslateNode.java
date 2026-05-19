package com.example.aitranslate.model.node;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single translatable unit extracted from a document.
 *
 * A document (e.g. a .lake file or Excel sheet) is parsed into a list of
 * TranslateNode objects. Each node carries the original content, its type,
 * its position in the source document, and (after translation) the translated
 * result. The position metadata allows the generator to write translated
 * content back to the exact location in the original document.
 */
@Getter
@Setter
@ToString
public class TranslateNode implements Serializable {

    private static final long serialVersionUID = 4850336966432098029L;

    /** Original content to be translated. */
    private String content;

    /**
     * Content format — determines how the content string should be interpreted.
     * PLAIN_TEXT: a single string.
     * JSON_ARRAY_STRING: a JSON array of strings, translated line by line.
     */
    private TranslateNodeContentTypeEnum contentType;

    /** Node type — determines which NodeParserHandler processes this node. */
    private TranslateNodeTypeEnum type;

    /** Translated content, populated after the LLM call completes. */
    private String translatedContent;

    /** Start/end byte offset of this node in the original document. */
    private TranslateNodePosition position;

    /** Per-handler key-value metadata (e.g. card ID, UML preamble, cell address). */
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Sets position from start and end offsets.
     */
    public void setPosition(int start, int end) {
        this.position = new TranslateNodePosition(start, end);
    }

    public void putMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadataByKey(String key) {
        return metadata.get(key);
    }

    /**
     * Returns the list of strings that should be sent to the translation service.
     * For PLAIN_TEXT nodes this is a single-element list.
     * For JSON_ARRAY_STRING nodes each array element is translated individually.
     */
    public List<String> getSourceTextListFromContent() {
        if (this.contentType == TranslateNodeContentTypeEnum.PLAIN_TEXT) {
            return Collections.singletonList(this.content);
        } else if (this.contentType == TranslateNodeContentTypeEnum.JSON_ARRAY_STRING) {
            return com.alibaba.fastjson.JSON.parseArray(this.content, String.class);
        }
        return Collections.singletonList(this.content);
    }
}
