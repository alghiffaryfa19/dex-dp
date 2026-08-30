package com.fauzan.nativedex;

import android.view.Surface;

interface INativeDexService {
    /**
     * Creates a virtual display with VIRTUAL_DISPLAY_FLAG_TRUSTED and others.
     * @return the displayId of the created virtual display, or error message.
     */
    String createTrustedVirtualDisplay(in Surface surface, int width, int height, int densityDpi);
    
    /**
     * Destroys the virtual display.
     */
    void destroyVirtualDisplay();
}
