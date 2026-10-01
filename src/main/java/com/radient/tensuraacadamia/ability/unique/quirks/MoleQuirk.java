package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.TickingSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class MoleQuirk extends Skill {

    private static final QuirkSkillsConfig.Mole CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Mole;

    private static final int TUNNEL = 0;

    private static final TagKey<Block> MAGIC_ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("tensura", "magic_ores"));

    private static final ResourceLocation TUNNEL_SPEED = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "mole_tunnel_speed");
    private static final ResourceLocation TUNNEL_STEP = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "mole_tunnel_step");
    private static final double TUNNEL_STEP_HEIGHT = 0.5D;
    private static final int TUNNEL_SIZE = 2;
    private static final int TUNNEL_SIZE_MASTERED = 3;
    private static final float STAIR_PITCH = 30.0F;
    private static final float SHAFT_PITCH = 70.0F;
    private static final int DIG_INTERVAL = 2;

    private static boolean tunneling = false;
    private static @Nullable LivingEntity breaker;

    public MoleQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.auraCost;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == TUNNEL ? "mole.tunnel" : super.getModeId(instance, mode);
    }

    private static Optional<ManasSkillInstance> getMole(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.MOLE.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // Mining Strength and ore affinity toggle

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled() || hasTunnelBoost(entity);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!TickingSkill.isTickingSkill(entity, this, TUNNEL)) {
            removeTunnelBoost(entity);
        }

        if (!instance.isToggled()) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }


    private static Tier getHandTier(boolean mastered) {
        return mastered ? Tiers.NETHERITE : Tiers.DIAMOND;
    }

    private static boolean isHandTool(BlockState state, boolean mastered) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return true;
        }

        return mastered && (state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE));
    }

    private static boolean canHandsHarvest(BlockState state, boolean mastered) {
        return isHandTool(state, mastered) && !state.is(getHandTier(mastered).getIncorrectBlocksForDrops());
    }

    private boolean hasStrongHands(ManasSkillInstance instance, Player player) {
        return player.getMainHandItem().isEmpty() && (instance.isToggled() || TickingSkill.isTickingSkill(player, this, TUNNEL));
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        getMole(player).filter(instance -> QuirkSkills.MOLE.get().hasStrongHands(instance, player)).ifPresent(instance -> {
            boolean mastered = instance.isMastered(player);
            if (isHandTool(event.getState(), mastered)) {
                event.setNewSpeed(event.getNewSpeed() * getHandTier(mastered).getSpeed());
            }
        });
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (event.canHarvest()) {
            return;
        }

        // The tunnel digs with the hand
        getMole(player).filter(instance -> tunneling || QuirkSkills.MOLE.get().hasStrongHands(instance, player)).ifPresent(instance -> {
            if (canHandsHarvest(event.getTargetBlock(), instance.isMastered(player))) {
                event.setCanHarvest(true);
            }
        });
    }

    // Fortune

    private boolean hasFortune(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled() || tunneling || TickingSkill.isTickingSkill(entity, this, TUNNEL);
    }

    // used by BlockDropsMixin
    public static void startDrops(@Nullable Entity entity) {
        breaker = entity instanceof LivingEntity living && getMole(living).filter(instance -> QuirkSkills.MOLE.get().hasFortune(instance, living)).isPresent() ? living : null;
    }

    public static void endDrops() {
        breaker = null;
    }

    @SubscribeEvent
    public static void onEnchantmentLevel(GetEnchantmentLevelEvent event) {
        if (breaker != null && event.isTargetting(Enchantments.FORTUNE)) {
            event.getHolder(Enchantments.FORTUNE).ifPresent(fortune -> event.getEnchantments().set(fortune, event.getEnchantments().getLevel(fortune) + CONFIG.fortuneBonus));
        }
    }

    // Used by MoleClient

    public static boolean hasOreAffinity(Player player) {
        return getMole(player).map(ManasSkillInstance::isToggled).orElse(false);
    }

    public static int getOreRange() {
        return CONFIG.oreRange;
    }

    public static boolean isAffinityOre(BlockState state) {
        return getAffinityColor(state) != -1;
    }

    public static int getAffinityColor(BlockState state) {
        if (state.is(BlockTags.DIAMOND_ORES)) {
            return 0x5CEBFF;
        }

        if (state.is(MAGIC_ORES)) {
            return 0xC060FF;
        }

        return state.is(Blocks.ANCIENT_DEBRIS) ? 0xE0773F : -1;
    }


    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != TUNNEL || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks == 0) {
            if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
                return false;
            }

            addTunnelBoost(entity);
        }

        if (heldTicks % DIG_INTERVAL == 0 && dig(level, instance, entity) && heldTicks % 4 == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F + entity.getRandom().nextFloat() * 0.3F);
        }

        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != TUNNEL) {
            return;
        }

        removeTunnelBoost(entity);
        if (heldTicks > 0) {
            instance.setCoolDown(CONFIG.tunnelCooldown, TUNNEL);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        removeTunnelBoost(entity);
    }

    private static void addTunnelBoost(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(TUNNEL_SPEED, CONFIG.tunnelSpeedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        AttributeInstance step = entity.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            step.addOrUpdateTransientModifier(new AttributeModifier(TUNNEL_STEP, TUNNEL_STEP_HEIGHT, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeTunnelBoost(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(TUNNEL_SPEED);
        }

        AttributeInstance step = entity.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            step.removeModifier(TUNNEL_STEP);
        }
    }

    private static boolean hasTunnelBoost(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(TUNNEL_SPEED);
    }

    private static boolean dig(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean mastered = instance.isMastered(entity);
        boolean dug = false;

        if (!GroundBlocks.canGrief(level, entity)) {
            return false;
        }

        tunneling = true;
        try {
            for (BlockPos pos : getDigArea(entity, mastered ? TUNNEL_SIZE_MASTERED : TUNNEL_SIZE)) {
                BlockState state = level.getBlockState(pos);
                if (!canTunnel(level, pos, state, mastered)) {
                    continue;
                }

                if (TensuraSkillEvents.SKILL_GRIEF_PRE.invoker().grief(instance, level, entity, pos.getX(), pos.getY(), pos.getZ()).isFalse()) {
                    continue;
                }

                boolean broken = entity instanceof ServerPlayer player ? player.gameMode.destroyBlock(pos) : level.destroyBlock(pos, true, entity);
                if (broken) {
                    GroundBlocks.griefed(level, instance, entity, pos);
                }
                dug |= broken;
            }
        } finally {
            tunneling = false;
        }

        return dug;
    }

    private static List<BlockPos> getDigArea(LivingEntity entity, int size) {
        List<BlockPos> area = new ArrayList<>();
        BlockPos feet = entity.blockPosition();
        float pitch = entity.getXRot();

        if (Math.abs(pitch) >= SHAFT_PITCH) {
            int bottom = pitch > 0.0F ? -1 : 0;
            int top = pitch > 0.0F ? size - 1 : size;
            for (int x : span(entity.getX(), size)) {
                for (int z : span(entity.getZ(), size)) {
                    for (int y = bottom; y <= top; y++) {
                        area.add(new BlockPos(x, feet.getY() + y, z));
                    }
                }
            }

            return area;
        }

        Direction forward = entity.getDirection();
        Direction side = forward.getClockWise();
        int[] across = span(side.getAxis() == Direction.Axis.X ? entity.getX() : entity.getZ(), size);
        boolean up = pitch <= -STAIR_PITCH;
        boolean down = pitch >= STAIR_PITCH;

        for (int depth = 0; depth <= 1; depth++) {
            for (int column : across) {
                BlockPos base = side.getAxis() == Direction.Axis.X
                        ? new BlockPos(column, feet.getY(), feet.getZ() + forward.getStepZ() * depth)
                        : new BlockPos(feet.getX() + forward.getStepX() * depth, feet.getY(), column);
                for (int y = up ? 1 : 0; y <= (up ? size : size - 1); y++) {
                    area.add(base.above(y));
                }

                if (down && depth == 1) {
                    area.add(base.below());
                }
            }
        }

        return area;
    }

    private static int[] span(double coord, int size) {
        int start = Mth.floor(coord - size * 0.5D + 0.5D);
        int[] blocks = new int[size];
        for (int i = 0; i < size; i++) {
            blocks[i] = start + i;
        }

        return blocks;
    }

    private static boolean canTunnel(ServerLevel level, BlockPos pos, BlockState state, boolean mastered) {
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        if (state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Tags.Blocks.STONES) || state.is(Tags.Blocks.COBBLESTONES) || state.is(Tags.Blocks.GRAVELS) || state.is(Tags.Blocks.SANDS)) {
            return true;
        }

        return (state.is(Tags.Blocks.ORES) || state.is(MAGIC_ORES)) && canHandsHarvest(state, mastered);
    }

}
