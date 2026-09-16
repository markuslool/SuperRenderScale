//~ if >= 1.21.11 'AutoConfig' -> 'AutoConfigClient' {
//? neoforge {

/*package dev.zelo.renderscale.platform.neoforge;

//? > 26.1
import net.minecraft.client.gui.Gui;

import dev.zelo.renderscale.Constants;
import dev.zelo.renderscale.RenderScale;
import dev.zelo.renderscale.RenderScaleHud;
import dev.zelo.renderscale.config.RenderScaleConfig;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class NeoforgeClientEntrypoint {
    // TODO: Consider using Lazy? (https://docs.neoforged.net/docs/misc/keymappings/#checking-a-keymapping)
    private static KeyMapping keyBinding;
    //? >= 1.21.9
    private static KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("renderscale", "category"));


    public NeoforgeClientEntrypoint(IEventBus eventBus, ModContainer modContainer) {
        //? >= 26.1 {
        keyBinding = new KeyMapping("key.renderscale.options", /^? < 26.2 {^/ /^GLFW.GLFW_KEY_O ^//^?} else {^/ GLFW.GLFW_KEY_U /^?}^/, category);
        //?} else {
        /^keyBinding = new KeyMapping("key.renderscale.options", GLFW.GLFW_KEY_O, /^¹? >= 1.21.9 {¹^/ category /^¹?} else {¹^/ /^¹"key.renderscale.category" ¹^//^¹?}¹^/);
         ^///?}

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, screen) -> NeoforgeClientEntrypoint.getConfigScreen(screen));

        NeoForge.EVENT_BUS.addListener(this::onWorldRenderStart);
        NeoForge.EVENT_BUS.addListener(this::onClientTickEnd);
        NeoForge.EVENT_BUS.addListener(this::onRenderGui);
    }

    public static Screen getConfigScreen(Screen parent) {
        return AutoConfigClient.getConfigScreen(RenderScaleConfig.class, parent).get();
    }

    //? >= 1.21.10 {
    public void onWorldRenderStart(RenderLevelStageEvent.AfterLevel event) {
//        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
        if (!RenderScale.getInstance().hasRun) {
            RenderScale.getInstance().resizeRenderTarget();
            RenderScale.getInstance().hasRun = true;
        }
//        }
    }
    //? } else {
    /^public void onWorldRenderStart(RenderLevelStageEvent event) {
//        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (!RenderScale.getInstance().hasRun) {
                RenderScale.getInstance().resizeRenderTarget();
                RenderScale.getInstance().hasRun = true;
            }
//        }
    }
    ^///? }

    public void onClientTickEnd(ClientTickEvent.Post event) {        if (Minecraft.getInstance().level == null && RenderScale.getInstance().hasRun) {
            RenderScale.getInstance().hasRun = false;
        }

        while (keyBinding.consumeClick()) {
            //? > 26.1 {
            Gui gui = Minecraft.getInstance().gui;
            gui.setScreen(AutoConfigClient.getConfigScreen(RenderScaleConfig.class, gui.screen()).get());
            //?} else
            //Minecraft.getInstance().setScreen(AutoConfigClient.getConfigScreen(RenderScaleConfig.class, Minecraft.getInstance().screen).get());
        }
    }

    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!RenderScaleHud.shouldShowHud(mc)) return;

        net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
        java.util.List<String> lines = RenderScaleHud.hudLines();
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

        g.fill(origin[0], origin[1], origin[0] + boxW, origin[1] + boxH, 0x80000000);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA;
            g.drawString(mc.font, lines.get(i), origin[0] + pad, origin[1] + pad + i * lineH, color, true);
        }
    }

//    public static void onDatapackReload() {
//        AutoConfigClient.getConfigHolder(RenderScaleConfig.class).load();
//    }

    //? < 1.21.11
    //@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
    //? >= 1.21.11
    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
//    @EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public class EventHandler {
//        @SubscribeEvent
//        public static void registerReloadManager(AddClientReloadListenersEvent event) {
//            event.addListener(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "load_config"),
//                    (ResourceManagerReloadListener) c -> NeoforgeClientEntrypoint.onDatapackReload());
//        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            RenderScale.init(Minecraft.getInstance());
        }

        @SubscribeEvent
        public static void registerBindings(RegisterKeyMappingsEvent event) {
            event.register(keyBinding);
        }
    }

}
*///?}
//~}
