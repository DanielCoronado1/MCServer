package com.dolfin.space.client.machines;
import com.dolfin.space.machines.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    public MachineScreen(MachineMenu m,Inventory inv,Component title) { super(m,inv,title); }
    @Override public void extractContents(GuiGraphicsExtractor g,int x,int y,float dt) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff26313d);
        for(var slot:menu.slots) g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff576474);
        int width=150*Math.min(menu.data.get(0),Math.max(1,menu.data.get(1)))/Math.max(1,menu.data.get(1));
        g.fill(leftPos+8,topPos+59,leftPos+158,topPos+65,0xff111820);
        g.fill(leftPos+8,topPos+59,leftPos+8+width,topPos+65,0xff55ddbb);
        super.extractContents(g,x,y,dt);
        if(x>=leftPos+8 && x<=leftPos+158 && y>=topPos+57 && y<=topPos+70) {
            String guide=switch(menu.kind) {
                case 0 -> "2 lingotes → placa (acero/cobre/oro/titanio). Carbón al costado.";
                case 1 -> "Redstone + cuarzo + placa oro → básico; básico + diamante + cristal → avanzado.";
                default -> "Lava + blaze + bidón vacío → combustible + cubeta. Carbón al costado.";
            };
            g.setTooltipForNextFrame(Component.literal(guide),x,y);
        }
    }
}
