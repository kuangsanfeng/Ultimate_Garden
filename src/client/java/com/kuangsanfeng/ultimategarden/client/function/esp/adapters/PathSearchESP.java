package com.kuangsanfeng.ultimategarden.client.function.esp.adapters;

import com.kuangsanfeng.ultimategarden.client.function.esp.ESPGeometry;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPColorMode;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPStyle;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Per-attempt, bounded planning observations. The renderer sees immutable copies. */
public final class PathSearchESP {
    public static final int NODE_CAP=96;
    private static final int ACTION_CAP=12;
    public record Node(long sequence,String phase,Point point) {}
    private final BooleanSupplier scanEnabled,calculationEnabled;
    private final ArrayDeque<Node> nodes=new ArrayDeque<>();
    private final ArrayDeque<ESPGeometry.Line> verified=new ArrayDeque<>();
    private long sequence;
    private boolean coarse=true;
    private List<ESPGeometry.Line> current=List.of();
    private Point rejected;
    public PathSearchESP(BooleanSupplier scanEnabled,BooleanSupplier calculationEnabled){
        this.scanEnabled=scanEnabled;this.calculationEnabled=calculationEnabled;
    }
    public boolean scanning(){return scanEnabled.getAsBoolean();}
    public boolean calculating(){return calculationEnabled.getAsBoolean();}
    public synchronized void expanded(String phase,Point point){
        if(!scanning()||!coarse)return;
        if(nodes.size()==NODE_CAP)nodes.removeFirst();
        nodes.addLast(new Node(++sequence,phase,point));
    }
    public synchronized List<Node> nodes(){return List.copyOf(nodes);}
    public synchronized void calculation(){coarse=false;nodes.clear();}
    public synchronized void coarse(){coarse=true;current=List.of();verified.clear();rejected=null;}
    public synchronized void choice(CorridorRepair.Choice choice,boolean accepted){
        if(!calculating()||choice==null)return;
        if(choice.castOrigin()!=null&&choice.forecast()!=null){
            var start=vec(choice.actions().getFirst().from());
            var cast=vec(choice.castOrigin().feet());
            var line=new ESPGeometry.Line(cast,vec(choice.forecast().landing()));
            var lines=new ArrayList<ESPGeometry.Line>();
            for(int i=0;i<8&&!start.equals(cast);i+=2)
                lines.add(new ESPGeometry.Line(start.lerp(cast,i/8.),start.lerp(cast,(i+1)/8.)));
            lines.add(line);
            current=List.copyOf(lines);
            if(accepted)verified(line);
        }
    }
    public synchronized void movement(Point before,Point after){
        if(calculating()&&!before.equals(after))verified(new ESPGeometry.Line(vec(before),vec(after)));
    }
    private void verified(ESPGeometry.Line line){
        if(verified.size()==ACTION_CAP)verified.removeFirst();
        verified.addLast(line);
    }
    public synchronized void reject(Point point){if(calculating())rejected=point;}
    public synchronized ESPGeometry scan(int ageBand){
        if(!scanning()||!coarse||nodes.isEmpty())return null;
        var boxes=new ArrayList<AABB>();
        for(var node:nodes){
            if(Math.min(3,(sequence-node.sequence())/16)!=ageBand)continue;
            Vec3 v=vec(node.point());
            boxes.add(new AABB(v,v).inflate(.28));
        }
        return geometry(List.of(),boxes,0x48EFFF,.95f-.24f*ageBand);
    }
    public synchronized ESPGeometry actions(boolean accepted){
        if(!calculating()||coarse)return null;
        var boxes=new ArrayList<AABB>();
        if(!accepted&&rejected!=null){
            var v=vec(rejected);
            boxes.add(new AABB(v,v).inflate(.45));
        }
        return geometry(accepted?List.copyOf(verified):current,boxes,accepted?0x67FFC7:0xFF9870,.85f);
    }
    private static Vec3 vec(Point p){return new Vec3(p.x(),p.y()+.15,p.z());}
    private static ESPGeometry geometry(List<ESPGeometry.Line> lines,List<AABB> boxes,int rgb,float alpha){
        var style=new ESPStyle(true,ESPColorMode.SOLID,rgb,alpha,2);
        return new ESPGeometry(lines,boxes,List.of(),null,style,style,style,style);
    }
}
