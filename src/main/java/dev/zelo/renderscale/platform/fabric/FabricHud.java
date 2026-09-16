package dev.zelo.renderscale.platform.fabric;

//? fabric {

import dev.zelo.renderscale.RenderScale;
import dev.zelo.renderscale.RenderScaleHud;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class FabricHud {
    private FabricHud() {
    }

    //? >= 26 {
    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor extractor) {
        Minecraft mc = Minecraft.getInstance();
        if (!RenderScaleHud.shouldShowHud(mc)) return;

        List<String> lines = RenderScaleHud.hudLines();
        int pad = 3;
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, mc.font.width(line));
        }
        int lineH = mc.font.lineHeight + 1;
        int boxW = w + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 - 1;

        int[] origin = RenderScaleHud.hudOrigin(
                RenderScale.getConfig().hudCorner, extractor.guiWidth(), extractor.guiHeight(), boxW, boxH);

        extractor.fill(origin[0], origin[1], origin[0] + boxW, origin[1] + boxH, 0x80000000);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA;
            extractor.text(mc.font, lines.get(i), origin[0] + pad, origin[1] + pad + i * lineH, color, true);
        }
    }
    //?} else {
    /*public static void render(net.minecraft.client.gui.GuiGraphics context) {
        Minecraft mc = Minecraft.getInstance();
        if (!RenderScaleHud.shouldShowHud(mc)) return;

        List<String> lines = RenderScaleHud.hudLines();
        int pad = 3;
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, mc.font.width(line));
        }
        int lineH = mc.font.lineHeight + 1;
        int boxW = w + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 - 1;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int[] origin = RenderScaleHud.hudOrigin(RenderScale.getConfig().hudCorner, sw, sh, boxW, boxH);

        context.fill(origin[0], origin[1], origin[0] + boxW, origin[1] + boxH, 0x80000000);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA;
            context.drawString(mc.font, lines.get(i), origin[0] + pad, origin[1] + pad + i * lineH, color, true);
        }
    }
    *///?}
}
//?}
