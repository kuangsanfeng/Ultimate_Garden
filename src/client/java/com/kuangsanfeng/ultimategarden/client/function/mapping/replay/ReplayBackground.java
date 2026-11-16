package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

/** CPU reference for grayscale/separable Gaussian tests. Live rendering uses the GPU ReplayBackdrop. */
public final class ReplayBackground {
    private ReplayBackground(){}
    public static int[] filter(int[] source,int width,int height,int strength){
        float[] gray=new float[source.length];for(int i=0;i<source.length;i++){int c=source[i];gray[i]=(float)((c>>16&255)*.2126+(c>>8&255)*.7152+(c&255)*.0722);}
        if(strength>0){int radius=(int)Math.ceil(strength*3.);double[] kernel=new double[radius*2+1];double total=0;for(int i=-radius;i<=radius;i++){double v=Math.exp(-.5*i*i/(strength*strength));kernel[i+radius]=v;total+=v;}for(int i=0;i<kernel.length;i++)kernel[i]/=total;
            float[] temp=new float[source.length],result=new float[source.length];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){double value=0;for(int k=-radius;k<=radius;k++)value+=gray[y*width+Math.clamp(x+k,0,width-1)]*kernel[k+radius];temp[y*width+x]=(float)value;}
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){double value=0;for(int k=-radius;k<=radius;k++)value+=temp[Math.clamp(y+k,0,height-1)*width+x]*kernel[k+radius];result[y*width+x]=(float)value;}gray=result;
        }
        int[] out=new int[source.length];for(int i=0;i<out.length;i++){int g=Math.clamp(Math.round(gray[i]),0,255);out[i]=0xFF000000|g<<16|g<<8|g;}return out;
    }
}
