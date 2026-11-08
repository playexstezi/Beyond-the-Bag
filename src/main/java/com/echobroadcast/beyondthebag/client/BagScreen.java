package com.echobroadcast.beyondthebag.client;

import com.echobroadcast.beyondthebag.network.BagNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BagScreen extends Screen {
    public BagScreen() { super(Component.translatable("screen.beyondthebag.title")); }

    @Override protected void init() {
        int w = 190, x = (this.width - w) / 2, y = this.height / 2 - 44;
        addRenderableWidget(Button.builder(Component.translatable("screen.beyondthebag.home"), b -> {
            BagNetwork.sendAction(0); onClose();
        }).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.beyondthebag.maze"), b -> {
            BagNetwork.sendAction(1); onClose();
        }).bounds(x, y + 26, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.beyondthebag.return"), b -> {
            BagNetwork.sendAction(2); onClose();
        }).bounds(x, y + 52, w, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 76, 0xE8D8B0);
        graphics.drawCenteredString(font, Component.translatable("screen.beyondthebag.subtitle"), width / 2, height / 2 - 62, 0xA9A39A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
