package com.example.aitranslate.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.alibaba.fastjson.TypeReference;
import com.example.aitranslate.model.node.TranslateNode;
import com.example.aitranslate.model.node.TranslateNodeContentTypeEnum;
import com.example.aitranslate.model.node.TranslateNodeTypeEnum;
import com.example.aitranslate.service.NodeParserHandler;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles draw board card nodes embedded in documents.
 *
 * Draw board content is stored as a card element: <card name="board" .../>
 * The card's value attribute contains a URL-encoded JSON payload whose
 * "diagramData.body" path holds an array of shape objects. Each shape has
 * an "html" field containing the display text wrapped in HTML, and a
 * recursive "children" array for nested shapes.
 *
 * parse()
 *   Selects all board card elements using Jsoup with position tracking.
 *   For each card, walks the shape tree (including children) and extracts
 *   the visible text from each shape's "html" field.
 *   Deduplicates extracted texts and stores them as a JSON array string
 *   in the node's content (JSON_ARRAY_STRING type — each element translated
 *   individually).
 *   The original shape-id-to-text mapping is stored in node metadata so
 *   generate() can write translated text back to the correct shapes.
 *
 * generate()
 *   Rebuilds the shape-id → translated-text dictionary by aligning the
 *   source text list with the translated text list (positional mapping).
 *   Walks the shape tree again, finds each shape by ID, and replaces the
 *   text content inside its "html" field while preserving HTML structure.
 *   Writes the updated JSON back into the card element and splices the
 *   result back into the original document at the recorded position.
 */
@Service
public class DrawBoardNodeParserHandler implements NodeParserHandler {

    private static final String CARD_SELECTOR        = "card[name=board]";
    private static final String HTML                 = "html";
    private static final String ID                   = "id";
    private static final String CHILDREN             = "children";
    private static final String JSON_DIAGRAM_DATA_BODY = "$.diagramData.body";
    private static final String ORIGINAL_CONTENT_KEY = "originalContent";

    @Override
    public TranslateNodeTypeEnum nodeType() {
        return TranslateNodeTypeEnum.DRAW_BOARD;
    }

