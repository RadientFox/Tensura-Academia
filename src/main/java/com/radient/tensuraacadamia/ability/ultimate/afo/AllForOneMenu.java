package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.ability.ultimate.AllForOne;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.registry.menu.MenuRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.menu.SkillCreationMenu;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Uses Tensura's skill creation menu and substitutes steal/transfer actions on the server. */
public final class AllForOneMenu extends SkillCreationMenu {
    private final ServerPlayer owner;
    private final UUID targetId;

    private AllForOneMenu(int containerId, ServerPlayer owner, UUID targetId, int mode,
                          List<ResourceLocation> entries) {
        super(containerId, QuirkSkills.ALL_FOR_ONE.get(), mode, entries);
        this.owner = owner;
        this.targetId = targetId;
    }

    public static void open(ServerPlayer player, LivingEntity target, int mode) {
        LivingEntity source = mode == 0 ? target : player;
        List<ResourceLocation> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : AllForOneStock.counts(source).entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) entries.add(entry.getKey());
        }
        Component title = Component.literal(switch (mode) {
            case 0 -> "All For One: Steal";
            case 1 -> "All For One: Transfer";
            default -> "All For One: Stock";
        });
        UUID targetId = target.getUUID();
        MenuRegistry.openExtendedMenu(player, new SimpleMenuProvider((containerId, inventory, ignored) ->
                new AllForOneMenu(containerId, player, targetId, mode, entries), title), buffer -> {
            buffer.writeResourceLocation(QuirkSkills.ALL_FOR_ONE.get().getRegistryName());
            buffer.writeInt(mode);
            buffer.writeCollection(entries, (buf, id) -> buf.writeResourceLocation(id));
        });
    }

    private LivingEntity target() {
        Entity entity = owner.serverLevel().getEntity(targetId);
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    public boolean stillValid(Player player) {
        LivingEntity target = target();
        return player == owner && target != null && target.isAlive()
                && owner.distanceToSqr(target) <= AllForOne.TARGET_RANGE * AllForOne.TARGET_RANGE
                && SkillUtils.hasSkill(owner, QuirkSkills.ALL_FOR_ONE.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (player != owner || !stillValid(player)) return false;
        if (button < 0 || button >= getSkills().size() || getMode() == 2) return false;
        return select(getSkills().get(button).getRegistryName());
    }

    private boolean select(ResourceLocation id) {
        LivingEntity target = target();
        if (target == null) return false;
        ManasSkillInstance allForOne = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ALL_FOR_ONE.get()).orElse(null);
        if (allForOne == null) return false;
        if (allForOne.onCoolDown(getMode())) {
            owner.displayClientMessage(Component.literal("All For One is on cooldown."), true);
            return false;
        }
        LivingEntity source = getMode() == 0 ? target : owner;
        LivingEntity destination = getMode() == 0 ? owner : target;
        Map<ResourceLocation, Integer> sourceCounts = AllForOneStock.counts(source);
        if (!sourceCounts.containsKey(id)) return false;
        if (getMode() == 0 && !canStealFrom(target, id, sourceCounts)) return false;
        double auraCost = getMode() == 0 ? acquisitionAuraCost(source, id) : 0.0D;
        var existence = TensuraStorages.getExistenceFrom(owner);
        if (existence.getAura() < auraCost) {
            owner.displayClientMessage(Component.literal("You need " + (long) Math.ceil(auraCost) + " aura to steal this ability."), true);
            return false;
        }
        if (!AllForOneStock.moveOne(source, destination, id)) return false;
        if (getMode() == 0) {
            existence.setAura(existence.getAura() - auraCost);
            existence.markDirty();
        }
        allForOne.setCoolDown(200, getMode());
        allForOne.markDirty();
        SkillAPI.getSkillsFrom(owner).markDirty();
        if (getMode() == 0) spawnStealEffects(target);
        else spawnTransferEffects(target);
        owner.sendSystemMessage(Component.literal(getMode() == 0 ? "Stole " : "Transferred ")
                .append(AllForOneStock.displayName(id)));
        if (getMode() == 1) QuirkSickness.apply(target);
        if (owner.isAlive() && target.isAlive()) open(owner, target, getMode());
        return true;
    }

    private double acquisitionAuraCost(LivingEntity source, ResourceLocation id) {
        var skill = SkillAPI.getSkillRegistry().get(id);
        if (!(skill instanceof TensuraSkill typed)) return 0.0D;
        var ownedInstance = SkillAPI.getSkillsFrom(source).getSkill(id).orElse(null);
        double acquisition = ownedInstance == null ? typed.getDefaultAcquiringMagiculeCost()
                : typed.getAcquiringMagiculeCost(ownedInstance);
        // One acquisition-cost point becomes one aura point, then stealing doubles it.
        return Double.isFinite(acquisition) ? Math.max(0.0D, acquisition * 2.0D) : Double.MAX_VALUE;
    }

    private void spawnStealEffects(LivingEntity target) {
        Vec3 start = target.getEyePosition();
        Vec3 end = owner.getEyePosition();
        for (int step = 1; step <= 14; step++) {
            Vec3 point = start.lerp(end, step / 15.0D);
            owner.serverLevel().sendParticles(ParticleTypes.SMOKE,
                    point.x, point.y, point.z, 3, 0.08D, 0.08D, 0.08D, 0.01D);
        }
        owner.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    private void spawnTransferEffects(LivingEntity target) {
        Vec3 start = owner.getEyePosition();
        Vec3 end = target.getEyePosition();
        for (int step = 1; step <= 14; step++) {
            Vec3 point = start.lerp(end, step / 15.0D);
            owner.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    point.x, point.y, point.z, 3, 0.08D, 0.08D, 0.08D, 0.01D);
        }
        owner.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    private boolean canStealFrom(LivingEntity target, ResourceLocation selected,
                                 Map<ResourceLocation, Integer> targetCounts) {
        String path = selected.getPath();
        if (path.contains("one_for_all") && path.contains("ember")) {
            owner.displayClientMessage(Component.literal("One For All Embers cannot be stolen."), true);
            return false;
        }
        boolean protectedByOneForAll = targetCounts.keySet().stream()
                .anyMatch(id -> id.equals(QuirkSkills.OFA_1ST.get().getRegistryName())
                        || id.getPath().contains("one_for_all") && id.getPath().contains("ember"));
        if (protectedByOneForAll && EnergyHelper.getMaxEP(owner) < 2.0D * EnergyHelper.getMaxEP(target)) {
            owner.displayClientMessage(Component.literal("One For All protects this target. You need twice their EP."), true);
            return false;
        }
        return true;
    }
}
