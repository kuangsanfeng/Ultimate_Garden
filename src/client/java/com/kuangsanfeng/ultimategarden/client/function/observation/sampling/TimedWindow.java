package com.kuangsanfeng.ultimategarden.client.function.observation.sampling;
import java.util.*;
import java.util.function.ToLongFunction;
/** Caller supplies one consistent time unit and continuity rules; aggregation stays outside. */
public final class TimedWindow<T> extends AbstractCollection<T> {
 private final ArrayDeque<T> values=new ArrayDeque<>();
 private final int capacity;private final ToLongFunction<T> time;
 public TimedWindow(int capacity,ToLongFunction<T> time){if(capacity<1)throw new IllegalArgumentException("capacity");this.capacity=capacity;this.time=Objects.requireNonNull(time);}
 @Override public boolean add(T value){addLast(value);return true;}
 public void addLast(T value){values.addLast(value);while(size()>capacity)removeFirst();}
 public void addFirst(T value){values.addFirst(value);while(size()>capacity)values.removeLast();}
 @Override public int size(){return values.size();}
 @Override public Iterator<T> iterator(){return Collections.unmodifiableCollection(values).iterator();}
 @Override public void clear(){values.clear();}
 public T getFirst(){return values.getFirst();}
 public T getLast(){return values.getLast();}
 public T peekLast(){return values.peekLast();}
 public T removeFirst(){return values.removeFirst();}
 public void resetIf(boolean discontinuity){if(discontinuity)clear();}
 public void expireInclusive(long cutoff){while(!isEmpty()&&time.applyAsLong(getFirst())<=cutoff)removeFirst();}
 /** Keep the segment crossing a fractional boundary for caller-side interpolation. */
 public void retainBoundary(double cutoff){
  while(size()>1){var first=removeFirst();if(time.applyAsLong(getFirst())>cutoff){addFirst(first);break;}}
 }
 public List<T> snapshot(){return List.copyOf(this);}
}
