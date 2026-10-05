package com.radient.tensuraacadamia.ability.ultimate.ofausers;

import com.github.hvnbael.trnightmare.compat.TextAnimatorCompat;
import com.github.hvnbael.trnightmare.util.SkillIconFrames;
import com.radient.tensuraacadamia.config.skills.OFAConfig;
import com.radient.tensuraacadamia.entity.GreyTornadoBlade;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.MHASounds;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.attribute.api.ManasCoreAttributes;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.data.TensuraBlockTags;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.enchantment.TensuraEnchantmentHelper;
import io.github.manasmods.tensura.entity.projectile.magic.WindSphereProjectile;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class OFA8th extends Skill {
        private static final OFAConfig.OFA8th CONFIG = ConfigRegistry.getConfig(OFAConfig.class).OFA8th;
        public static final ResourceLocation OFA8th = ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_8");

    public OFA8th() {
            super(SkillType.ULTIMATE);
        }

        boolean colorName = false;

    public @Nullable MutableComponent getColoredName() {
        MutableComponent name = this.getName();

        if (name == null) {
            return null;
        } else if (colorName == true){
            return TextAnimatorCompat.useOnClient() ? TextAnimatorCompat.skillAnimatedDisplayName(name, ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_8")) : name.withStyle(ChatFormatting.WHITE);

        } else {
            return TextAnimatorCompat.useOnClient() ? TextAnimatorCompat.skillAnimatedDisplayName(name, ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_8")) : name.withStyle(ChatFormatting.WHITE);
        }
    }

    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    private static final int MAX_COWLING_TIME_DRAWBACK = 20 * 15 ;

    private static final ResourceLocation MOVEMENT_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "full_cowling_speed");
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "full_cowling_damage");
    private static final ResourceLocation ARMOR_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "full_cowling_armor");
    private static final ResourceLocation RECOIL_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "full_cowling_recoil");
    private static final int RECOIL_INTERVAL = 20 * 3;
    private static final int RECOIL_RECOVERY = 20 * 60 * 20;
    private static final String NEXT_RECOIL_TAG = "cowlingNextRecoil";
    private static final String RECOIL_EXPIRES_TAG = "tracadamia_cowling_recoil_expires";

    private static final String ACTIVE_TAG = "tracadamia_full_cowling_active";
    private static final String TIME_TAG = "tracadamia_full_cowling_time";


    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return true;
    }


    public int getModes(ManasSkillInstance instance) {
        return 4;
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
            return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/ultimate/one_for_all_8.png");
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


    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        Level var6 = entity.level();

        CompoundTag tag = instance.getOrCreateTag();
        double percentUsed =  (tag.getDouble("outputPercent"));
        boolean fullCowlingOn = tag.getBoolean("fullCowling");

        if (var6 instanceof ServerLevel serverLevel) {
            if (entity.isAlive() && entity.tickCount % 10 == 0
                    && (tag.getBoolean("fullCowling")
                    || instance.getOrCreateTag().getBoolean("cowlingParticles"))) {
                for (int i = 0; i < 5; i++) {
                    serverLevel.sendParticles(MHAParticles.OFA_COWLING_8.get(),
                            entity.getRandomX(1.0D), entity.getRandomY(), entity.getRandomZ(1.0D), 0,
                            entity.getRandom().nextGaussian() * 0.02D,
                            entity.getRandom().nextGaussian() * 0.02D,
                            entity.getRandom().nextGaussian() * 0.02D, 1.0D);
                }
            }

            float radius = 30.0F;
            List<LivingEntity> list = entity.level().getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius), (living) -> {
                return !living.is(entity) && living.isAlive() && living.isAlliedTo(entity);
            });
            Iterator var10 = list.iterator();
            while (var10.hasNext()) {
                Level var2 = entity.level();

                LivingEntity subordinate = (LivingEntity) var10.next();
                if (var2 instanceof ServerLevel level) {
                    int quantity = list.size();
                    subordinate.addEffect(new MobEffectInstance(MHAEffects.OTHERSINSPIRE, 100, 0, false, false));
                }
            }


            if (fullCowlingOn){


                double x = entity.getX();
                double y = entity.getY() + entity.getBbHeight() * 0.5D;
                double z = entity.getZ();



                int time = tag.getInt("activatedTimes");
                tag.putInt("activatedTimes", time + 1);
                if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
                    TensuraParticleHelper.spawnServerParticles(entity.level(), MHAParticles.OFA_COWLING_8.get(), x, y, z, 15, 0.1D, 0.1D, 0.1D, 0.1D, true);
                }

                tickRecoil(tag, entity, percentUsed);



                addDamageModifier(entity, (CONFIG.fullcowldamage * percentUsed));
                addArmorModifier(entity, (CONFIG.fullcowlarmor * percentUsed));
                addspeedModifier(entity, (CONFIG.fullcowlSpeed * percentUsed));

                if (percentUsed == 1){
                    colorName = true;
                }else if ((percentUsed >= 0.45) && SkillUtils.hasSkill(entity, QuirkSkills.GEARSHIFT.get())){

                    Optional<ManasSkillInstance> opt = SkillAPI.getSkillsFrom(entity).getSkill((ManasSkill) QuirkSkills.GEARSHIFT.get());
                    ManasSkillInstance inst = (ManasSkillInstance) opt.get();
                    if (inst.getTag().getBoolean("tracadamia_gearshift_overdrive")) {
                        colorName = true;
                    }

                }
                else {
                    colorName = false;
                }

            }else {



                colorName = false;
                AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
                AttributeInstance attribute2 = entity.getAttribute(Attributes.ARMOR);
                AttributeInstance attribute3 = entity.getAttribute(Attributes.MOVEMENT_SPEED);

                if (attribute != null) {
                    attribute.removeModifier(ATTACK_DAMAGE_MODIFIER);
                }
                if (attribute2 != null) {
                    attribute2.removeModifier(ARMOR_MODIFIER);
                }
                if (attribute3 != null) {
                    attribute3.removeModifier(MOVEMENT_MODIFIER);
                }
                tag.putInt("activatedTimes", 1);
                tag.remove(NEXT_RECOIL_TAG);
                startRecoilRecovery(entity);

            }
        }
    }

    private static void addDamageModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(ATTACK_DAMAGE_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void tickRecoil(CompoundTag tag, LivingEntity entity, double output) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null || !entity.isAlive()) return;
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(RECOIL_EXPIRES_TAG)) attribute.removeModifier(RECOIL_MODIFIER);
        AttributeModifier previous = attribute.getModifier(RECOIL_MODIFIER);
        if (previous != null) data.putLong(RECOIL_EXPIRES_TAG, Long.MAX_VALUE);
        long now = entity.level().getGameTime();
        if (!tag.contains(NEXT_RECOIL_TAG)) tag.putLong(NEXT_RECOIL_TAG, now + RECOIL_INTERVAL);
        if (now < tag.getLong(NEXT_RECOIL_TAG)) return;
        tag.putLong(NEXT_RECOIL_TAG, now + RECOIL_INTERVAL);
        output = Mth.clamp(output, 0, 1);
        if (output <= 0 || EnergyHelper.getMaxEP(entity) >= output * CONFIG.fullOutputEP) return;
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power")
                    .withStyle(ChatFormatting.WHITE), true);
        }
        double reduction = previous == null ? 0 : -previous.amount();
        double normalMaxHealth = entity.getMaxHealth() / (1 - reduction);
        entity.hurt(entity.damageSources().source(TensuraDamageTypes.BLOOD_DRAIN),
                (float) (normalMaxHealth * (output < 1 ? output : 0.9)));
        reduction = Math.max(reduction, output < 1 ? output * output : 0.9);
        attribute.removeModifier(RECOIL_MODIFIER);
        attribute.addPermanentModifier(new AttributeModifier(RECOIL_MODIFIER, -reduction,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
        data.putLong(RECOIL_EXPIRES_TAG, Long.MAX_VALUE);
    }

    private static void startRecoilRecovery(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null && !data.contains(RECOIL_EXPIRES_TAG)) {
            attribute.removeModifier(RECOIL_MODIFIER);
        }
        if (data.getLong(RECOIL_EXPIRES_TAG) == Long.MAX_VALUE) {
            data.putLong(RECOIL_EXPIRES_TAG, entity.level().getGameTime() + RECOIL_RECOVERY);
        }
    }

    @SubscribeEvent
    public static void expireRecoil(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(RECOIL_EXPIRES_TAG)
                || entity.level().getGameTime() < data.getLong(RECOIL_EXPIRES_TAG)) return;
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) attribute.removeModifier(RECOIL_MODIFIER);
        data.remove(RECOIL_EXPIRES_TAG);
    }

    private static void addspeedModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(MOVEMENT_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }


    private static void addArmorModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attributes.ARMOR);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }


    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        startRecoilRecovery(entity);
        AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance attribute2 = entity.getAttribute(Attributes.ARMOR);
        AttributeInstance attribute3 = entity.getAttribute(Attributes.MOVEMENT_SPEED);


        if (attribute != null) {
            attribute.removeModifier(ATTACK_DAMAGE_MODIFIER);
        }
        if (attribute2 != null) {
            attribute2.removeModifier(ARMOR_MODIFIER);
        }
        if (attribute3 != null) {
            attribute3.removeModifier(MOVEMENT_MODIFIER);
        }

    }

    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity attacker, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (attacker == target) return true;
        var data = attacker.getPersistentData();
        CompoundTag tag = instance.getOrCreateTag();
        if (attacker instanceof ServerPlayer player) {


            if (tag.getBoolean("texasActive")){

                Vec3 vec3;
                double damage = getCurrnetDamage(instance, (Player) attacker);

                amount.set((float) ((double)amount.get() + damage));
                tag.putBoolean("texasActive", false);

                label61: {
                    if (!target.getType().is(TensuraEntityTags.NO_FORCED_MOVE)) {
                        if (!(target instanceof Player)) {
                            break label61;
                        }

                        if (!player.getAbilities().invulnerable) {
                            break label61;
                        }
                    }


                    attacker.level().playSound((Player)null, attacker.getX(), attacker.getY(), attacker.getZ(), (SoundEvent)TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    return true;
                }

                double scale = 10;
                    /*
                    if (SkillUtils.isSkillToggled(entity, (ManasSkill) ExtraSkills.GRAVITY_DOMINATION.get())) {
                        scale += CONFIG.entityThrowDomination;
                    } else if (SkillUtils.isSkillToggled(entity, (ManasSkill)ExtraSkills.GRAVITY_MANIPULATION.get())) {
                        scale += CONFIG.entityThrowManipulation;
                    }


                     */
                TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLE.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);

                attacker.swing(InteractionHand.MAIN_HAND, true);
                TensuraParticleHelper.addServerParticlesAroundSelf(target, ParticleTypes.CLOUD, 1.0);
                attacker.level().playSound((Player)null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);
                vec3 = (new Vec3(target.getX() - attacker.getX(), target.getY() - attacker.getY() + 0.5, target.getZ() - attacker.getZ())).scale(1.0 / (double)target.distanceTo(attacker));
                Changeable<Vec3> changeable = Changeable.of(vec3.normalize().scale(scale));
                if (!((TensuraEntityEvents.ForceMovementEvent)TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker()).move(target, attacker, instance, changeable).isFalse()) {
                    target.setDeltaMovement((Vec3)changeable.get());
                    target.hasImpulse = true;
                    target.hurtMarked = true;
                }





                return true;
            }else if (tag.getBoolean("detroitActive")){


                Vec3 vec3;
                double damage = getCurrnetDamage(instance, (Player) attacker);
                TensuraParticleHelper.spawnServerParticles(target.level(), (ParticleOptions) MHAParticles.SMASH_PARTICLE.get(), target.getX(), target.getY(), target.getZ(), 1, 0.08, 0.08, 0.08, 0.2, true);

                amount.set((float) ((double)amount.get() + damage));
                tag.putBoolean("detroitActive", false);
                return true;
            }





        }
        tag.putBoolean("detroitActive", false);
        tag.putBoolean("texasActive", false);
        return true;
    }



    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        var data = entity.getPersistentData();
        CompoundTag tag = instance.getOrCreateTag();
        int subMode = data.getInt("modeV");
        int smashMode = tag.getInt("modeSmash");
        double percentage = tag.getDouble("outputPercent");

        switch (mode) {

            case  0->{
                if (entity instanceof Player) {
                    int nextMode = subMode + 1;
                    if (nextMode > 14) {
                        nextMode = 1;
                    }
                    Player player = (Player) entity;

                    player.getPersistentData().putInt("modeV", nextMode);
                    switch (nextMode) {
                        case 1-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.1").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 1);
                            tag.putDouble("outputPercent", 0.01);
                        }
                        case 2-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.2").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 2);
                            tag.putDouble("outputPercent", 0.05);
                        }
                        case 3-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.3").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 3);
                            tag.putDouble("outputPercent", 0.08);
                        }
                        case 4-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.4").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 4);
                            tag.putDouble("outputPercent", 0.12);
                        }
                        case 5-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.5").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 5);
                            tag.putDouble("outputPercent", 0.16);
                        }
                        case 6-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.6").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 6);
                            tag.putDouble("outputPercent", 0.20);
                        }
                        case 7-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.7").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 7);
                            tag.putDouble("outputPercent", 0.30);
                        }
                        case 8-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.8").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 8);
                            tag.putDouble("outputPercent", 0.45);
                        }
                        case 9-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.9").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 9);
                            tag.putDouble("outputPercent", 0.55);
                        }
                        case 10-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.10").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 10);
                            tag.putDouble("outputPercent", 0.65);
                        }
                        case 11-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.11").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 11);
                            tag.putDouble("outputPercent", 0.75);
                        }
                        case 12-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.12").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 12);
                            tag.putDouble("outputPercent", 0.80);
                        }
                        case 13-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.13").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 13);
                            tag.putDouble("outputPercent", 0.90);
                        }
                        case 14-> {
                            player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.14").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                            player.getPersistentData().putInt("modeV", 14);
                            tag.putDouble("outputPercent", 1);
                        }
                    }
                }
            }case 1->{

                Player player = (Player) entity;

                if (player.isCrouching()){

                    if (entity instanceof Player) {
                        int nextMode = smashMode + 1;
                        if (nextMode > 5) {
                            nextMode = 1;
                        }

                        tag.putInt("modeSmash", nextMode);
                        switch (nextMode) {
                            case 1 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.detroit").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                tag.putInt("modeSmash", 1);

                            }
                            case 2 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.carolina").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                tag.putInt("modeSmash", 2);
                            }
                            case 3 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.delaware").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                tag.putInt("modeSmash", 3);

                            }
                            case 4 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.texas").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                tag.putInt("modeSmash", 4);
                            }
                            case 5 -> {
                                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.oklahoma").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                                tag.putInt("modeSmash", 5);
                            }
                        }
                    }

                }else {
                    switch (smashMode){

                        case 1 -> detroitSmash(instance, entity);

                        case 2 -> carolinaSmash(instance, entity);

                        case 3 -> delawareSmash(instance, entity);

                        case 4 -> texaSmash(instance, entity);

                        case 5 -> oklahomaSmash(instance, entity);


                    }
                }





            }case 2->{
                fullCowlActivate(instance, entity);

                if (entity instanceof Player player) {
                    player.displayClientMessage(Component.literal("Output: " + tag.getInt("outputPercent")), false);
                }
            }





            case 3->{

                LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, 5.0, false);


                if (entity instanceof Player){
                    if (target instanceof Player){

                        if ((!SkillUtils.hasSkill(target, (ManasSkill) QuirkSkills.OFA_1ST.get())) && (!SkillUtils.hasSkill(target, (ManasSkill) QuirkSkills.OFA_2ND.get()))) {
                            if (!(instance.getMastery() < (double) 0.0F) && !instance.isTemporarySkill()) {
                                TensuraSkillInstance eye = new TensuraSkillInstance(QuirkSkills.OFA_9TH.get());
                               // TensuraSkillInstance eye3 = new TensuraSkillInstance(QuirkSkills.OFA1st_Embers.get());
                                eye.getOrCreateTag().putBoolean("NoMagiculeCost", true);
                              //  eye3.getOrCreateTag().putBoolean("NoMagiculeCost", true);
                                SkillHelper.learnSkill(target, eye);
                              //  SkillHelper.learnSkill(entity, eye3);
                                SkillAPI.getSkillsFrom(entity).forgetSkill(QuirkSkills.OFA_8TH.get());
                                if (entity instanceof ServerPlayer player) {
                                    player.displayClientMessage(Component.translatable("tracadamia.skill.ofa.pass").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)), false);
                                }
                                if (target instanceof ServerPlayer player) {
                                    player.displayClientMessage(Component.translatable("tracadamia.skill.ofa.passed").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)), false);
                                }
                            }
                        }

                    }

                }




            }









        }





    }

    private void detroitSmash(ManasSkillInstance instance, LivingEntity entity){
        Player player = (Player) entity;

        CompoundTag tag = instance.getOrCreateTag();
        var data = player.getPersistentData();
        boolean SmashActive = tag.getBoolean("detroitActive");

        if (SmashActive){
            if (entity instanceof Player){
                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.detroit.deactive").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
            }
            tag.putBoolean("detroitActive",false);
        }else {
            player.level().playSound((Player)null, player.getX(), player.getY(), player.getZ(), (SoundEvent) MHASounds.CHARGING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (entity instanceof Player){
                player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.detroit.active").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
            }
            tag.putBoolean("detroitActive",true);
        }
    }

    private void texaSmash(ManasSkillInstance instance, LivingEntity entity){
        Player player = (Player) entity;
        CompoundTag tag = instance.getOrCreateTag();
        boolean SmashActive = tag.getBoolean("texasActive");


        double percentUsed =  (tag.getDouble("outputPercent"));
        if (percentUsed > 0.2) {
            if (SmashActive) {
                if (entity instanceof Player) {
                    player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.texas.deactive").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                }
                tag.putBoolean("texasActive", false);
            } else {
                player.level().playSound((Player) null, player.getX(), player.getY(), player.getZ(), (SoundEvent) MHASounds.CHARGING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

                if (entity instanceof Player) {
                    player.displayClientMessage(Component.translatable("tracadamia.skill.mode.power.texas.active").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), true);
                }
                tag.putBoolean("texasActive", true);
            }
        }
    }

    private void carolinaSmash(ManasSkillInstance instance, LivingEntity entity){
        Player player = (Player) entity;


        if (!EnergyHelper.isOutOfEnergy(entity, instance, 2)) {
            instance.addMasteryPoint(entity);
            ServerLevel level = (ServerLevel)entity.level();
            double range = CONFIG.carolinaDistance;
            BlockHitResult result = ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, ClipContext.Fluid.NONE, range);
            BlockPos resultPos = result.getBlockPos().relative(result.getDirection());
            Vec3 vec3 = ObjectSelectionHelper.getFloorPos(resultPos);
            if (!level.getBlockState(resultPos).canBeReplaced()) {
                vec3 = ObjectSelectionHelper.getFloorPos(resultPos.above());
            }

            if (level.getBlockState(resultPos).is(TensuraBlockTags.SKILL_NOT_TELEPORTABLE)) {
                level.playSound((Player)null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent) TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            } else if (!entity.level().getWorldBorder().isWithinBounds(ObjectSelectionHelper.getBlockPos(vec3))) {
                entity.sendSystemMessage(Component.translatable("tensura.skill.teleport.out_border").withStyle(ChatFormatting.RED));
            } else {
                Vec3 source = entity.position().add(0.0, (double)(entity.getBbHeight() / 2.0F), 0.0);
                Vec3 offSetToTarget = vec3.subtract(source);

                for(int particleIndex = 1; particleIndex < Mth.floor(offSetToTarget.length()); ++particleIndex) {
                    Vec3 particlePos = source.add(offSetToTarget.normalize().scale((double)particleIndex));
                    level.sendParticles(ParticleTypes.CLOUD, particlePos.x, particlePos.y, particlePos.z, 1, 0.0, 0.0, 0.0, 0.0);
                    TensuraParticleHelper.addServerParticlesAroundPos(entity.getRandom(), level, particlePos, ParticleTypes.EXPLOSION, 3.0);
                    TensuraParticleHelper.addServerParticlesAroundPos(entity.getRandom(), level, particlePos, ParticleTypes.SWEEP_ATTACK, 2.0);
                    AABB aabb = (new AABB(ObjectSelectionHelper.getBlockPos(particlePos))).inflate(Math.max(entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 2.0));
                    List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, aabb, (targetx) -> {
                        return !targetx.is(entity) && !targetx.isAlliedTo(entity);
                    });
                    if (!list.isEmpty()) {
                        float bonus = (float) getCurrnetDamage(instance, player);
                        float amount = (float)(entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * entity.getAttributeValue(ManasCoreAttributes.CRITICAL_DAMAGE_MULTIPLIER));
                        Iterator var19 = list.iterator();

                        while(var19.hasNext()) {
                            LivingEntity target = (LivingEntity)var19.next();
                            if (target.invulnerableTime < 40) {
                                DamageSource damageSource = this.createSource(instance, entity, DamageTypes.MOB_ATTACK, 2);
                                if (target.hurt(damageSource, amount + bonus)) {
                                    ItemStack stack = entity.getMainHandItem();
                                    stack.getItem().hurtEnemy(stack, target, entity);
                                    EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, damageSource, stack);
                                    TensuraEnchantmentHelper.doAdditionalAfterDamage(level, target, entity, damageSource, stack, amount + bonus);
                                    entity.level().playSound((Player)null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE, entity.getSoundSource(), 1.0F, 1.0F);
                                    if (level instanceof ServerLevel) {
                                        ServerLevel serverLevel = level;
                                        serverLevel.getChunkSource().broadcastAndSend(entity, new ClientboundAnimatePacket(entity, 4));
                                    }
                                }

                                TensuraEnchantmentHelper.doAdditionalAfterAttack(level, target, entity, damageSource, entity.getMainHandItem(), amount + bonus);
                                target.invulnerableTime = 40;
                            }
                        }
                    }
                }

                entity.resetFallDistance();
                entity.unRide();
                entity.teleportTo(vec3.x(), vec3.y(), vec3.z());
                entity.swing(InteractionHand.MAIN_HAND, true);
                level.playSound((Player)null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent)TensuraSoundEvents.INSTANT_MOVE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

    }


    private void delawareSmash(ManasSkillInstance instance, LivingEntity entity){
        CompoundTag tag = instance.getOrCreateTag();
        var data = entity.getPersistentData();
        double percentUsed =  (tag.getDouble("outputPercent"));
        if (percentUsed > 0.2) {
            Player player = (Player) entity;
            if (!EnergyHelper.isOutOfEnergy(entity, instance, 2)) {
                instance.addMasteryPoint(entity);
                entity.swing(InteractionHand.MAIN_HAND, true);
                WindSphereProjectile windSphere = new WindSphereProjectile(entity.level(), entity);
                windSphere.setSpeed(2.0F);
                windSphere.setDamage((float) getCurrnetDamage(instance, player));
                windSphere.setNoGravity(true);
                windSphere.setKnockForce(3.0F);
                windSphere.setBurnTicks(-1);
                windSphere.setSkill(entity, instance, this, 2);
                windSphere.setPosAndShoot(entity);
                entity.level().addFreshEntity(windSphere);
                entity.level().playSound((Player) null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent) TensuraSoundEvents.CAST_WIND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    private void oklahomaSmash(ManasSkillInstance instance, LivingEntity entity){
        CompoundTag tag = instance.getOrCreateTag();
        double percentUsed =  (tag.getDouble("outputPercent"));
        if (percentUsed > 0.3) {
            Player player = (Player) entity;
            if (!EnergyHelper.isOutOfEnergy(entity, instance, 2)) {

                instance.addMasteryPoint(entity);
                entity.swing(InteractionHand.MAIN_HAND, true);
                GreyTornadoBlade windSphere = new GreyTornadoBlade(entity.level(), entity);

                windSphere.setSpeed(2.0F);
                windSphere.setDamage((float) (getCurrnetDamage(instance, player)/2));
                windSphere.setNoGravity(true);
                windSphere.setSize(3);
                windSphere.setSecondaryDamage((float) (getCurrnetDamage(instance, player)/2));
                windSphere.setSecondaryDamage((float) (getCurrnetDamage(instance, player)/2));
                windSphere.setBurnTicks(-1);
                windSphere.setSkill(entity, instance, this, 2);
                entity.level().addFreshEntity(windSphere);
                entity.level().playSound((Player) null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent) TensuraSoundEvents.CAST_WIND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    public static double getCurrnetDamage(ManasSkillInstance instance, Player player){
        double fullDamage =  CONFIG.fullDamage;
        var data = player.getPersistentData();
        CompoundTag tag = instance.getOrCreateTag();
        double percentUsed =  (tag.getDouble("outputPercent"));
        boolean fullCowlingOn = tag.getBoolean("fullCowling");
        double UsedDamage = 0;
        double UsedDamagePre = 0;

        if (fullCowlingOn){
            UsedDamagePre = (int) (fullDamage * CONFIG.fullcowlsmash);
            UsedDamage = UsedDamagePre + (fullDamage* percentUsed);

        }else {

            UsedDamage = (fullDamage * percentUsed);


        }

        return (float) UsedDamage;
    }







    private void fullCowlActivate(ManasSkillInstance instance, LivingEntity entity){

        CompoundTag tag = instance.getOrCreateTag();
        double percentUsed =  (tag.getDouble("outputPercent"));
        boolean fullCowlingOn = tag.getBoolean("fullCowling");
        if (instance.getMastery() >= (instance.getMaxMastery() * 0.1)){


            if (fullCowlingOn){
                tag.putBoolean("fullCowling", false);
                tag.remove(NEXT_RECOIL_TAG);
                startRecoilRecovery(entity);
            }else {
                tag.putBoolean("fullCowling", true);
                tag.putLong(NEXT_RECOIL_TAG, entity.level().getGameTime() + RECOIL_INTERVAL);

                colorName = false;
                AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
                AttributeInstance attribute2 = entity.getAttribute(Attributes.ARMOR);
                AttributeInstance attribute3 = entity.getAttribute(Attributes.MOVEMENT_SPEED);

                if (attribute != null) {
                    attribute.removeModifier(ATTACK_DAMAGE_MODIFIER);
                }
                if (attribute2 != null) {
                    attribute2.removeModifier(ARMOR_MODIFIER);
                }
                if (attribute3 != null) {
                    attribute3.removeModifier(MOVEMENT_MODIFIER);
                }

            }


            if (!entity.level().isClientSide()) {
                var visualData = instance.getOrCreateTag();
                visualData.putBoolean("cowlingParticles", !visualData.getBoolean("cowlingParticles"));
                instance.markDirty();
            }
        }

    }








    static {
        ICON_FRAMES = com.radient.tensuraacadamia.ability.ultimate.ofausers.OFA8th.build("ofa", 32);
    }

    public static ResourceLocation[] build(String prefix, int count) {
        ResourceLocation[] frames = new ResourceLocation[count];

        for(int i = 0; i < count; ++i) {
            frames[i] = ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skills/" + prefix + (i + 1) + ".png");
        }

        return frames;
    }
}
