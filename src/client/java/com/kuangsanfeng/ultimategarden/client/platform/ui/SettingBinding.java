package com.kuangsanfeng.ultimategarden.client.platform.ui;
import java.util.function.*;
public record SettingBinding<T>(Supplier<T> read,Consumer<T> write,BooleanSupplier editable) {
 public T value(){return read.get();}
 public boolean set(T value){if(!editable.getAsBoolean())return false;write.accept(value);return true;}
}
