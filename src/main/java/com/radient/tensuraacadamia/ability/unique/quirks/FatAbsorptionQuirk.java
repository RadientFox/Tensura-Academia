package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class FatAbsorptionQuirk extends Skill {

    private static final QuirkSkillsConfig.FatAbsorption CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).FatAbsorption;

    private static final int RESTRAIN = 0;
    private static final int SPEAR = 1;
    private static final int BMI_CHECK = 2;

    private static final DecimalFormat FAT_FORMAT = new DecimalFormat("#.#");

    private static final String FAT_TAG = "fat";
    private static final String SPEAR_FAT_TAG = "spearFat";
    private static final String RESTRAIN_TARGET_TAG = "restrainTarget";
    private static final String RESTRAIN_X_TAG = "restrainX";
    private static final String RESTRAIN_Y_TAG = "restrainY";
    private static final String RESTRAIN_Z_TAG = "restrainZ";
    private static final String RESTRAINED_TAG = "tracadamia_fat_restrained";

    private static final float CAKE_SLICE_SATURATION = 0.4F;

    private static final ResourceLocation FAT_STOCK = ResourceLocation.fromNamespaceAndPath("tracadamia", "fat_stock");
    private static final ResourceLocation SPEAR_DAMAGE = ResourceLocation.fromNamespaceAndPath("tracadamia", "fat_absorption_spear");

    public FatAbsorptionQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == RESTRAIN ? BMI_CHECK : mode - 1;
        }

        return mode == BMI_CHECK ? RESTRAIN : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case RESTRAIN -> "fat_absorption.restrain";
            case SPEAR -> "fat_absorption.spear";
            case BMI_CHECK -> "fat_absorption.bmi_check";
            default -> super.getModeId(instance, mode);
        };
    }

    // fat stock, true passive

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateFatStock(instance, entity);
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateFatStock(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        clearRestrain(instance, entity);

        setModifier(entity, Attributes.ARMOR, 0.0D);
        setModifier(entity, Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    private static void stockFat(ManasSkillInstance instance, LivingEntity entity, float saturation) {
        if (saturation <= 0.0F) {
            return;
        }

        double gained = saturation * CONFIG.fatPerSaturation;
        setFat(instance, getFat(instance) + gained);
        updateFatStock(instance, entity);
        instance.addMasteryPoint(entity);
        SkillAPI.getSkillsFrom(entity).markDirty();

        sendMessage(entity, Component.translatable("tracadamia.skill.fat_absorption.fat_gained", FAT_FORMAT.format(getFat(instance)), FAT_FORMAT.format(gained)).withStyle(ChatFormatting.GOLD));
    }

    //called by FatAbsorptionCommand
    public static void setStoredFat(ManasSkillInstance instance, LivingEntity entity, double fat) {
        setFat(instance, fat);
        updateFatStock(instance, entity);
        SkillAPI.getSkillsFrom(entity).markDirty();
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        double fat = getFat(instance);
        if (fat <= 0.0D || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        double reduction = Math.min(CONFIG.maxDamageReduction, fat / 3.0D * CONFIG.damageReductionPerFat);
        float reduced = (float) (amount.get() * (1.0D - reduction));
        amount.set(reduced);

        setFat(instance, fat - reduced * CONFIG.damageFatLoss);
        updateFatStock(instance, owner);
        return true;
    }

    // used by FatAbsorptionClient
    public static float getTorsoGrowth(ManasSkillInstance instance) {
        return (float) Math.sqrt(Math.min(1.0D, getFat(instance) / CONFIG.torsoMaxFat));
    }

    public static float getTorsoWidth(float growth) {
        return (float) (1.0D + (CONFIG.torsoMaxWidth - 1.0D) * growth);
    }

    public static float getTorsoDepth(float growth) {
        return (float) (1.0D + (CONFIG.torsoMaxDepth - 1.0D) * growth);
    }

    private static void updateFatStock(ManasSkillInstance instance, LivingEntity entity) {
        double third = getFat(instance) / 3.0D;
        setModifier(entity, Attributes.ARMOR, Math.min(CONFIG.maxArmor, third * CONFIG.armorPerFat));
        setModifier(entity, Attributes.KNOCKBACK_RESISTANCE, Math.min(CONFIG.maxKnockbackResistance, third * CONFIG.knockbackResistancePerFat));
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, double amount) {
        AttributeInstance attributeInstance = entity.getAttribute(attribute);
        if (attributeInstance == null) {
            return;
        }

        if (amount <= 0.0D) {
            attributeInstance.removeModifier(FAT_STOCK);
            return;
        }

        AttributeModifier current = attributeInstance.getModifier(FAT_STOCK);
        if (current != null && current.amount() == amount) {
            return;
        }

        attributeInstance.addOrReplacePermanentModifier(new AttributeModifier(FAT_STOCK, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static double getFat(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : tag.getDouble(FAT_TAG);
    }

    private static void setFat(ManasSkillInstance instance, double fat) {
        instance.getOrCreateTag().putDouble(FAT_TAG, Math.max(0.0D, fat));
        instance.markDirty();
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        switch (mode) {
            case RESTRAIN -> startRestrain(instance, entity);
            case SPEAR -> toggleSpear(instance, entity);
            case BMI_CHECK -> sendMessage(entity, Component.translatable("tracadamia.skill.fat_absorption.bmi", FAT_FORMAT.format(getFat(instance))).withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != RESTRAIN) {
            return false;
        }

        LivingEntity target = getRestrainTarget(instance, entity);
        if (target == null || getFat(instance) < CONFIG.restrainFat || !isInRestrainRange(entity, target)) {
            clearRestrain(instance, entity);
            return false;
        }

        restrain(instance, target);

        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == RESTRAIN) {
            clearRestrain(instance, entity);
        }
    }

    // restrain
    private static void startRestrain(ManasSkillInstance instance, LivingEntity entity) {
        clearRestrain(instance, entity);

        if (getFat(instance) < CONFIG.restrainFat) {
            sendMessage(entity, Component.translatable("tracadamia.skill.fat_absorption.not_enough_fat", FAT_FORMAT.format(CONFIG.restrainFat)).withStyle(ChatFormatting.RED));
            return;
        }

        LivingEntity target = findRestrainTarget(entity);
        if (target == null) {
            sendMessage(entity, Component.translatable("tensura.targeting.not_targeted").withStyle(ChatFormatting.RED));
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        tag.putUUID(RESTRAIN_TARGET_TAG, target.getUUID());
        tag.putDouble(RESTRAIN_X_TAG, target.getX());
        tag.putDouble(RESTRAIN_Y_TAG, target.getY());
        tag.putDouble(RESTRAIN_Z_TAG, target.getZ());
        instance.markDirty();

        restrain(instance, target);
        entity.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private static void restrain(ManasSkillInstance instance, LivingEntity target) {
        CompoundTag tag = instance.getOrCreateTag();
        Vec3 lockPos = new Vec3(tag.getDouble(RESTRAIN_X_TAG), tag.getDouble(RESTRAIN_Y_TAG), tag.getDouble(RESTRAIN_Z_TAG));

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 9, false, false, false));
        target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.ANTI_SKILL), 10, 0, false, false, false));
        target.setDeltaMovement(Vec3.ZERO);

        if (target.position().distanceToSqr(lockPos) > 0.0001D) {
            target.teleportTo(lockPos.x, lockPos.y, lockPos.z);
        }

        target.getPersistentData().putLong(RESTRAINED_TAG, target.level().getGameTime() + 2L);
    }

    private static void clearRestrain(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(RESTRAIN_TARGET_TAG)) {
            return;
        }

        LivingEntity target = getRestrainTarget(instance, entity);
        if (target != null) {
            target.getPersistentData().remove(RESTRAINED_TAG);
        }

        tag.remove(RESTRAIN_TARGET_TAG);
        tag.remove(RESTRAIN_X_TAG);
        tag.remove(RESTRAIN_Y_TAG);
        tag.remove(RESTRAIN_Z_TAG);
        instance.markDirty();
    }

    private static boolean isRestrained(LivingEntity entity) {
        return entity.getPersistentData().getLong(RESTRAINED_TAG) >= entity.level().getGameTime();
    }

    private static @Nullable LivingEntity getRestrainTarget(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(RESTRAIN_TARGET_TAG) || !(entity.level() instanceof ServerLevel level)) {
            return null;
        }

        return level.getEntity(tag.getUUID(RESTRAIN_TARGET_TAG)) instanceof LivingEntity target && target.isAlive() ? target : null;
    }

    private static boolean isInRestrainRange(LivingEntity entity, LivingEntity target) {
        return entity.getBoundingBox().inflate(CONFIG.restrainRange).intersects(target.getBoundingBox());
    }

    private static @Nullable LivingEntity findRestrainTarget(LivingEntity entity) {
        double range = entity.getBbWidth() + CONFIG.restrainRange + 1.0D;
        Vec3 start = entity.getEyePosition();
        Vec3 look = entity.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(range));
        AABB searchBox = entity.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);

        LivingEntity closest = null;
        double closestDistance = range * range;

        for (LivingEntity target : entity.level().getEntitiesOfClass(LivingEntity.class, searchBox, other -> other != entity && other.isAlive() && !other.isSpectator())) {
            Optional<Vec3> hit = target.getBoundingBox().inflate(0.3D).clip(start, end);
            if (hit.isEmpty() || !isInRestrainRange(entity, target)) {
                continue;
            }

            double distance = start.distanceToSqr(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = target;
            }
        }

        return closest;
    }

    // shield into spear
    private static void toggleSpear(ManasSkillInstance instance, LivingEntity entity) {
        double spearFat = getSpearFat(instance);
        if (spearFat > 0.0D) {
            setFat(instance, getFat(instance) + spearFat);
            setSpearFat(instance, entity, 0.0D);
            updateFatStock(instance, entity);
            sendMessage(entity, Component.translatable("tracadamia.skill.fat_absorption.spear_cancel").withStyle(ChatFormatting.GRAY));
            return;
        }

        double fat = getFat(instance);
        if (fat <= 0.0D) {
            sendMessage(entity, Component.translatable("tracadamia.skill.fat_absorption.no_fat").withStyle(ChatFormatting.RED));
            return;
        }

        setSpearFat(instance, entity, fat);
        setFat(instance, 0.0D);
        updateFatStock(instance, entity);
        sendMessage(entity, Component.translatable("tracadamia.skill.fat_absorption.spear_ready", FAT_FORMAT.format(fat)).withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        double spearFat = getSpearFat(instance);
        if (spearFat <= 0.0D || !isSpearAttack(owner, source)) {
            return true;
        }

        setSpearFat(instance, owner, 0.0D);
        instance.addMasteryPoint(owner);
        return true;
    }

    private static boolean isSpearAttack(LivingEntity owner, DamageSource source) {
        return source.getDirectEntity() == owner && TensuraDamageHelper.isPhysicalAttack(source);
    }

    private static float getSpearBypassLevel(ManasSkillInstance instance) {
        double spearFat = getSpearFat(instance);
        if (spearFat >= CONFIG.spearIgnoreFat) {
            return 2.0F;
        }

        if (spearFat >= CONFIG.spearNullificationFat) {
            return 1.0F;
        }

        if (spearFat >= CONFIG.spearResistanceFat) {
            return 1.0F;
        }

        return 0.0F;
    }

    private static double getSpearFat(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : tag.getDouble(SPEAR_FAT_TAG);
    }

    private static void setSpearFat(ManasSkillInstance instance, LivingEntity entity, double fat) {
        instance.getOrCreateTag().putDouble(SPEAR_FAT_TAG, fat);
        instance.markDirty();

        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }

        double bonus = fat * CONFIG.spearDamagePerFat;
        if (bonus <= 0.0D) {
            attack.removeModifier(SPEAR_DAMAGE);
            return;
        }

        attack.addOrReplacePermanentModifier(new AttributeModifier(SPEAR_DAMAGE, bonus, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void spawnSpearSparks(Player player) {
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double side = 0.4D * player.getScale();
        double forward = 0.15D * player.getScale();
        double x = player.getX() - Mth.cos(yaw) * side - Mth.sin(yaw) * forward;
        double y = player.getY() + player.getBbHeight() * 0.42D;
        double z = player.getZ() - Mth.sin(yaw) * side + Mth.cos(yaw) * forward;

        TensuraParticleHelper.spawnServerParticles(player.level(), TensuraParticleTypes.YELLOW_LIGHTNING_SPARK.get(), x, y, z, 1, 0.08D, 0.08D, 0.08D, 0.02D, true);
        TensuraParticleHelper.spawnServerParticles(player.level(), TensuraParticleTypes.LIGHTNING_SPARK.get(), x, y, z, 1, 0.08D, 0.08D, 0.08D, 0.02D, true);
    }

    private static void sendMessage(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    private static Optional<ManasSkillInstance> getFatAbsorption(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.FAT_ABSORPTION.get()).filter(instance -> instance.getMastery() >= 0.0D && instance.canInteractSkill(entity));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 2 != 0) {
            return;
        }

        getFatAbsorption(player).filter(instance -> getSpearFat(instance) > 0.0D).ifPresent(instance -> spawnSpearSparks(player));
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        FoodProperties food = event.getItemStack().getFoodProperties(player);
        if (food == null || player.canEat(food.canAlwaysEat()) || getFatAbsorption(player).isEmpty()) {
            return;
        }

        player.startUsingItem(event.getHand());
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Optional<ManasSkillInstance> fatAbsorption = getFatAbsorption(player);
        if (fatAbsorption.isEmpty()) {
            return;
        }

        if (player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = event.getItemStack();

        boolean candleCake = state.getBlock() instanceof CandleCakeBlock;
        if (candleCake) {
            if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
                return;
            }

            if (state.getValue(CandleCakeBlock.LIT) && stack.isEmpty() && event.getHitVec().getLocation().y - pos.getY() > 0.5D) {
                return;
            }
        } else if (!(state.getBlock() instanceof CakeBlock) || (stack.is(ItemTags.CANDLES) && state.getValue(CakeBlock.BITES) == 0)) {
            return;
        }

        if (!level.isClientSide) {
            eatCakeSlice(level, pos, candleCake ? Blocks.CAKE.defaultBlockState() : state, player);
            if (candleCake) {
                Block.dropResources(state, level, pos);
            }

            stockFat(fatAbsorption.get(), player, CAKE_SLICE_SATURATION);
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }

    private static void eatCakeSlice(Level level, BlockPos pos, BlockState state, Player player) {
        player.awardStat(Stats.EAT_CAKE_SLICE);
        player.getFoodData().eat(2, 0.1F);
        level.gameEvent(player, GameEvent.EAT, pos);

        int bites = state.getValue(CakeBlock.BITES);
        if (bites < CakeBlock.MAX_BITES) {
            level.setBlock(pos, state.setValue(CakeBlock.BITES, bites + 1), 3);
        } else {
            level.removeBlock(pos, false);
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        }
    }

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        FoodProperties food = event.getItem().getFoodProperties(entity);
        if (food == null) {
            return;
        }

        getFatAbsorption(entity).ifPresent(instance -> stockFat(instance, entity, food.saturation()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (isRestrained(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || !isSpearAttack(attacker, source)) {
            return;
        }

        getFatAbsorption(attacker).ifPresent(instance -> {
            float level = getSpearBypassLevel(instance);
            TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
            if (level > tensuraSource.tensura$getResistanceBypassLevel()) {
                tensuraSource.tensura$setResistanceBypassLevel(level);
            }
        });
    }

}
