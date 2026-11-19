import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.NodeBudget;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.TargetCoordinates;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.TransmissionEquipment;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorPlanner;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.SegmentedPlanner;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.SearchAudit;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.TransmissionEquipment.Kind.*;

public final class PathStartupRegression {
    private static void check(boolean pass,String reason) {
        if (!pass) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        for (String text : List.of("\u76ee\u6807 -12 70 34","go -12 70 34","-12 70 34"))
            check(TargetCoordinates.extract(text).orElseThrow().equals(new Point(-12,70,34)),text);
        check(TargetCoordinates.extract("\u4f4d\u7f6e X: -12.5, Y: 70, Z: 34.25 \u5df2\u786e\u8ba4").orElseThrow()
                .equals(new Point(-12.5,70,34.25)),"Labeled decimal");
        for (String text : List.of("you get 1 he 2 and her 3","\u76ee\u6807 -12 \u9ad8\u5ea6 70 34","12,70,34","- 12 70 34","12 70 34 56","1 2 3 and 4 5 6","/kill @s"))
            check(TargetCoordinates.extract(text).isEmpty(),text);
        check(TargetCoordinates.extractReceived("1 2 3 message X: -12.5, Y: 70, Z: 34.25").orElseThrow()
                .equals(new Point(-12.5,70,34.25)),"Explicit coordinate overrides unmarked one");
        for (String s : List.of("200k","200K","200,000","200\uff0c000")) check(NodeBudget.parse(s)==200000,s);
        check(NodeBudget.format(200000).equals("200K") && NodeBudget.format(200001).equals("200001"),"Exact format");
        check(NodeBudget.parse("2000K")==NodeBudget.parse("2m") && NodeBudget.format(2_000_000).equals("2M"),"Largest exact unit");
        check(NodeBudget.parse("9T")==9_000_000_000_000L && NodeBudget.format(9_000_000_000_000L).equals("9T"),"Long budget");
        for (String s : List.of("go213312","213312foo","20KK","0","-5K","9999999999999999T")) {
            try {NodeBudget.parse(s);throw new AssertionError(s);}
            catch (IllegalArgumentException | ArithmeticException expected) {}
        }
        var settings=new PathSettings();
        check(settings.searchNodesPerSegment()==2_000_000 && settings.maximumSearchSegments()==2 && settings.coordinateWindowMs()==5000,"Defaults");
        try {settings.setBudget(Long.MAX_VALUE,2);throw new AssertionError("Product overflow");}
        catch (ArithmeticException expected) {}
        check(settings.searchNodesPerSegment()==2_000_000,"Previous value preserved");
        settings.setBudget(1_000_000_000_000L,3);
        var serialized=new com.google.gson.JsonObject();serialized.add("pathFinding",settings.toJson());
        var reloaded=new PathSettings();
        check(!reloaded.load(serialized)&&reloaded.searchNodesPerSegment()==1_000_000_000_000L&&reloaded.maximumSearchSegments()==3,"Round trip");
        check(settings.aim(PathSettings.Aim.SPEED)==300 && settings.aim(PathSettings.Aim.CURVE)==15
                && settings.aim(PathSettings.Aim.RATE)==60 && settings.aim(PathSettings.Aim.TOLERANCE)==.5
                && settings.aim(PathSettings.Aim.CORRECTION)==.25,"Aim defaults");
        var sorted=List.of(new TransmissionEquipment(8,AOTE,12,0,0),
                new TransmissionEquipment(1,AOTV,11,5,300), new TransmissionEquipment(7,AOTE,11,5,200),
                new TransmissionEquipment(0,AOTV,11,4,500), new TransmissionEquipment(4,AOTV,11,5,300),
                new TransmissionEquipment(3,AOTE,11,5,300));
        var order=sorted.stream().sorted(TransmissionEquipment.PREFERENCE.reversed()).map(TransmissionEquipment::slot).toList();
        check(order.equals(List.of(8,1,4,3,7,0)),"Lexicographic equipment order "+order);

        var attributes=new net.minecraft.nbt.CompoundTag();
        attributes.putString("id","ASPECT_OF_THE_VOID");
        attributes.putInt("tuned_transmission",4);
        var enchants=new net.minecraft.nbt.CompoundTag();
        enchants.putInt("ultimate_wise",5);
        attributes.put("enchantments",enchants);
        check(TransmissionEquipment.read(5,attributes,List.of("Intelligence: +1,250","Ability: Ether Transmission",
                "Teleport 57 blocks ahead of you")).orElseThrow().equals(new TransmissionEquipment(5,AOTV,12,5,1250)),
                "Synchronized attributes and lore ignore Etherwarp");
        check(TransmissionEquipment.read(5,attributes,List.of("Ability: Instant Transmission RIGHT CLICK","Teleport 13 blocks ahead of you"))
                .orElseThrow().range()==13,"Confirmed ordinary ability range");

        Point start=new Point(.5,0,.5), end=new Point(15.5,0,.5);
        Terrain line=new Terrain() {
            public boolean clear(Point p){return p.y()==0 && p.z()==.5 && p.x()>=.5 && p.x()<=15.5;}
            public boolean supported(Point p){return clear(p);}
            public boolean sweep(Point a,Point b){return clear(a)&&clear(b);}
            public Optional<Point> ground(Point p){return clear(p)?Optional.of(p):Optional.empty();}
            public Set<String> missing(){return Set.of();}
        };
        var options=new Options(Mode.NORMAL,false,true,12,1,5,Weights.defaults());
        var full=SegmentedPlanner.plan(line,options,10,CorridorPlanner.Timing.defaults(),()->false,null,start,end,-90,0);
        check(full.status()==Status.ARRIVED && full.end().equals(end) && full.segments().size()==15,"Segmented completion "+full);
        check(full.explored()>5&&full.explored()<=50,"Shared accounting");
        for (int i=1;i<full.segments().size();i++)
            check(full.segments().get(i-1).to().equals(full.segments().get(i).from()),"Exact seam");
        var single=SegmentedPlanner.plan(line,options,1,CorridorPlanner.Timing.defaults(),()->false,null,start,end,-90,0);
        check(single.status()==Status.BUDGET_EXHAUSTED && single.explored()==5 && !single.segments().isEmpty()
                && single.reason().equals("SEGMENT_LIMIT"),"Partial must stay incomplete");
        var stuck=SegmentedPlanner.plan(line,new Options(Mode.NORMAL,false,true,12,1,1,Weights.defaults()),10,
                CorridorPlanner.Timing.defaults(),()->false,null,start,end,-90,0);
        check(stuck.reason().equals("SEGMENT_LIMIT") && stuck.explored()==10 && stuck.end().x()>.5,
                "One-node slices must resume even when the first slice has no prefix: "+stuck);
        retainedFlightFrontier();

        System.out.println("PASS path startup: coordinates, limits, equipment priorities, aim defaults, segmented seams and incomplete prefixes");
    }
    private static void retainedFlightFrontier() {
        Point start=new Point(.5,10,.5), end=new Point(400.5,10,.5);
        Terrain incompleteMap=new Terrain() {
            public boolean clear(Point p){return p.y()==10 && p.z()==.5 && (p.x()>=.5 && p.x()<300 || p.equals(end));}
            public boolean supported(Point p){return p.equals(start)||p.equals(end);}
            public boolean sweep(Point a,Point b){return a.equals(b);}
            public boolean corridorSweep(Point a,Point b){return clear(a)&&clear(b)&&Math.abs(a.x()-b.x())<=4;}
            public Optional<Point> ground(Point p){return Optional.empty();}
            public Set<String> missing(){return Set.of("fixture-missing.litematic");}
        };
        var events=new ArrayList<Map.Entry<String,Map<String,Object>>>();
        var trace=new SearchAudit((name,data)->events.add(Map.entry(name,data)));
        var options=new Options(Mode.NORMAL,true,true,12,1,20,Weights.defaults());
        var result=SegmentedPlanner.plan(incompleteMap,options,4,CorridorPlanner.Timing.defaults(),()->false,trace,start,end,0,0);
        var starts=events.stream().filter(e->e.getKey().equals("SEGMENT_START")).map(Map.Entry::getValue).toList();
        var ends=events.stream().filter(e->e.getKey().equals("SEGMENT_END")).map(Map.Entry::getValue).toList();
        var stages=events.stream().filter(e->e.getKey().equals("STAGE")).map(Map.Entry::getValue).toList();
        check(starts.size()==4 && starts.stream().skip(1).allMatch(e->e.get("resume").equals("RETAINED_FRONTIER")),"Same flight frontiers across rounds");
        check(ends.size()==4 && ends.stream().allMatch(e->e.get("expanded").equals(20L))
                && result.explored()==80 && result.reason().equals("SEGMENT_LIMIT"),"Only four shared slices");
        check(stages.stream().anyMatch(e->e.get("stage").equals("WALKING_CORRIDOR") && e.get("status")==Status.MAP_MISSING)
                && events.stream().anyMatch(e->e.getKey().equals("BUDGET_TRANSFER")),"Missing walking data must not discard active flight search");
        var firstFlight=stages.stream().filter(e->e.get("stage").equals("FLIGHT_CORRIDOR")).findFirst().orElseThrow();
        check(firstFlight.get("expanded").equals(12L) && stages.stream().filter(e->e.get("stage").equals("FLIGHT_CORRIDOR"))
                .anyMatch(e->((Number)e.get("stageExpanded")).longValue()>20),"Flight resumes after quota and retains visited state");
    }

}
