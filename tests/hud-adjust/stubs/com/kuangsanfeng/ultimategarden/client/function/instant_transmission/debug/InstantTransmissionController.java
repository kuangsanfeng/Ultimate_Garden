package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;
public final class InstantTransmissionController {
 public static final InstantTransmissionController INSTANCE=new InstantTransmissionController();
 public TransmissionHudSnapshot value=new TransmissionHudSnapshot(false,false,"STOPPED","NONE",null,0,0,1,null,null,0.,0.,true,null,null,null,0,0,0,0,null);
 public TransmissionHudSnapshot hudSnapshot(){return value;}
}
