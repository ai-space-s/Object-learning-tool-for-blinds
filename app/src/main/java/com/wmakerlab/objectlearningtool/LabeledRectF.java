package com.wmakerlab.objectlearningtool;

import android.graphics.RectF;

public class LabeledRectF extends RectF {
    private String label;

    public LabeledRectF(float left, float top, float right, float bottom, String label) {
        super(left, top, right, bottom);
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
