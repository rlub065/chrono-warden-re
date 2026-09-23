package com.chrono;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = ChronoMod.MOD_ID)
public class ChronoManager {

    private static final Map<UUID, PlayerState> STATE = new HashMap<>();

    // Costs
    private static final int COST_PLAYTIME = 100;
    private static final int COST_FAST_FORWARD = 75;
    private static final int COST_CANNON_CHARGED = 35; // lvl 2+
    private static final int COST_CANNON_BASIC = 15;
    private static final int COST_TREMOR = 25;
    private static final int COST_HALT = 30;
    private static final int COST_ANCHOR_PLACE = 20;
    private static final int COST_ANCHOR_RETURN = 15;
    private static final int COST_BARRAGE = 0;

    private static final int MAX_CHARGE = 100;
    private static final int ANCHOR_DURATION = 600; // 30 sec

    public static class PlayerState {
        boolean standActive = false;
        int charge = MAX_CHARGE;
        long lastChargeTick = 0;

        long fastForwardEnd = 0;
        int fastForwardAttacks = 0;
        long haltEnd = 0;
        long playtimeEnd = 0;

        Vec3 anchorPos = null;
        long anchorExpire = 0;
        float anchorHp = 0;
        boolean canResurrect = true;

        Set<UUID> frozen = new HashSet<>();
        // projectiles frozen by playtime keep velocity for restore
        Map<UUID, Vec3> savedProjectileVel = new HashMap<>();

        int barrageHitsLeft = 0;
        LivingEntity barrageTarget = null;

        int pullTicks = 0;
        Vec3 pullCenter = null;
        int pullRadius = 0;

        long zoneEnd = 0;
        Vec3 zoneCenter = null;
        double zoneRadius = 0;
    }

    private static PlayerState get(ServerPlayer p) {
        return STATE.computeIfAbsent(p.getUUID(), u -> new PlayerState());
    }

    public static void toggleFromItem(ServerPlayer p) {
        PlayerState s = get(p);
        toggleStand(p, s);
    }

    public static void handleKey(ServerPlayer p, int key, int action, int data) {
        if (action != 0) return;
        PlayerState s = get(p);
        if (!s.standActive && key != 0) {
            msg(p, "§cСначала активируй стенд (ПКМ активатором)");
            return;
        }
        long now = p.level().getGameTime();
        boolean shift = data != 0;

        switch (key) {
            case 0 -> {} // summon only via item now
            case 1 -> fastForward(p, s, now);
            case 2 -> startBarrage(p, s, now);
            case 3 -> halt(p, s, now);
            case 4 -> tremor(p, s);
            case 5 -> cannon(p, s, data);
            case 6 -> timeAnchor(p, s, now, shift);
            case 7 -> playtimeOver(p, s, now);
        }
    }

    private static boolean spend(ServerPlayer p, PlayerState s, int cost) {
        if (s.charge < cost) {
            msg(p, "§cНедостаточно заряда! §7[" + s.charge + "/" + MAX_CHARGE + "] нужно " + cost);
            return false;
        }
        s.charge -= cost;
        showCharge(p, s);
        return true;
    }

    private static void showCharge(ServerPlayer p, PlayerState s) {
        int bars = s.charge / 10;
        StringBuilder sb = new StringBuilder("§bЗаряд §3[");
        for (int i = 0; i < 10; i++) sb.append(i < bars ? "§b|" : "§8|");
        sb.append("§3] §f").append(s.charge);
        p.displayClientMessage(Component.literal(sb.toString()), true);
    }

