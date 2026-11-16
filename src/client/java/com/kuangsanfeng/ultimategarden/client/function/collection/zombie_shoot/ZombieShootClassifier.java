package com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
public final class ZombieShootClassifier {
 public static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
 public static String classify(List<String> items,boolean baby){
  String[] suffix={"helmet","chestplate","leggings","boots"};
  for(String material:List.of("leather","iron","golden","diamond")){
   boolean match=items.size()==4;for(int i=0;i<items.size()&&i<4;i++)match&=items.get(i).equals("minecraft:"+material+"_"+suffix[i]);
   if(match)return (material.equals("golden")?"GOLD":material.toUpperCase(Locale.ROOT))+(baby?"_BABY":"_ADULT");
  }return "UNCLASSIFIED";
 }
 public static List<String> items(Zombie z){var out=new ArrayList<String>();for(var slot:SLOTS)out.add(BuiltInRegistries.ITEM.getKey(z.getItemBySlot(slot).getItem()).toString());return List.copyOf(out);}
}

