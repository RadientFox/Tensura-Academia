package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.extra.HakiSkill;
import io.github.manasmods.tensura.ability.skill.unique.FighterSkill;
import io.github.manasmods.tensura.config.ability.skill.ExtraSkillConfig;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;

public class Power_Stock extends Skill {
        private static final QuirkSkillsConfig.Power_Stock CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Power_Stock;
        public static final ResourceLocation POWER_STOCK = ResourceLocation.fromNamespaceAndPath("tracadamia", "power_stock");

    public Power_Stock() {
            super(Skill.SkillType.UNIQUE);
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
            return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/power_stock.png");
        }

        public String getModeId(ManasSkillInstance instance, int mode) {
            String var10000;
            switch (mode) {
                case 0 -> var10000 = "power_stock.output";
                case 1 -> var10000 = "power_stock.smash";
                default -> var10000 = super.getModeId(instance, mode);
            }

            return var10000;
        }


    public void onLearnSkill(
            ManasSkillInstance instance,
            LivingEntity entity,
            SkillEvents.UnlockSkillEvent event
    ) {
        if (!(entity instanceof Player player)) return;

        var data = player.getPersistentData();
        int currentEP = (int) EnergyHelper.getBaseMaxEP(player);

        data.putBoolean("power_has_skill", true);
        data.putInt("power_last_ep", currentEP);
        data.putInt("power_stored_ep", 0);
        data.putBoolean("power_mode_active", false);
        data.putInt("power_mode_spent", 0);
    }


    private static final DecimalFormat decimalFormat = new DecimalFormat("#.#");
    public static void changeEPUsed(ManasSkillInstance instance, LivingEntity entity, double delta) {
        CompoundTag tag = instance.getOrCreateTag();
        double oldScale = tag.getDouble("scale");
        double newScale = getNewScale(delta, oldScale);
        if (tag.getDouble("scale") != newScale) {
            tag.putDouble("scale", newScale);
            if (entity instanceof Player) {
                Player player = (Player)entity;
                player.displayClientMessage(Component.translatable("tensura.skill.power_scale", new Object[]{decimalFormat.format(newScale * 100.0) + "%"}).setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA)), true);
            }

            instance.markDirty();
        }


    }



    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        switch (mode) {
            case 1:
                var data = entity.getPersistentData();
                int before = data.getInt("power_stored_ep");


                if (before <= 0) {
                    entity.sendSystemMessage(
                            Component.literal("No stored EP.")
                    );
                    return;
                }

                if (!data.getBoolean("power_mode_active")) {

                    double scale = instance.getTag() == null ? 0.0 : instance.getTag().getDouble("scale");
                    double percent = scale == 0.0 ? 1.0 : Math.min(scale, 1.0);
                    int spent = consumeStoredEPPercentAndGetSpent((Player) entity, (float) percent);
                    int after = data.getInt("power_stored_ep");

                    data.putInt("power_mode_spent", spent);

                    entity.sendSystemMessage(
                            Component.literal("Stored EP: " + before + " → " + after)
                    );

                    entity.sendSystemMessage(
                            Component.literal("Spent: " + spent)
                    );
                    data.putBoolean("power_mode_active", true);

                }
            }
        }



    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity attacker, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        var data = attacker.getPersistentData();
        TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLE.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);
        if (attacker instanceof ServerPlayer player) {
            RandomSource rng = player.getRandom();
            int tempChance = 15;
            if (rng.nextInt(100) < tempChance) {

                int total = (int) (data.getInt("power_stored_ep") * 0.25);
                float bonusDamagecheep = total * 0.05f;

                amount.set(amount.get() + bonusDamagecheep);
            //    TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLES.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);

                if (data.getBoolean("power_mode_active")) {

                    int spent = data.getInt("power_mode_spent");

                    float bonusDamage = spent * 0.05f;
                    player.sendSystemMessage(
                            Component.literal("Spent: " + spent)
                    );
              //      TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLES.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);

                    amount.set(amount.get() + bonusDamage);
                    data.putInt("power_mode_spent", 0);
                    data.putBoolean("power_mode_active", false);
                }

            } else {


                if (data.getBoolean("power_mode_active")) {

                    int spent = data.getInt("power_mode_spent");
                    if (!(spent <= 0)) {

                        float bonusDamage = spent * 0.05f;
                        player.sendSystemMessage(
                                Component.literal("Spent: " + spent)
                        );
                        //    TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLES.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);
                        amount.set(amount.get() + bonusDamage);
                        data.putInt("power_mode_spent", 0);
                        data.putBoolean("power_mode_active", false);
                    }
                }
            }
        }
        return true;
    }



    public static int consumeStoredEPPercentAndGetSpent(Player player, float percent) {
        if (percent <= 0f) return 0;
        if (percent > 1f) percent = 1f;

        var data = player.getPersistentData();

        int stored = data.getInt("power_stored_ep");
        if (stored <= 0) return 0;

        int cost = Math.round(stored * percent);
        if (cost < 1) cost = 1;

        int newValue = stored - cost;
        if (newValue < 0) newValue = 0;

        data.putInt("power_stored_ep", newValue);
        return cost;
    }


    public boolean canScroll(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return instance.getMastery() >= 0.0;
    }

    public void onScroll(ManasSkillInstance instance, LivingEntity entity, double delta, int mode) {
        switch (mode) {
            case 1:
            changeEPUsed(instance, entity, delta);
        }
    }

    private static double getNewScale(double delta, double oldScale) {
        double newScale;
        if (oldScale == 0.1) {
            if (delta >= 0.0) {
                newScale = 0.2;
            } else {
                newScale = 0.05;
            }
        } else if (oldScale == 0.05) {
            if (delta >= 0.0) {
                newScale = 0.1;
            } else {
                newScale = 0.01;
            }
        } else if (oldScale == 0.01) {
            if (delta >= 0.0) {
                newScale = 0.05;
            } else {
                newScale = 0.001;
            }
        } else if (oldScale <= 0.001) {
            if (delta >= 0.0) {
                newScale = 0.01;
            } else {
                newScale = 1.0;
            }
        } else {
            newScale = oldScale + delta * 0.1;
            if (newScale > 1.0) {
                newScale = 0.001;
            }
        }

        return newScale;
    }


}
