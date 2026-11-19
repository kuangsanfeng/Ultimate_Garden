package com.kuangsanfeng.ultimategarden.client.function.esp.adapters;

import com.kuangsanfeng.ultimategarden.client.function.esp.ESPGeometry;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPColorMode;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPStyle;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Movement;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Segment;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Route overlay. The planning worker builds the shell, and the client publishes immutable draw data. */
public final class PathRouteESP {
    private static final int CELL_SIZE=2;
    private static final ESPStyle LINES=new ESPStyle(true,ESPColorMode.SOLID,0x45BFE8,.78f,2f);
    private static final ESPStyle OUTLINES=new ESPStyle(true,ESPColorMode.SOLID,0x81E7FF,.95f,2f);
    private static final ESPStyle SHELL=new ESPStyle(true,ESPColorMode.SOLID,0x2379A9,.18f,0);
    private static final ESPStyle GUIDE=new ESPStyle(true,ESPColorMode.SOLID,0xF7EC82,1f,3.5f);
    private static Prepared route;
    private static volatile ESPGeometry frame;

    public record Prepared(CorridorRepair.Rehearsal route, List<ESPGeometry.Face> faces) {
        public Prepared { faces=List.copyOf(faces); }
    }

    private PathRouteESP() {}

    public static boolean corridorVisible(boolean enabled,boolean onlyCorridor,boolean regional){
        return enabled&&(!onlyCorridor||regional);
    }

    public static ESPGeometry snapshot() {
        var overlay=frame;
        if(overlay==null)return null;
        var config=com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient.getConfigManager().getESPConfig();
        if(route!=null&&corridorVisible(config.pathCorridor(),config.pathCorridorOnly(),route.route().corridor().regional()))return overlay;
        return new ESPGeometry(overlay.lines(),overlay.outlines(),List.of(),overlay.guide(),LINES,OUTLINES,SHELL,GUIDE);
    }

    public static void clear() {
        route=null;
        frame=null;
    }

    public static Prepared prepare(CorridorRepair.Rehearsal result, BooleanSupplier cancelled) {
        double radius=result.corridor().radius();
        Set<Long> cells=new HashSet<>();
        for (Segment segment : result.corridor().referenceSegments()) {
            if (cancelled.getAsBoolean()) return null;
            Vec3 from=center(segment.from());
            Vec3 to=center(segment.to());
            Point bend=CorridorRepair.referenceBend(segment);
            if (bend==null) {
                include(cells,from,to,radius);
            } else {
                Vec3 endOfFirstLeg=center(bend);
                include(cells,from,endOfFirstLeg,radius);
                include(cells,endOfFirstLeg,to,radius);
            }
        }
        return new Prepared(result,exterior(cells,cancelled));
    }

    public static void show(Prepared prepared) {
        route=prepared;
        advance(0,0);
    }

    public static void advance(int previewIndex, double progress) {
        next(null,null,0,previewIndex,progress);
    }

    public static void next(Segment current, CorridorRepair.Choice choice, int legIndex, int previewIndex, double progress) {
        if (route==null) return;
        List<Segment> actions=new ArrayList<>();
        if (current!=null) {
            if (choice==null) actions.add(current);
            else actions.addAll(choice.actions().subList(legIndex,choice.actions().size()));
        }
        double covered=choice==null?progress:choice.progress();
        List<CorridorRepair.Choice> choices=route.route().choices();
        for (int i=previewIndex; i<choices.size(); i++) {
            var remaining=choices.get(i);
            if (choice!=null && (remaining==choice || remaining.progress()<=covered+.01)) continue;
            actions.addAll(remaining.actions());
        }

        var points=new ArrayList<Vec3>();
        if (!actions.isEmpty()) {
            addPoint(points,actions.getFirst().from());
            for (int i=0; i<actions.size(); i++) {
                var action=actions.get(i);
                if (i==0 || i==actions.size()-1 || action.movement()!=Movement.WALK
                        || !continuesStraight(action,actions.get(i+1))) addPoint(points,action.to());
            }
        }
        var lines=new ArrayList<ESPGeometry.Line>();
        for (int i=1; i<points.size(); i++) lines.add(new ESPGeometry.Line(points.get(i-1),points.get(i)));
        var boxes=new ArrayList<AABB>();
        for (Vec3 point : points) boxes.add(new AABB(point,point).inflate(.28));
        Vec3 goal=marker(route.route().corridor().goal());
        if (!points.contains(goal)) boxes.add(new AABB(goal,goal).inflate(.35));
        Vec3 guide=actions.isEmpty()?null:marker(actions.getFirst().to());
        frame=new ESPGeometry(lines,boxes,route.faces(),guide,LINES,OUTLINES,SHELL,GUIDE);
    }

