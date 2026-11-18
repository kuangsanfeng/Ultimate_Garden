package com.kuangsanfeng.ultimategarden.client.platform.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RoundedRect {
    private RoundedRect() { }
    @FunctionalInterface private interface Quad { void add(int x0,int y0,int x1,int y1); }
    /** Immutable local coordinates, reusable after scrolling without rebuilding geometry. */
    public static final class Geometry {
        private final int[] quads;
        private Geometry(int[] quads) { this.quads=quads; }
        public void draw(GuiGraphicsExtractor g,int x,int y,int color) {
            if ((color>>>24)==0)return;
            for(int i=0;i<quads.length;i+=4)g.fill(x+quads[i],y+quads[i+1],x+quads[i+2],y+quads[i+3],color);
        }
    }
    public static Geometry geometry(int width,int height,int radius,boolean outline) {
        var points=new java.util.ArrayList<Integer>();
        Quad sink=(a,b,c,d)->{if(c>a&&d>b){points.add(a);points.add(b);points.add(c);points.add(d);}};
        if(outline)emitOutline(sink,0,0,width,height,radius);else emitFill(sink,0,0,width,height,radius);
        int[] coordinates=new int[points.size()];for(int i=0;i<coordinates.length;i++)coordinates[i]=points.get(i);
        return new Geometry(coordinates);
    }
    public static void fill(GuiGraphicsExtractor g,int x,int y,int width,int height,int radius,int color) {
        if((color>>>24)!=0)emitFill((a,b,c,d)->g.fill(a,b,c,d,color),x,y,width,height,radius);
    }
    private static void emitFill(Quad sink,int x,int y,int width,int height,int radius) {
        if(width<=0||height<=0)return;
        int r=Math.max(0,Math.min(radius,Math.min(width,height)/2));
        if(height>2*r)sink.add(x,y+r,x+width,y+height-r);
        for(int row=0;row<r;row++) {
            int inset=inset(row,height,r);
            sink.add(x+inset,y+row,x+width-inset,y+row+1);
            sink.add(x+inset,y+height-row-1,x+width-inset,y+height-row);
        }
    }
    /** Coalesced straight sides: cost depends on radius, not expanded content height. */
    public static void outline(GuiGraphicsExtractor g,int x,int y,int width,int height,int radius,int color) {
        if((color>>>24)!=0)emitOutline((a,b,c,d)->g.fill(a,b,c,d,color),x,y,width,height,radius);
    }
    private static void emitOutline(Quad sink,int x,int y,int width,int height,int radius) {
        if(width<2||height<2)return;
        int r=Math.max(0,Math.min(radius,Math.min(width,height)/2));
        for(int row=0;row<height;) {
            int outer=inset(row,height,r);
            if(row==0||row==height-1) {sink.add(x+outer,y+row,x+width-outer,y+row+1);row++;}
            else {
                int inner=1+inset(row-1,height-2,Math.max(0,r-1));
                int end=row>=Math.max(1,r)&&row<height-Math.max(1,r)?height-Math.max(1,r):row+1;
                sink.add(x+outer,y+row,x+inner,y+end);
                sink.add(x+width-inner,y+row,x+width-outer,y+end);row=end;
            }
        }
    }
    private static int inset(int row, int height, int radius) {
        int edge = Math.min(row, height - 1 - row);
        if (edge >= radius) return 0;
        double dy = radius - edge - .5;
        return (int)Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
    }
}
