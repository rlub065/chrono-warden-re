package com.chrono;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ChronoMod.MOD_ID, value = Dist.CLIENT)
public class ChronoClient {

    public static final String CATEGORY = "key.categories.chrono";

    public static int charge = 0;
    public static boolean standActive = false;
    public static boolean blocking = false;
    public static int anchorLeft = 0;

    public static final KeyMapping[] KEYS = new KeyMapping[8];
    private static final boolean[] WAS_DOWN = new boolean[8];
    private static int cannonHoldTicks = 0;
    private static int lastShownLevel = 0;
    private static boolean wasAttack;
    private static boolean wasUse;
    private static int m1Cd;

    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.42f, 0.78f, 1.00f), 1.15f);
    private static final DustParticleOptions CYAN_BIG = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1.00f), 1.55f);
    private static final DustParticleOptions HAND = new DustParticleOptions(new Vector3f(0.15f, 0.35f, 0.70f), 1.1f);

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

    public static void applySync(SyncPacket msg) {
        charge = msg.charge;
        standActive = msg.standActive;
        blocking = msg.blocking;
        anchorLeft = msg.anchorLeft;
    }

    @Mod.EventBusSubscriber(modid = ChronoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            for (int i = 1; i < KEYS.length; i++) event.register(KEYS[i]);
        }
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!standActive) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(true);
        }
        // RMB with activator still allowed for toggle
        if (event.isUseItem()) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.is(ChronoMod.CHRONO_ACTIVATOR.get()) && !mc.player.getOffhandItem().is(ChronoMod.CHRONO_ACTIVATOR.get())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean shift = mc.options.keyShift.isDown();
        if (m1Cd > 0) m1Cd--;

        // M1
        boolean attack = mc.options.keyAttack.isDown();
        if (standActive && attack && !wasAttack && m1Cd <= 0) {
            ChronoMod.CHANNEL.sendToServer(new KeyPacket(8, 0, 0));
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            m1Cd = 7;
        }
        wasAttack = attack;

        // Block on RMB (not activator)
        boolean holdingActivator = mc.player.getMainHandItem().is(ChronoMod.CHRONO_ACTIVATOR.get())
                || mc.player.getOffhandItem().is(ChronoMod.CHRONO_ACTIVATOR.get());
        boolean use = mc.options.keyUse.isDown() && standActive && !holdingActivator;
        if (use && !wasUse) {
            ChronoMod.CHANNEL.sendToServer(new KeyPacket(9, 0, 1));
        } else if (!use && wasUse) {
            ChronoMod.CHANNEL.sendToServer(new KeyPacket(9, 1, 0));
        }
        wasUse = use;

        // Cannon hold
        boolean cannonDown = KEYS[5].isDown();
        if (cannonDown) {
            cannonHoldTicks++;
            int level = cannonHoldTicks >= 50 ? 3 : (cannonHoldTicks >= 20 ? 2 : 1);
            double x = mc.player.getX(), y = mc.player.getY() + 1.2, z = mc.player.getZ();
            if (level == 1) mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.04, 0);
            else if (level == 2) {
                mc.level.addParticle(ParticleTypes.END_ROD, x + (Math.random() - 0.5) * 0.5, y, z + (Math.random() - 0.5) * 0.5, 0, 0.06, 0);
            } else {
                mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0, 0.04, 0);
            }
            if (level != lastShownLevel) lastShownLevel = level;
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

        if (standActive) drawClock(mc);
    }

    private static void drawClock(Minecraft mc) {
        var p = mc.player;
        var look = p.getLookAngle();
        var right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x);
        if (right.lengthSqr() < 0.001) right = new net.minecraft.world.phys.Vec3(1, 0, 0);
        right = right.normalize();
        var c = p.position().add(0, 1.45, 0).add(look.scale(-0.85)).add(right.scale(0.75));
        double t = p.tickCount * 0.09;
        int teeth = 12;
        for (int i = 0; i < teeth; i++) {
            double a = t + i * (Math.PI * 2 / teeth);
            double r = (i % 2 == 0) ? 0.72 : 0.54;
            mc.level.addParticle(CYAN, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 0, 0, 0);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * (Math.PI * 2 / 16);
            mc.level.addParticle(CYAN, c.x + Math.cos(a) * 0.42, c.y, c.z + Math.sin(a) * 0.42, 0, 0, 0);
        }
        double hour = t * 0.15;
        double min = t * 0.55;
        for (int i = 1; i <= 4; i++) {
            double u = i / 4.0 * 0.32;
            mc.level.addParticle(HAND, c.x + Math.cos(hour) * u, c.y, c.z + Math.sin(hour) * u, 0, 0, 0);
        }
        for (int i = 1; i <= 6; i++) {
            double u = i / 6.0 * 0.38;
            mc.level.addParticle(HAND, c.x + Math.cos(min) * u, c.y, c.z + Math.sin(min) * u, 0, 0, 0);
        }
        mc.level.addParticle(CYAN_BIG, c.x, c.y, c.z, 0, 0, 0);
    }
}
