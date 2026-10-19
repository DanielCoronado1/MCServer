package com.dolfin.space.client.ships;

import com.dolfin.space.ships.*;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {
    private Button craft;
    private Button fast;

    public AssemblyScreen(AssemblyMenu m, Inventory inv, Component title) {
        super(m, inv, title, 176, 195);
    }

    protected void init() {
        super.init();
        for (int i = 0; i < 3; i++) {
            final int tier = i;
            var choice =
                    addRenderableWidget(
                            Button.builder(
                                            Component.translatable(
                                                    "space.tier." + ShipTier.safe(i).name()),
                                            b ->
                                                    minecraft.gameMode.handleInventoryButtonClick(
                                                            menu.containerId, tier))
                                    .bounds(leftPos + 5 + i * 56, topPos + 20, 54, 20)
                                    .build());
            if (i == 2) fast = choice;
        }
        craft =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("space.ui.assemble"),
                                        b ->
                                                minecraft.gameMode.handleInventoryButtonClick(
                                                        menu.containerId, 3))
                                .bounds(leftPos + 8, topPos + 87, 160, 20)
                                .build());
    }

    protected void containerTick() {
        super.containerTick();
        craft.active = menu.ready();
        fast.active = AssemblyMenu.fastUnlocked(minecraft.player.getInventory());
    }

    public void extractContents(GuiGraphicsExtractor g, int x, int y, float dt) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff26313d);
        g.text(
                font,
                Component.translatable("space.ui.empty_table"),
                leftPos + 8,
                topPos + 47,
                0xffbbbbbb);
        for (int i = 0; i < menu.slots.size(); i++) {
            var s = menu.slots.get(i);
            g.fill(
                    leftPos + s.x - 1,
                    topPos + s.y - 1,
                    leftPos + s.x + 17,
                    topPos + s.y + 17,
                    i < 7 && menu.missing(i) ? 0xffa83333 : 0xff576474);
        }
        super.extractContents(g, x, y, dt);
        for (int i = 0; i < menu.tier().template.size(); i++) {
            var s = menu.slots.get(i);
            if (x >= leftPos + s.x
                    && x < leftPos + s.x + 16
                    && y >= topPos + s.y
                    && y < topPos + s.y + 16) {
                var part = menu.tier().template.get(i);
                g.setTooltipForNextFrame(
                        Component.translatable(
                                "space.part",
                                part.count(),
                                Component.translatable(
                                        part.id().equals("minecraft:chest")
                                                ? "block.minecraft.chest"
                                                : "item." + part.id().replace(':', '.'))),
                        x,
                        y);
            }
        }
    }
}
