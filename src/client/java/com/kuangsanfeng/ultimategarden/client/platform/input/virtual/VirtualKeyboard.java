package com.kuangsanfeng.ultimategarden.client.platform.input.virtual;
import com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput;


import com.kuangsanfeng.ultimategarden.client.mixin.KeyboardHandlerAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import com.kuangsanfeng.ultimategarden.client.mixin.KeyMappingAccessor;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft internal virtual keyboard.
 *
 * Replaces original C++:
 *
 * KeyDown()
 * KeyUp()
 * KeyClick()
 * SendChar()
 * KeyStop()
 */
public class VirtualKeyboard {

    private final Minecraft minecraft;
    private java.util.function.BooleanSupplier inputAllowed = () -> true;
    public void setInputAllowed(java.util.function.BooleanSupplier value) { inputAllowed = value; }

    private final java.util.Set<KeyMapping> pressedKeys =
            java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<>()
            );

    public VirtualKeyboard(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public boolean hasHeldKeys() { return !pressedKeys.isEmpty(); }
    /** Read-only observation of this keyboard's holds and the current bound hardware keys. */
    public java.util.Map<String,Object> diagnosticState() {
        var held=new java.util.LinkedHashMap<String,Object>();
        var physical=new java.util.LinkedHashMap<String,Object>();
        for(String name:java.util.List.of("W","A","S","D","SPACE","SHIFT")) {
            KeyMapping mapping=findKeyMapping(name);held.put(name,mapping==null?null:pressedKeys.contains(mapping));
            Boolean down=null;
            try { if(mapping!=null) {
                var key=((KeyMappingAccessor)mapping).getKey();
                if(key.getValue()>=0) {
                    long window=minecraft.getWindow().handle();
                    if(key.getType()==InputConstants.Type.KEYSYM) down=GLFW.glfwGetKey(window,key.getValue())==GLFW.GLFW_PRESS;
                    else if(key.getType()==InputConstants.Type.MOUSE) down=GLFW.glfwGetMouseButton(window,key.getValue())==GLFW.GLFW_PRESS;
                }
            } } catch(RuntimeException unavailable) { down=null; }
            physical.put(name,down);
        }
        var result=new java.util.LinkedHashMap<String,Object>();result.put("virtualHeld",held);result.put("physicalBoundKeys",physical);
        return result;
    }
    public void reapplyHeldState() { for (KeyMapping mapping : pressedKeys) mapping.setDown(true); }
    private boolean routesToGame() {
        return minecraft.screen == null || minecraft.screen instanceof com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen menu && menu.keepsFarmingInput();
    }

    // =========================================================
    // KeyDown
    // =========================================================

    //KeyDown()
    public void keyDown(String key) {

        KeyMapping mapping = findKeyMapping(key);

        if (mapping == null) {
            return;
        }

        mapping.setDown(true);
        pressedKeys.add(mapping);
    }

    // =========================================================
    // KeyUp
    // =========================================================

    //KeyUp()
    public void keyUp(String key) {

        KeyMapping mapping = findKeyMapping(key);

        if (mapping == null) {
            return;
        }

        mapping.setDown(false);
        pressedKeys.remove(mapping);
    }

    // =========================================================
    // KeyClick
    // =========================================================

    //KeyClick()
    public void keyClick(String key) {

        KeyMapping mapping = findKeyMapping(key);

        if(mapping == null)
            return;


        InputConstants.Key inputKey =
                ((KeyMappingAccessor) mapping).getKey();


        minecraft.execute(() -> {
            if (!inputAllowed.getAsBoolean()) return;


            if(routesToGame()) {

                PlayerGuiInput.virtualDispatch(() -> KeyMapping.click(inputKey));


            } else {

                sendKeyboardEvent(inputKey);

            }

        });
    }

    // =========================================================
    // Slot
    // =========================================================

    //KeyClick()
    public void slot(int slot) {

        if(slot < 1 || slot > 9)
            return;


        KeyMapping mapping = findSlotMapping(slot);

        if(mapping == null)
            return;


        InputConstants.Key inputKey =
                ((KeyMappingAccessor) mapping).getKey();


        minecraft.execute(() -> {
            if (!inputAllowed.getAsBoolean()) return;


            if(routesToGame()) {

                PlayerGuiInput.virtualDispatch(() -> KeyMapping.click(inputKey));


            } else {

                sendKeyboardEvent(inputKey);

            }

        });
    }

    private void sendKeyboardEvent(InputConstants.Key inputKey){

        KeyboardHandlerAccessor accessor =
                (KeyboardHandlerAccessor)
                        minecraft.keyboardHandler;


        KeyEvent event =
                new KeyEvent(
                        inputKey.getValue(),
                        0,
                        0
                );


        PlayerGuiInput.virtualDispatch(() -> {
        accessor.invokeKeyPress(
                minecraft.getWindow().handle(),
                GLFW.GLFW_PRESS,
                event
        );


        accessor.invokeKeyPress(
                minecraft.getWindow().handle(),
                GLFW.GLFW_RELEASE,
                event
        );
        });
    }

    // =========================================================
    // SendChar
    // =========================================================

    //SendChar()
    public void typeCharacter(char character) {

        if (character >= 'a' && character <= 'z') {
            keyClick(
                    String.valueOf(
                            Character.toUpperCase(character)
                    )
            );
            return;
        }

        if (character >= 'A' && character <= 'Z') {
            keyDown("SHIFT");
            keyClick(String.valueOf(character));
            keyUp("SHIFT");
            return;
        }

        if (character >= '0' && character <= '9') {
            keyClick(String.valueOf(character));
            return;
        }

        if (character == ' ') {
            keyClick("SPACE");
            return;
        }

        if (character == '/') {
            keyClick("SLASH");
            return;
        }

        if (character == '-') {
            keyClick("MINUS");
            return;
        }

        if (character == '_') {
            keyDown("SHIFT");
            keyClick("MINUS");
            keyUp("SHIFT");
        }
    }

    // =========================================================
    // KeyMapping
    // =========================================================

    private KeyMapping findKeyMapping(String key) {

        if (key == null) {
            return null;
        }

        String normalized = key.toUpperCase();

        KeyMapping[] mappings = minecraft.options.keyMappings;

        for (KeyMapping mapping : mappings) {

            String translationKey = mapping.getName();

            if (translationKey == null) {
                continue;
            }

            if (matchesKey(mapping, normalized)) {
                return mapping;
            }
        }

        return null;
    }

    private boolean matchesKey(
            KeyMapping mapping,
            String key
    ) {

        String name = mapping.getName();

        if (key.equals("W") && name.equals("key.forward")) {
            return true;
        }

        if (key.equals("S") && name.equals("key.back")) {
            return true;
        }

        if (key.equals("A") && name.equals("key.left")) {
            return true;
        }

        if (key.equals("D") && name.equals("key.right")) {
            return true;
        }

        if (key.equals("SPACE") && name.equals("key.jump")) {
            return true;
        }

        if (key.equals("SHIFT") && name.equals("key.sneak")) {
            return true;
        }

        if (key.equals("E") && name.equals("key.inventory")) {
            return true;
        }

        return false;
    }



    private KeyMapping findSlotMapping(int slot) {

        String expected = "key.hotbar." + slot;

        for (KeyMapping mapping : minecraft.options.keyMappings) {

            if (expected.equals(mapping.getName())) {
                return mapping;
            }
        }

        return null;
    }

    // =========================================================
    // Stop
    // =========================================================

    //KeyStop()
    public void releaseAll() {

        for (KeyMapping mapping : pressedKeys) {
            mapping.setDown(false);
        }

        pressedKeys.clear();
    }
}
