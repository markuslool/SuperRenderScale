package dev.zelo.renderscale;

import dev.zelo.renderscale.config.HudCorner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Loader-agnostic HUD data: formatting and visibility live here,
 * drawing itself is a tiny per-loader glue (mappings differ per loader).
 */
public final class RenderScaleHud {
    private RenderScaleHud() {
    }

    public static boolean shouldShowHud(Minecraft mc) {
        try {
            if (mc == null || mc.level == null) return false;
            // NOTE: no F1/hideGui check: the flag moved in 26.x and there is no
            // stable cross-version accessor. Revisit if the overlay annoys.
            return RenderScale.getConfig() != null && RenderScale.getConfig().hudEnabled;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> hudLines() {
        List<String> lines = new ArrayList<>(2);
        try {
            RenderScale rs = RenderScale.getInstance();
            var cfg = RenderScale.getConfig();
            int pct = Math.round(cfg.getScale() * 100.0f);
            lines.add("RenderScale " + pct + "% [" + Constants.BUILD_TAG + "]" + (cfg.dynamicScale ? " ~dyn" : ""));
            int fps = rs != null ? (int) Math.round(rs.getAverageFps()) : 0;
            String mode = rs != null ? rs.getUpscalerName() : "?";
            lines.add(fps + " FPS \u00B7 " + mode);
        } catch (Exception e) {
            lines.add("RenderScale ?");
        }
        return lines;
    }

    /** Top-left origin of the box for the given corner. Pure math, no MC types. */
    public static int[] hudOrigin(HudCorner corner, int sw, int sh, int w, int h) {
        int m = 8;
        if (corner == null) corner = HudCorner.TOP_LEFT;
        switch (corner) {
            case TOP_RIGHT:
                return new int[]{sw - m - w, m};
            case BOTTOM_LEFT:
                return new int[]{m, sh - m - h};
            case BOTTOM_RIGHT:
                return new int[]{sw - m - w, sh - m - h};
            case TOP_LEFT:
            default:
                return new int[]{m, m};
        }
    }
}
