package dev.zelo.renderscale.config;

import dev.zelo.renderscale.RenderScale;
import dev.zelo.renderscale.compat.iris.IrisCompatibility;
import dev.zelo.renderscale.platform.Platform;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

//? iris
import net.irisshaders.iris.api.v0.IrisApi;

@Config(name = "renderscale")
public class RenderScaleConfig implements ConfigData {
    public float scale = 1.0f;
    public boolean forceLinear = false;

    //? >= 1.21.11 {
    @ConfigEntry.Gui.Tooltip()
    public boolean fsr = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean fsrcnnx = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean miniespcn = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean miniespcnq = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean temporal = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean fsr2 = false;

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 100, min = 0)
    public int fsr2Sharpness = 80;

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 100, min = 0)
    public int fsr2Reactive = 50;

    @ConfigEntry.Gui.Tooltip()
    public boolean fsr2NoHistory = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean temporalWithShaders = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean temporalEasu = true;

    //? >= 1.21.11 {
    @ConfigEntry.Gui.Tooltip()
    public boolean temporalReproj = true;

    @ConfigEntry.Gui.Tooltip()
    public boolean temporalReprojDepth = false;

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 2, min = 0)
    public int temporalDebug = 0;
    //?}

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 100, min = 0)
    public int temporalSharpness = 20;

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 100, min = 0)
    public int fsrSharpness = 80;
    //?}

    //? >= 1.21.11 {
    /** RCAS attenuation 0 (sharpest) .. 1 (softest). Slider is intuitive: higher = sharper. */
    public float getRcasAttenuation() {
        return (100 - Math.clamp(fsrSharpness, 0, 100)) / 100.0f;
    }

    /** Same mapping for the FSR2 final RCAS pass. */
    public float getFsr2RcasAttenuation() {
        return (100 - Math.clamp(fsr2Sharpness, 0, 100)) / 100.0f;
    }

    /** Reactive strength 0 (off) .. 1 (drop history fast on emissive/flicker pixels). */
    public float getFsr2ReactiveStrength() {
        return Math.clamp(fsr2Reactive, 0, 100) / 100.0f;
    }
    //?}

    @ConfigEntry.Gui.Tooltip()
    public boolean dynamicScale = false;

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 120, min = 20)
    public int targetFps = 60;

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 100, min = 25)
    public int dynMinScale = 50;

    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.BoundedDiscrete(max = 200, min = 50)
    public int dynMaxScale = 100;

    @ConfigEntry.Gui.Tooltip()
    public boolean hudEnabled = false;

    @ConfigEntry.Gui.Tooltip()
    public HudCorner hudCorner = HudCorner.TOP_LEFT;

//  double UltraQuality = 1.3; // (0.77)
//  double Quality = 1.5;      // (0.67)
//  double Balanced = 1.7;     // (0.59)
//  double Performance = 2.0;  // (0.5)

    // TODO: Support oculus?
    //? iris {
    @ConfigEntry.Category("iris")
    @ConfigEntry.Gui.Tooltip()
    public float irisScale = -1.0f;
    //?}

    public static ConfigHolder<RenderScaleConfig> init() {
        // Register config
        ConfigHolder<RenderScaleConfig> holder = AutoConfig.register(RenderScaleConfig.class, JanksonConfigSerializer::new);

        // Change resolution upon save!
        holder.registerSaveListener((manager, data) -> {
            RenderScale.getInstance().onResolutionChanged();
            IrisCompatibility.reloadShaders();
            return null;
        });

        return holder;
    }

    public float getScale() {
        // To avoid 0x0 crashes if the user FOR SOME REASON puts 0 as the scale
        float safeScale = Math.max(0.01f, scale);

        //? iris {
        if (RenderScale.PLATFORM.isModLoaded("iris")) {
            if (IrisApi.getInstance().isShaderPackInUse() && irisScale > 0.0f) {
                return irisScale;
            } else {
                return safeScale;
            }
        } else {
            return safeScale;
        }
        //?} else {
         /*return safeScale;
        *///?}
    }

    // true -> linear, false -> nearest
    public boolean getFilter() {
        //? >= 1.21.11 {
        return fsr || fsr2 || fsrcnnx || miniespcn || miniespcnq || temporal || forceLinear || getScale() > 1.0;
        //?} else
        //return forceLinear || getScale() > 1.0;
    }
}
