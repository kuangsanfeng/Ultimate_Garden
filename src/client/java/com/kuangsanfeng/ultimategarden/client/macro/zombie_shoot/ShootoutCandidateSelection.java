package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Ordered one-pass selection: grace belongs to an existing lock, never a candidate. */
public final class ShootoutCandidateSelection {
 /** Snapshot order survives each shot; a completed high-priority target cannot restart ahead of its peers. */
 public static final class Relock<T> {
  private final ArrayDeque<T> remaining=new ArrayDeque<>();
  public void clear(){remaining.clear();}
  public T select(boolean maySeed,Supplier<List<T>> ordered,Predicate<T> eligible,Predicate<T> solvable,Consumer<T> begin){
   if(remaining.isEmpty()&&maySeed)remaining.addAll(ordered.get());
   while(!remaining.isEmpty()){
    T target=remaining.removeFirst();
    if(eligible.test(target)&&solvable.test(target)){begin.accept(target);return target;}
   }
   return null;
  }
 }
 public static <T> T firstOrRescan(Supplier<T> select,Runnable rescan){
  T target=select.get();if(target!=null)return target;rescan.run();return select.get();
 }
 public static <T> T first(Iterable<T> ordered,Predicate<T> valid,Predicate<T> solvable){
  Set<T> checked=Collections.newSetFromMap(new IdentityHashMap<>());
  for(var target:ordered)if(checked.add(target)&&valid.test(target)&&solvable.test(target))return target;
  return null;
 }
}
