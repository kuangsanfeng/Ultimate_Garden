package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Sampled volume and its exterior. Only geometry admitted to this preview can occlude a face. */
public record ReplayMesh(List<Part> parts,double focusY,List<String> errors) {
    public enum Mode { SURFACE, TWO_LAYERS, ALL }
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1){}
    public record Material(int color,boolean opaque,List<Box> boxes){public Material{boxes=List.copyOf(boxes);}}
    @FunctionalInterface public interface Materials {Material resolve(ChunkSnapshot.State state);}
    public record Face(double x,double y,double z,double w,double h,double d,int side,int color){}
    public record Part(Coordinate coordinate,List<Face> faces,double top,int depthSamples){
        public Part(Coordinate coordinate,List<Face> faces,double top){this(coordinate,faces,top,0);}
        public Part{faces=List.copyOf(faces);}
    }
    public ReplayMesh{parts=List.copyOf(parts);errors=List.copyOf(errors);}
    private record Solid(Box box,Material material,ChunkSnapshot.State state,int owner,boolean interior){}
    private static final class Column {
        final List<Solid> solids=new ArrayList<>();double top;int candidates;
    }
    private record Rectangle(double u0,double v0,double u1,double v1){}
    private static final class Grid {
        final int step;int owner;final Map<Coordinate,Column[][]> chunks=new LinkedHashMap<>();
        Grid(int step){this.step=step;}
        Column at(double x,double z){long bx=(long)Math.floor(x),bz=(long)Math.floor(z),cx=Math.floorDiv(bx,16),cz=Math.floorDiv(bz,16);
            if(cx<Integer.MIN_VALUE||cx>Integer.MAX_VALUE||cz<Integer.MIN_VALUE||cz>Integer.MAX_VALUE)return null;
            var columns=chunks.get(new Coordinate((int)cx,(int)cz));return columns==null?null:columns[(int)Math.floorMod(bx,16)/step][(int)Math.floorMod(bz,16)/step];
        }
    }
    private static boolean cube(Material m){return m.boxes.size()==1&&m.boxes.getFirst().equals(new Box(0,0,0,1,1,1));}
    public static ReplayMesh build(Map<Coordinate,ChunkSnapshot> data,List<Coordinate> selected,int step,Mode mode,Materials materials,BooleanSupplier cancelled,List<String> errors){
        step=Math.clamp(step,1,16);var grid=new Grid(step);var cache=new HashMap<ChunkSnapshot.State,Material>();int cells=(16+step-1)/step;
        for(var key:new LinkedHashSet<>(selected)){
            if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();var chunk=data.get(key);if(chunk==null)continue;
            Column[][] columns=new Column[cells][cells];grid.chunks.put(key,columns);
            for(int ix=0;ix<cells;ix++)for(int iz=0;iz<cells;iz++){
                if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();int x=ix*step,z=iz*step,X=Math.min(16,x+step),Z=Math.min(16,z+step);var column=new Column();columns[ix][iz]=column;
                var top=ReplaySampler.sample(chunk,x,z,X,Z,0,chunk.height()-1);if(top==null)continue;column.top=(top.y()+1)/16.;
                ReplaySampler.Sample[] levels=new ReplaySampler.Sample[top.y()+1];levels[top.y()]=top;
                for(int y=0;y<top.y();y++)levels[y]=ReplaySampler.sample(chunk,x,z,X,Z,y,y);
                var depth=ReplaySampler.depthSamples(chunk,x,z,X,Z,mode);column.candidates=depth.size();var interiorY=new HashSet<Integer>();if(mode!=Mode.SURFACE)for(var sample:depth)interiorY.add(sample.y());
                for(int y=0;y<levels.length;y++){
                    var sample=levels[y];if(sample==null)continue;var material=cache.computeIfAbsent(sample.state(),materials::resolve);int end=y+1;boolean interior=interiorY.contains(y);
                    // Equal full voxels may merge vertically without losing source states or exterior height.
                    if(cube(material)&&!interior)while(end<levels.length&&!interiorY.contains(end)&&levels[end]!=null&&levels[end].state().equals(sample.state()))end++;
                    int owner=grid.owner++;double baseX=(double)key.x()*16+x,baseZ=(double)key.z()*16+z;
                    for(var box:material.boxes){var world=new Box(baseX+box.x0*(X-x),y+box.y0,baseZ+box.z0*(Z-z),baseX+box.x1*(X-x),end-1+box.y1,baseZ+box.z1*(Z-z));column.solids.add(new Solid(world,material,sample.state(),owner,interior));}
                    y=end-1;
                }
            }
        }
        var result=new ArrayList<Part>();double sum=0;int topCount=0;
        for(var entry:grid.chunks.entrySet()){
            var faces=new ArrayList<Face>();double tops=0;int count=0,candidates=0;
            for(var row:entry.getValue())for(var column:row){
                if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();candidates+=column.candidates;if(!column.solids.isEmpty()){tops+=column.top;count++;sum+=column.top;topCount++;}
                for(var solid:column.solids)for(int side=0;side<6;side++)exterior(grid,column,solid,side,faces);
            }
            result.add(new Part(entry.getKey(),faces,count==0?0:tops/count,candidates));
        }
        return new ReplayMesh(result,topCount==0?0:sum/topCount,errors);
    }
    private static double min(Box b,int axis){return axis==0?b.x0:axis==1?b.y0:b.z0;}
    private static double max(Box b,int axis){return axis==0?b.x1:axis==1?b.y1:b.z1;}
    private static Rectangle rectangle(Box b,int axis){return axis==0?new Rectangle(b.y0,b.z0,b.y1,b.z1):axis==1?new Rectangle(b.x0,b.z0,b.x1,b.z1):new Rectangle(b.x0,b.y0,b.x1,b.y1);}
    private static void exterior(Grid grid,Column column,Solid solid,int side,List<Face> output){
        int axis=side/2;boolean positive=(side&1)==1;Box b=solid.box;double plane=positive?max(b,axis):min(b,axis);
        var pieces=new ArrayList<Rectangle>();pieces.add(rectangle(b,axis));
        var neighbors=new ArrayList<Solid>(column.solids);
        double x=(b.x0+b.x1)/2,z=(b.z0+b.z1)/2;
        if(axis==0)x=positive?Math.nextUp(plane):Math.nextDown(plane);if(axis==2)z=positive?Math.nextUp(plane):Math.nextDown(plane);
        var other=grid.at(x,z);if(other!=null&&other!=column)neighbors.addAll(other.solids);
        for(var neighbor:neighbors){
            if(neighbor==solid)continue;
            // Extra depth candidates keep their hidden opaque faces; the raster depth buffer still
            // occludes them. Surface contour and same-voxel shape seams always use real coverage.
            if(solid.interior&&neighbor.owner!=solid.owner&&neighbor.material.opaque)continue;
            if(!neighbor.material.opaque&&neighbor.owner!=solid.owner&&!neighbor.state.equals(solid.state))continue;
            if(positive?!(min(neighbor.box,axis)<=plane&&max(neighbor.box,axis)>plane):!(min(neighbor.box,axis)<plane&&max(neighbor.box,axis)>=plane))continue;
            var clip=rectangle(neighbor.box,axis);var remaining=new ArrayList<Rectangle>();for(var piece:pieces)subtract(piece,clip,remaining);pieces=remaining;if(pieces.isEmpty())return;
        }
        for(var r:pieces){double x0,y0,z0,w,h,d;
            if(axis==0){x0=plane;y0=r.u0;z0=r.v0;w=0;h=r.u1-r.u0;d=r.v1-r.v0;}
            else if(axis==1){x0=r.u0;y0=plane;z0=r.v0;w=r.u1-r.u0;h=0;d=r.v1-r.v0;}
            else{x0=r.u0;y0=r.v0;z0=plane;w=r.u1-r.u0;h=r.v1-r.v0;d=0;}
            output.add(new Face(x0/16,y0/16,z0/16,w/16,h/16,d/16,side,solid.material.color));
        }
    }
    private static void subtract(Rectangle r,Rectangle clip,List<Rectangle> out){
        double u0=Math.max(r.u0,clip.u0),v0=Math.max(r.v0,clip.v0),u1=Math.min(r.u1,clip.u1),v1=Math.min(r.v1,clip.v1);
        if(u1<=u0||v1<=v0){out.add(r);return;}
        add(out,r.u0,r.v0,u0,r.v1);add(out,u1,r.v0,r.u1,r.v1);add(out,u0,r.v0,u1,v0);add(out,u0,v1,u1,r.v1);
    }
    private static void add(List<Rectangle> out,double u0,double v0,double u1,double v1){if(u1>u0&&v1>v0)out.add(new Rectangle(u0,v0,u1,v1));}
}
