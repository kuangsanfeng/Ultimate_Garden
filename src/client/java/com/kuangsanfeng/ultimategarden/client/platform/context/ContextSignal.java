package com.kuangsanfeng.ultimategarden.client.platform.context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Ordered, synchronous notifications. Subscribers own cleanup; no macro policy lives here. */
public final class ContextSignal<T> {
 private final List<Consumer<T>> listeners = new ArrayList<>();
 public void subscribe(Consumer<T> listener) { listeners.add(java.util.Objects.requireNonNull(listener)); }
 public void publish(T event) { for (var listener : List.copyOf(listeners)) listener.accept(event); }
}
