package com.kuangsanfeng.ultimategarden.client.macro.farm;
public final class FarmController {
 public boolean running,held,action,countdown;
 public record InputOwnership(boolean running,boolean heldInput,boolean actionPending){}
 public InputOwnership inputOwnership(){return new InputOwnership(running,held,action);}
 public boolean canEditRouteConfiguration(){throw new AssertionError("Route editing must never gate input ownership");}
}
