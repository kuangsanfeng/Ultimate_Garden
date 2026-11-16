
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter;
import java.nio.file.*;import java.util.*;
public class LogIsolationRegression {
 public static void main(String[] args)throws Exception{
  var root=Files.createTempDirectory("ug-jsonl-channels");
  var first=new JsonlWriter(root,"first",true,w->{});
  var second=new JsonlWriter(root,"second",true,w->{});
  if(!first.offer("first.jsonl",Map.of("event","START"))||!second.offer("second.jsonl",Map.of("event","START")))throw new AssertionError();
  first.endBatch("first.jsonl",Map.of("event","END"));first.close();first.awaitClose(3000);
  if(first.offer("first.jsonl",Map.of("late",true)))throw new AssertionError();
  if(!second.offer("second.jsonl",Map.of("event","STILL_ACTIVE")))throw new AssertionError();
  second.endBatch("second.jsonl",Map.of("event","END"));second.close();second.awaitClose(3000);
  if(Files.readAllLines(root.resolve("first.jsonl")).size()!=2||Files.readAllLines(root.resolve("second.jsonl")).size()!=3)throw new AssertionError();
  var blocked=root.resolve("blocked");Files.writeString(blocked,"file");
  var broken=new JsonlWriter(blocked,"broken",true,w->{});broken.awaitClose(3000);
  if(broken.error==null||!Files.exists(root.resolve("second.jsonl")))throw new AssertionError();
  System.out.println("PASS independent JSONL channels, terminal drain, post-close rejection and failure isolation");
 }
}
