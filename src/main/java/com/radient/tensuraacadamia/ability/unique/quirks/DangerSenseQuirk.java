package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class DangerSenseQuirk extends Skill {


    public DangerSenseQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/dangersense.png");
    }

    public boolean canBeSlotted(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return false;
    }

    @Override
    public MutableComponent getSkillDescription() {
        return Component.literal("Detects threats with a sharp stinging pain in the head, scaling with the danger level. Great for dodging malice, though too many at once can be intense.");
    }

    private static final ResourceLocation DANGERSENSE = ResourceLocation.fromNamespaceAndPath("tracademia", "dangersense");

    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return true;
    }

    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, false, false, true));
        AttributeInstance melee = entity.getAttribute(TensuraAttributes.AUTO_MELEE_DODGE_CHANCE);
        if (melee != null) {
            melee.addOrReplacePermanentModifier(new AttributeModifier(DANGERSENSE, 80.0D, AttributeModifier.Operation.ADD_VALUE));
        }

        AttributeInstance projectile = entity.getAttribute(TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE);
        if (projectile != null) {
            projectile.addOrReplacePermanentModifier(new AttributeModifier(DANGERSENSE, 800.0D, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        entity.removeEffect(MobEffects.REGENERATION);
        AttributeInstance melee = entity.getAttribute(TensuraAttributes.AUTO_MELEE_DODGE_CHANCE);
        if (melee != null) {
            melee.removeModifier(DANGERSENSE);
        }

        AttributeInstance projectile = entity.getAttribute(TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE);
        if (projectile != null) {
            projectile.removeModifier(DANGERSENSE);
        }
    }


    public boolean onBeingTargeted(ManasSkillInstance instance, Changeable<LivingEntity> owner, LivingEntity attacker) {
        if (!instance.isToggled()) {
            return true;
        } else {
            Object var5 = owner.get();
            if (var5 instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer)var5;
                if (!(attacker instanceof Mob)) {
                    return true;
                } else {
                    Mob mob = (Mob)attacker;
                    if (mob.getTarget() == null || !player.is(mob.getTarget())) {
                        if (player.getRandom().nextBoolean()) {
                            instance.addMasteryPoint(player);
                        }

                        this.sendSound(player, mob);
                    }

                    return true;
                }
            } else {
                return true;
            }
        }
    }

    private void sendSound(ServerPlayer user, LivingEntity target) {
        Vec3 eyeVec = user.getEyePosition();
        Vec3 soundPos = eyeVec.add(target.getEyePosition().subtract(eyeVec).normalize().scale((double)5.0F));
        user.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BELL_BLOCK), SoundSource.HOSTILE, soundPos.x(), eyeVec.y(), soundPos.z(), 1.0F, 1.0F, user.getRandom().nextLong()));
    }


}
