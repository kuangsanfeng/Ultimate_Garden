import java.util.*;import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.*;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.InstantTransmissionConfig;
public final class FlightRoutingRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Flight/route "+checks);}
 static final Terrain CLEAR=new Terrain(){public Check transfer(Point a,Point b){return new Check(Knowledge.CLEAR,"CLEAR");}public Check descent(Point a,Point b){return new Check(Knowledge.CLEAR,"SUPPORTED_CLEAR_COLUMN");}};
 public static void main(String[] args){var model=new FlightModel();var cap=Capability.standard();var origin=new Point(.5,0,.5);
  check(model.displacement(0,0,12).equals(new Point(0,0,12)));check(model.displacement(45,0,12).equals(new Point(-8,0,8)));check(model.displacement(45,-45,12).equals(new Point(-6,9,6)));check(model.displacement(0,-90,12).equals(new Point(0,12,0)));
  var fixed=model.predict(origin,new Point(-17.5,27,18.5),45,-45,3,cap);check(fixed.points().equals(List.of(new Point(-5.5,9,6.5),new Point(-11.5,18,12.5),new Point(-17.5,27,18.5))));check(fixed.status()==FlightResult.Status.HIT_TARGET);check(fixed.confidence().equals(Set.of(FlightResult.Confidence.MEASURED)));
  check(model.predict(origin,origin,0,0,4,cap).confidence().contains(FlightResult.Confidence.EXTRAPOLATED));check(model.predict(new Point(.3,0,.4),origin,0,-45,1,cap).confidence().contains(FlightResult.Confidence.ESTIMATED));
  check(model.predict(origin,origin,90,45,1,new Capability(8,3,200000,.001)).confidence().contains(FlightResult.Confidence.ESTIMATED));
  check(model.displacement(0,45,12).y()==-8);check(model.predict(origin,origin,0,91,1,cap).status()==FlightResult.Status.UNSUPPORTED);
  var exact=model.solve(origin,fixed.end(),cap);check(exact.error()==0);check(exact.clicks()<=3);var same=model.solve(origin,fixed.end(),cap);check(exact.equals(same));
  var near=model.predict(origin,new Point(.6,0,12.6),0,0,1,cap);check(near.end().sameBlock(new Point(.6,0,12.6)));check(near.error()>0);check(near.status()==FlightResult.Status.APPROXIMATE);
  check(model.solve(origin,new Point(1000,0,0),new Capability(12,1,1,.001)).budgetExhausted());
  var planner=new RoutePlanner();var tiny=new Capability(.001,1,200000,.001);var target=new Point(.5,0,.5);
  var drop=planner.plan(new Point(.5,12,.5),target,tiny,RouteStrategy.DROP_ALIGN,CLEAR);check(drop.status()==RouteResult.Status.REACHED);check(drop.lastTransmission().y()==12);check(drop.landing().y()==0);check(drop.lastTransmission().distance(drop.landing())==12);
  check(planner.plan(new Point(.5,13,.5),target,tiny,RouteStrategy.DROP_ALIGN,CLEAR).status()==RouteResult.Status.NO_SOLUTION);
  var platform=new Terrain(){public Check transfer(Point a,Point b){return new Check(Knowledge.CLEAR,"CLEAR");}public Check descent(Point a,Point b){return new Check(Knowledge.BLOCKED,"INTERMEDIATE_PLATFORM");}};
  check(planner.plan(new Point(.5,12,.5),target,tiny,RouteStrategy.DROP_ALIGN,platform).status()==RouteResult.Status.NO_SOLUTION);
  var boundary=planner.plan(new Point(.5,0,6.5),target,tiny,RouteStrategy.UPPER_HEMISPHERE_NEAREST,CLEAR);check(boundary.status()==RouteResult.Status.REACHED);check(boundary.error()==6);check(boundary.points().isEmpty());
  check(planner.plan(new Point(.5,-1,.5),target,tiny,RouteStrategy.UPPER_HEMISPHERE_NEAREST,CLEAR).status()==RouteResult.Status.NO_SOLUTION);
  var nearest=planner.plan(origin,new Point(.5,0,24.5),cap,RouteStrategy.UPPER_HEMISPHERE_NEAREST,CLEAR);check(nearest.error()==0);check(nearest.segments().getFirst().clicks()==2);
  var direct=planner.plan(origin,new Point(.5,0,24.5),cap,RouteStrategy.DIRECT_AIM,CLEAR);check(direct.points().size()==2);check(direct.error()==0);check(direct.landing()==null);
  check(planner.plan(origin,new Point(.5,0,6.5),cap,RouteStrategy.DIRECT_AIM,CLEAR).points().isEmpty());check(planner.plan(origin,new Point(.5,0,12.5),tiny,RouteStrategy.DIRECT_AIM,CLEAR).status()==RouteResult.Status.NO_DISPLACEMENT);
  check(planner.plan(origin,new Point(.5,0,12.5),cap,RouteStrategy.DIRECT_AIM,Terrain.UNKNOWN).status()==RouteResult.Status.GEOMETRIC_UNKNOWN);
  var blocked=new Terrain(){public Check transfer(Point a,Point b){return new Check(Knowledge.BLOCKED,"WALL");}public Check descent(Point a,Point b){return new Check(Knowledge.BLOCKED,"PLATFORM");}};
  check(planner.plan(origin,new Point(.5,0,12.5),cap,RouteStrategy.DIRECT_AIM,blocked).status()==RouteResult.Status.BLOCKED);
  var cfg=new InstantTransmissionConfig();check(cfg.strategy()==RouteStrategy.DIRECT_AIM);cfg.setStrategy(RouteStrategy.DROP_ALIGN);var parent=new JsonObject();parent.add("instantTransmission",cfg.toJson());var reloaded=new InstantTransmissionConfig();check(!reloaded.load(parent));check(reloaded.strategy()==RouteStrategy.DROP_ALIGN);check(reloaded.plan().equals(cfg.plan()));
  parent.getAsJsonObject("instantTransmission").addProperty("strategy","UNKNOWN");check(reloaded.load(parent));check(reloaded.strategy()==RouteStrategy.DIRECT_AIM);
  int[] up={0,1,2,3,4,5,6,7,8,9,9,10,11,11,11,12,12,12,12};for(int i=0;i<19;i++)check(model.displacement(0,-5*i,12).y()==up[i]);
  check(model.displacement(45,-45,12).distance(new Point(0,0,0))!=12);
  check(planner.plan(origin,new Point(.5,0,7.5),new Capability(100,4,200000,.001),RouteStrategy.DIRECT_AIM,CLEAR).status()==RouteResult.Status.REPEATED_POINT);
  check(planner.plan(origin,new Point(.5,0,100.5),new Capability(12,1,200000,.001),RouteStrategy.DIRECT_AIM,CLEAR).status()==RouteResult.Status.LIMIT);
  check(planner.plan(origin,new Point(.5,0,100.5),new Capability(12,1,1,.001),RouteStrategy.UPPER_HEMISPHERE_NEAREST,CLEAR).budgetExhausted());
  System.out.println("PASS: "+checks+" pure flight/routing/model confidence/terrain/budget/strategy persistence checks. Client/server execution Not Verified In-Game.");
 }
}
