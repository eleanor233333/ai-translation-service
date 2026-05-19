package com.example.aitranslate.service;

import com.example.aitranslate.model.node.TranslateNode;
import com.example.aitranslate.model.node.TranslateNodeTypeEnum;

import java.util.List;

/**
 * Pluggable handler for parsing and generating a specific document node type.
 *
 * Documents contain heterogeneous content: plain text, PlantUML diagram source,
 * draw board JSON, etc. Each content type has its own structure and cannot be
 * processed uniformly. This interface defines the contract for a handler that
 * knows how to:
 *
 *   1. parse()    — extract translatable segments from raw document content,
 *                   producing a list of TranslateNode objects with position metadata.
 *
 *   2. generate() — take a TranslateNode with translatedContent populated and
 *                   write the translation back to the correct location in the
 *                   original document content.
 *
 * Adding support for a new document type requires only a new implementation of
 * this interface — the core translation pipeline does not need to change.
 */
public interface NodeParserHandler {

    /**
     * Parses raw document content into a list of translatable nodes.
     *
     * @param content raw document content (text, JSON, UML source, etc.)
     * @return ordered list of TranslateNode, each with position and contentType set
     */
    List<TranslateNode> parse(String content) throws Exception;

    /**
     * Writes the translated content of a single node back into the original document.
     *
     * @param content   original document content
     * @param node      TranslateNode with translatedContent populated
     * @return document content with the translated segment substituted in place
     */
    String generate(String content, TranslateNode node) throws Exception;

    /**
     * Returns the node type this handler is responsible for.
     * Used by the dispatch layer to route nodes to the correct handler.
     */
    TranslateNodeTypeEnum nodeType();
}
