package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
/** One client-thread pending task. Context references are retained only until cancellation. */
public final class ShootoutAutoStart {
 public record Context(Object connection,Object world,Object player,long account){}
 private Context pending;private long trigger,lastMessage;private java.lang.ref.WeakReference<Object> lastConnection=new java.lang.ref.WeakReference<>(null),lastWorld=new java.lang.ref.WeakReference<>(null),lastPlayer=new java.lang.ref.WeakReference<>(null);private long lastAccount;
 public static boolean matches(String text){if(text==null)return false;return text.replaceAll("(?i)\\u00a7[0-9A-FK-ORX]","").replaceAll("[\\s\\p{Zs}]+"," ").trim().equals("[NPC] Carnival Cowboy: Good luck, pal!");}
 private static boolean same(Context a,Context b){return a!=null&&b!=null&&a.connection()==b.connection()&&a.world()==b.world()&&a.player()==b.player()&&a.account()==b.account();}
 public void cancel(){pending=null;}
 public boolean pending(){return pending!=null;}
 public void receive(String text,long now,boolean enabled,boolean running,Context context){
  if(!enabled||running||context==null||!matches(text)||pending!=null)return;
  // Text carries no event ID; a short debounce suppresses duplicate delivery after Stop.
  if(lastConnection.get()==context.connection()&&lastWorld.get()==context.world()&&lastPlayer.get()==context.player()&&lastAccount==context.account()&&now-lastMessage<2_000_000_000L)return;
  pending=context;trigger=now;lastMessage=now;lastConnection=new java.lang.ref.WeakReference<>(context.connection());lastWorld=new java.lang.ref.WeakReference<>(context.world());lastPlayer=new java.lang.ref.WeakReference<>(context.player());lastAccount=context.account();
 }
 public boolean due(long now,boolean enabled,boolean running,Context context){
  if(!enabled||running||context==null||!same(pending,context)){cancel();return false;}
  if(now-trigger<200_000_000L)return false;
  cancel();return true;
 }
}
