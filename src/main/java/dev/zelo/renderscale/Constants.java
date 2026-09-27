package dev.zelo.renderscale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "renderscale";
    public static final String MOD_NAME = "RenderScale";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    // Manual build tag: bump on every test build so screenshots/logs self-identify.
    // b44 = MiniESPCN-Q x2 port (conv1 3-32 + conv2 32-16 k5 + conv3 shuffle,
    // 13 core passes, no bicubic base), wins over MiniESPCN/FSRCNNX.
    public static final String BUILD_TAG = "b44";
}
