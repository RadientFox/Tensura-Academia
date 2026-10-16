package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.ability.unique.quirks.MultiArms;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.entity.template.GreaterSpiritEntity;
import io.github.manasmods.tensura.entity.template.subclass.IArenaBoss;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

// Small helpers shared across New Order
final class NewOrderUtil {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;
    private static final double CALLOUT_RANGE = 48.0D;
    private static final double IMPACT_RANGE = 96.0D;

    record Aim(Vec3 point, @Nullable LivingEntity entity, @Nullable BlockHitResult block) {}

    record Frame(long time, float amount) {}

    private NewOrderUtil() {
    }

    static void dirty(ManasSkillInstance instance, LivingEntity entity) {
        instance.markDirty();
        SkillAPI.getSkillsFrom(entity).markDirty();
    }

    static Component readName(String json, ServerLevel level) {
        if (json.isEmpty()) {
            return Component.empty();
        }

        try {
            MutableComponent name = Component.Serializer.fromJson(json, level.registryAccess());
            return name == null ? Component.empty() : name;
        } catch (RuntimeException exception) {
            return Component.literal(json);
        }
    }

    static void callout(ServerLevel level, Vec3 pos, @Nullable Entity follow, Component text, int color) {
        NewOrderPayloads.CalloutPayload callout = new NewOrderPayloads.CalloutPayload(pos, follow == null ? -1 : follow.getId(), text, color);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) <= CALLOUT_RANGE * CALLOUT_RANGE) {
                PacketDistributor.sendToPlayer(player, callout);
            }
        }
    }

    static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void message(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        message(entity, message.copy().withStyle(ChatFormatting.RED));
    }

    // Draws the air impacts wherever something was hit
    static void impact(ServerLevel level, Vec3 at, int kind, float size) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, IMPACT_RANGE, new NewOrderPayloads.ImpactPayload(at, kind, size));
    }

    // Enhanced form enhances skills the user uses too
    // It was somewhat lackluster as just a physical damage/armor buff
    static float ruleDamage(LivingEntity user, double damage) {
        return (float) (NewOrderRules.hasRule(user, NewOrderRule.ENHANCE) ? damage * (1.0D + CONFIG.enhanceRuleDamagePercent) : damage);
    }

    static float meleeDamage(LivingEntity user, double percent) {
        return ruleDamage(user, user.getAttributeValue(Attributes.ATTACK_DAMAGE) * percent);
    }

    static DamageSource windSource(ManasSkillInstance instance, LivingEntity user, int mode) {
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, user, TensuraDamageTypes.WIND_ELEMENTAL, mode);
        ((TensuraDamageSource) source).tensura$setElement(Element.WIND);
        return source;
    }

    static boolean hit(LivingEntity target, DamageSource source, float damage) {
        target.invulnerableTime = 0;
        return target.hurt(source, damage);
    }

    static Aim aim(LivingEntity user, double range) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getViewVector(1.0F);
        BlockHitResult block = user.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(user, eye, end, user.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
                other -> other instanceof LivingEntity && other.isAlive() && !other.isSpectator() && other.isPickable(), eye.distanceToSqr(end));
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return new Aim(living.getBoundingBox().getCenter(), living, null);
        }
        return new Aim(end, null, block.getType() == HitResult.Type.BLOCK ? block : null);
    }

    static boolean isBoss(LivingEntity target) {
        return !MultiArms.canGrab(target) || target instanceof IArenaBoss || target instanceof GreaterSpiritEntity;
    }

    static float frame(Map<UUID, Frame> frames, UUID id, long time, float amount, int window) {
        Frame last = frames.get(id);
        if (last != null && time - last.time() < window && time >= last.time()) {
            if (amount <= last.amount()) {
                return 0.0F;
            }
            frames.put(id, new Frame(last.time(), amount));
            return amount - last.amount();
        }

        frames.put(id, new Frame(time, amount));
        return amount;
    }


    static void fling(LivingEntity target, LivingEntity cause, ManasSkillInstance instance, Vec3 velocity) {
        if (target.getType().is(TensuraEntityTags.NO_FORCED_MOVE)) {
            return;
        }

        double resistance = Math.max(0.0D, 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Changeable<Vec3> push = Changeable.of(velocity.scale(resistance));
        if (TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(target, cause, instance, push).isFalse() || push.get() == null) {
            return;
        }

        target.setDeltaMovement(push.get());
        target.hurtMarked = true;
        target.hasImpulse = true;
    }

}
