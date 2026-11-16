package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

/** Reference-page orthographic basis, expressed in chunk units. No Minecraft dependency. */
public final class ReplayCamera {
    public double x=-2.07,y=1.52,z=1.40,panX,panY,zoom=1,targetZoom=1;
    private double sx,sz=1,elevation=Math.atan2(1.52,Math.hypot(-2.07,1.40));
    public record Point(double x,double y,double depth) {}
    public void axis(int axis,double value){
        if(!Double.isFinite(value))return;
        if(axis==1)y=Math.rint(Math.clamp(value,-3,3)*100)/100;
        else{double other=axis==0?z:x,limit=Math.floor(Math.sqrt(Math.max(0,6.25-other*other))*100+1e-9)/100;value=Math.rint(Math.clamp(value,-limit,limit)*100)/100;if(axis==0)x=value;else z=value;}
    }
    public double yaw(){normalize();return Math.atan2(sx,sz);}
    public double pitch(){normalize();return elevation;}
    private void normalize(){double h=Math.hypot(x,z);if(Math.hypot(h,y)<1e-6)return;if(h>1e-6){sx=x/h;sz=z/h;}elevation=Math.atan2(y,h);}
    public void rotate(double yaw){normalize();double h=Math.hypot(x,z);sx=Math.sin(yaw);sz=Math.cos(yaw);if(h>1e-6){x=sx*h;z=sz*h;}}
    public Point basis(double px,double py,double pz){normalize();double p=pitch(),d=sx*px+sz*pz;return new Point(sz*px-sx*pz,d*Math.sin(p)-py*Math.cos(p),d*Math.cos(p)+py*Math.sin(p));}
    public Point project(double px,double py,double pz,int cx,int cz,double width,double height,double scale){var p=basis(px-cx-.5,py,pz-cz-.5);return new Point(width*.5+panX+p.x*scale,height*.55+panY+p.y*scale,p.depth);}
    public void wheel(double minecraftSteps){targetZoom=Math.clamp(targetZoom*Math.exp(.00175*100*minecraftSteps),.4,4.6);}
    public void advance(double millis){zoom+=(targetZoom-zoom)*(1-Math.exp(-Math.clamp(millis,0,48)/85));if(Math.abs(targetZoom-zoom)<.0004)zoom=targetZoom;}
    public ReplayCamera copy(){normalize();var c=new ReplayCamera();c.x=x;c.y=y;c.z=z;c.panX=panX;c.panY=panY;c.zoom=zoom;c.targetZoom=targetZoom;c.sx=sx;c.sz=sz;c.elevation=elevation;return c;}
}
