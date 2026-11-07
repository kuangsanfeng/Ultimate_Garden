package net.minecraft.client;
public final class Minecraft {
    public final Options options = new Options();
    public final MouseHandler mouseHandler = new MouseHandler();
    public static final class Options { public final Key keyAttack = new Key(); }
    public static final class Key {
        public boolean down;
        public void setDown(boolean down) { this.down = down; }
    }
}
