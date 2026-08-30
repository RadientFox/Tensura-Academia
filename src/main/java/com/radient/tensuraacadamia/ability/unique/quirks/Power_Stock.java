package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
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
                if (entity instanceof ServerPlayer player) {
                    var data = entity.getPersistentData();
                    int storted = data.getInt("power_stored_ep");
                    boolean active = data.getBoolean("power_active");



                    double scale = instance.getTag() == null ? 0.0 : instance.getTag().getDouble("scale");
                    double percent = scale == 0.0 ? 1.0 : Math.min(scale, 1.0);
                    if (data.getBoolean("power_active") == false){
                        if (percent > 0) {

                            int epSpent = getCurrentStock((Player) entity, (float) percent);

                                player.displayClientMessage(Component.translatable("tracadamia.skill.power_stock.ouputamount", new Object[]{ epSpent}).setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_BLUE)), false);
                                data.putBoolean("power_active", true);


                        }
                    }

                }
        }

    }



    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity attacker, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        var data = attacker.getPersistentData();
        TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLE.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);
        if (attacker instanceof ServerPlayer player) {
            RandomSource rng = player.getRandom();
            int tempChance = (int) CONFIG.evolvingMight;
            if (rng.nextInt(100) < tempChance) {



                    float damage = (float) (CONFIG.evolvingMightPower * data.getInt("power_used"));
                    amount.set((Float)amount.get() + damage);
                    data.putBoolean("power_active", false);
                    return true;





            } else {


                if (data.getBoolean("power_active") == false){
                    return true;
                }
                else {

                    float damage = (float) (CONFIG.stockpileConversion * data.getInt("power_used"));
                    amount.set((Float)amount.get() + damage);
                    data.putBoolean("power_active", false);
                    return true;
                }


            }
        }
        data.putBoolean("power_active", false);
        return true;
    }



    static int totalStored = 0;

    public static int getCurrentStock(Player player, float percent){

        var data = player.getPersistentData();
        int currentEP = (int) EnergyHelper.getBaseMaxEP(player) + 1;




        int spentEP = Math.max(0, (int) (data.getInt("power_stored_ep") * percent));

        totalStored += spentEP;
        data.putInt("power_used", spentEP);

        /*

        current max ep
        spent ep
        ep left = max - spent

        on use{

        percent * ep left

        spent = percent * ep left


        }

         */



        int stored = (currentEP - totalStored);


        data.putInt("power_stored_ep", stored);



        return spentEP;
    }


    public boolean canScroll(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return instance.getMastery() >= 0.0;
    }

    public void onScroll(ManasSkillInstance instance, LivingEntity entity, double delta, int mode) {
        switch (mode) {
            case 0:
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
