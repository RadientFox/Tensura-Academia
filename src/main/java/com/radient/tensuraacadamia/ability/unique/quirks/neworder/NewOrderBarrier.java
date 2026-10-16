package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// The Air Will Protect Me
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderBarrier {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    private static final String HEALTH_TAG = "barrierHealth";
    private static final String MAX_TAG = "barrierMax";
    public static final double BARRIER_GAP = 0.5D;
    private static final int HIT_FRAMES = 10;

    private static final Map<UUID, NewOrderUtil.Frame> HITS = new HashMap<>();

    public record BarrierHealth(float health, float max) {}

    private NewOrderBarrier() {
    }

    // null when there's no barrier
    public static @Nullable BarrierHealth getBarrier(LivingEntity entity) {
        CompoundTag tag = NewOrderQuirk.getNewOrder(entity).map(ManasSkillInstance::getTag).orElse(null);
        if (tag == null || tag.getFloat(HEALTH_TAG) <= 0.0F) {
            return null;
        }
        return new BarrierHealth(tag.getFloat(HEALTH_TAG), tag.getFloat(MAX_TAG));
    }

    public static AABB getBox(LivingEntity entity) {
        AABB body = entity.getBoundingBox();
        double half = Math.max(body.getXsize(), body.getYsize()) * 0.5D + BARRIER_GAP * entity.getScale();
        return new AABB(body.getCenter(), body.getCenter()).inflate(half);
    }

    static boolean raise(ServerLevel level, LivingEntity player, ManasSkillInstance instance, int mode) {
        if (EnergyHelper.isOutOfEnergy(player, instance, mode)) {
            return false;
        }

        boolean renewed = getBarrier(player) != null;
        float max = (float) (instance.isMastered(player) ? CONFIG.protectHealthMastered : CONFIG.protectHealth);
        CompoundTag tag = instance.getOrCreateTag();
        tag.putFloat(HEALTH_TAG, max);
        tag.putFloat(MAX_TAG, max);
        NewOrderUtil.dirty(instance, player);
        HITS.remove(player.getUUID());
        instance.addMasteryPoint(player);

        player.swing(InteractionHand.MAIN_HAND, true);
        AABB box = getBox(player);
        Vec3 center = box.getCenter();
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_INHALE, 1.0F, renewed ? 1.3F : 1.0F);
        NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8F, 0.8F);
        NewOrderUtil.callout(level, new Vec3(player.getX(), box.maxY + 0.6D, player.getZ()), player, NewOrderQuirk.getCalloutName(instance, mode), NewOrderRules.RULE_COLORS[NewOrderRule.AIR]);
        return true;
    }

    private static @Nullable Vec3 getFrom(DamageSource source, LivingEntity entity) {
        if (source.getEntity() == entity) {
            return null;
        }
        if (source.getSourcePosition() != null) {
            return source.getSourcePosition();
        }
        return source.getDirectEntity() != null ? source.getDirectEntity().position() : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (!(entity.level() instanceof ServerLevel level) || event.getAmount() <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        ManasSkillInstance instance = NewOrderQuirk.getNewOrder(entity).orElse(null);
        CompoundTag tag = instance == null ? null : instance.getTag();
        if (tag == null || tag.getFloat(HEALTH_TAG) <= 0.0F || !NewOrderRules.hasRule(entity, NewOrderRule.AIR_CONTROL)) {
            return;
        }

        Vec3 from = getFrom(source, entity);
        if (from == null) {
            return;
        }

        AABB box = getBox(entity);
        float taken = NewOrderUtil.frame(HITS, entity.getUUID(), level.getGameTime(), event.getAmount(), HIT_FRAMES);
        float health = tag.getFloat(HEALTH_TAG) - taken;
        Vec3 hit = new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY), Mth.clamp(from.z, box.minZ, box.maxZ));
        if (health <= 0.0F) {
            event.setAmount(-health);
            shatter(level, entity, instance, box);
            return;
        }

        event.setCanceled(true);
        if (taken > 0.0F) {
            tag.putFloat(HEALTH_TAG, health);
            NewOrderUtil.dirty(instance, entity);
            level.sendParticles(ParticleTypes.SMALL_GUST, hit.x, hit.y, hit.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            NewOrderUtil.playSound(level, hit, SoundEvents.BREEZE_DEFLECT, 1.0F, 1.1F + level.random.nextFloat() * 0.3F);
        }
    }

    private static void shatter(ServerLevel level, LivingEntity entity, ManasSkillInstance instance, AABB box) {
        clear(entity, instance);
        instance.setCoolDown(CONFIG.protectCooldown, NewOrderQuirk.PROTECT);
        Vec3 center = box.getCenter();
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_DEATH, 1.0F, 1.4F);
        NewOrderUtil.playSound(level, center, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.9F);
    }

    private static void clear(LivingEntity entity, ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(HEALTH_TAG)) {
            tag.remove(HEALTH_TAG);
            tag.remove(MAX_TAG);
            NewOrderUtil.dirty(instance, entity);
        }
        HITS.remove(entity.getUUID());
    }

    static void forget(LivingEntity entity, ManasSkillInstance instance) {
        if (getBarrier(entity) != null && entity.level() instanceof ServerLevel level) {
            Vec3 center = getBox(entity).getCenter();
            level.sendParticles(ParticleTypes.SMALL_GUST, center.x, center.y, center.z, 4, 0.6D, 0.8D, 0.6D, 0.0D);
            NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_IDLE_AIR, 1.0F, 1.2F);
        }
        clear(entity, instance);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!HITS.isEmpty() && event.getServer().getTickCount() % 200 == 0) {
            long time = event.getServer().overworld().getGameTime();
            HITS.values().removeIf(hit -> time - hit.time() > HIT_FRAMES);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HITS.clear();
    }

}
