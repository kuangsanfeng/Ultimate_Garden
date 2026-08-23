package com.kuangsanfeng.ultimategarden.client.macro.route;

import com.kuangsanfeng.ultimategarden.client.macro.crop.CropType;
import java.util.Set;

/**
 * Crop route data.
 */
public class RouteData {

    private final CropType cropType;
    private final Set<RouteKey> routeTrue;
    private final Set<RouteKey> routeFalse;
    private final boolean useAlternateSlot;

    public RouteData(
            CropType cropType,
            Set<RouteKey> routeTrue,
            Set<RouteKey> routeFalse,
            boolean useAlternateSlot
    ) {
        this.cropType = cropType;
        this.routeTrue = routeTrue;
        this.routeFalse = routeFalse;
        this.useAlternateSlot = useAlternateSlot;
    }

    public Set<RouteKey> getRoute(boolean eventSelect) {
        return eventSelect ? routeTrue : routeFalse;
    }

    public boolean useAlternateSlot() {
        return useAlternateSlot;
    }

    public CropType getCropType() {
        return cropType;
    }
}