package com.example.aitranslate.model.node;

public enum TranslateNodeTypeEnum {
    /** Plain text or HTML prose. Handled by TextNodeParserHandler. */
    TEXT,
    /** PlantUML diagram source. Handled by PlantUmlNodeParserHandler. */
    PLANT_UML,
    /** Draw board card JSON. Handled by DrawBoardNodeParserHandler. */
    DRAW_BOARD
}
