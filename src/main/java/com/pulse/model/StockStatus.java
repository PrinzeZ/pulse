package com.pulse.model;
// adding comments so yall understand , yes i changed it to proper instance names
//nothing much here
public enum StockStatus {
    GREEN,   // quantity >= threshold
    YELLOW,  // 0 < quantity < threshold
    RED;     // quantity == 0

    public static StockStatus from(int quantity, int threshold) {
        if (quantity == 0) return RED;
        if (quantity < threshold) return YELLOW;
        return GREEN;
    }
}
//basically similar to a traffic light or the phrase giving a green light
// given a quantity and a threshold, which light do we show? 