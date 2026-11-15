package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.config.ESPConfig;
import com.kuangsanfeng.ultimategarden.client.macro.esp.*;
import com.kuangsanfeng.ultimategarden.client.macro.esp.style.*;
import net.minecraft.world.phys.*;
/** Extracts the existing solver snapshot only; never calls a solver or player input. */
public final class ZombieShootoutESP {
 private final ESPConfig config;
 public ZombieShootoutESP(ESPConfig config){this.config=config;}
 public static int color(String classification){if(classification==null)return -1;return switch(classification){case "LEATHER_ADULT","LEATHER_BABY"->0xB8734E;case "IRON_ADULT","IRON_BABY"->0xD4D5D2;case "GOLD_ADULT","GOLD_BABY"->0xF8D34E;case "DIAMOND_ADULT","DIAMOND_BABY"->0x4ED8D4;default->-1;};}
 public static AABB bounds(Vec3 p){return new AABB(p.x-.25,p.y-.25,p.z-.25,p.x+.25,p.y+.25,p.z+.25);}
 public void register(ESPManager manager){for(int rgb:new int[]{0xB8734E,0xD4D5D2,0xF8D34E,0x4ED8D4}){
  String id="SHOOTOUT_INTERCEPT_"+Integer.toHexString(rgb).toUpperCase(java.util.Locale.ROOT);
  var disabled=new ESPStyle(false,ESPColorMode.SOLID,0,0,2);
  var style=new ESPTargetStyle(new ESPStyle(true,ESPColorMode.SOLID,rgb,1,2),disabled,disabled);
  manager.registerFixed(id,style,(context,targets)->{if(!config.isShowShootoutInterceptBox())return;var sample=ZombieShootoutController.INSTANCE.interceptDebug(context.level());if(sample!=null&&color(sample.classification())==rgb)targets.accept(new ESPTarget(id,bounds(sample.point())));});
 }}
}
