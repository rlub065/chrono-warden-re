package com.chrono;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ChronoMod.MOD_ID, value = Dist.CLIENT)
public class ChronoClient {

    public static final String CATEGORY = "key.categories.chrono";

    // 0 unused, 1=fastforward, 2=barrage, 3=halt, 4=tremor, 5=cannon, 6=anchor, 7=playtime
    public static final KeyMapping[] KEYS = new KeyMapping[8];
    private static final boolean[] WAS_DOWN = new boolean[8];
    private static int cannonHoldTicks = 0;
    private static int lastShownLevel = 0;

    static {
        KEYS[0] = new KeyMapping("key.chrono.summon", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
        KEYS[1] = new KeyMapping("key.chrono.fastforward", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);
        KEYS[2] = new KeyMapping("key.chrono.barrage", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);
        KEYS[3] = new KeyMapping("key.chrono.halt", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
        KEYS[4] = new KeyMapping("key.chrono.tremor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
        KEYS[5] = new KeyMapping("key.chrono.cannon", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, CATEGORY);
        KEYS[6] = new KeyMapping("key.chrono.anchor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
        KEYS[7] = new KeyMapping("key.chrono.playtime", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    }

    @Mod.EventBusSubscriber(modid = ChronoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            for (int i = 1; i < KEYS.length; i++) event.register(KEYS[i]);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean shift = mc.options.keyShift.isDown();

        boolean cannonDown = KEYS[5].isDown();
        if (cannonDown) {
            cannonHoldTicks++;
            int level = cannonHoldTicks >= 50 ? 3 : (cannonHoldTicks >= 20 ? 2 : 1);
            if (mc.level != null) {
                double x = mc.player.getX(), y = mc.player.getY() + 1.2, z = mc.player.getZ();
                if (level == 1) mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.04, 0);
                else if (level == 2) {
                    mc.level.addParticle(ParticleTypes.END_ROD, x + (Math.random() - 0.5) * 0.5, y, z + (Math.random() - 0.5) * 0.5, 0, 0.06, 0);
                } else {
                    mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0, 0.04, 0);
                    mc.level.addParticle(ParticleTypes.END_ROD, x + (Math.random() - 0.5), y, z + (Math.random() - 0.5), 0, 0.08, 0);
                }
            }
            if (level != lastShownLevel) {
                lastShownLevel = level;
                String t = level == 1 ? "§bCannon §7[1]" : level == 2 ? "§bCannon §e[2 Pull]" : "§bCannon §c[3 Zone]";
                mc.player.displayClientMessage(Component.literal(t), true);
            }
        } else if (WAS_DOWN[5]) {
            ChronoMod.CHANNEL.sendToServer(new KeyPacket(5, 0, cannonHoldTicks));
            cannonHoldTicks = 0;
            lastShownLevel = 0;
        }
        WAS_DOWN[5] = cannonDown;

        for (int i = 1; i < KEYS.length; i++) {
            if (i == 5) continue;
            boolean down = KEYS[i].isDown();
            if (down && !WAS_DOWN[i]) {
                ChronoMod.CHANNEL.sendToServer(new KeyPacket(i, 0, shift ? 1 : 0));
            }
            WAS_DOWN[i] = down;
        }
    }
}
