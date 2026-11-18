import java.util.*;import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
public final class SearchAuditRegression {
 public static void main(String[] args){
  Terrain flat=new Terrain(){public boolean clear(Point p){return true;}public boolean supported(Point p){return true;}public boolean sweep(Point a,Point b){return true;}public Optional<Point> ground(Point p){return Optional.empty();}public Set<String> missing(){return Set.of();}};
  var events=new ArrayList<Map.Entry<String,Map<String,Object>>>();var audit=new SearchAudit((e,d)->events.add(Map.entry(e,new LinkedHashMap<>(d))));
  var o=new Options(Mode.WEIGHTED,false,true,12,1,2001,Weights.defaults());var start=new Point(.5,0,.5);var goal=new Point(10000.5,0,.5);
  var plain=new PathPlanner().plan(start,goal,0,0,o,flat,()->false);var traced=new PathPlanner().plan(start,goal,0,0,o,flat,()->false,audit);
  if(plain.status()!=traced.status()||traced.explored()!=2001||plain.explored()!=2001)throw new AssertionError("Budget changed or off by one");
  var progress=events.stream().filter(e->e.getKey().equals("SEARCH_PROGRESS")).toList();if(progress.size()!=2||!progress.get(0).getValue().get("expanded").equals(1000L)||!progress.get(1).getValue().get("expanded").equals(2000L))throw new AssertionError("Progress cadence");
  if(!events.stream().anyMatch(e->e.getKey().equals("SEARCH_LIMIT"))||!events.stream().anyMatch(e->e.getKey().equals("REJECTION_SUMMARY")))throw new AssertionError("Missing terminal diagnostics");
  for(var e:events)if(e.getKey().equals("REJECTION_SUMMARY")){var samples=(Map<?,?>)e.getValue().get("samples");for(Object list:samples.values())if(((List<?>)list).size()>3)throw new AssertionError("Unbounded rejection samples");}
  Terrain closed=new Terrain(){public boolean clear(Point p){return p.equals(start)||p.equals(goal);}public boolean supported(Point p){return true;}public boolean sweep(Point a,Point b){return false;}public Optional<Point> ground(Point p){return Optional.empty();}public Set<String> missing(){return Set.of();}};
  var closedEvents=new ArrayList<String>();var exhausted=new PathPlanner().plan(start,goal,0,0,o,closed,()->false,new SearchAudit((e,d)->closedEvents.add(e)));
  if(exhausted.status()!=Status.UNREACHABLE||!closedEvents.contains("OPEN_LIST_EXHAUSTED")||closedEvents.contains("SEARCH_LIMIT"))throw new AssertionError("Open-list exhaustion conflated with budget");
  Terrain unknown=new Terrain(){public boolean clear(Point p){return false;}public boolean supported(Point p){return false;}public boolean sweep(Point a,Point b){return false;}public Optional<Point> ground(Point p){return Optional.empty();}public Set<String> missing(){return Set.of("missing.litematic");}};
  if(new PathPlanner().plan(start,goal,0,0,o,unknown,()->false).status()!=Status.MAP_MISSING)throw new AssertionError("Missing map conflated with unreachable");
  System.out.println("PASS diagnostics: exact 2001 expansion cap, 1000 cadence, bounded samples, terminal reasons, unchanged traced/untraced outcome");
 }
}
