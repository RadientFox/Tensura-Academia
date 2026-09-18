package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.extra.StickySteelThreadSkill;
import io.github.manasmods.tensura.entity.projectile.WebBulletProjectile;
import io.github.manasmods.tensura.registry.item.TensuraToolItems;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class Blackwhip extends Skill {
    private static final QuirkSkillsConfig.Power_Stock CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Power_Stock;
    public static final ResourceLocation BLACKWHIP = ResourceLocation.fromNamespaceAndPath("tracadamia", "power_stock");

    public Blackwhip() {
        super(SkillType.UNIQUE);
    }


    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {

        if (reverse) {
            return mode == 0 ? 1 : mode - 1;
        } else {
            return mode == 1 ? 0 : mode + 1;
        }
    }

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/blackwhip.png");
    }

    public String getModeId(ManasSkillInstance instance, int mode) {
        String var10000;
        switch (mode) {
            case 0 -> var10000 = "blackwhip.output";
            case 1 -> var10000 = "blackwhip.smash";
            default -> var10000 = super.getModeId(instance, mode);
        }

        return var10000;
    }

    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            Level level = entity.level();
            CompoundTag tag = instance.getOrCreateTag();

            switch (mode){
                case 0 ->{
/*

                    Entity oldBullet = entity.level().getEntity(tag.getInt("BulletID"));
                    if (oldBullet instanceof BlackwhipProjectile) {
                        oldBullet.discard();
                    }

                    if (entity.isShiftKeyDown()) {
                        return;
                    }

                    BlackwhipProjectile bullet = new BlackwhipProjectile(level, 1, 1, 1);
                    bullet.setSlinger(true);
                    Vec3 vector = entity.getViewVector(1.0F);
                    bullet.shoot(vector.x(), vector.y(), vector.z(), 2.0F, 0.0F);
                    entity.swing(entity.getUsedItemHand());
                    level.addFreshEntity(bullet);
                    tag.putInt("BulletID", bullet.getId());
                    entity.swing(InteractionHand.MAIN_HAND, true);
                    level.playSound((Player)null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent) TensuraSoundEvents.STICKY_STEEL_THREAD.get(), SoundSource.PLAYERS, 1.0F, 1.0F);



 */

                }


            }



        }


    }



}
