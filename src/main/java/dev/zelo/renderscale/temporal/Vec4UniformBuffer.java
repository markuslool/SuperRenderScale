//? >= 1.21.11 {
package dev.zelo.renderscale.temporal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Uniform uploads through vanilla's {@link MappableRingBuffer}.
 *
 * <p>Why a ring: uniform bindings must be aligned to
 * {@code minUniformOffsetAlignment} (256 on NVIDIA/Vulkan) and must not be
 * overwritten while the GPU still reads them. One 512B block holds two
 * 256-aligned slots: params at +0, reprojection at +256.
 */
public final class Vec4UniformBuffer implements AutoCloseable {
    private static final int BLOCK = 512;
    private final MappableRingBuffer ring;

    public Vec4UniformBuffer(String label) {
        //? > 26.1 {
        ring = new MappableRingBuffer(() -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE, BLOCK);
        //?} else {
        /*ring = new MappableRingBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, BLOCK);
        *///?}
    }

    //? > 26.1 {
    public GpuBufferSlice upload(CommandEncoder encoder, float x, float y, float z, float w) {
        GpuBufferSlice slot = ring.currentBuffer().slice(0, 16);
        ByteBuffer bb = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
        bb.putFloat(x).putFloat(y).putFloat(z).putFloat(w).flip();
        encoder.writeToBuffer(slot, bb);
        return slot;
    }

    /**
     * Depth-reprojection block: PrevVP (mat4) + InvVP (mat4) + flags (vec4), 144 bytes.
     * Flags: x = use reprojection, y = debug view (0/1/2), z = sample real depth
     * (0 = far-plane assumption: exact for rotation, fallback when the backend
     * doesn't store depth to the texture, e.g. Vulkan transient).
     */
    public GpuBufferSlice uploadReproj(CommandEncoder encoder, Matrix4f prevVP, Matrix4f invVP,
            float useFlag, float debugMode, float useDepth) {
        GpuBufferSlice slot = ring.currentBuffer().slice(256, 144);
        ByteBuffer bb = ByteBuffer.allocateDirect(144).order(ByteOrder.LITTLE_ENDIAN);
        float[] m = new float[16];
        prevVP.get(m);
        for (float f : m) bb.putFloat(f);
        invVP.get(m);
        for (float f : m) bb.putFloat(f);
        bb.putFloat(useFlag).putFloat(debugMode).putFloat(useDepth).putFloat(0.0f);
        bb.flip();
        encoder.writeToBuffer(slot, bb);
        return slot;
    }
    //?} else {
    /*public GpuBufferSlice upload(float x, float y, float z, float w) {
        GpuBufferSlice slot = ring.currentBuffer().slice(0, 16);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var view = encoder.mapBuffer(slot, false, true)) {
            Std140Builder.intoBuffer(view.data()).putVec4(new Vector4f(x, y, z, w));
        }
        return slot;
    }

    // Fallback: identity matrices, flag passthrough.
    public GpuBufferSlice uploadReproj(float useFlag, float debugMode, float useDepth) {
        GpuBufferSlice slot = ring.currentBuffer().slice(256, 144);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var view = encoder.mapBuffer(slot, false, true)) {
            ByteBuffer bb = view.data().order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 16; i++) bb.putFloat(i % 5 == 0 ? 1.0f : 0.0f);
            for (int i = 0; i < 16; i++) bb.putFloat(i % 5 == 0 ? 1.0f : 0.0f);
            bb.putFloat(useFlag).putFloat(debugMode).putFloat(useDepth).putFloat(0.0f);
        }
        return slot;
    }
    *///?}

    /** Advance the ring; call once per frame after the last upload. */
    public void nextFrame() {
        ring.rotate();
    }

    @Override
    public void close() {
        ring.close();
    }
}
//?} else {
/*// Temporal upscaling requires the 1.21.11+ render pipeline.
*///?}
