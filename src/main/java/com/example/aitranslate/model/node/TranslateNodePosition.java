package com.example.aitranslate.model.node;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class TranslateNodePosition implements Serializable {

    private static final long serialVersionUID = 3112514238626083061L;

    /** Byte offset where this node starts in the original document. */
    private int start;

    /** Byte offset where this node ends in the original document. */
    private int end;

    public TranslateNodePosition(int start, int end) {
        this.start = start;
        this.end = end;
    }
}
