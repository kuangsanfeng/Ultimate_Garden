package net.minecraft.client.gui;
public final class GuiGraphicsExtractor {
 public final int[][] pixels;public int calls;
 public GuiGraphicsExtractor(int w,int h){pixels=new int[h][w];}
 public void fill(int x0,int y0,int x1,int y1,int color){calls++;for(int y=Math.max(0,y0);y<Math.min(pixels.length,y1);y++)for(int x=Math.max(0,x0);x<Math.min(pixels[0].length,x1);x++)pixels[y][x]++;}
}
