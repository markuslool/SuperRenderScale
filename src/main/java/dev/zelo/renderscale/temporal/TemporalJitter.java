package dev.zelo.renderscale.temporal;

/**
 * Halton(2,3) jitter sequence, same idea as TAA in BSL/Complementary:
 * sub-pixel offset in [-0.5, 0.5] texels, period 8/16.
 * No dependency on Iris internals — camera-only reprojection.
 */
public final class TemporalJitter {
    private static final int PERIOD = 8;
    private int frameIndex = 0;

    // Halton base 2 / base 3, centered to [-0.5, 0.5]
    private static float halton(int index, int base) {
        float f = 1.0f;
        float r = 0.0f;
        int i = index;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r - 0.5f;
    }

    public float[] nextJitter() {
        int i = (frameIndex % PERIOD) + 1;
        frameIndex++;
        return new float[]{halton(i, 2), halton(i, 3)};
    }

    public void reset() {
        frameIndex = 0;
    }

    public int getFrameIndex() {
        return frameIndex;
    }
}
