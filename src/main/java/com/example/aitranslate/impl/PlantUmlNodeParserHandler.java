package com.example.aitranslate.impl;

import com.alibaba.fastjson.JSONObject;
import com.example.aitranslate.model.node.TranslateNode;
import com.example.aitranslate.model.node.TranslateNodeContentTypeEnum;
import com.example.aitranslate.model.node.TranslateNodeTypeEnum;
import com.example.aitranslate.service.NodeParserHandler;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Handles PlantUML diagram nodes embedded in documents.
 *
 * Documents embed PlantUML as a card element: <card name="diagram" .../>
 * The card's data attribute contains a JSON payload with a "code" field
 * holding the raw PlantUML source.
 *
 * parse()
 *   Selects all diagram card elements using Jsoup with position tracking.
 *   Extracts the UML source from the card's JSON payload.
 *   Each card becomes one TranslateNode; the entire UML source is sent to
 *   the LLM for translation (whole-translation mode — the LLM is instructed
 *   to translate only the human-readable labels while preserving PlantUML
 *   syntax).
 *
 * generate()
 *   Writes the translated UML code back into the card's JSON payload.
 *   Removes the cached render URL ("url" field) so the diagram is re-rendered
 *   with the translated content.
 *   Optionally appends the original UML as a comment block after @enduml
 *   for review purposes (controlled by config flag).
 */
@Service
public class PlantUmlNodeParserHandler implements NodeParserHandler {

    private static final String PLANTUML_SELECTOR = "card[name=diagram]";
    private static final String P_UML             = "puml";

    // set to true to append original UML as a comment after translation
    private boolean appendOriginalPlantUML = false;

    @Override
    public TranslateNodeTypeEnum nodeType() {
        return TranslateNodeTypeEnum.PLANT_UML;
    }

    @Override
    public List<TranslateNode> parse(String content) {
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        Document doc = Jsoup.parse(content, "", parser);

        return doc.select(PLANTUML_SELECTOR)
                .stream()
                .map(this::parsePlantUMLCard)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public String generate(String content, TranslateNode translateNode) {
        String originalUmlCode   = translateNode.getContent();
        String translatedUmlCode = translateNode.getTranslatedContent();

        if (StringUtils.isBlank(translatedUmlCode)) {
            return content;
        }

        // optionally append original UML as a comment for review
        if (appendOriginalPlantUML) {
            String appendCode = StringUtils.removeStartIgnoreCase(originalUmlCode, "@startuml");
            appendCode = StringUtils.removeEndIgnoreCase(appendCode.trim(), "@enduml");
            appendCode = String.format("/'%nOriginal PlantUML Code:%n%n%s%n'/", appendCode);
            translatedUmlCode = translatedUmlCode + "\n\n" + appendCode;
        }

        // locate the card element at the position recorded during parse()
        Document document = getFirstCardDoc(content, translateNode);
        Element cardElement = document.selectFirst(PLANTUML_SELECTOR);
        if (cardElement == null) {
            return content;
        }

        JSONObject cardJson = parseCardJsonFromElement(cardElement);
        if (cardJson == null) {
            return content;
        }

        // update UML code and remove cached render URL to force re-render
        cardJson.put("code", translatedUmlCode);
        cardJson.remove("url");

        encodeCardDataJson(cardElement, cardJson);

        StringBuilder sb = new StringBuilder(content);
        sb.replace(
                translateNode.getPosition().getStart(),
                translateNode.getPosition().getEnd(),
                document.html()
        );
        return sb.toString();
    }

    // ── private ──────────────────────────────────────────────────────────────

    private TranslateNode parsePlantUMLCard(Element cardElement) {
        try {
            TranslateNode node = new TranslateNode();
            node.setType(TranslateNodeTypeEnum.PLANT_UML);
            node.setPosition(
                    cardElement.sourceRange().startPos(),
                    cardElement.endSourceRange().endPos()
            );

            JSONObject cardJson = parseCardJsonFromElement(cardElement);
            if (cardJson == null) {
                return null;
            }

            String cardType = cardJson.getString("type");
            String umlCode  = cardJson.getString("code");

            // only process puml-type diagram cards with non-blank UML source
            if (!Objects.equals(P_UML, cardType) || StringUtils.isBlank(umlCode)) {
                return null;
            }

            node.setContent(umlCode);
            node.setContentType(TranslateNodeContentTypeEnum.PLAIN_TEXT);
            return node;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts and URL-decodes the JSON payload from a card element's data attribute.
     */
    private JSONObject parseCardJsonFromElement(Element cardElement) {
        try {
            String dataAttr = cardElement.attr("value");
            if (StringUtils.isBlank(dataAttr)) {
                return null;
            }
            String decoded = java.net.URLDecoder.decode(dataAttr, java.nio.charset.StandardCharsets.UTF_8);
            return com.alibaba.fastjson.JSON.parseObject(decoded);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * URL-encodes the updated JSON and writes it back to the card element's data attribute.
     */
    private void encodeCardDataJson(Element cardElement, JSONObject cardJson) {
        try {
            String encoded = java.net.URLEncoder.encode(
                    com.alibaba.fastjson.JSON.toJSONString(cardJson),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            cardElement.attr("value", encoded);
        } catch (Exception ignored) {
        }
    }

    /**
     * Parses the document fragment containing only the card at the node's position.
     */
    private Document getFirstCardDoc(String content, TranslateNode node) {
        String fragment = content.substring(
                node.getPosition().getStart(),
                node.getPosition().getEnd()
        );
        Parser parser = Parser.htmlParser();
        return Jsoup.parse(fragment, "", parser);
    }
}
