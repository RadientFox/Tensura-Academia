package com.radient.tensuraacadamia.ability.ultimate.ofausers;

import com.github.hvnbael.trnightmare.compat.TextAnimatorCompat;
import com.github.hvnbael.trnightmare.util.SkillIconFrames;
import com.radient.tensuraacadamia.config.skills.OFAConfig;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class OFA1st extends Skill {
        private static final OFAConfig.OFA1st CONFIG = ConfigRegistry.getConfig(OFAConfig.class).OFA1st;
        public static final ResourceLocation OFA1ST = ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_1");

    public OFA1st() {
            super(SkillType.ULTIMATE);
        }

        boolean colorName = false;

    public @Nullable MutableComponent getColoredName() {
        MutableComponent name = this.getName();

        if (name == null) {
            return null;
        } else if (colorName == true){
            return TextAnimatorCompat.useOnClient() ? TextAnimatorCompat.skillAnimatedDisplayName(name, ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_2")) : name.withStyle(ChatFormatting.WHITE);

        } else {
            return TextAnimatorCompat.useOnClient() ? TextAnimatorCompat.skillAnimatedDisplayName(name, ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_1")) : name.withStyle(ChatFormatting.WHITE);
        }
    }

        public int getModes(ManasSkillInstance instance) {
            return 3;
        }

        public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {

            if (reverse) {
                return mode == 0 ? 3 : mode - 1;
            } else {
                return mode == 3 ? 0 : mode + 1;
            }
        }


    private static final ResourceLocation[] ICON_FRAMES;
    private final int[] iconTick = new int[]{0};
    public ResourceLocation getSkillIcon() {
        if (colorName) {
            return SkillIconFrames.pickAnimated(ICON_FRAMES, this.iconTick);
        }else {
            return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/ultimate/one_for_all_1.png");
        }
    }


        public String getModeId(ManasSkillInstance instance, int mode) {
            String var10000;
            switch (mode) {
                case 0 -> var10000 = "one_for_all_1.output";
                case 1 -> var10000 = "one_for_all_1.smash";
                case 2 -> var10000 = "one_for_all_1.cowling";
                case 3 -> var10000 = "one_for_all_1.bestow";
                default -> var10000 = super.getModeId(instance, mode);
            }

            return var10000;
        }



    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity attacker, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        var data = attacker.getPersistentData();
        TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLE.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);
        if (attacker instanceof ServerPlayer player) {


                if (data.getBoolean("detroitActive") == false){
                    return true;
                }
                else {

                    double damage = getCurrnetDamage((Player) attacker);
                    player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.detroit.active" + damage).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), false);

                    amount.set((float) ((double)amount.get() + damage));
                    data.putBoolean("detroitActive", false);
                    return true;
                }



        }
        data.putBoolean("detroitActive", false);
        return true;
    }



    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        var data = entity.getPersistentData();
        Player player = (Player) entity;
        int subMode = data.getInt("modeV");
        int smashMode = data.getInt("modeSmash");
        int percentage = data.getInt("outputPercent");

        switch (mode) {

            case  0->{
                if (entity instanceof Player) {
                    int nextMode = subMode + 1;
                    if (nextMode > 14) {
                        nextMode = 1;
                    }

                    player.getPersistentData().putInt("modeV", nextMode);
                    switch (nextMode) {
                        case 1-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.1").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 1);
                        data.putInt("outputPercent", 1);
                        }
                        case 2-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.2").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 2);
                            data.putInt("outputPercent", 5);
                        }
                        case 3-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.3").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 3);
                            data.putInt("outputPercent", 8);


                        }
                        case 4-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.4").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 4);
                            data.putInt("outputPercent", 12);


                        }
                        case 5-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.5").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 5);
                            data.putInt("outputPercent", 16);

                        }
                        case 6-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.6").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 6);
                            data.putInt("outputPercent", 20);
                        }
                        case 7-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.7").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 7);
                            data.putInt("outputPercent", 30);
                        }
                        case 8-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.8").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 8);
                            data.putInt("outputPercent", 45);


                        }
                        case 9-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.9").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 9);
                            data.putInt("outputPercent", 55);
                        }
                        case 10-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.10").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 10);
                            data.putInt("outputPercent", 65);
                        }
                        case 11-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.11").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 11);
                            data.putInt("outputPercent", 75);
                        }
                        case 12-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.12").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 12);
                            data.putInt("outputPercent", 80);
                        }
                        case 13-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.13").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 13);
                            data.putInt("outputPercent", 90);
                        }
                        case 14-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.14").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 14);
                            data.putInt("outputPercent", 100);
                        }
                    }
                }
            }case 1->{

                if (player.isCrouching()){

                    if (entity instanceof Player) {
                        int nextMode = smashMode + 1;
                        if (nextMode > 5) {
                            nextMode = 1;
                        }

                        player.getPersistentData().putInt("modeSmash", nextMode);
                        switch (nextMode) {
                            case 1 -> {
                                player.displayClientMessage(Component.translatable("tensuraacadamia.skill.mode.power.detroit").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                player.getPersistentData().putInt("modeSmash", 1);

                            }
                            case 2 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.carolina").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                player.getPersistentData().putInt("modeSmash", 2);
                            }
                            case 3 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.delaware").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                player.getPersistentData().putInt("modeSmash", 3);

                            }
                            case 4 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.texas").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                player.getPersistentData().putInt("modeSmash", 4);
                            }
                            case 5 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.oklahoma").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                player.getPersistentData().putInt("modeSmash", 5);
                            }
                        }
                    }

                }else {
                    switch (smashMode){

                        case 1 -> detroitSmash(instance, entity);

                    }
                }





            }case 2->{
                player.displayClientMessage(Component.literal("Output: " + data.getInt("outputPercent")), false);
                if (data.getInt("outputPercent") == 100){
                    colorName = true;
                }else {
                    colorName = false;
                }
            }









        }





    }

    private void detroitSmash(ManasSkillInstance instance, LivingEntity entity){
        Player player = (Player) entity;

        var data = player.getPersistentData();
        boolean SmashActive = data.getBoolean("detroitActive");


        if (SmashActive){
            if (entity instanceof Player){
                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.detroit.deactive").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);

            }
            data.putBoolean("detroitActive",false);

        }else {
            if (entity instanceof Player){
                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.detroit.active").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);

            }
            data.putBoolean("detroitActive",true);

        }


    }


    public static double getCurrnetDamage(Player player){
        double fullDamage = (int) CONFIG.fullDamage;
        var data = player.getPersistentData();
        double percentUsed = (int) (data.getInt("outputPercent") * 0.1);
        boolean fullCowlingOn = data.getBoolean("fullCowling");
        double UsedDamage = 0;
        double UsedDamagePre = 0;

        if (fullCowlingOn){
            UsedDamagePre = (int) (fullDamage * CONFIG.fullcowlsmash);
            UsedDamage = UsedDamagePre + (fullDamage* percentUsed);

        }else {

            UsedDamage = (fullDamage * percentUsed);

        }

        return UsedDamage;
    }

        static {
        ICON_FRAMES = OFA1st.build("ofa", 32);
    }

    public static ResourceLocation[] build(String prefix, int count) {
        ResourceLocation[] frames = new ResourceLocation[count];

        for(int i = 0; i < count; ++i) {
            frames[i] = ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skills/" + prefix + (i + 1) + ".png");
        }

        return frames;
    }
}
