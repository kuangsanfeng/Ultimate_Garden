package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.minecraft.client.Minecraft;import java.util.function.Consumer;
public final class ClientTickEvents {public static final Event END_CLIENT_TICK=new Event();public static final class Event {public void register(Consumer<Minecraft> c){}}}
