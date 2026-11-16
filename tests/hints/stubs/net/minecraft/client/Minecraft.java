package net.minecraft.client;

import java.util.*;
import net.minecraft.network.chat.Component;
public final class Minecraft {
 public static final Minecraft INSTANCE=new Minecraft(); public Object player=new Object(); public final Gui gui=new Gui();
 public static Minecraft getInstance(){return INSTANCE;}
 public static final List<String> messages=new ArrayList<>();
 public static final class Gui { public Chat getChat(){return new Chat();} }
 public static final class Chat { public void addClientSystemMessage(Component c){messages.add(c.text());} }
}
