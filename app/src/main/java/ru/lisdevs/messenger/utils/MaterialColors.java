package ru.lisdevs.messenger.utils;

import android.app.WallpaperColors;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import ru.lisdevs.messenger.R;

public class MaterialColors {
    private final int primaryColor;
    private final int secondaryColor;
    private final int tertiaryColor;
    private final int surfaceColor;
    private final int onSurfaceColor;

    @RequiresApi(api = Build.VERSION_CODES.S)
    public MaterialColors(WallpaperColors wallpaperColors) {
        this.primaryColor = wallpaperColors.getPrimaryColor().toArgb();
        this.secondaryColor = wallpaperColors.getSecondaryColor() != null ?
                wallpaperColors.getSecondaryColor().toArgb() : adjustColor(primaryColor, 0.8f);
        this.tertiaryColor = wallpaperColors.getTertiaryColor() != null ?
                wallpaperColors.getTertiaryColor().toArgb() : adjustColor(primaryColor, 0.6f);

        this.surfaceColor = calculateSurfaceColor(primaryColor);
        this.onSurfaceColor = calculateOnSurfaceColor(surfaceColor);
    }

    public MaterialColors(int primary, int secondary, int tertiary) {
        this.primaryColor = primary;
        this.secondaryColor = secondary;
        this.tertiaryColor = tertiary;
        this.surfaceColor = calculateSurfaceColor(primary);
        this.onSurfaceColor = calculateOnSurfaceColor(surfaceColor);
    }

    private int calculateSurfaceColor(int primary) {
        float[] hsv = new float[3];
        Color.colorToHSV(primary, hsv);
        hsv[2] = Math.min(hsv[2] * 0.15f, 0.15f); // Darken for surface
        return Color.HSVToColor(hsv);
    }

    private int calculateOnSurfaceColor(int surface) {
        return ColorUtils.calculateContrast(surface, Color.WHITE) > 4.5f ?
                Color.WHITE : Color.BLACK;
    }

    private int adjustColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] *= factor; // Adjust saturation
        hsv[2] = Math.min(hsv[2] * 1.2f, 1.0f); // Adjust brightness
        return Color.HSVToColor(hsv);
    }

    // Getters
    public int getPrimaryColor() { return primaryColor; }
    public int getSecondaryColor() { return secondaryColor; }
    public int getTertiaryColor() { return tertiaryColor; }
    public int getSurfaceColor() { return surfaceColor; }
    public int getOnSurfaceColor() { return onSurfaceColor; }
}