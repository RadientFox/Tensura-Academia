package com.radient.tensuraacadamia.quirks.acid_sweat;

import com.radient.tensuraacadamia.quirks.acid.AcidEntities;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class AcidSweatAction extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(AcidSweatAction.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(AcidSweatAction.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(AcidSweatAction.class, EntityDataSerializers.LONG);
    private UUID creator;
    private int punches;
    private boolean clapped;

    public AcidSweatAction(EntityType<? extends AcidSweatAction> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public AcidSweatAction(Level level, LivingEntity owner, int mode) {
        this(AcidEntities.SWEAT_ACTION.get(), level);
        creator = owner.getUUID();
        entityData.set(OWNER, owner.getId());
        entityData.set(MODE, mode);
        entityData.set(START, level.getGameTime());
        setPos(owner.position());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(MODE, 0);
        builder.define(START, 0L);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    public UUID creatorId() {
        return creator;
    }

    public LivingEntity creator() {
        return level().getEntity(entityData.get(OWNER)) instanceof LivingEntity owner ? owner : null;
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public long age() {
        return Math.max(0, level().getGameTime() - entityData.get(START));
    }

    public int punchesDelivered() {
        return punches;
    }

    @Override
    public void tick() {
        super.tick();
        var owner = creator();
        if (owner != null) setPos(owner.position());
        if (!(level() instanceof ServerLevel level)) return;
        var instance = owner == null ? null : SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ACID_SWEAT.get()).orElse(null);
        if (owner == null || !owner.isAlive() || owner.level() != level || instance == null || !instance.canInteractSkill(owner)) {
            discard();
            return;
        }
        long age = age();
        if (mode() == 0) {
            if (!clapped && age >= AcidSweatQuirk.CLAP_STARTUP) {
                clapped = true;
                AcidSweatQuirk.clap(level, owner, instance);
            }
            if (age >= AcidSweatQuirk.CLAP_STARTUP + 8) discard();
        } else {
            if (punches < AcidSweatQuirk.PUNCH_COUNT && age >= AcidSweatQuirk.BARRAGE_STARTUP + punches * AcidSweatQuirk.PUNCH_INTERVAL)
                AcidSweatQuirk.punch(level, owner, instance, punches++);
            if (punches >= AcidSweatQuirk.PUNCH_COUNT && age >= AcidSweatQuirk.BARRAGE_STARTUP + AcidSweatQuirk.PUNCH_COUNT * AcidSweatQuirk.PUNCH_INTERVAL)
                discard();
        }
    }
}
