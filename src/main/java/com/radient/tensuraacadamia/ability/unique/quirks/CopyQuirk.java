package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CopyQuirk extends Skill {

    private static final int COPY_DURATION = 60 * 5;
    private static final int COPY_COOLDOWN = 5;

    private static final int NORMAL_COPY_LIMIT = 1;
    private static final int MASTERED_COPY_LIMIT = 2;
    private static final String MOD_NAMESPACE = "tracadamia";
    private static final String COPY_TAG = "CopyQuirkCopy";

    public CopyQuirk() {
        super(SkillType.UNIQUE);
    }

    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return true;
    }

    public boolean canBeSlotted(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return false;
    }


    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return "copy.copy";
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity entity, DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            copyFromEntity(instance, entity, attacker);
        }

        return true;
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity entity, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        copyFromEntity(instance, entity, target);

        return true;
    }

    private void copyFromEntity(ManasSkillInstance copyInstance, LivingEntity owner, LivingEntity target) {
        if (target == null || target == owner) {
            return;
        }

        Collection<ManasSkillInstance> targetSkills = SkillAPI.getSkillsFrom(target).getLearnedSkills();

        List<ManasSkillInstance> availableQuirks = new ArrayList<>();

        for (ManasSkillInstance targetInstance : targetSkills) {
            if (targetInstance == null) {
                continue;
            }

            if (!copyInstance.isToggled()) {
                continue;
            }

            if (!MOD_NAMESPACE.equals(targetInstance.getSkillId().getNamespace())) {
                continue;
            }

            if (targetInstance.isTemporarySkill()) {
                continue;
            }

            if (copyInstance.getCoolDown(0) > 0) {
                continue;
            }

            if (targetInstance.isSubInstance()) {
                continue;
            }

            if (!(targetInstance.getSkill() instanceof Skill skill)) {
                continue;
            }

            if (skill.getType() != SkillType.UNIQUE) {
                continue;
            }

            if (skill == this) {
                continue;
            }

            availableQuirks.add(targetInstance);
        }

        if (availableQuirks.isEmpty()) {
            return;
        }

        ManasSkillInstance source = availableQuirks.get(owner.getRandom().nextInt(availableQuirks.size()));

        copyQuirk(copyInstance, owner, target, source);
    }

    private void copyQuirk(ManasSkillInstance copyInstance, LivingEntity owner, LivingEntity target, ManasSkillInstance source) {
        ManasSkill skill = source.getSkill();

        if (hasSkill(owner, skill)) {
            return;
        }

        if (hasCopiedQuirk(owner, skill)) {
            return;
        }

        int maxCopies = copyInstance.isMastered(owner) ? MASTERED_COPY_LIMIT : NORMAL_COPY_LIMIT;

        while (getCopiedQuirks(owner).size() >= maxCopies) {
            removeOldestCopy(owner);
        }

        Changeable<ManasSkill> changeable = Changeable.of(skill);

        if (TensuraSkillEvents.SKILL_PLUNDER.invoker().plunder(target, owner, false, changeable).isFalse()) {
            return;
        }

        ManasSkill copiedSkill = changeable.get();

        if (copiedSkill == null) {
            return;
        }

        if (SkillHelper.learnSkill(owner, copiedSkill, COPY_DURATION)) {

            for (ManasSkillInstance learned : SkillAPI.getSkillsFrom(owner).getLearnedSkills()) {

                if (learned.getSkill() == copiedSkill) {
                    learned.getOrCreateTag().putBoolean(COPY_TAG, true);
                    learned.markDirty();
                    break;
                }
            }

            copyInstance.addMasteryPoint(owner);
            copyInstance.setCoolDown(COPY_COOLDOWN, 0);
        }
    }

    private boolean hasSkill(LivingEntity entity, ManasSkill skill) {
        for (ManasSkillInstance instance : SkillAPI.getSkillsFrom(entity).getLearnedSkills()) {

            if (instance.getSkill() == skill) {
                return true;
            }
        }

        return false;
    }

    private List<ManasSkillInstance> getCopiedQuirks(LivingEntity entity) {
        List<ManasSkillInstance> copies = new ArrayList<>();

        for (ManasSkillInstance instance : SkillAPI.getSkillsFrom(entity).getLearnedSkills()) {

            if (instance.getTag() != null && instance.getTag().getBoolean(COPY_TAG)) {

                copies.add(instance);
            }
        }

        return copies;
    }

    private boolean hasCopiedQuirk(LivingEntity entity, ManasSkill skill) {
        for (ManasSkillInstance instance : getCopiedQuirks(entity)) {
            if (instance.getSkill() == skill) {
                return true;
            }
        }

        return false;
    }

    private void removeOldestCopy(LivingEntity entity) {
        List<ManasSkillInstance> copies = getCopiedQuirks(entity);

        if (copies.isEmpty()) {
            return;
        }

        ManasSkillInstance oldest = copies.get(0);

        for (ManasSkillInstance instance : copies) {
            if (instance.getRemoveTime() > oldest.getRemoveTime()) {
                oldest = instance;
            }
        }

        SkillAPI.getSkillsFrom(entity).forgetSkill(oldest.getSkillId(), Component.empty());
    }
}