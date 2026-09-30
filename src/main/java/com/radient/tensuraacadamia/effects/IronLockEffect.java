package com.radient.tensuraacadamia.effects;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class IronLockEffect extends MobEffect {
    private static final DustParticleOptions IRON = new DustParticleOptions(new Vector3f(0.75F, 0.78F, 0.82F), 0.9F);
    public IronLockEffect() { super(MobEffectCategory.HARMFUL, 0xC4BEC0); }
    public static void syncIndicator(LivingEntity target) {
        var effect = target.getEffect(MHAEffects.IRON_LOCK);
        if (effect != null && target.level() instanceof ServerLevel level)
            level.getChunkSource().broadcast(target, new ClientboundUpdateMobEffectPacket(target.getId(), effect, false));
    }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
    @Override public boolean applyEffectTick(LivingEntity target, int amplifier) {
        target.setDeltaMovement(Vec3.ZERO); target.hurtMarked = true; target.fallDistance = 0;
        if (target instanceof Mob mob) mob.getNavigation().stop();
        if (target.tickCount % 20 == 0) syncIndicator(target);
        if (target.level() instanceof ServerLevel level && target.tickCount % 4 == 0) {
            double half = target.getBbWidth() / 2 + 0.25;
            for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) for (double y : new double[]{0.25, 0.55, 0.9})
                level.sendParticles(IRON, target.getX() + x * half, target.getY() + y * target.getBbHeight(),
                        target.getZ() + z * half, 1, 0, 0, 0, 0);
        }
        var data = target.getPersistentData();
        if (data.contains("AlchemyLockX")) {
            Vec3 anchor = new Vec3(data.getDouble("AlchemyLockX"), data.getDouble("AlchemyLockY"), data.getDouble("AlchemyLockZ"));
            if (target.position().distanceToSqr(anchor) > 0.0001) {
                target.setPos(anchor);
                if (target instanceof ServerPlayer player) player.connection.teleport(anchor.x, anchor.y, anchor.z, target.getYRot(), target.getXRot());
            }
        }
        return true;
    }
}
