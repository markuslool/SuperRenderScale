//? > 26.1 {
package dev.zelo.renderscale.mixin.accessors;

import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public interface MixinCameraAccessor {
    @Accessor("projection")
    Projection renderScale$getProjection();
}
//?} else {
/*// Camera projection access is only needed where depth reprojection exists (> 26.1).
*///?}