    private static void toggleStand(ServerPlayer p, PlayerState s) {
        s.standActive = !s.standActive;
        if (s.standActive) {
            msg(p, "§b§lCHRONO WARDEN §3— активирован");
            showCharge(p, s);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.9f, 1.6f);
            ServerLevel w = p.serverLevel();
            w.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.9, 0.6, 0.1);
            w.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 12, 0.35, 0.5, 0.35, 0.04);
        } else {
            msg(p, "§7Chrono Warden деактивирован");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9f, 1.3f);
            s.fastForwardEnd = 0;
        }
    }

    private static void fastForward(ServerPlayer p, PlayerState s, long now) {
        if (now < s.fastForwardEnd) {
            s.fastForwardEnd = 0;
            s.fastForwardAttacks = 0;
            p.removeEffect(MobEffects.MOVEMENT_SPEED);
            p.removeEffect(MobEffects.DIG_SPEED);
            msg(p, "§7Fast Forward отключён");
            return;
        }
        if (!spend(p, s, COST_FAST_FORWARD)) return;
        s.fastForwardEnd = now + 200; // 10 sec
        s.fastForwardAttacks = 0;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1, false, false));
        msg(p, "§b§lFAST FORWARD");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.8f);
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.7, 0.4, 0.08);
    }

    private static void startBarrage(ServerPlayer p, PlayerState s, long now) {
        // free
        if (now < s.fastForwardEnd) s.fastForwardAttacks++;
        LivingEntity t = findTarget(p, 5.0);
        if (t == null) {
            msg(p, "§cНет цели");
            return;
        }
        s.barrageTarget = t;
        s.barrageHitsLeft = 10;
        msg(p, "§bBARRAGE!");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.5f);
        if (s.fastForwardAttacks >= 5) {
            s.fastForwardEnd = 0;
            s.fastForwardAttacks = 0;
            p.removeEffect(MobEffects.MOVEMENT_SPEED);
            p.removeEffect(MobEffects.DIG_SPEED);
            msg(p, "§7Fast Forward закончился");
        }
    }

    // HALT — projectiles lose energy and fall
    private static void halt(ServerPlayer p, PlayerState s, long now) {
        if (now < s.haltEnd) {
            s.haltEnd = 0;
            unfreezeHalt(p.serverLevel(), s);
            msg(p, "§eHalt снят");
            return;
        }
        if (!spend(p, s, COST_HALT)) return;
        s.haltEnd = now + 60; // 3 sec
        ServerLevel w = p.serverLevel();
        AABB box = p.getBoundingBox().inflate(14);
        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
            s.frozen.add(e.getUUID());
            e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setTarget(null);
            }
        }
        // projectiles: stop + will fall when unfrozen (no saved vel)
        for (Entity e : w.getEntitiesOfClass(Entity.class, box, e -> e instanceof Projectile)) {
            s.frozen.add(e.getUUID());
            e.setDeltaMovement(Vec3.ZERO);
            e.setNoGravity(true);
            // do NOT save velocity — they lose energy
        }
        msg(p, "§3§lHALT");
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.65f);
        w.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 35, 1.2, 1.2, 1.2, 0.06);
    }

    private static void tremor(ServerPlayer p, PlayerState s) {
        if (!spend(p, s, COST_TREMOR)) return;
        ServerLevel w = p.serverLevel();
        BlockPos center = p.blockPosition();
        AABB box = p.getBoundingBox().inflate(8);
        int hits = 0;
        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
            e.hurt(p.damageSources().magic(), 10.0f);
            e.invulnerableTime = 0;
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.65, 0));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            hits++;
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 25) continue;
                BlockPos pos = center.offset(dx, -1, dz);
                BlockState state = w.getBlockState(pos);
                if (state.isAir() || !state.isSolidRender(w, pos)) continue;
                w.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 3, 0.2, 0.08, 0.2, 0.015);
                if (p.getRandom().nextFloat() < 0.12f) {
                    try {
                        net.minecraft.world.entity.item.FallingBlockEntity.fall(w, pos.above(), state);
                    } catch (Exception ignored) {}
                }
            }
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.1f, 0.7f);
        msg(p, "§6TREMOR (" + hits + ")");
    }

    private static void cannon(ServerPlayer p, PlayerState s, int holdTicks) {
        int level = 1;
        if (holdTicks >= 50) level = 3;
        else if (holdTicks >= 20) level = 2;

        int cost = level == 1 ? COST_CANNON_BASIC : COST_CANNON_CHARGED;
        if (!spend(p, s, cost)) return;

        ServerLevel w = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 start = p.getEyePosition();

        if (level == 1) {
            for (int d = 1; d <= 16; d++) {
                Vec3 pos = start.add(look.scale(d * 0.7));
                w.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.01);
                AABB box = new AABB(pos.x - 0.35, pos.y - 0.35, pos.z - 0.35, pos.x + 0.35, pos.y + 0.35, pos.z + 0.35);
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
                    e.hurt(p.damageSources().magic(), 7.0f);
                    e.invulnerableTime = 0;
                    e.setDeltaMovement(look.scale(0.6).add(0, 0.2, 0));
                    e.hurtMarked = true;
                }
            }
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9f, 0.7f);
            msg(p, "§bCannon §7[LVL 1]");
        } else if (level == 2) {
            Vec3 end = start.add(look.scale(14));
            s.pullCenter = end;
            s.pullTicks = 22;
            s.pullRadius = 5;
            w.sendParticles(ParticleTypes.REVERSE_PORTAL, end.x, end.y, end.z, 30, 1.0, 1.0, 1.0, 0.08);
            w.playSound(null, end.x, end.y, end.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.6f, 1.5f);
            msg(p, "§bCannon §e[LVL 2]");
        } else {
            Vec3 end = start.add(look.scale(12));
            s.zoneCenter = end;
            s.zoneRadius = 5.5;
            s.zoneEnd = p.level().getGameTime() + 70; // 3.5 sec
            AABB box = new AABB(end.x - 5.5, end.y - 5.5, end.z - 5.5, end.x + 5.5, end.y + 5.5, end.z + 5.5);
            for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
                s.frozen.add(e.getUUID());
                e.setNoGravity(true);
                e.setDeltaMovement(Vec3.ZERO);
                if (e instanceof Mob mob) {
                    mob.setNoAi(true);
                    mob.setTarget(null);
                }
            }
            w.sendParticles(ParticleTypes.REVERSE_PORTAL, end.x, end.y, end.z, 50, 2.0, 2.0, 2.0, 0.1);
            w.playSound(null, end.x, end.y, end.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 0.8f);
            msg(p, "§cCannon §4[LVL 3 — Zone]");
        }
    }

    private static void timeAnchor(ServerPlayer p, PlayerState s, long now, boolean shift) {
        if (shift || s.anchorPos == null || now > s.anchorExpire) {
            if (!spend(p, s, COST_ANCHOR_PLACE)) return;
            s.anchorPos = p.position();
            s.anchorExpire = now + ANCHOR_DURATION;
            s.anchorHp = p.getHealth();
            s.canResurrect = true;
            msg(p, "§3TIME ANCHOR §bустановлен §7(30 сек)");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.9f, 1.4f);
            p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.12);
        } else {
            if (!spend(p, s, COST_ANCHOR_RETURN)) return;
            float oldHp = p.getHealth();
            p.teleportTo(s.anchorPos.x, s.anchorPos.y, s.anchorPos.z);
            float diff = s.anchorHp - oldHp;
            if (diff > 0) p.heal(Math.min(15, diff));
            else p.setHealth(Math.max(1, p.getHealth() + Math.max(-15, diff)));
            msg(p, "§bВозврат к якорю");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.2f);
            p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 35, 0.5, 0.8, 0.5, 0.15);
        }
    }

    // PLAYTIME — projectiles KEEP velocity
    private static void playtimeOver(ServerPlayer p, PlayerState s, long now) {
        if (now < s.playtimeEnd) {
            s.playtimeEnd = 0;
            unfreezePlaytime(p.serverLevel(), s);
            msg(p, "§ePlaytime's Over завершён");
            return;
        }
        if (!spend(p, s, COST_PLAYTIME)) return;
        s.playtimeEnd = now + 100; // 5 sec
        ServerLevel w = p.serverLevel();
        AABB box = p.getBoundingBox().inflate(28);
        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
            s.frozen.add(e.getUUID());
            e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setTarget(null);
            }
        }
        // projectiles: freeze but SAVE velocity so they continue after
        for (Entity e : w.getEntitiesOfClass(Entity.class, box, e -> e instanceof Projectile)) {
            s.frozen.add(e.getUUID());
            s.savedProjectileVel.put(e.getUUID(), e.getDeltaMovement());
            e.setDeltaMovement(Vec3.ZERO);
            e.setNoGravity(true);
        }
        msg(p, "§4§lPLAYTIME'S OVER");
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.4f, 0.55f);
        w.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 70, 2.0, 2.0, 2.0, 0.1);
    }

    private static void unfreezeHalt(ServerLevel w, PlayerState s) {
        for (UUID id : s.frozen) {
            Entity e = w.getEntity(id);
            if (e != null) {
                e.setNoGravity(false);
                if (e instanceof Projectile) {
                    // lose energy — drop down
                    e.setDeltaMovement(new Vec3(0, -0.15, 0));
                }
                if (e instanceof LivingEntity le && le instanceof Mob mob) {
                    mob.setNoAi(false);
                }
            }
        }
        s.frozen.clear();
    }

    private static void unfreezePlaytime(ServerLevel w, PlayerState s) {
        for (UUID id : s.frozen) {
            Entity e = w.getEntity(id);
            if (e != null) {
                e.setNoGravity(false);
                if (e instanceof Projectile) {
                    Vec3 vel = s.savedProjectileVel.getOrDefault(id, e.getDeltaMovement());
                    e.setDeltaMovement(vel); // restore full energy
                }
                if (e instanceof LivingEntity le && le instanceof Mob mob) {
                    mob.setNoAi(false);
                }
            }
        }
        s.frozen.clear();
        s.savedProjectileVel.clear();
    }

    private static void unfreeze(ServerLevel w, PlayerState s) {
        unfreezePlaytime(w, s);
    }

    private static LivingEntity findTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.55;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> e != p && e.isAlive())) {
            Vec3 to = e.getEyePosition().subtract(eye).normalize();
            double dot = look.dot(to);
            if (dot > bestDot && eye.distanceTo(e.getEyePosition()) <= range) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private static void msg(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();

        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            PlayerState s = STATE.get(p.getUUID());
            if (s == null) continue;
            ServerLevel w = p.serverLevel();

            // charge regen: +1 every 8 ticks while stand active
            if (s.standActive && s.charge < MAX_CHARGE && now - s.lastChargeTick >= 8) {
                s.charge = Math.min(MAX_CHARGE, s.charge + 1);
                s.lastChargeTick = now;
            }

            // anchor expire
            if (s.anchorPos != null && now > s.anchorExpire) {
                s.anchorPos = null;
                msg(p, "§7Time Anchor истёк");
            }

            // barrage
            if (s.barrageHitsLeft > 0 && s.barrageTarget != null) {
                if (!s.barrageTarget.isAlive() || s.barrageTarget.distanceTo(p) > 7) {
                    s.barrageHitsLeft = 0;
                    s.barrageTarget = null;
                } else {
                    s.barrageTarget.hurt(p.damageSources().playerAttack(p), 2.8f);
                    s.barrageTarget.invulnerableTime = 0;
                    w.sendParticles(ParticleTypes.CRIT, s.barrageTarget.getX(), s.barrageTarget.getY() + 1, s.barrageTarget.getZ(), 4, 0.2, 0.3, 0.2, 0.06);
                    s.barrageHitsLeft--;
                    if (s.barrageHitsLeft <= 0) s.barrageTarget = null;
                }
            }

            // pull lvl2
            if (s.pullTicks > 0 && s.pullCenter != null) {
                s.pullTicks--;
                AABB box = new AABB(s.pullCenter.x - s.pullRadius, s.pullCenter.y - s.pullRadius, s.pullCenter.z - s.pullRadius,
                        s.pullCenter.x + s.pullRadius, s.pullCenter.y + s.pullRadius, s.pullCenter.z + s.pullRadius);
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
                    Vec3 toCenter = s.pullCenter.subtract(e.position()).normalize().scale(0.3);
                    e.setDeltaMovement(e.getDeltaMovement().add(toCenter));
                    e.hurtMarked = true;
                }
                if (s.pullTicks <= 0) {
                    for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
                        e.hurt(p.damageSources().magic(), 12.0f);
                        e.invulnerableTime = 0;
                        Vec3 away = e.position().subtract(s.pullCenter).normalize().scale(1.1).add(0, 0.4, 0);
                        e.setDeltaMovement(away);
                        e.hurtMarked = true;
                    }
                    w.sendParticles(ParticleTypes.EXPLOSION, s.pullCenter.x, s.pullCenter.y, s.pullCenter.z, 3, 0.3, 0.3, 0.3, 0.05);
                    w.playSound(null, s.pullCenter.x, s.pullCenter.y, s.pullCenter.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.15f);
                    s.pullCenter = null;
                }
            }

            // zone lvl3
            if (s.zoneEnd > now && s.zoneCenter != null) {
                AABB box = new AABB(s.zoneCenter.x - s.zoneRadius, s.zoneCenter.y - s.zoneRadius, s.zoneCenter.z - s.zoneRadius,
                        s.zoneCenter.x + s.zoneRadius, s.zoneCenter.y + s.zoneRadius, s.zoneCenter.z + s.zoneRadius);
                for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
                    s.frozen.add(e.getUUID());
                    e.setDeltaMovement(Vec3.ZERO);
                    e.setNoGravity(true);
                    if (e instanceof Mob mob) {
                        mob.setNoAi(true);
                        mob.setTarget(null);
                    }
                }
            } else if (s.zoneEnd > 0 && now >= s.zoneEnd) {
                s.zoneEnd = 0;
                s.zoneCenter = null;
                unfreeze(w, s);
            }

            if (s.haltEnd > 0 && now >= s.haltEnd) {
                s.haltEnd = 0;
                unfreezeHalt(w, s);
                msg(p, "§eHalt закончился");
            }
            if (s.playtimeEnd > 0 && now >= s.playtimeEnd) {
                s.playtimeEnd = 0;
                unfreezePlaytime(w, s);
                msg(p, "§ePlaytime's Over закончился");
            }

            if (s.haltEnd > now || s.playtimeEnd > now || s.zoneEnd > now) {
                for (UUID id : new HashSet<>(s.frozen)) {
                    Entity e = w.getEntity(id);
                    if (e == null) {
                        s.frozen.remove(id);
                        continue;
                    }
                    e.setDeltaMovement(Vec3.ZERO);
                    e.setNoGravity(true);
                    if (e instanceof LivingEntity le && le instanceof Mob mob) {
                        mob.setNoAi(true);
                        mob.setTarget(null);
                    }
                }
            }

            if (s.fastForwardEnd > 0 && now >= s.fastForwardEnd) {
                s.fastForwardEnd = 0;
                s.fastForwardAttacks = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        PlayerState s = STATE.get(p.getUUID());
        if (s == null || !s.standActive) return;

        event.setAmount(event.getAmount() * 0.85f);

        if (s.canResurrect && s.anchorPos != null && p.level().getGameTime() <= s.anchorExpire
                && p.getHealth() - event.getAmount() <= 0) {
            if (p.position().distanceTo(s.anchorPos) < 10) {
                event.setCanceled(true);
                p.setHealth(8.0f);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1));
                s.canResurrect = false;
                msg(p, "§aTEMPORAL RESURRECTION!");
                p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9f, 1.0f);
            }
        }
    }
}
