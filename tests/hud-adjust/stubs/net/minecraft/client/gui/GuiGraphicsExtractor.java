package net.minecraft.client.gui;
import java.util.*;
public final class GuiGraphicsExtractor {
 public int backgrounds;public String title;public final List<String> values=new ArrayList<>();
 public Pose pose(){return new Pose();}public static class Pose {public void pushMatrix(){}public void popMatrix(){}public void translate(float x,float y){}public void scale(float s){}}
 public void text(Object font,String value,int x,int y,int color,boolean shadow){values.add(value);}
}
