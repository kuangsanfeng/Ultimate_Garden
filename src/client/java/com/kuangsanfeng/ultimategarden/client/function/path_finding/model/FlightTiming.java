package com.kuangsanfeng.ultimategarden.client.function.path_finding.model;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Terrain;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCorrection;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewAngles;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewRotationPlanner;

/** Forecasts native aim frames and END_CLIENT_TICK movement samples without sending input. */
public final class FlightTiming {
    public static final double TICK_MS = 50;
    public static final double VERTICAL_DRAG = 0.9800000190734863;
    private static final double GRAVITY = .08;

    public record Aim(double peak, double curve, int hz, double tolerance, double correction,
                      double sensitivity, boolean invertX, boolean invertY, double draw) {
        public static Aim defaults() {return new Aim(300,15,20,.5,.25,.5,false,false,.5);}
        public Aim withDraw(double value) {return new Aim(peak,curve,hz,tolerance,correction,sensitivity,invertX,invertY,value);}
    }
    public record Motion(Point feet, double vx, double vy, double vz, boolean grounded) {
        public static Motion confirmed(Point feet, Terrain terrain) {
            return new Motion(feet,0,0,0,terrain.supported(feet));
        }
        public Motion jump(double impulse) {return new Motion(feet,vx,impulse,vz,false);}
    }
    public record Ready(double cooldownMs, double confirmationMs, int earliestTick) {
        public int castTick(double aimMs) {
            int aimTick=aimMs==0?0:(int)Math.floor(aimMs/TICK_MS+1e-9)+1;
            int gateTick=(int)Math.ceil(Math.max(cooldownMs,confirmationMs)/TICK_MS);
            return Math.max(earliestTick,Math.max(aimTick,gateTick));
        }
    }
    public record AimTime(double ms, double yaw, double pitch) {}

    private FlightTiming() {}

    public static AimTime estimate(double yaw,double pitch,double targetYaw,double targetPitch,Aim aim) {
        var correction=new ShootoutAimCorrection();
        double error=ViewAngles.error(yaw,pitch,targetYaw,targetPitch);
        if (error<=aim.tolerance()+1e-9) return new AimTime(0,yaw,pitch);
        float y=(float)yaw,p=(float)pitch;
        double period=1000.0/aim.hz(),dt=Math.min(.05,period/1000);
        for (int frame=1; frame*period<10_000; frame++) {
            var rotation=ViewRotationPlanner.rotation(y,p,targetYaw,targetPitch,aim.peak(),aim.curve(),dt);
            y=applyNative(y,rotation.yawDelta(),aim.sensitivity(),aim.invertX());
            p=Math.clamp(applyNative(p,rotation.pitchDelta(),aim.sensitivity(),aim.invertY()),-90,90);
            double ms=frame*period;
            if (correction.ready(ViewAngles.error(y,p,targetYaw,targetPitch),aim.tolerance(),aim.correction(),Math.round(ms*1e6),aim::draw))
                return new AimTime(ms,y,p);
        }
        return null;
    }

    private static float applyNative(float angle,double delta,double sensitivity,boolean inverted) {
        double scaled=sensitivity*.6F+.2F;
        double raw=ViewRotationPlanner.rawDelta(angle,delta,sensitivity,inverted);
        return angle+(float)(raw*(scaled*scaled*scaled*8)*(inverted?-1:1))*.15F;
    }

    public static Motion predict(Motion motion,int ticks,Terrain terrain) {
        Motion at=motion;
        for(int i=0; i<ticks; i++) {
            at=advance(at,terrain);
            if(at==null) return null;
            if(at.grounded()&&at.vx()==0&&at.vz()==0) break;
        }
        return at;
    }

    private static Motion advance(Motion state,Terrain terrain) {
        Point feet=state.feet();
        double vy=state.grounded()&&state.vy()<=0?0:state.vy();
        var vertical=terrain.motionSweep(feet,feet.add(new Point(0,vy,0)));
        if(vertical==Terrain.MotionCheck.UNKNOWN) return null;
        Point at=feet;
        boolean collision=vertical==Terrain.MotionCheck.BLOCKED;
        if (!collision) at=feet.add(new Point(0,vy,0));
        else {
            double low=0,high=1;
            for (int i=0;i<18;i++) {
                double part=(low+high)*.5;
                var checked=terrain.motionSweep(feet,feet.add(new Point(0,vy*part,0)));
                if (checked==Terrain.MotionCheck.UNKNOWN) return null;
                if (checked==Terrain.MotionCheck.CLEAR) low=part;
                else high=part;
            }
            double y=feet.y()+vy*low;
            // Vanilla collision shapes put the feet on block-shape faces (multiples of 1/16 here).
            double face=Math.rint(y*16)/16;
            if(Math.abs(face-y)<.0003) {
                Point exact=new Point(feet.x(),face,feet.z());
                if(terrain.motionSweep(feet,exact)==Terrain.MotionCheck.CLEAR) y=face;
            }
            at=new Point(feet.x(),y,feet.z());
        }
        Point x=at.add(new Point(state.vx(),0,0)),z;
        var xCheck=state.vx()==0?Terrain.MotionCheck.CLEAR:terrain.motionSweep(at,x);
        if(xCheck==Terrain.MotionCheck.UNKNOWN) return null;
        if(xCheck==Terrain.MotionCheck.CLEAR) at=x;
        z=at.add(new Point(0,0,state.vz()));
        var zCheck=state.vz()==0?Terrain.MotionCheck.CLEAR:terrain.motionSweep(at,z);
        if(zCheck==Terrain.MotionCheck.UNKNOWN) return null;
        if(zCheck==Terrain.MotionCheck.CLEAR) at=z;
        boolean grounded=vy<=0 && (state.grounded()||collision||terrain.supported(at)) && terrain.supported(at);
        // Only inertial motion during aiming. No ground-speed assumption is applied in the air.
        double drag=grounded?.546:.91;
        double vx=xCheck==Terrain.MotionCheck.CLEAR?state.vx()*drag:0;
        double vz=zCheck==Terrain.MotionCheck.CLEAR?state.vz()*drag:0;
        return new Motion(at,Math.abs(vx)<.003?0:vx,grounded?0:VERTICAL_DRAG*((collision?0:vy)-GRAVITY),
                Math.abs(vz)<.003?0:vz,grounded);
    }

    public static double jumpImpulse(double measuredHeight) {
        if(measuredHeight<=1.25) return .42;
        double low=.42,high=1.5;
        for(int i=0;i<16;i++){
            double mid=(low+high)*.5,y=0,v=mid;
            while(v>0){y+=v;v=VERTICAL_DRAG*(v-GRAVITY);}
            if(y<measuredHeight) low=mid; else high=mid;
        }
        return (low+high)*.5;
    }
}
