package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import java.util.*;

/** Presentation ordering never reinterprets an option's stable enum ID. */
public final class ReplayDepth {
    private ReplayDepth(){}
    public static List<ReplayMesh.Mode> options(ReplayMesh.Mode current){var list=new ArrayList<ReplayMesh.Mode>();list.add(current);for(var mode:ReplayMesh.Mode.values())if(mode!=current)list.add(mode);return List.copyOf(list);}
}
