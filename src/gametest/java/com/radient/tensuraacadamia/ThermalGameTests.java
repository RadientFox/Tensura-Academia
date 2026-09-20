package com.radient.tensuraacadamia;

import com.radient.tensuraacadamia.ability.unique.quirks.HalfColdHalfHot;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tracadamia")
@PrefixGameTestTemplate(false)
public final class ThermalGameTests {
    private static boolean listening;
    private record Setup(Cow caster, Cow target, HalfColdHalfHot skill, ManasSkillInstance instance) {}

    private static Setup setup(GameTestHelper helper) {
        if (!listening) {
            listening = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
                        if (event.getEntity().getPersistentData().getBoolean("thermal_test_caster"))
                            new RuntimeException("THERMAL_CASTER_DEATH " + event.getSource()).printStackTrace();
                    });
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) -> {
                        if (event.getEntity().getPersistentData().getBoolean("thermal_test_caster"))
                            new RuntimeException("THERMAL_CASTER_REMOVED " + event.getEntity().getRemovalReason()).printStackTrace();
                    });
        }
        Cow caster = helper.spawnWithNoFreeWill(EntityType.COW, 5, 2, 2);
        caster.getPersistentData().putBoolean("thermal_test_caster", true);
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 5, 2, 8);
        caster.setNoGravity(true);
        target.setNoGravity(true);
        caster.setYRot(0);
        caster.setXRot(0);
        caster.setYHeadRot(0);
        caster.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        caster.setHealth(1000);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        HalfColdHalfHot skill = QuirkSkills.HALF_COLD_HALF_HOT.get();
        SkillAPI.getSkillsFrom(caster).updateSkill(skill.createDefaultInstance(), true);
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(caster).getSkill(skill).orElseThrow();
        instance.setMastery(0);
        instance.getOrCreateTag().putInt("thermal_state", 1);
        var existence = TensuraStorages.getExistenceFrom(caster);
        existence.setEP(2000000);
        existence.setMagicule(1000000);
        existence.setSpiritualHealth(1000000);
        existence.setAura(1000000);
        return new Setup(caster, target, skill, instance);
    }

    @GameTest(template = "thermal_empty", timeoutTicks = 50)
    public static void wall_finishes_despite_repeated_presses(GameTestHelper helper) {
        Setup s = setup(helper);
        double before = TensuraStorages.getExistenceFrom(s.caster).getAura();
        double cost = 500;
        s.skill.onPressed(s.instance, s.caster, 0, 3);
        helper.assertTrue(s.instance.getOrCreateTag().hasUUID("thermal_flame_target"), "Wall must acquire the target");
        helper.assertTrue(Math.abs(TensuraStorages.getExistenceFrom(s.caster).getAura() - (before - cost)) < 0.01,
                "Wall must consume aura immediately once");
        for (int tick = 1; tick <= 4; tick++) helper.runAfterDelay(tick,
                () -> s.skill.onPressed(s.instance, s.caster, 0, 3));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(s.target.getHealth() < 1000, "Wall must deal damage within ten real ticks; remaining="
                    + s.instance.getOrCreateTag().getInt("thermal_flame_ticks") + " targetAlive=" + s.target.isAlive()
                    + " casterAlive=" + s.caster.isAlive() + " sameInstance="
                    + (SkillAPI.getSkillsFrom(s.caster).getSkill(s.skill).orElse(null) == s.instance)
                    + " lastDamage=" + s.target.getLastDamageSource());
            helper.assertTrue(!s.instance.getOrCreateTag().hasUUID("thermal_flame_target"), "Wall must finish");
            float health = s.target.getHealth();
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(s.target.getHealth() == health, "Wall must only hit once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "thermal_empty", timeoutTicks = 50)
    public static void flashfire_beam_hits_once_per_press(GameTestHelper helper) {
        Setup s = setup(helper);
        s.skill.onPressed(s.instance, s.caster, 0, 1);
        helper.assertTrue(s.target.getHealth() < 1000, "Flashfire beam must hit on press");
        float health = s.target.getHealth();
        s.skill.onPressed(s.instance, s.caster, 0, 1);
        helper.assertTrue(s.target.getHealth() == health, "Flashfire cooldown must block a second press");
        helper.succeed();
    }

    @GameTest(template = "thermal_empty", timeoutTicks = 40)
    public static void pale_blade_hits_targets_during_its_flight(GameTestHelper helper) {
        Setup s = setup(helper);
        s.instance.setMastery(5000);
        s.instance.getOrCreateTag().putInt("thermal_state", 2);
        double auraBefore = TensuraStorages.getExistenceFrom(s.caster).getAura();
        s.skill.onPressed(s.instance, s.caster, 0, 7);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(s.target.getHealth() < 1000,
                    "Pale Blade must apply both damage types to a target in its swept hitbox");
            double auraAfter = TensuraStorages.getExistenceFrom(s.caster).getAura();
            helper.assertTrue(auraAfter <= auraBefore - 2000,
                    "Pale Blade must spend its 2,000 aura cost; before=" + auraBefore + " after=" + auraAfter);
            helper.succeed();
        });
    }

    @GameTest(template = "thermal_empty", timeoutTicks = 50)
    public static void rapid_jet_hits_finish_without_delayed_damage(GameTestHelper helper) {
        Setup s = setup(helper);
        s.instance.getOrCreateTag().putBoolean("jet_kindling", true);
        Cow nearby = helper.spawnWithNoFreeWill(EntityType.COW, 7, 2, 8);
        nearby.setNoGravity(true);
        nearby.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        nearby.setHealth(1000);
        for (int tick = 1; tick <= 3; tick++) helper.runAfterDelay(tick, () ->
                s.skill.onDamageEntity(s.instance, s.caster, s.target,
                        helper.getLevel().damageSources().mobAttack(s.caster), Changeable.of(1F)));
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(nearby.getHealth() < 1000, "Jet's expanding damage must reach nearby entities promptly");
            float health = nearby.getHealth();
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(nearby.getHealth() == health, "Jet must not leave queued hits behind");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "thermal_empty", timeoutTicks = 30)
    public static void insufficient_aura_blocks_cast(GameTestHelper helper) {
        Setup s = setup(helper);
        TensuraStorages.getExistenceFrom(s.caster).setAura(0);
        s.skill.onPressed(s.instance, s.caster, 0, 1);
        s.skill.onPressed(s.instance, s.caster, 0, 3);
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(s.target.getHealth() == 1000, "No aura must block damage");
            helper.assertTrue(!s.instance.getOrCreateTag().hasUUID("thermal_flame_target"), "No aura must block wall creation");
            helper.succeed();
        });
    }

    @GameTest(template = "thermal_empty", timeoutTicks = 30)
    public static void cooldowns_follow_abilities_when_switching(GameTestHelper helper) {
        Setup s = setup(helper);
        s.skill.onPressed(s.instance, s.caster, 0, 1);
        helper.assertTrue(s.instance.getCooldownList().get(1) == 30, "Flashfire cooldown must be 30 seconds");
        double paid = TensuraStorages.getExistenceFrom(s.caster).getAura();
        helper.assertTrue(paid == 999000, "Flashfire must cost 1000 aura");
        s.skill.onPressed(s.instance, s.caster, 0, 1);
        helper.assertTrue(TensuraStorages.getExistenceFrom(s.caster).getAura() == paid, "Blocked repeat must not cost aura");
        s.skill.onPressed(s.instance, s.caster, 0, 0);
        helper.assertTrue(s.instance.getCooldownList().get(1) == 0, "Cold Ice Wall must not inherit Flashfire cooldown");
        s.skill.onPressed(s.instance, s.caster, 0, 3);
        helper.assertTrue(s.instance.getCooldownList().get(3) == 2, "Ice Spikes cooldown must be two seconds");
        helper.assertTrue(TensuraStorages.getExistenceFrom(s.caster).getAura() == paid - 200, "Ice Spikes must cost 200 aura");
        s.skill.onPressed(s.instance, s.caster, 0, 0);
        helper.assertTrue(s.instance.getCooldownList().get(1) == 30, "Switching back must preserve Flashfire cooldown");
        s.instance.setMastery(5000);
        s.skill.onPressed(s.instance, s.caster, 0, 4);
        helper.assertTrue(s.instance.getCooldownList().get(4) == 30, "Phosphor Flashfire must share its original cooldown");
        helper.assertTrue(s.instance.getCooldownList().get(3) == 2, "Phosphor Ice Spikes must share its original cooldown");
        helper.succeed();
    }

    private static void hit(Setup s, float amount, boolean cold) {
        try {
            var method = HalfColdHalfHot.class.getDeclaredMethod("hurt", net.minecraft.server.level.ServerLevel.class,
                    net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.entity.LivingEntity.class,
                    float.class, boolean.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, s.caster.level(), s.caster, s.target, amount, cold, false);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = "thermal_empty", timeoutTicks = 30)
    public static void tensura_element_boosts_and_resistances_apply(GameTestHelper helper) {
        Setup s = setup(helper);
        s.caster.getAttribute(TensuraAttributes.FLAME_BOOST).setBaseValue(1);
        hit(s, 100, false);
        float normal = 1000 - s.target.getHealth();
        helper.assertTrue(normal > 0, "Heat hit must damage target");
        s.target.setHealth(1000);
        s.caster.getAttribute(TensuraAttributes.FLAME_BOOST).setBaseValue(2);
        hit(s, 100, false);
        helper.assertTrue(1000 - s.target.getHealth() > normal * 1.9, "Tensura flame boost must amplify heat damage");
        s.target.setHealth(1000);
        s.caster.getAttribute(TensuraAttributes.WATER_BOOST).setBaseValue(1);
        hit(s, 100, true);
        float cold = 1000 - s.target.getHealth();
        s.target.setHealth(1000);
        s.caster.getAttribute(TensuraAttributes.WATER_BOOST).setBaseValue(2);
        hit(s, 100, true);
        helper.assertTrue(1000 - s.target.getHealth() > cold * 1.9, "Tensura water boost must amplify cold damage");
        s.caster.getAttribute(TensuraAttributes.FLAME_BOOST).setBaseValue(1);
        var resistance = ResistanceSkills.HEAT_RESISTANCE.get().createDefaultInstance();
        resistance.setToggled(true);
        SkillAPI.getSkillsFrom(s.target).updateSkill(resistance, true);
        s.target.setHealth(1000);
        hit(s, 100, false);
        helper.assertTrue(1000 - s.target.getHealth() < normal, "Tensura heat resistance must reduce heat damage");
        helper.succeed();
    }
}
