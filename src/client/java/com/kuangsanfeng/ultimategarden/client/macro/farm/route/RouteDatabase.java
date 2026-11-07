package com.kuangsanfeng.ultimategarden.client.macro.farm.route;

import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.*;
import java.util.*;
import java.util.stream.Collectors;

/** Retained unused API, now adapted from the same JSON definitions. */
public final class RouteDatabase {
    private RouteDatabase() { }
    public static RouteData get(CropType type) {
        var crop = CropDatabase.get(type.getValue());
        if (!crop.isExecutable()) return null;
        return new RouteData(type,
            crop.keys(true).stream().map(RouteKey::valueOf).collect(Collectors.toSet()),
            crop.keys(false).stream().map(RouteKey::valueOf).collect(Collectors.toSet()),
            crop.useAlternateSlot());
    }
}
