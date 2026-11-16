
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.ESPConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.PestConfig;
import java.util.*;

import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.adapters.PestESP;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.ESPTarget;
import net.minecraft.world.entity.EntityType;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
public final class PestESPRegression {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("Pest ESP check "+checks);}
 public static void main(String[] args){
  var config=new PestConfig();var collector=new PestESP(config);
  var bodies=new ArrayList<LevelExtractionContext.Body>();
  for(var type:List.of(EntityType.BAT,EntityType.SILVERFISH,EntityType.OTHER))
   for(boolean alive:new boolean[]{true,false}) for(boolean removed:new boolean[]{true,false})
    bodies.add(new LevelExtractionContext.Body(type,alive,removed));
  var context=new LevelExtractionContext(bodies);var output=new ArrayList<ESPTarget>();
  check(config.isPestESPEnabled());collector.collect(context,output::add);check(output.size()==2);
  for(var target:output){check(target.type().equals(ESPConfig.SILVERFISH));check(target.bounds().minX==.5);check(target.bounds().maxX==1.5);}
  config.setPestESPEnabled(false);output.clear();collector.collect(context,output::add);check(output.isEmpty());
  config.setPestESPEnabled(true);collector.collect(context,output::add);check(output.size()==2);
  config.resetToDefault();check(config.isPestESPEnabled());
  System.out.println("PASS: "+checks+" production PestESP Bat/Silverfish collection, shared toggle, exclusion and interpolation checks; entity/context boundaries are stubbed.");
 }
}