    private static boolean continuesStraight(Segment current, Segment next) {
        if (next.movement()!=Movement.WALK || !current.to().equals(next.from())) return false;
        Vec3 first=marker(current.to()).subtract(marker(current.from())).normalize();
        Vec3 second=marker(next.to()).subtract(marker(next.from())).normalize();
        return first.dot(second)>.999;
    }

    private static void addPoint(List<Vec3> points, Point point) {
        Vec3 p=marker(point);
        if (points.isEmpty() || !p.equals(points.getLast())) points.add(p);
    }

    private static Vec3 marker(Point point) { return new Vec3(point.x(),point.y()+.25,point.z()); }
    private static Vec3 center(Point point) { return new Vec3(point.x(),point.y()+.9,point.z()); }
    private static int cell(double coordinate) { return (int)Math.floor(coordinate/CELL_SIZE); }

    private static void include(Set<Long> cells, Vec3 from, Vec3 to, double radius) {
        double dx=to.x-from.x, dy=to.y-from.y, dz=to.z-from.z;
        double lengthSquared=dx*dx+dy*dy+dz*dz;
        for (int x=cell(Math.min(from.x,to.x)-radius); x<=cell(Math.max(from.x,to.x)+radius); x++) {
            double px=(x+.5)*CELL_SIZE-from.x;
            for (int y=cell(Math.min(from.y,to.y)-radius); y<=cell(Math.max(from.y,to.y)+radius); y++) {
                double py=(y+.5)*CELL_SIZE-from.y;
                for (int z=cell(Math.min(from.z,to.z)-radius); z<=cell(Math.max(from.z,to.z)+radius); z++) {
                    long key=BlockPos.asLong(x,y,z);
                    if (cells.contains(key)) continue;
                    double pz=(z+.5)*CELL_SIZE-from.z;
                    double t=lengthSquared==0?0:Math.clamp((px*dx+py*dy+pz*dz)/lengthSquared,0,1);
                    double xGap=px-t*dx, yGap=py-t*dy, zGap=pz-t*dz;
                    if (xGap*xGap+yGap*yGap+zGap*zGap<=radius*radius) cells.add(key);
                }
            }
        }
    }

    private static List<ESPGeometry.Face> exterior(Set<Long> cells, BooleanSupplier cancelled) {
        List<ESPGeometry.Face> faces=new ArrayList<>();
        Direction[] directions=Direction.values();
        for (long cell : cells) {
            if (cancelled.getAsBoolean()) return List.of();
            int x=BlockPos.getX(cell), y=BlockPos.getY(cell), z=BlockPos.getZ(cell);
            AABB bounds=null;
            for (Direction side : directions) {
                long adjacent=BlockPos.asLong(x+side.getStepX(),y+side.getStepY(),z+side.getStepZ());
                if (cells.contains(adjacent)) continue;
                if (bounds==null) bounds=new AABB(x*CELL_SIZE,y*CELL_SIZE,z*CELL_SIZE,
                        (x+1)*CELL_SIZE,(y+1)*CELL_SIZE,(z+1)*CELL_SIZE);
                faces.add(new ESPGeometry.Face(bounds,side));
            }
        }
        return faces;
    }
}
