package dev.zelo.renderscale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "renderscale";
    public static final String MOD_NAME = "RenderScale";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    // Manual build tag: bump on every test build so screenshots/logs self-identify.
    // b36 = manual perspective from setup params + shared jitter helper.
    public static final String BUILD_TAG = "b36";
}
