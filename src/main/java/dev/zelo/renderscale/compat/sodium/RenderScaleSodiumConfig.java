//? sodium {
package dev.zelo.renderscale.compat.sodium;

import dev.zelo.renderscale.RenderScale;
import dev.zelo.renderscale.config.RenderScaleConfig;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RenderScaleSodiumConfig implements ConfigEntryPoint {
    private static final Identifier SCALE = id("scale");
    private static final Identifier FORCE_LINEAR = id("force_linear");
    //? >= 1.21.11
    private static final Identifier FSR = id("fsr");
    //? >= 1.21.11
    private static final Identifier FSRCNNX = id("fsrcnnx");
    //? >= 1.21.11
    private static final Identifier MINIESPCN = id("miniespcn");
    //? >= 1.21.11
    private static final Identifier MINIESPCNQ = id("miniespcnq");
    //? >= 1.21.11
    private static final Identifier TEMPORAL = id("temporal");
    //? >= 1.21.11
    private static final Identifier FSR2 = id("fsr2");
    //? >= 1.21.11
    private static final Identifier FSR2_SHARPNESS = id("fsr2_sharpness");
    //? >= 1.21.11
    private static final Identifier FSR2_REACTIVE = id("fsr2_reactive");
    //? >= 1.21.11
    private static final Identifier FSR2_NO_HISTORY = id("fsr2_no_history");
    //? >= 1.21.11
    private static final Identifier TEMPORAL_WITH_SHADERS = id("temporal_with_shaders");
    //? >= 1.21.11
    private static final Identifier TEMPORAL_EASU = id("temporal_easu");
    //? >= 1.21.11
    private static final Identifier TEMPORAL_REPROJ = id("temporal_reproj");
    //? >= 1.21.11
    private static final Identifier TEMPORAL_DEBUG = id("temporal_debug");
    //? >= 1.21.11
    private static final Identifier TEMPORAL_REPROJ_DEPTH = id("temporal_reproj_depth");
    //? >= 1.21.11
    private static final Identifier TEMPORAL_SHARPNESS = id("temporal_sharpness");
    //? >= 1.21.11
    private static final Identifier FSR_SHARPNESS = id("fsr_sharpness");
    private static final Identifier DYNAMIC_SCALE = id("dynamic_scale");
    private static final Identifier HUD_ENABLED = id("hud_enabled");    private static final Identifier TARGET_FPS = id("target_fps");
    private static final Identifier DYN_MIN_SCALE = id("dyn_min_scale");
    private static final Identifier DYN_MAX_SCALE = id("dyn_max_scale");
    //? iris
    private static final Identifier IRIS_SCALE = id("iris_scale");

    public static final Identifier MONO = Identifier.fromNamespaceAndPath("renderscale", "textures/gui/config-icon-mono.png");
    public static final Identifier COLOUR = Identifier.fromNamespaceAndPath("renderscale", "textures/gui/config-icon.png");

    private final StorageEventHandler storageHandler = RenderScale.CONFIG::save;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .setIcon(MONO)
                .setNonTintedIcon(COLOUR)
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(0x02c934))
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable("text.autoconfig.renderscale.category.default"))
                        .addOptionGroup(builder.createOptionGroup()
//                                .setName(Component.translatable("text.autoconfig.renderscale.category.default"))
                                .addOption(builder.createIntegerOption(SCALE)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.scale"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.scale.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(this::setScalePercent, this::getScalePercent)
                                        .setDefaultValue(100)
                                        .setRange(1, 200, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatPercent)
//                                        .setImpact(OptionImpact.VARIES)
                                )
                                .addOption(builder.createBooleanOption(DYNAMIC_SCALE)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.dynamicScale"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.dynamicScale.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().dynamicScale = v, () -> config().dynamicScale)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.VARIES)
                                )
                                .addOption(builder.createBooleanOption(HUD_ENABLED)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.hudEnabled"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.hudEnabled.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().hudEnabled = v, () -> config().hudEnabled)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createIntegerOption(TARGET_FPS)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.targetFps"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.targetFps.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().targetFps = v, () -> config().targetFps)
                                        .setDefaultValue(60)
                                        .setRange(20, 120, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatFps)
                                        .setImpact(OptionImpact.VARIES)
                                )
                                .addOption(builder.createIntegerOption(DYN_MIN_SCALE)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.dynMinScale"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.dynMinScale.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().dynMinScale = v, () -> config().dynMinScale)
                                        .setDefaultValue(50)
                                        .setRange(25, 100, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatPercent)
                                        .setImpact(OptionImpact.VARIES)
                                )
                                .addOption(builder.createIntegerOption(DYN_MAX_SCALE)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.dynMaxScale"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.dynMaxScale.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().dynMaxScale = v, () -> config().dynMaxScale)
                                        .setDefaultValue(100)
                                        .setRange(50, 200, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatPercent)
                                        .setImpact(OptionImpact.VARIES)
                                )
                                .addOption(builder.createBooleanOption(FORCE_LINEAR)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.forceLinear"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.forceLinear.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().forceLinear = v, () -> config().forceLinear)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.LOW)
                                )
                                //? >= 1.21.11 {
                                .addOption(builder.createBooleanOption(FSR)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.fsr"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.fsr.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().fsr = v, () -> config().fsr)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.MEDIUM)
                                )
                                .addOption(builder.createBooleanOption(FSRCNNX)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.fsrcnnx"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.fsrcnnx.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().fsrcnnx = v, () -> config().fsrcnnx)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.HIGH)
                                )
                                .addOption(builder.createBooleanOption(MINIESPCN)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.miniespcn"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.miniespcn.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().miniespcn = v, () -> config().miniespcn)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.HIGH)
                                )
                                .addOption(builder.createBooleanOption(MINIESPCNQ)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.miniespcnq"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.miniespcnq.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().miniespcnq = v, () -> config().miniespcnq)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.HIGH)
                                )
                                .addOption(builder.createBooleanOption(TEMPORAL)                                        .setName(Component.translatable("text.autoconfig.renderscale.option.temporal"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.temporal.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().temporal = v, () -> config().temporal)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.MEDIUM)
                                )
                                .addOption(builder.createBooleanOption(FSR2)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.fsr2"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.fsr2.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().fsr2 = v, () -> config().fsr2)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.MEDIUM)
                                )
                                .addOption(builder.createIntegerOption(FSR2_SHARPNESS)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.fsr2Sharpness"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.fsr2Sharpness.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().fsr2Sharpness = v, () -> config().fsr2Sharpness)
                                        .setDefaultValue(80)
                                        .setRange(0, 100, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatPercent)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createIntegerOption(FSR2_REACTIVE)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.fsr2Reactive"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.fsr2Reactive.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().fsr2Reactive = v, () -> config().fsr2Reactive)
                                        .setDefaultValue(50)
                                        .setRange(0, 100, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatPercent)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createBooleanOption(FSR2_NO_HISTORY)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.fsr2NoHistory"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.fsr2NoHistory.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().fsr2NoHistory = v, () -> config().fsr2NoHistory)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createBooleanOption(TEMPORAL_WITH_SHADERS)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.temporalWithShaders"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.temporalWithShaders.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().temporalWithShaders = v, () -> config().temporalWithShaders)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.MEDIUM)
                                )
                                .addOption(builder.createBooleanOption(TEMPORAL_EASU)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.temporalEasu"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.temporalEasu.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().temporalEasu = v, () -> config().temporalEasu)
                                        .setDefaultValue(true)
                                        .setImpact(OptionImpact.MEDIUM)
                                )
                                .addOption(builder.createBooleanOption(TEMPORAL_REPROJ)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.temporalReproj"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.temporalReproj.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().temporalReproj = v, () -> config().temporalReproj)
                                        .setDefaultValue(true)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createIntegerOption(TEMPORAL_DEBUG)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.temporalDebug"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.temporalDebug.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().temporalDebug = v, () -> config().temporalDebug)
                                        .setDefaultValue(0)
                                        .setRange(0, 2, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatTaaDebug)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createBooleanOption(TEMPORAL_REPROJ_DEPTH)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.temporalReprojDepth"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.temporalReprojDepth.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().temporalReprojDepth = v, () -> config().temporalReprojDepth)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createIntegerOption(TEMPORAL_SHARPNESS)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.temporalSharpness"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.temporalSharpness.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().temporalSharpness = v, () -> config().temporalSharpness)
                                        .setDefaultValue(20)
                                        .setRange(0, 100, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatPercent)
                                        .setImpact(OptionImpact.LOW)
                                )
                                .addOption(builder.createIntegerOption(FSR_SHARPNESS)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.fsrSharpness"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.fsrSharpness.@Tooltip"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(v -> config().fsrSharpness = v, () -> config().fsrSharpness)
                                        .setDefaultValue(80)
                                        .setRange(0, 100, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatPercent)
                                        .setImpact(OptionImpact.LOW)
                                )
                                //?}
                        )
                )
                        //? iris {
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable("text.autoconfig.renderscale.category.iris"))
                                .addOptionGroup(builder.createOptionGroup()
//                                .setName(Component.translatable("text.autoconfig.renderscale.category.iris"))
                                .addOption(builder.createIntegerOption(IRIS_SCALE)
                                        .setName(Component.translatable("text.autoconfig.renderscale.option.irisScale.sodium"))
                                        .setTooltip(Component.translatable("text.autoconfig.renderscale.option.irisScale.@Tooltip.sodium"))
                                        .setStorageHandler(this.storageHandler)
                                        .setBinding(this::setIrisScalePercent, this::getIrisScalePercent)
                                        .setDefaultValue(0)
                                        .setRange(0, 200, 1)
                                        .setValueFormatter(RenderScaleSodiumConfig::formatIrisScale)
//                                        .setImpact(OptionImpact.VARIES)
                                )
                        )
                );
                        //?}
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("renderscale", path);
    }

    private static RenderScaleConfig config() {
        return RenderScale.getConfig();
    }

    private int getScalePercent() {
        return Math.round(config().scale * 100.0f);
    }

    private void setScalePercent(int value) {
        config().scale = value / 100.0f;
    }

    private int getIrisScalePercent() {
        if (config().irisScale <= 0.0f) {
            return 0;
        }

        return Math.round(config().irisScale * 100.0f);
    }

    private void setIrisScalePercent(int value) {
        config().irisScale = value <= 0 ? -1.0f : value / 100.0f;
    }

    private static Component formatPercent(int value) {
        return Component.literal(value + "%");
    }

    private static Component formatFps(int value) {
        return Component.literal(value + " FPS");
    }

    private static Component formatTaaDebug(int value) {
        return switch (value) {
            case 1 -> Component.translatable("text.autoconfig.renderscale.option.temporalDebug.depth");
            case 2 -> Component.translatable("text.autoconfig.renderscale.option.temporalDebug.motion");
            default -> Component.translatable("text.autoconfig.renderscale.option.temporalDebug.off");
        };
    }

    private static Component formatIrisScale(int value) {
        if (value <= 0) {
            return Component.translatable("text.autoconfig.renderscale.option.irisScale.same");
        }

        return formatPercent(value);
    }
}
//?}