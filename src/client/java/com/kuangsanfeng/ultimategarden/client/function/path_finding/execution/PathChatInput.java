package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import java.util.function.Consumer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;

/** An isolated native chat screen. No draft, history or command suggestions survive this session. */
final class PathChatInput extends ChatScreen {
    private final Consumer<String> submit;
    private boolean consumed;
    PathChatInput(Consumer<String> submit) {
        super("",false);
        this.submit=submit;
    }
    @Override protected void init() {
        super.init();
        input.setResponder(value->{});
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!event.isConfirmation()) return super.keyPressed(event);
        handleChatInput(input.getValue(),false);
        minecraft.setScreen(null);
        return true;
    }
    @Override public void handleChatInput(String text,boolean addToRecent) {
        if (consumed) return;
        consumed=true;
        submit.accept(text);
    }
    @Override public void removed() {
        minecraft.gui.getChat().resetChatScroll();
        consumed=true;
    }
}
