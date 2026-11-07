package com.kuangsanfeng.ultimategarden.client.macro.farm.route;


public class SlotSelector {

    // SelectYP()
    // 对应 C++: return (eventCount % 2) + 51;
    // ASCII: '3' = 51, '4' = 52

    public static int getSlot(
            int eventCount
    ) {
        if (eventCount % 2 == 0) {
            return 3;
        } else {
            return 4;
        }
    }
}