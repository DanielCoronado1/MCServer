package com.dolfin.space.client.oxygen;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;
import com.dolfin.space.oxygen.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.sounds.SoundEvents;

public final class OxygenClient {
    private static OxygenPayload state = new OxygenPayload(0,0,true);
    private OxygenClient() {}
    public static void init() {
        MenuScreens.register(OxygenModule.MENU,HopperScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(OxygenPayload.TYPE,(payload,context) -> {
            state = payload;
            var player=context.client().player;
            if (payload.low() && player!=null && KeplerDimension.isKepler(player.level().dimension()) && player.tickCount % 40 <20)
                player.playSound(SoundEvents.NOTE_BLOCK_BIT.value(),0.5F,0.6F);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> state = new OxygenPayload(0,0,true));
        HudElementRegistry.addLast(DolfinSpace.id("oxygen"),(gui,delta) -> {
            var mc=Minecraft.getInstance(); var player=mc.player;
            if (player==null || !KeplerDimension.isKepler(player.level().dimension())) return;
            if (state.low() && player.tickCount%20<10) return;
            int count=state.capacity()>0?(int)Math.ceil(10.0*state.remaining()/state.capacity()):0;
            int x=gui.guiWidth()/2+10,y=gui.guiHeight()-59;
            for (int i=0;i<10;i++) {
                int bx=x+i*8, color=i<count?0xff55ddff:0xff334455;
                gui.fill(bx+1,y,bx+6,y+1,color); gui.fill(bx,y+1,bx+7,y+6,color); gui.fill(bx+1,y+6,bx+6,y+7,color);
                gui.fill(bx+2,y+2,bx+5,y+5,0xff112233);
            }
        });
    }
}
