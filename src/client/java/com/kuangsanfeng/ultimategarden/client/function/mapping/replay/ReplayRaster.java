package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Orthographic software depth buffer: geometry remains normally occluded, never an x-ray overlay. */
public final class ReplayRaster {
    public record Image(int width,int height,int[] argb,int[] owners,List<Coordinate> chunks,List<Outline> outlines){
        public Coordinate hit(int x,int y){if(x<0||y<37||x>=width||y>=height-29)return null;int id=owners[y*width+x];if(id>=0)return chunks.get(id);
            for(int i=outlines.size()-1;i>=0;i--){var o=outlines.get(i);if(inside(o.points,x,y))return o.chunk;}return null;}
    }
    public record Outline(Coordinate chunk,ReplayCamera.Point[] points,double depth){}
    private final int width,height;private final int[] pixels,owner;private final double[] depth;
    private ReplayRaster(int width,int height){this.width=width;this.height=height;pixels=new int[width*height];owner=new int[pixels.length];Arrays.fill(owner,-1);depth=new double[pixels.length];Arrays.fill(depth,-Double.MAX_VALUE);}
    public static Image render(ReplayMesh mesh,ReplayCamera camera,int cx,int cz,int w,int h,Set<Coordinate> selected,BooleanSupplier cancelled){
        return render(mesh,camera,cx,cz,w,h,selected,true,cancelled);
    }
    public static Image render(ReplayMesh mesh,ReplayCamera camera,int cx,int cz,int w,int h,Set<Coordinate> selected,boolean showChunkBorders,BooleanSupplier cancelled){
        var out=new ReplayRaster(Math.max(1,w),Math.max(1,h));double extent=1;for(var part:mesh.parts())extent=Math.max(extent,Math.max(Math.abs((double)part.coordinate().x()-cx),Math.abs((double)part.coordinate().z()-cz)));
        double scale=Math.min(w*.79,h*.71)*camera.zoom/(extent*2.05+2)*1.11;
        var chunks=new ArrayList<Coordinate>();var outlines=new ArrayList<Outline>();var transparent=new ArrayList<Pending>();
        var ordered=mesh.parts().stream().sorted(Comparator.comparingDouble(p->camera.basis(p.coordinate().x(),0,p.coordinate().z()).depth())).toList();
        for(var part:ordered){if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();int id=chunks.size();chunks.add(part.coordinate());
            for(var f:part.faces()){
                if((f.color()>>>24)<255)transparent.add(new Pending(f,id,selected.contains(part.coordinate())));
                else paint(out,f,camera,cx,cz,w,h,scale,mesh.focusY(),id,selected.contains(part.coordinate()));
            }
            double x=part.coordinate().x(),z=part.coordinate().z(),y=(camera.pitch()<0?part.faces().stream().mapToDouble(ReplayMesh.Face::y).min().orElse(part.top()):part.top())-mesh.focusY();var p=new ReplayCamera.Point[]{camera.project(x,y,z,cx,cz,w,h,scale),camera.project(x+1,y,z,cx,cz,w,h,scale),camera.project(x+1,y,z+1,cx,cz,w,h,scale),camera.project(x,y,z+1,cx,cz,w,h,scale)};
            outlines.add(new Outline(part.coordinate(),p,camera.basis(x,y,z).depth()));
        }
        transparent.sort(Comparator.comparingDouble(p->camera.basis(p.face.x()+p.face.w()/2,p.face.y()+p.face.h()/2,p.face.z()+p.face.d()/2).depth()));
        for(var p:transparent)paint(out,p.face,camera,cx,cz,w,h,scale,mesh.focusY(),p.id,p.selected);
        if(showChunkBorders)for(var o:outlines){int color=selected.contains(o.chunk)?0xFFA3FFEF:o.chunk.x()==cx&&o.chunk.z()==cz?0xFFF3DCA4:0x99DEEDC7;for(int i=0;i<4;i++)out.line(o.points[i],o.points[(i+1)%4],color);}
        return new Image(out.width,out.height,out.pixels,out.owner,List.copyOf(chunks),List.copyOf(outlines));
    }
    private record Pending(ReplayMesh.Face face,int id,boolean selected){}
    private static void paint(ReplayRaster out,ReplayMesh.Face f,ReplayCamera camera,int cx,int cz,int w,int h,double scale,double focusY,int id,boolean selected){
        double nx=f.side()==0?-1:f.side()==1?1:0,ny=f.side()==2?-1:f.side()==3?1:0,nz=f.side()==4?-1:f.side()==5?1:0;
        if(camera.basis(nx,ny,nz).depth()<=0)return;double[][] vertices=vertices(f);var points=new ReplayCamera.Point[4];
        for(int i=0;i<4;i++)points[i]=camera.project(vertices[i][0],vertices[i][1]-focusY,vertices[i][2],cx,cz,w,h,scale);
        double shade=f.side()==3?1:f.side()<2?.72:f.side()>3?.84:.6;int color=shade(f.color(),shade);if(selected)color=blend(color,0x604EDFD7);
        out.triangle(points[0],points[1],points[2],color,id);out.triangle(points[0],points[2],points[3],color,id);
    }
    private static double[][] vertices(ReplayMesh.Face f){double x=f.x(),y=f.y(),z=f.z(),X=x+f.w(),Y=y+f.h(),Z=z+f.d();return switch(f.side()){
        case 0->new double[][]{{x,y,z},{x,Y,z},{x,Y,Z},{x,y,Z}};case 1->new double[][]{{X,y,z},{X,Y,z},{X,Y,Z},{X,y,Z}};
        case 2->new double[][]{{x,y,z},{X,y,z},{X,y,Z},{x,y,Z}};case 3->new double[][]{{x,Y,z},{X,Y,z},{X,Y,Z},{x,Y,Z}};
        case 4->new double[][]{{x,y,z},{X,y,z},{X,Y,z},{x,Y,z}};default->new double[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};};}
    private void triangle(ReplayCamera.Point a,ReplayCamera.Point b,ReplayCamera.Point c,int color,int id){
        double area=edge(a,b,c.x(),c.y());if(Math.abs(area)<1e-9)return;
        int x0=(int)Math.clamp(Math.floor(Math.min(a.x(),Math.min(b.x(),c.x()))),0,width-1),x1=(int)Math.clamp(Math.ceil(Math.max(a.x(),Math.max(b.x(),c.x()))),0,width-1);
        int y0=(int)Math.clamp(Math.floor(Math.min(a.y(),Math.min(b.y(),c.y()))),37,Math.max(37,height-30)),y1=(int)Math.clamp(Math.ceil(Math.max(a.y(),Math.max(b.y(),c.y()))),37,Math.max(37,height-30));
        for(int y=y0;y<=y1&&y<height;y++)for(int x=x0;x<=x1;x++){double u=edge(b,c,x+.5,y+.5)/area,v=edge(c,a,x+.5,y+.5)/area,t=1-u-v;if(u<0||v<0||t<0)continue;double d=u*a.depth()+v*b.depth()+t*c.depth();int index=y*width+x;if(d>=depth[index]){pixels[index]=blend(pixels[index],color);depth[index]=d;owner[index]=id;}}
    }
    private static double edge(ReplayCamera.Point a,ReplayCamera.Point b,double x,double y){return (b.x()-a.x())*(y-a.y())-(b.y()-a.y())*(x-a.x());}
    public static boolean inside(ReplayCamera.Point[] p,double x,double y){double sign=0;for(int i=0;i<4;i++){double e=edge(p[i],p[(i+1)%4],x,y);if(Math.abs(e)<.001)continue;if(sign!=0&&Math.signum(sign)!=Math.signum(e))return false;sign=e;}return sign!=0;}
    private void line(ReplayCamera.Point a,ReplayCamera.Point b,int color){double len=Math.max(Math.abs(b.x()-a.x()),Math.abs(b.y()-a.y()));if(len>100000)return;int count=Math.max(1,(int)Math.ceil(len));for(int i=0;i<=count;i++){double t=(double)i/count;int x=(int)Math.round(a.x()+(b.x()-a.x())*t),y=(int)Math.round(a.y()+(b.y()-a.y())*t);if(x>=0&&x<width&&y>=37&&y<height-29)pixels[y*width+x]=blend(pixels[y*width+x],color);}}
    private static int shade(int c,double k){return c&0xFF000000|(int)((c>>16&255)*k)<<16|(int)((c>>8&255)*k)<<8|(int)((c&255)*k);}
    private static int blend(int old,int c){int alpha=c>>>24;if(alpha==255)return c;if(alpha==0)return old;int oldAlpha=(old>>>24)*(255-alpha)/255,a=alpha+oldAlpha;return a<<24|(((c>>16&255)*alpha+(old>>16&255)*oldAlpha)/a)<<16|(((c>>8&255)*alpha+(old>>8&255)*oldAlpha)/a)<<8|((c&255)*alpha+(old&255)*oldAlpha)/a;}
}
