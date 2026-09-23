package com.chrono;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChronoMod.MOD_ID, value = Dist.CLIENT)
public class ChronoOverlay {

    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!ChronoClient.standActive && ChronoClient.charge <= 0) return;

        GuiGraphics g = event.getGuiGraphics();
        int sw = event.getWindow().getGuiScaledWidth();
        int sh = event.getWindow().getGuiScaledHeight();

        int barW = 140;
        int barH = 8;
        int x = sw / 2 - barW / 2;
        int y = sh - 64;

        g.fill(x - 2, y - 13, x + barW + 2, y + barH + 4, 0xC0050A14);
        g.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xFF1A3A55);

        int fill = Math.max(0, Math.min(barW, (int) (barW * (ChronoClient.charge / 100.0))));
        int color = ChronoClient.charge >= 100 ? 0xFF55EEFF : 0xFF33AADD;
        if (ChronoClient.blocking) color = 0xFF88AAFF;
        g.fill(x, y, x + fill, y + barH, color);
        g.fill(x, y, x + fill, y + 2, 0x66FFFFFF);

        String title = ChronoClient.standActive ? "CHRONO WARDEN" : "не активен";
        g.drawCenteredString(mc.font, title, sw / 2, y - 12, ChronoClient.standActive ? 0x88DDFF : 0x888888);
        g.drawCenteredString(mc.font, ChronoClient.charge + "/100", sw / 2, y + 1, 0xFFFFFF);

        if (ChronoClient.blocking) {
            g.drawCenteredString(mc.font, "BLOCK", sw / 2, y + barH + 5, 0xAACCFF);
        }
        if (ChronoClient.anchorLeft > 0) {
            g.drawString(mc.font, "якорь " + ChronoClient.anchorLeft + "с", x + barW + 6, y, 0x66CCFF, false);
        }
    }
}
