import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public final class RoundedGeometryRegression {
 static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("geometry "+checks);}
 static int inset(int row,int h,int r){int e=Math.min(row,h-1-row);if(e>=r)return 0;double d=r-e-.5;return (int)Math.ceil(r-Math.sqrt(r*r-d*d));}
 static void reference(GuiGraphicsExtractor g,int w,int h,int radius){if(w<2||h<2)return;int r=Math.max(0,Math.min(radius,Math.min(w,h)/2));for(int y=0;y<h;y++){int outer=inset(y,h,r);if(y==0||y==h-1)g.fill(outer,y,w-outer,y+1,-1);else{int inner=1+inset(y-1,h-2,Math.max(0,r-1));g.fill(outer,y,inner,y+1,-1);g.fill(w-inner,y,w-outer,y+1,-1);}}}
 public static void main(String[] args){
  for(int w:new int[]{2,3,10,17,80})for(int h:new int[]{2,3,10,29,80,2000})for(int r:new int[]{0,1,8,10,20}){
   var old=new GuiGraphicsExtractor(w,h);var now=new GuiGraphicsExtractor(w,h);reference(old,w,h,r);var geometry=RoundedRect.geometry(w,h,r,true);geometry.draw(now,0,0,-1);
   for(int y=0;y<h;y++)for(int x=0;x<w;x++)check(old.pixels[y][x]==now.pixels[y][x]);
   if(w==80&&h==2000&&r==10)check(now.calls<50&&old.calls>3900);
  }
  // Two rings plus the inset fill cover exactly the original rounded panel, without alpha stacking.
  for(int h:new int[]{29,2000}){
   var expected=new GuiGraphicsExtractor(80,h);var gradient=new GuiGraphicsExtractor(80,h);
   RoundedRect.geometry(80,h,10,false).draw(expected,0,0,-1);
   for(int i=0;i<2;i++)RoundedRect.geometry(80-2*i,h-2*i,10-i,true).draw(gradient,i,i,-1);
   RoundedRect.geometry(76,h-4,8,false).draw(gradient,2,2,-1);
   for(int y=0;y<h;y++)for(int x=0;x<80;x++)check(gradient.pixels[y][x]==expected.pixels[y][x]);
  }
  System.out.println("PASS: "+checks+" pixel-exact rounded outline/cache geometry, tall-panel bounded calls and two-pixel non-overlapping gradient coverage.");
 }
}
