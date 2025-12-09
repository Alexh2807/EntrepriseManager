/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package de.lightplugins.economy.utils;

import com.google.common.base.Strings;
import de.lightplugins.economy.master.Main;

public class ProgressionBar {
    public String getProgressBar(double current, double max, int totalBars, char symbol, String completedColor, String notCompletedColor) {
        double percent = current / max;
        int progressBars = (int)((double)totalBars * percent);
        return Main.colorTranslation.hexTranslation(Strings.repeat(completedColor + symbol, progressBars) + Strings.repeat(notCompletedColor + symbol, totalBars - progressBars));
    }
}

