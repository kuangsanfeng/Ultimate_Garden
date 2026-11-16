package com.kuangsanfeng.ultimategarden.client.platform.input.view;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewAngles;


/** O(1) ephemeral state. Submitted frames cannot be compared before native consumption. */
public class ExpectedViewTracker {
 private boolean active,pending,warning;private float yaw,pitch;private long sequence;
 public boolean active(){return active;}public void begin(float y,float p){clear();active=true;yaw=y;pitch=p;}
 public void submit(double dx,double dy,double sensitivity,boolean invertX,boolean invertY){if(!active||pending)throw new IllegalStateException("Unmatched input phase");double ss=sensitivity*.6F+.2F,scale=ss*ss*ss*8;float y=(float)(dx*scale*(invertX?-1:1))*.15F,p=(float)(dy*scale*(invertY?-1:1))*.15F;yaw+=y;pitch=Math.clamp(pitch+p,-90,90);pending=true;sequence++;}
 public Double consumed(float y,float p){if(!active||!pending)return null;pending=false;return ViewAngles.error(yaw,pitch,y,p);}
 public Double stationary(float y,float p){return !active||pending?null:ViewAngles.error(yaw,pitch,y,p);}
 public int evaluate(Double error,double warn,double stop){if(error==null)return 0;if(error+1e-9>=stop)return 2;if(error+1e-9>=warn){if(!warning){warning=true;return 1;}}else warning=false;return 0;}
 public void clear(){active=false;pending=false;warning=false;yaw=0;pitch=0;sequence=0;}
}
