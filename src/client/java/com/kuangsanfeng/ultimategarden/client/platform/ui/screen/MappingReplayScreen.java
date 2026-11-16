package com.kuangsanfeng.ultimategarden.client.platform.ui.screen;

import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import com.kuangsanfeng.ultimategarden.client.function.mapping.replay.*;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.*;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.StyledButton;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.ToggleSwitch;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import java.math.BigInteger;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Full-screen local-map viewer. Preview state never changes the recorder layer or persisted origin. */
public final class MappingReplayScreen extends Screen {
    private static final int PANEL=0xFFE9F2EE,INPUT=0xFFF3F8F6,INK=0xFF172C26,SUB=0xFF48665C,LINE=0xFFCCDCD5,MINT=0xFF1B978C,BAR=0xFF192724;
    private static final AtomicInteger IDS=new AtomicInteger();
    private final Screen parent;private final LanguageManager language;private final ConfigManager configs;
    private final ReplaySession session;private final ReplayBackdrop backdrop=new ReplayBackdrop();
    private final ReplayCamera camera=new ReplayCamera();private final ReplaySelection selection=new ReplaySelection();private final MappingConfirmation confirmation=new MappingConfirmation();
    private final Identifier modelId=Identifier.fromNamespaceAndPath("ultimate_garden","mapping/replay_"+IDS.incrementAndGet());
    private DynamicTexture modelTexture;private ReplayRaster.Image displayed,uploaded;
    private LocalMapLibrary.World world;private Festival layer=Festival.NONE;private int centerX,centerZ,resolution=2,blur=9;private String renderDistance="16";private ReplayMesh.Mode mode=ReplayMesh.Mode.SURFACE;
    private boolean loaded,closed,dirty=true,editingFiles;private long lastFrame;private String message="",operationMessage="";
    private int side,top,footer=34;private final double[] scroll={0,0};private final int[] contentHeight={0,0};
    private record Placement(AbstractWidget widget,int side,int y){}
    private record Label(String key,int side,int y){}
    private final List<Placement> placements=new ArrayList<>();private final List<Label> labels=new ArrayList<>();private final Map<EditBox,Numeric> numbers=new IdentityHashMap<>();
    private record Numeric(BigInteger min,BigInteger max,Consumer<String> apply,String last){}
    private FlatButton mapButton,festivalButton,deleteButton,deleteMapButton;
    private ToggleSwitch bordersButton;
    private record Option(String id,String text,boolean selected,Runnable choose){}
    private List<Option> popup;private int popupY,popupScroll;
    private final Set<Integer> held=new HashSet<>();private int dragSide=-1,dragAxis=-1;private boolean canvasPressed,dragged;private double pressX,pressY,startScroll,startYaw;private Coordinate pressTile;
    public MappingReplayScreen(Screen parent,ConfigManager configs){super(Component.literal("Mapping Replay"));this.parent=parent;this.configs=configs;language=new LanguageManager(configs);session=new ReplaySession(MappingPaths.root(Minecraft.getInstance().gameDirectory.toPath()));}
    private String text(String key,Object...args){return language.text("ui.ultimategarden.replay."+key,args);}
    public void processWorldBackground(){backdrop.processWorld();}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        side=width>=680?154:Math.max(64,Math.min(109,width/4));top=width-2*side<300?150:94;
        footer=Math.max(34,font.split(Component.literal(text("hints")),Math.max(20,width-2*side-20)).size()*9+12);
        clearWidgets();placements.clear();labels.clear();numbers.clear();popup=null;confirmation.cancel();int y=42;
        labels.add(new Label("map",0,y));mapButton=button(0,y+fieldOffset("map"),()->world==null?text("empty"):world.config().worldName(),()->{
            popup(session.index.worlds().stream().map(w->new Option(w.config().worldName(),w.config().worldName(),world!=null&&world.config().worldName().equals(w.config().worldName()),()->chooseWorld(w))).toList(),mapButton.getY()+24);
        });y+=fieldOffset("map")+43;
        labels.add(new Label("festival",0,y));festivalButton=button(0,y+fieldOffset("festival"),()->festivalName(layer),()->{if(world!=null)popup(Arrays.stream(Festival.values()).filter(f->world.config().festivals().contains(f)).map(f->new Option(f.name(),festivalName(f),f==layer,()->{if(f!=layer){layer=f;selection.clear();confirmation.cancel();buildModel();}})).toList(),festivalButton.getY()+24);});y+=fieldOffset("festival")+43;
        labels.add(new Label("resolution",0,y));numeric(0,y+fieldOffset("resolution"),Integer.toString(resolution),BigInteger.ONE,BigInteger.valueOf(16),value->{resolution=Integer.parseInt(value);buildModel();},true);y+=fieldOffset("resolution")+43;
        labels.add(new Label("occlusion",0,y));button(0,y+fieldOffset("occlusion"),()->text("mode."+mode.name()),this::openDepthOptions);placements.getLast().widget.setTooltip(Tooltip.create(Component.literal(text("occlusion.help"))));y+=fieldOffset("occlusion")+43;
        labels.add(new Label("distance",0,y));numeric(0,y+fieldOffset("distance"),renderDistance,BigInteger.ONE,null,value->{renderDistance=value;buildModel();},false);y+=fieldOffset("distance")+43;
        labels.add(new Label("chunk_borders",0,y));bordersButton=new ToggleSwitch(0,y+fieldOffset("chunk_borders"),text("chunk_borders"),()->ThemePalette.from(configs.getUIConfig()),()->configs.getUIConfig().isMappingReplayShowChunkBorders(),this::toggleBorders);addWidget(bordersButton);placements.add(new Placement(bordersButton,0,y+fieldOffset("chunk_borders")));bordersButton.setTooltip(Tooltip.create(Component.literal(text("chunk_borders.help"))));y+=fieldOffset("chunk_borders")+43;
        labels.add(new Label("blur",0,y));numeric(0,y+fieldOffset("blur"),Integer.toString(blur),BigInteger.ZERO,BigInteger.valueOf(24),value->{blur=Integer.parseInt(value);backdrop.blur(blur);},false);y+=fieldOffset("blur")+43;contentHeight[0]=y+12;
        deleteButton=button(1,44,()->text("delete_selected"),this::deleteSelected);deleteMapButton=button(1,82,()->confirmation.armed(world,"delete")?language.text("ui.ultimategarden.mapping.confirm"):text("delete_map"),()->{
            if(world!=null&&confirmation.click(world,"delete",System.nanoTime()))deleteWorld();
        });deleteMapButton.setHeight(Math.max(48,font.split(Component.literal(language.text("ui.ultimategarden.mapping.confirm")),Math.max(20,side-32)).size()*10+12));contentHeight[1]=Math.max(280,deleteMapButton.getHeight()+240);
        int cw=width-2*side;for(int axis=0;axis<3;axis++){int a=axis;var box=new EditBox(font,0,0,Math.max(25,(cw-88)/3),18,Component.literal("XYZ".substring(a,a+1)));box.setMaxLength(24);box.setTextShadow(false);box.setBordered(false);box.setTextColor(0xFFF3F8F6);box.setValue(String.format(Locale.ROOT,"%.2f",a==0?camera.x:a==1?camera.y:camera.z));if(a==1)box.setTooltip(Tooltip.create(Component.literal(text("camera_y.help"))));addWidget(box);placements.add(new Placement(box,2,a));}
        positions();dirty=true;
        if(!loaded){loaded=true;refreshIndex(true);}else if(world!=null)buildModel();
    }
    private int fieldOffset(String key){return Math.max(9,font.split(Component.literal(text(key)),Math.max(20,side-24)).size()*9)+5;}
    private int yAt(String key){return labels.stream().filter(l->l.key.equals(key)).findFirst().orElseThrow().y;}
    private String festivalName(Festival f){return language.text("ui.ultimategarden.mapping.festival."+f.name());}
    private FlatButton button(int column,int y,Supplier<String> label,Runnable action){var b=new FlatButton(label,action);addWidget(b);placements.add(new Placement(b,column,y));return b;}
    private void numeric(int column,int y,String value,BigInteger min,BigInteger max,Consumer<String> apply,boolean fraction){
        var box=new EditBox(font,0,0,side-(fraction?49:24),20,Component.literal(value));box.setMaxLength(512);box.setTextShadow(false);box.setValue(value);box.setTextColor(INK);box.setTextColorUneditable(SUB);box.setBordered(false);addWidget(box);placements.add(new Placement(box,column,y));numbers.put(box,new Numeric(min,max,apply,value));String key=max==null?"distance":max.intValue()==16?"resolution":"blur";box.setTooltip(Tooltip.create(Component.literal(text(key+".help"))));
    }
    private void positions(){
        for(int c=0;c<2;c++)scroll[c]=Math.clamp(scroll[c],0,Math.max(0,contentHeight[c]-height));int cw=width-2*side;
        for(var p:placements){var w=p.widget;if(p.side==2){int a=p.y;if(cw<300){w.setX(side+65);w.setY(8+a*32);w.setWidth(Math.max(20,cw-75));}else{w.setX(side+82+a*Math.max(1,(cw-92)/3));w.setY(7);w.setWidth(Math.max(20,(cw-106)/3));}continue;}
            int x=p.side==0?12:width-side+12;w.setX(x);w.setY(p.y-(int)scroll[p.side]);w.setWidth(w instanceof ToggleSwitch?44:Math.max(16,side-24));
            if(w instanceof EditBox&&p.side==0&&p.y==yAt("resolution")+fieldOffset("resolution")){w.setX(x+25);w.setWidth(Math.max(16,side-49));}
            w.visible=w.getY()+w.getHeight()>0&&w.getY()<height;
        }
        if(mapButton!=null)mapButton.active=!session.index.worlds().isEmpty()&&!editingFiles;if(festivalButton!=null)festivalButton.active=world!=null&&!editingFiles;
        if(deleteButton!=null)deleteButton.active=world!=null&&!selection.selected.isEmpty()&&!editingFiles;if(deleteMapButton!=null)deleteMapButton.active=world!=null&&!editingFiles;
    }
    private void refreshIndex(boolean initial){String retained=operationMessage;session.invalidateFiles();session.refresh(index->Minecraft.getInstance().execute(()->{
        if(closed)return;String current=world==null?com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoService.snapshot().island():world.config().worldName();
        var next=index.worlds().stream().filter(w->w.config().worldName().equals(current)).findFirst().orElse(index.worlds().isEmpty()?null:index.worlds().getFirst());
        if(initial||world==null||next==null||!next.config().worldName().equals(world.config().worldName()))chooseWorld(next);else{world=next;buildModel();}
        operationMessage=retained;message=retained+(retained.isBlank()||index.errors().isEmpty()?"":"; ")+String.join("; ",index.errors());positions();
    }));}
    private void chooseWorld(LocalMapLibrary.World value){world=value;message="";operationMessage="";layer=Festival.NONE;centerX=centerZ=0;camera.panX=camera.panY=0;selection.clear();confirmation.cancel();popup=null;buildModel();}
    private void buildModel(){displayed=null;uploaded=null;dirty=true;if(world!=null)session.build(world,layer,centerX,centerZ,renderDistance,resolution,mode,new ReplayMaterials());else session.clear();}
    private void selectDepth(ReplayMesh.Mode requested){if(mode==requested)return;mode=requested;buildModel();}
    private void toggleBorders(){var ui=configs.getUIConfig();ui.setMappingReplayShowChunkBorders(!ui.isMappingReplayShowChunkBorders());if(!configs.saveUIConfig())message=text("preferences_save_failed");dirty=true;}
    private void openDepthOptions(){popup(ReplayDepth.options(mode).stream().map(m->new Option(m.name(),text("mode."+m.name()),m==mode,()->selectDepth(m))).toList(),(int)(yAt("occlusion")-scroll[0])+fieldOffset("occlusion"));}
    private void popup(List<Option> values,int y){commitFocus();popup=values;popupY=Math.clamp(y,0,Math.max(0,height-24));popupScroll=0;held.clear();}
    private int optionHeight(int index){return Math.max(24,font.split(Component.literal(popup.get(index).text),Math.max(10,side-38)).size()*9+10);}
    private int popupHit(double y){int rowY=popupY;for(int i=popupScroll;i<popup.size()&&rowY<height;i++){int h=optionHeight(i);if(y>=rowY&&y<rowY+h)return i;rowY+=h;}return -1;}
    private void deleteSelected(){if(world==null||selection.selected.isEmpty())return;confirmation.cancel();editingFiles=true;var target=world;var chosen=Set.copyOf(selection.selected);var chosenLayer=layer;
        MappingController.INSTANCE.editLocalMap(target.config().worldName(),chosenLayer,chosen,false).whenComplete((result,error)->Minecraft.getInstance().execute(()->{if(closed)return;editingFiles=false;operationMessage=error==null?String.join("; ",result.failures()):error.toString();message=operationMessage;selection.clear();refreshIndex(false);}));}
    private void deleteWorld(){if(world==null)return;editingFiles=true;var target=world;MappingController.INSTANCE.editLocalMap(target.config().worldName(),layer,Set.of(),true).whenComplete((result,error)->Minecraft.getInstance().execute(()->{if(closed)return;editingFiles=false;operationMessage=error==null?String.join("; ",result.failures()):error.toString();message=operationMessage;world=null;selection.clear();refreshIndex(true);}));}
    private void commit(EditBox box){var n=numbers.get(box);if(n==null)return;BigInteger value=ReplayNumbers.commit(box.getValue(),n.last,n.min,n.max);String text=value.toString();box.setValue(text);numbers.put(box,new Numeric(n.min,n.max,n.apply,text));if(!text.equals(n.last))n.apply.accept(text);}
    private void commitFocus(){if(getFocused() instanceof EditBox box){if(numbers.containsKey(box))commit(box);else commitAxis(box);}setFocused(null);}
    private void commitAxis(EditBox box){for(var p:placements)if(p.widget==box&&p.side==2){double x=camera.x,y=camera.y,z=camera.z;double current=p.y==0?x:p.y==1?y:z;
        if(!box.getValue().equals(String.format(Locale.ROOT,"%.2f",current)))try{double value=Double.parseDouble(box.getValue());if(Double.isFinite(value))camera.axis(p.y,value);}catch(NumberFormatException ignored){}
        syncAxes();dirty|=x!=camera.x||y!=camera.y||z!=camera.z;}}
    private void syncAxes(){for(var p:placements)if(p.side==2&&p.widget instanceof EditBox box)box.setValue(String.format(Locale.ROOT,"%.2f",p.y==0?camera.x:p.y==1?camera.y:camera.z));}
    private boolean canvas(double x,double y){return x>=side&&x<width-side&&y>=top&&y<height-footer;}
    private Coordinate hit(double x,double y){return world==null||displayed==null?null:displayed.hit((int)x-side,(int)y-top);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(dy==0)return false;
        if(popup!=null&&x<side){popupScroll=Math.clamp(popupScroll-(int)Math.signum(dy),0,Math.max(0,popup.size()-1));return true;}
        for(var p:placements)if(p.side==0&&p.widget instanceof EditBox box&&box.isMouseOver(x,y)&&y>=0&&y<height){commit(box);var n=numbers.get(box);BigInteger v=ReplayNumbers.wheel(n.last,n.last,n.min,n.max,dy);box.setValue(v.toString());commit(box);return true;}
        if(x<side||x>=width-side){int c=x<side?0:1;scroll[c]-=dy*24;positions();return true;}
        if(canvas(x,y)){camera.wheel(dy);dirty=true;return true;}return true;
    }
    private int sliderAt(double x,double y){int cw=width-2*side;for(int a=0;a<3;a++){int sx=cw<300?side+65:side+82+a*(cw-92)/3,sy=cw<300?28+a*32:31,sw=cw<300?cw-75:(cw-106)/3;if(x>=sx&&x<=sx+sw&&Math.abs(y-sy)<7)return a;}return -1;}
    private void slider(int axis,double x){int cw=width-2*side;double sx=cw<300?side+65:side+82+axis*(cw-92)/3,sw=Math.max(1,cw<300?cw-75:(cw-106)/3),t=Math.clamp((x-sx)/sw,0,1);camera.axis(axis,axis==1?-3+t*6:-2.5+t*5);syncAxes();dirty=true;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(popup!=null){if(e.x()<side&&e.y()>=popupY){int row=popupHit(e.y());if(row>=0&&row<popup.size()){var action=popup.get(row).choose;popup=null;action.run();return true;}}popup=null;return true;}
        if(e.button()==0&&(dragAxis=sliderAt(e.x(),e.y()))>=0){commitFocus();slider(dragAxis,e.x());return true;}
        if(canvas(e.x(),e.y())){commitFocus();held.clear();var tile=hit(e.x(),e.y());if(e.button()==1){if(tile!=null){centerX=tile.x();centerZ=tile.z();camera.panX=camera.panY=0;confirmation.cancel();buildModel();}return true;}
            if(e.button()==0){canvasPressed=true;dragged=false;pressX=e.x();pressY=e.y();pressTile=tile;startYaw=camera.yaw();return true;}}
        if(e.x()<side||e.x()>=width-side){int c=e.x()<side?0:1;var hit=placements.stream().filter(p->p.side==c&&p.widget.visible&&p.widget.isMouseOver(e.x(),e.y())).findFirst();
            if(hit.isPresent()){if(getFocused()!=hit.get().widget)commitFocus();return super.mouseClicked(e,doubleClick);}commitFocus();if(e.button()==0&&contentHeight[c]>height){dragSide=c;pressY=e.y();startScroll=scroll[c];return true;}return true;}
        if(getFocused() instanceof EditBox box&&!box.isMouseOver(e.x(),e.y()))commitFocus();return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(dragAxis>=0){slider(dragAxis,e.x());return true;}if(dragSide>=0){scroll[dragSide]=startScroll+pressY-e.y();positions();return true;}
        if(canvasPressed){double distance=Math.hypot(e.x()-pressX,e.y()-pressY);if(distance>=3)dragged=true;if(pressTile==null&&dragged){camera.rotate(startYaw+(e.x()-pressX)*.009);syncAxes();dirty=true;}return true;}return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragAxis>=0||dragSide>=0){dragAxis=dragSide=-1;return true;}
        if(canvasPressed){canvasPressed=false;if(!dragged&&Math.hypot(e.x()-pressX,e.y()-pressY)<5){boolean ctrl=(e.modifiers()&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SUPER))!=0,shift=(e.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0;selection.click(pressTile,ctrl,shift,displayed==null?List.of():displayed.chunks());dirty=true;}return true;}return super.mouseReleased(e);
    }
    @Override public boolean keyPressed(KeyEvent e){if(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.MacroKeyBindings.matchesMenuKey(e)&&!(getFocused() instanceof EditBox)){onClose();return true;}if(e.key()==GLFW.GLFW_KEY_ESCAPE){if(popup!=null){popup=null;return true;}onClose();return true;}
        if(popup!=null){if(e.key()==GLFW.GLFW_KEY_UP||e.key()==GLFW.GLFW_KEY_DOWN){popupScroll=Math.clamp(popupScroll+(e.key()==GLFW.GLFW_KEY_DOWN?1:-1),0,Math.max(0,popup.size()-1));return true;}if(e.key()==GLFW.GLFW_KEY_ENTER&&!popup.isEmpty()){var action=popup.get(popupScroll).choose;popup=null;action.run();return true;}return true;}
        if(getFocused() instanceof EditBox box){if(e.key()==GLFW.GLFW_KEY_ENTER||e.key()==GLFW.GLFW_KEY_KP_ENTER){commitFocus();return true;}return super.keyPressed(e);}
        if(e.key()>=GLFW.GLFW_KEY_RIGHT&&e.key()<=GLFW.GLFW_KEY_UP){held.add(e.key());return true;}return super.keyPressed(e);}
    @Override public boolean keyReleased(KeyEvent e){held.remove(e.key());return super.keyReleased(e);}
    @Override public void tick(){if(!minecraft.isWindowActive()){held.clear();canvasPressed=false;dragSide=dragAxis=-1;}}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
        long now=System.nanoTime();double dt=lastFrame==0?16:Math.min(48,(now-lastFrame)/1_000_000.);lastFrame=now;
        if(!held.isEmpty()){camera.panX+=((held.contains(GLFW.GLFW_KEY_LEFT)?1:0)-(held.contains(GLFW.GLFW_KEY_RIGHT)?1:0))*dt*.29;camera.panY+=((held.contains(GLFW.GLFW_KEY_UP)?1:0)-(held.contains(GLFW.GLFW_KEY_DOWN)?1:0))*dt*.29;dirty=true;}
        double old=camera.zoom;camera.advance(dt);dirty|=old!=camera.zoom;positions();backdrop.draw(g,width,height);
        g.fill(0,0,side,height,PANEL);g.fill(width-side,0,width,height,PANEL);g.fill(side,0,width-side,top,BAR);g.fill(side,height-footer,width-side,height,BAR);
        g.fill(side,top-1,width-side,top,0xFF40534D);g.fill(side-1,0,side,height,LINE);g.fill(width-side,0,width-side+1,height,LINE);g.fill(side,height-footer,width-side,height-footer+1,0xFF40534D);
        if(!session.loading&&session.image==null)dirty=true;
        if(dirty&&!session.loading&&world!=null){dirty=false;session.frame(camera,centerX,centerZ,width-2*side,Math.max(1,height-top-footer),selection.selected,configs.getUIConfig().isMappingReplayShowChunkBorders());}
        if(session.image!=null&&session.image!=uploaded){displayed=session.image;uploaded=displayed;upload(displayed);}
        g.enableScissor(side,top,width-side,height-footer);if(world!=null&&displayed!=null&&modelTexture!=null)g.blit(modelId,side,top,width-side,height-footer,0,1,0,1);g.disableScissor();
        if(world==null||session.loading){g.text(font,text(world==null?"empty":"loading"),side+12,top+45,0xFFCFDED8,false);}
        g.text(font,text("rotate_hint"),side+12,top+12,0xFFBFD3CC,false);
        g.text(font,text("center_count",centerX,centerZ,displayed==null?0:displayed.chunks().size()),side+12,height-footer-14,0xFFCDE1D9,false);
        g.enableScissor(side+4,height-footer+2,width-side-4,height);g.textWithWordWrap(font,Component.literal(text("hints")),side+10,height-footer+6,Math.max(20,width-2*side-20),0xFFBAD0C8,false);g.disableScissor();
        drawCamera(g);if(sliderAt(mouseX,mouseY)==1)g.setTooltipForNextFrame(font,font.split(Component.literal(text("camera_y.help")),170),mouseX,mouseY);
        for(int c=0;c<2;c++){int x=c==0?0:width-side;g.enableScissor(x,0,x+side,height);g.text(font,text(c==0?"title":"operations"),x+12,17-(int)scroll[c],INK,false);
            for(var label:labels)if(label.side==c){int y=label.y-(int)scroll[c];g.textWithWordWrap(font,Component.literal(text(label.key)),x+12,y,side-24,SUB,false);if(label.key.equals("resolution"))g.text(font,"1 /",12,y+fieldOffset("resolution")+5,INK,false);}
            for(var p:placements)if(p.side==c&&p.widget.visible){if(p.widget instanceof EditBox)g.fill(p.widget.getX()-4,p.widget.getY()-4,p.widget.getRight()+4,p.widget.getBottom()+3,INPUT);p.widget.extractRenderState(g,mouseX,mouseY,partial);}
            if(c==1){int y=deleteMapButton.getY()+deleteMapButton.getHeight()+18;String selected=selection.selected.isEmpty()?text("none_selected"):text("selected_count",selection.selected.size())+"\n"+selection.selected.stream().limit(6).map(p->p.x()+", "+p.z()).reduce((a,b)->a+"\n"+b).orElse("");g.textWithWordWrap(font,Component.literal(selected),x+12,y,side-24,SUB,false);
                String errors=message.isBlank()?session.error:message;if(errors.isBlank()&&!session.mesh.errors().isEmpty())errors=String.join("; ",session.mesh.errors());if(!errors.isBlank())g.textWithWordWrap(font,Component.literal(errors),x+12,y+100,side-24,0xFF823C40,false);contentHeight[1]=Math.max(280,deleteMapButton.getHeight()+230+Math.max(font.split(Component.literal(selected),Math.max(10,side-24)).size()*9,font.split(Component.literal(errors),Math.max(10,side-24)).size()*9));}
            g.disableScissor();}
        for(var p:placements)if(p.side==2)p.widget.extractRenderState(g,mouseX,mouseY,partial);
        if(popup!=null){g.enableScissor(0,0,side,height);int y=popupY;for(int i=popupScroll;i<popup.size();i++){int h=optionHeight(i);if(y>=height)break;g.fill(8,y,side-8,y+h,INPUT);g.fill(8,y+h-1,side-8,y+h,LINE);if(popup.get(i).selected){line(g,12,y+10,15,y+13,MINT);line(g,15,y+13,21,y+6,MINT);}g.textWithWordWrap(font,Component.literal(popup.get(i).text),26,y+5,side-38,INK,false);y+=h;}g.disableScissor();}
    }
    private String angleText(){double pitch=Math.toDegrees(camera.pitch());String direction=Math.abs(pitch)<1e-6?"level":pitch>0?"down":"up";double shown=language.current()==com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageType.CHINESE?Math.abs(pitch):pitch;return text("angles."+direction,Math.toDegrees(camera.yaw()),shown);}
    private void drawCamera(GuiGraphicsExtractor g){int cw=width-2*side;for(int a=0;a<3;a++){int x=cw<300?side+65:side+82+a*(cw-92)/3,y=cw<300?28+a*32:31,w=Math.max(1,cw<300?cw-75:(cw-106)/3);g.text(font,"XYZ".substring(a,a+1),x-12,y-19,0xFFBAD0C8,false);g.fill(x,y,x+w,y+2,0xFF48665C);double v=a==0?camera.x:a==1?camera.y:camera.z,t=a==1?(v+3)/6:(v+2.5)/5;int knob=(int)(x+t*w);g.fill(knob-2,y-3,knob+3,y+5,MINT);}
        int y=cw<300?110:54;g.enableScissor(side,0,width-side,top);g.text(font,String.format(Locale.ROOT,"(%.2f, %.2f, %.2f)",camera.x,camera.y,camera.z),side+12,y,0xFFF3F8F6,false);g.text(font,angleText(),side+12,y+14,0xFFBAD0C8,false);g.disableScissor();
        double[][] axes={{22,0,0},{0,24,0},{0,0,22}};int[] colors={0xFFF3A5A0,0xFFA2E6AA,0xFFA7C7F9};for(int a=0;a<3;a++){var p=camera.basis(axes[a][0],axes[a][1],axes[a][2]);int ox=side+31,oy=35,ex=(int)(ox+p.x()),ey=(int)(oy+p.y());line(g,ox,oy,ex,ey,colors[a]);g.text(font,"XYZ".substring(a,a+1),ex+2,ey-4,colors[a],false);}
    }
    private static void line(GuiGraphicsExtractor g,int x,int y,int X,int Y,int color){int n=Math.max(1,Math.max(Math.abs(X-x),Math.abs(Y-y)));for(int i=0;i<=n;i++){int px=x+(X-x)*i/n,py=y+(Y-y)*i/n;g.fill(px,py,px+2,py+2,color);}}
    private void upload(ReplayRaster.Image frame){var image=new NativeImage(frame.width(),frame.height(),false);for(int y=0;y<frame.height();y++)for(int x=0;x<frame.width();x++)image.setPixel(x,y,frame.argb()[y*frame.width()+x]);if(modelTexture==null){modelTexture=new DynamicTexture(()->"Mapping depth-tested map",image);minecraft.getTextureManager().register(modelId,modelTexture);}else if(modelTexture.getPixels().getWidth()!=frame.width()||modelTexture.getPixels().getHeight()!=frame.height()){minecraft.getTextureManager().release(modelId);modelTexture=new DynamicTexture(()->"Mapping depth-tested map",image);minecraft.getTextureManager().register(modelId,modelTexture);}else{modelTexture.setPixels(image);modelTexture.upload();}}
    @Override public void onClose(){confirmation.cancel();held.clear();canvasPressed=false;dragSide=dragAxis=-1;minecraft.setScreen(parent);}
    @Override public void removed(){closed=true;confirmation.cancel();held.clear();popup=null;session.close();backdrop.close();if(modelTexture!=null){minecraft.getTextureManager().release(modelId);modelTexture=null;}}
    private final class FlatButton extends StyledButton {
        private final Supplier<String> label;
        FlatButton(Supplier<String> label,Runnable action){super(0,0,100,28,"",false,()->ThemePalette.from(configs.getUIConfig()),action);this.label=label;}
        @Override public void onPress(InputWithModifiers input){if(this==deleteMapButton&&!(input instanceof MouseButtonEvent))return;super.onPress(input);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt){boolean armed=this==deleteMapButton&&confirmation.armed(world,"delete");g.fill(getX(),getY(),getRight(),getBottom(),armed?0xFFF4DAD9:active?INPUT:0xFFE4EDE8);g.fill(getX(),getBottom()-1,getRight(),getBottom(),LINE);if(armed)g.textWithWordWrap(font,Component.literal(label.get()),getX()+5,getY()+7,Math.max(10,getWidth()-10),INK,false);else g.text(font,font.plainSubstrByWidth(label.get(),Math.max(10,getWidth()-10)),getX()+5,getY()+7,active?INK:0xFF61776F,false);}
    }
}
