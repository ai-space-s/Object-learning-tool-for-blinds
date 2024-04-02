package com.wmakerlab.objectlearningtool;

public class TouchRegion {
    float x1, y1, x2, y2;

    TouchRegion(float x1, float y1, float x2, float y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    boolean isInRegion(float x, float y) {
        return (x >= x1 && x <= x2) && (y >= y1 && y <= y2);
    }
}