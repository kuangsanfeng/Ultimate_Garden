package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.OptionalInt;

/** Same-frame GPU grayscale/Gaussian. Runs after world rendering and before any GUI is drawn. */
public final class ReplayBackdrop implements AutoCloseable {
    private static final Identifier SHADER=Identifier.fromNamespaceAndPath("ultimate_garden","core/mapping_backdrop");
    private static final RenderPipeline FILTER=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ultimate_garden","pipeline/mapping_backdrop"))
            .withVertexShader(SHADER).withFragmentShader(SHADER).withSampler("InSampler")
            .withUniform("ReplayBlur",UniformType.UNIFORM_BUFFER).withCull(false).build());
    private TextureTarget horizontal,vertical;
    private MappableRingBuffer horizontalUniform,verticalUniform;
    private int strength=9;
    private boolean closed;
    private long worldFrames;

    public void blur(int value){strength=Math.clamp(value,0,24);}
    public void processWorld(){
        if(closed)return;RenderSystem.assertOnRenderThread();var client=Minecraft.getInstance();
        if(client.level==null)return;
        int w=Math.max(1,client.getWindow().getGuiScaledWidth()),h=Math.max(1,client.getWindow().getGuiScaledHeight());
        if(horizontal==null||horizontal.width!=w||horizontal.height!=h){
            releaseTargets();horizontal=new TextureTarget("Mapping live horizontal",w,h,false);vertical=new TextureTarget("Mapping live vertical",w,h,false);
        }
        if(horizontalUniform==null){
            horizontalUniform=new MappableRingBuffer(()->"Mapping horizontal blur",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_MAP_WRITE,16);
            verticalUniform=new MappableRingBuffer(()->"Mapping vertical blur",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_MAP_WRITE,16);
        }
        var main=client.getMainRenderTarget();
        filter(main,horizontal,horizontalUniform,1f/w,0);
        filter(horizontal,vertical,verticalUniform,0,1f/h);
        vertical.blitAndBlendToTexture(main.getColorTextureView());worldFrames++;
    }
    private void filter(RenderTarget source,RenderTarget target,MappableRingBuffer uniform,float dx,float dy){
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        try(var mapped=encoder.mapBuffer(uniform.currentBuffer(),false,true)){
            Std140Builder.intoBuffer(mapped.data()).putVec4(dx,dy,strength,0);
        }
        try(var pass=encoder.createRenderPass(()->"Mapping live grayscale/Gaussian",target.getColorTextureView(),OptionalInt.empty())){
            pass.setPipeline(FILTER);pass.setUniform("ReplayBlur",uniform.currentBuffer());
            pass.bindTexture("InSampler",source.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0,3);
        }
        uniform.rotate();
    }
    public void draw(GuiGraphicsExtractor g,int width,int height){
        // Current world is already in the main target. GUI submission only dims it.
        if(Minecraft.getInstance().level==null)g.fill(0,0,width,height,0xFF343B39);
        g.fill(0,0,width,height,0xA8152420);
    }
    private void releaseTargets(){if(horizontal!=null){horizontal.destroyBuffers();horizontal=null;}if(vertical!=null){vertical.destroyBuffers();vertical=null;}}
    @Override public void close(){closed=true;releaseTargets();if(horizontalUniform!=null){horizontalUniform.close();verticalUniform.close();horizontalUniform=verticalUniform=null;}}
}
