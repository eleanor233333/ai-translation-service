package com.example.aitranslate.impl;

import com.example.aitranslate.model.node.TranslateNode;
import com.example.aitranslate.model.node.TranslateNodeContentTypeEnum;
import com.example.aitranslate.model.node.TranslateNodeTypeEnum;
import com.example.aitranslate.service.NodeParserHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles plain text and HTML prose nodes.
 *
 * parse()
 *   Enables Jsoup's position-tracking parser so every TextNode carries its
 *   exact start/end byte offset in the original HTML string.
 *   Traverses the DOM, collects non-blank TextNodes, and produces one
 *   TranslateNode per text segment with its offset recorded.
 *
 *   Zero-width characters (ZWSP, ZWNJ, ZWJ, BOM, etc.) are treated as blank
 *   and filtered out — they appear frequently in document content and would
 *   waste LLM calls if passed through.
 *
 * generate()
 *   Uses the start/end offset stored during parse() to splice the translated
 *   text directly into the original HTML string at the correct position.
 */
@Service
public class TextNodeParserHandler implements NodeParserHandler {

    @Override
    public TranslateNodeTypeEnum nodeType() {
        return TranslateNodeTypeEnum.TEXT;
    }

    @Override
    public List<TranslateNode> parse(String content) {
        return parseHtmlToTextList(content);
    }

    @Override
    public String generate(String content, TranslateNode translateNode) {
        StringBuilder sb = new StringBuilder(content);
        sb.replace(
                translateNode.getPosition().getStart(),
                translateNode.getPosition().getEnd(),
                translateNode.getTranslatedContent()
        );
        return sb.toString();
    }

    // ── private ──────────────────────────────────────────────────────────────

    /**
     * Parses HTML content into a list of translatable text nodes.
     *
     * Key: Jsoup's setTrackPosition(true) attaches source-range metadata to
     * every node, giving us the byte offsets needed for accurate write-back
     * without regex or string-matching heuristics.
     */
    private List<TranslateNode> parseHtmlToTextList(String html) {
        try {
            Parser parser = Parser.htmlParser().setTrackPosition(true);
            Document doc = Jsoup.parse(html, "", parser);

            List<TranslateNode> nodeList = new ArrayList<>();

            NodeTraversor.traverse(new NodeVisitor() {
                @Override
                public void head(Node node, int depth) {
                    if (!(node instanceof TextNode textNode)) {
                        return;
                    }

                    int start = textNode.sourceRange().startPos();
                    int end   = textNode.sourceRange().endPos();
                    String text = textNode.getWholeText();

                    if (isBlankIncludingZeroWidth(text)) {
                        return;
                    }

                    TranslateNode translateNode = new TranslateNode();
                    translateNode.setPosition(start, end);
                    translateNode.setContent(text);
                    translateNode.setContentType(TranslateNodeContentTypeEnum.PLAIN_TEXT);
                    translateNode.setType(TranslateNodeTypeEnum.TEXT);
                    nodeList.add(translateNode);
                }

                @Override
                public void tail(Node node, int depth) {
                }
            }, doc);

            return nodeList;

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Returns true if the string is blank or contains only zero-width characters.
     *
     * Standard Character.isWhitespace() does not catch zero-width characters
     * common in document content (ZWSP U+200B, ZWNJ U+200C, ZWJ U+200D,
     * Word Joiner U+2060, BOM U+FEFF). These would be sent to the LLM as
     * non-blank strings and waste translation capacity.
     */
    private boolean isBlankIncludingZeroWidth(CharSequence cs) {
        if (cs == null || cs.isEmpty()) {
            return true;
        }
        int len = cs.length();
        for (int i = 0; i < len; ) {
            int cp = Character.codePointAt(cs, i);
            if (!Character.isWhitespace(cp)
                    && cp != 0x200B   // Zero Width Space
                    && cp != 0x200C   // Zero Width Non-Joiner
                    && cp != 0x200D   // Zero Width Joiner
                    && cp != 0x2060   // Word Joiner
                    && cp != 0xFEFF)  // BOM / Zero Width No-Break Space
            {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }
}