    @Override
    public List<TranslateNode> parse(String content) throws Exception {
        Parser parser = Parser.xmlParser().setTrackPosition(true);
        Document doc = Jsoup.parse(content, "", parser);

        return doc.select(CARD_SELECTOR)
                .stream()
                .map(this::parseDrawBoardCard)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public String generate(String content, TranslateNode translateNode) {
        return assembleDrawBoardContent(content, translateNode);
    }

    // ── parse helpers ─────────────────────────────────────────────────────────

    private TranslateNode parseDrawBoardCard(Element cardElement) {
        try {
            TranslateNode node = new TranslateNode();
            node.setType(TranslateNodeTypeEnum.DRAW_BOARD);
            node.setPosition(
                    cardElement.sourceRange().startPos(),
                    cardElement.endSourceRange().endPos()
            );

            JSONObject boardJson = parseCardJsonFromElement(cardElement);
            if (boardJson == null) {
                return null;
            }

            // walk the shape tree rooted at diagramData.body
            List<Map<String, String>> shapeIdToText = new ArrayList<>();
            if (JSONPath.eval(boardJson, JSON_DIAGRAM_DATA_BODY) instanceof JSONArray bodyArray) {
                shapeIdToText = parseDrawBoardShapes(bodyArray);
            }

            if (CollectionUtils.isEmpty(shapeIdToText)) {
                return null;
            }

            // deduplicate text values for translation
            List<String> sourceTexts = shapeIdToText.stream()
                    .flatMap(map -> map.values().stream())
                    .filter(v -> v != null && !v.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            // content = JSON array of strings (translated individually)
            node.setContent(JSON.toJSONString(sourceTexts));
            node.setContentType(TranslateNodeContentTypeEnum.JSON_ARRAY_STRING);

            // store shape-id mapping in metadata for write-back
            node.putMetadata(ORIGINAL_CONTENT_KEY, JSON.toJSONString(shapeIdToText));

            return node;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Walks the shape array recursively. For each shape:
     * - extracts visible text from the "html" field (via Jsoup text())
     * - recurses into "children" arrays
     * Returns a list of {shapeId → text} maps preserving order.
     */
    private List<Map<String, String>> parseDrawBoardShapes(JSONArray bodyArray) {
        List<Map<String, String>> result = new ArrayList<>();

        for (int i = 0; i < bodyArray.size(); i++) {
            JSONObject shape = bodyArray.getJSONObject(i);

            if (shape.get(HTML) instanceof String htmlContent) {
                String text = Jsoup.parse(htmlContent).text();
                if (StringUtils.isNotBlank(text)) {
                    Map<String, String> entry = new HashMap<>();
                    entry.put(shape.getString(ID), text);
                    result.add(entry);
                }
            }

            if (shape.get(CHILDREN) instanceof JSONArray children) {
                result.addAll(parseDrawBoardShapes(children));
            }
        }

        return result;
    }

    // ── generate helpers ──────────────────────────────────────────────────────

    private String assembleDrawBoardContent(String content, TranslateNode translateNode) {
        // restore shape-id → originalText mapping from metadata
        List<Map<String, String>> shapeIdToText = JSON.parseObject(
                translateNode.getMetadataByKey(ORIGINAL_CONTENT_KEY),
                new TypeReference<>() {}
        );

        // build shape-id → {id: originalText} intermediate map
        Map<String, Map<String, String>> shapeMap = shapeIdToText.stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Map<String, String> v = new HashMap<>();
                            v.put(ID, entry.getValue());
                            return v;
                        },
                        (a, b) -> b
                ));

        List<String> sourceTexts     = JSON.parseArray(translateNode.getContent(), String.class);
        List<String> translatedTexts = JSON.parseArray(translateNode.getTranslatedContent(), String.class);

        Map<String, Map<String, String>> translatedDict =
                buildTranslateDict(shapeMap, sourceTexts, translatedTexts);
        if (translatedDict == null) {
            return content;
        }

        // parse document fragment at the recorded position
        Document document = getFirstCardDoc(content, translateNode);
        Element cardElement = document.selectFirst(CARD_SELECTOR);
        if (cardElement == null) {
            return content;
        }

        JSONObject boardJson = parseCardJsonFromElement(cardElement);
        if (boardJson == null) {
            return content;
        }

        if (JSONPath.eval(boardJson, JSON_DIAGRAM_DATA_BODY) instanceof JSONArray bodyArray) {
            updateTranslatedText(bodyArray, translatedDict);
        }

        encodeCardDataJson(cardElement, boardJson);

        StringBuilder sb = new StringBuilder(content);
        sb.replace(
                translateNode.getPosition().getStart(),
                translateNode.getPosition().getEnd(),
                document.html()
        );
        return sb.toString();
    }

    /**
     * Walks the shape tree and replaces each shape's HTML text content
     * with the translated text from translatedDict, keyed by shape ID.
     * Preserves the surrounding HTML structure of the shape's html field.
     */
    private void updateTranslatedText(JSONArray bodyArray,
                                       Map<String, Map<String, String>> translatedDict) {
        for (int i = 0; i < bodyArray.size(); i++) {
            JSONObject shape = bodyArray.getJSONObject(i);

            if (shape.get(HTML) instanceof String htmlContent) {
                String shapeId = shape.getString(ID);
                if (StringUtils.isBlank(shapeId)) {
                    continue;
                }

                Map<String, String> dictEntry = translatedDict.get(shapeId);
                if (dictEntry == null) {
                    continue;
                }

                String translatedText = dictEntry.get("content");
                if (StringUtils.isBlank(translatedText)) {
                    continue;
                }

                // replace text nodes inside the shape's HTML, preserving structure
                Parser parser = Parser.xmlParser();
                Document htmlDoc = Jsoup.parse(htmlContent, "", parser);
                htmlDoc.outputSettings().prettyPrint(false);

                List<TextNode> textNodes = htmlDoc.select("*").textNodes();
                if (!textNodes.isEmpty()) {
                    textNodes.forEach(tn -> tn.text(""));
                    textNodes.get(0).text(translatedText);
                    shape.put(HTML, htmlDoc.html());
                }
            }

            if (shape.get(CHILDREN) instanceof JSONArray children) {
                updateTranslatedText(children, translatedDict);
            }
        }
    }

    /**
     * Aligns source and translated text lists positionally to build a
     * shape-id → {id: originalText, content: translatedText} dictionary.
     * Returns null if the list sizes differ (translation was partial/failed).
     */
    private Map<String, Map<String, String>> buildTranslateDict(
            Map<String, Map<String, String>> shapeMap,
            List<String> sourceTexts,
            List<String> translatedTexts) {

        if (CollectionUtils.size(sourceTexts) != CollectionUtils.size(translatedTexts)) {
            return null;
        }

        Map<String, String> sourceToTranslated = new HashMap<>();
        for (int i = 0; i < sourceTexts.size(); i++) {
            sourceToTranslated.put(sourceTexts.get(i), translatedTexts.get(i));
        }

        shapeMap.forEach((shapeId, entry) -> {
            String original   = entry.get(ID);
            String translated = sourceToTranslated.get(original);
            entry.put("content", translated);
        });

        return shapeMap;
    }

    // ── card element helpers ──────────────────────────────────────────────────

    private JSONObject parseCardJsonFromElement(Element cardElement) {
        try {
            String dataAttr = cardElement.attr("value");
            if (StringUtils.isBlank(dataAttr)) {
                return null;
            }
            String decoded = java.net.URLDecoder.decode(dataAttr, java.nio.charset.StandardCharsets.UTF_8);
            return JSON.parseObject(decoded);
        } catch (Exception e) {
            return null;
        }
    }

    private void encodeCardDataJson(Element cardElement, JSONObject cardJson) {
        try {
            String encoded = java.net.URLEncoder.encode(
                    JSON.toJSONString(cardJson),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            cardElement.attr("value", encoded);
        } catch (Exception ignored) {
        }
    }

    private Document getFirstCardDoc(String content, TranslateNode node) {
        String fragment = content.substring(
                node.getPosition().getStart(),
                node.getPosition().getEnd()
        );
        return Jsoup.parse(fragment, "", Parser.htmlParser());
    }
}
