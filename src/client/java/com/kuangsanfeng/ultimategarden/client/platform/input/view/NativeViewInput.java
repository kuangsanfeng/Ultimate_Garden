package com.kuangsanfeng.ultimategarden.client.platform.input.view;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewAngles;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewRotationPlanner;

import net.minecraft.client.Minecraft;import net.minecraft.client.input.MouseButtonInfo;
import com.kuangsanfeng.ultimategarden.client.mixin.ShootoutMouseAccessor;
/** Source-scoped native mouse consumer; never writes player rotation or focus state. */
public class NativeViewInput {
 public interface ViewObserver {boolean before(float yaw,float pitch);void submitted(double dx,double dy,double sensitivity,boolean invertX,boolean invertY);void consumed(float yaw,float pitch);}
 public interface DiagnosticsObserver {
  boolean active();
  long queued(ViewRotationPlanner.Rotation rotation,double dt,double dx,double dy,double sensitivity,boolean invertX,boolean invertY,String submission);
  void applied(long sequence,float beforeYaw,float beforePitch,float afterYaw,float afterPitch);
 }
 private DiagnosticsObserver diagnostics;public void setDiagnosticsObserver(DiagnosticsObserver value){diagnostics=value;}
 private String lastStep="NO_FRAME";
 public String lastStep(){return lastStep;}
 private String hudAimMode;public String aimMode(){return hudAimMode;}
 private ViewObserver observer;public void setViewObserver(ViewObserver value){observer=value;}
 private static boolean dispatch;private boolean clickPending,rightOwned;private long lastUpdate;
 public static boolean dispatching(){return dispatch;}
 public void clearPendingMotion(Minecraft c){var m=(ShootoutMouseAccessor)c.mouseHandler;m.shootout$setDX(0);m.shootout$setDY(0);}
 public void resetAim(){hudAimMode=null;lastStep="NO_FRAME";}
 public double aim(Minecraft c,double yaw,double pitch,double peak,double curvature){
  long now=System.nanoTime();double dt=lastUpdate==0?.05:Math.clamp((now-lastUpdate)/1e9,0,.05);lastUpdate=now;
  return aim(c,yaw,pitch,peak,curvature,dt);
 }
 /** Frame scheduler supplies the actual elapsed step; native consumption and observation remain synchronous. */
 public double aim(Minecraft c,double yaw,double pitch,double peak,double curvature,double dt){
  dt=Math.clamp(dt,0,.05);
  var rotation=ViewRotationPlanner.rotation(c.player.getYRot(),c.player.getXRot(),yaw,pitch,peak,curvature,dt);
  double error=rotation.error();hudAimMode="TANH";
  if(rotation.step()==0){lastStep="ZERO_PLANNED_STEP";return error;}
  double dx=ViewRotationPlanner.rawDelta(c.player.getYRot(),rotation.yawDelta(),c.options.sensitivity().get(),c.options.invertMouseX().get());
  double dy=ViewRotationPlanner.rawPitchDelta(c.player.getXRot(),rotation.pitchDelta(),c.options.sensitivity().get(),c.options.invertMouseY().get());
  boolean tracing=diagnostics!=null&&diagnostics.active();
  if(dx==0&&dy==0){lastStep="ZERO_QUANTIZED";if(tracing)diagnostics.queued(rotation,dt,0,0,c.options.sensitivity().get(),c.options.invertMouseX().get(),c.options.invertMouseY().get(),"ZERO_QUANTIZED");return error;}
  if(observer!=null&&!observer.before(c.player.getYRot(),c.player.getXRot())){lastStep="VIEW_OBSERVER_REJECTED";if(tracing)diagnostics.queued(rotation,dt,0,0,c.options.sensitivity().get(),c.options.invertMouseX().get(),c.options.invertMouseY().get(),"VIEW_OBSERVER_REJECTED");return error;}
  if(c.options.smoothCamera||c.player.isScoping())throw new IllegalStateException("UNSUPPORTED_SMOOTH_CAMERA_OR_SCOPING");
  var m=(ShootoutMouseAccessor)c.mouseHandler;double oldX=m.shootout$getDX(),oldY=m.shootout$getDY();
  try{dispatch=true;m.shootout$setDX(dx);m.shootout$setDY(dy);if(observer!=null)observer.submitted(m.shootout$getDX(),m.shootout$getDY(),c.options.sensitivity().get(),c.options.invertMouseX().get(),c.options.invertMouseY().get());
   long sequence=tracing?diagnostics.queued(rotation,dt,m.shootout$getDX(),m.shootout$getDY(),c.options.sensitivity().get(),c.options.invertMouseX().get(),c.options.invertMouseY().get(),"SUBMITTED"):0;
   float beforeYaw=tracing?c.player.getYRot():0,beforePitch=tracing?c.player.getXRot():0;
   m.shootout$turn(dt);
   lastStep="NATIVE_TURN_CONSUMED";
   if(tracing)diagnostics.applied(sequence,beforeYaw,beforePitch,c.player.getYRot(),c.player.getXRot());
   if(observer!=null)observer.consumed(c.player.getYRot(),c.player.getXRot());}
  finally{m.shootout$setDX(oldX);m.shootout$setDY(oldY);dispatch=false;}
  return ViewAngles.error(c.player.getYRot(),c.player.getXRot(),yaw,pitch);
 }
 public boolean click(Minecraft c){if(c.mouseHandler.isRightPressed()||c.options.keyUse.isDown())return false;var m=(ShootoutMouseAccessor)c.mouseHandler;try{dispatch=true;rightOwned=true;clickPending=true;m.shootout$button(c.getWindow().handle(),new MouseButtonInfo(1,0),1);return true;}finally{try{if(rightOwned){m.shootout$button(c.getWindow().handle(),new MouseButtonInfo(1,0),0);rightOwned=false;}}finally{dispatch=false;}}}
 public void consumed(){clickPending=false;}
 public void cancel(Minecraft c){try{if(rightOwned){dispatch=true;try{((ShootoutMouseAccessor)c.mouseHandler).shootout$button(c.getWindow().handle(),new MouseButtonInfo(1,0),0);}finally{((ShootoutMouseAccessor)c.mouseHandler).shootout$setRightPressed(false);c.options.keyUse.setDown(false);rightOwned=false;}}}finally{dispatch=false;if(clickPending){while(c.options.keyUse.consumeClick()){}clickPending=false;}lastUpdate=0;hudAimMode=null;}}
}
