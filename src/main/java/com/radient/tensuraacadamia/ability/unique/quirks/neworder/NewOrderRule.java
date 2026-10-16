package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

// New Order's rules
public enum NewOrderRule {
    HARDEN(NewOrderRule.AIR, 0),
    AIR_CONTROL(NewOrderRule.AIR, 0),
    ENHANCE(NewOrderRule.USER, 0),
    SOUL(NewOrderRule.USER, 0),
    DEGRADE(NewOrderRule.USER, 0),
    REBEL(NewOrderRule.USER, 0),
    REFLECT(NewOrderRule.USER, 0),
    FLY(NewOrderRule.USER, 0),
    WATER_WALK(NewOrderRule.USER, 0),
    UNSEEN(NewOrderRule.USER, 0),
    SEE_ALL(NewOrderRule.USER, 0),
    REPEL(NewOrderRule.USER, 0),
    FLAMES(NewOrderRule.USER, 0),
    GRAVITY(NewOrderRule.USER, 0),
    IMMOVABLE(NewOrderRule.USER, 0),
    HEAL(NewOrderRule.USER, 0),
    CLEAR_MIND(NewOrderRule.USER, 0),
    NO_AGGRO(NewOrderRule.USER, 0),
    NO_QUIRKS(NewOrderRule.ENTITY, NewOrderSeals.SEAL_QUIRKS),
    NO_UNIQUE(NewOrderRule.ENTITY, NewOrderSeals.SEAL_UNIQUE),
    NO_ULTIMATE(NewOrderRule.ENTITY, NewOrderSeals.SEAL_ULTIMATE),
    NO_RECOVER(NewOrderRule.ENTITY, NewOrderSeals.SEAL_RECOVER),
    WEAKNESS(NewOrderRule.ENTITY, NewOrderSeals.SEAL_WEAKEN),
    CRUMBLE(NewOrderRule.ENTITY, NewOrderSeals.SEAL_CRUMBLE),
    BLINDNESS(NewOrderRule.ENTITY, NewOrderSeals.SEAL_BLIND),
    WEIGHTLESS(NewOrderRule.ENTITY, NewOrderSeals.SEAL_WEIGHTLESS),
    GROUNDED(NewOrderRule.ENTITY, NewOrderSeals.SEAL_GROUNDED),
    ANTI_TELEPORT(NewOrderRule.ENTITY, NewOrderSeals.SEAL_NO_TELEPORT),
    NO_MOVE(NewOrderRule.ENTITY, NewOrderSeals.SEAL_NO_MOVE),
    NO_SPEAK(NewOrderRule.ENTITY, NewOrderSeals.SEAL_SILENCE),
    NO_ITEMS(NewOrderRule.ENTITY, NewOrderSeals.SEAL_NO_ITEMS),
    WILL_DIE(NewOrderRule.ENTITY, 0),
    SHIELD(NewOrderRule.GROUND, 0),
    FIST(NewOrderRule.GROUND, 0),
    SLIPPERY(NewOrderRule.GROUND, 0),
    ELASTIC(NewOrderRule.GROUND, 0),
    NO_FALL(NewOrderRule.GROUND, 0),
    SPIKY(NewOrderRule.GROUND, 0);



    // What was named
    public static final int NONE = -1;
    public static final int AIR = 0;
    public static final int USER = 1;
    public static final int ENTITY = 2;
    public static final int GROUND = 3;
    public static final int KINDS = 4;
    private static final int[] COLORS = {0xD8F3FF, 0x7FDBFF, 0xFF6B6B, 0xE0B070};
    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private final int kind;
    private final int seal;

    NewOrderRule(int kind, int seal) {
        this.kind = kind;
        this.seal = seal;
    }

    public int getKind() {
        return this.kind;
    }

    int getSeal() {
        return this.seal;
    }

    public int getColor() {
        return COLORS[this.kind];
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent getName() {
        return Component.translatable("tracadamia.skill.new_order.rule." + getId());
    }

    public MutableComponent getDescription() {
        return Component.translatable("tracadamia.skill.new_order.rule." + getId() + ".desc", getDescriptionArgs());
    }

    private Object[] getDescriptionArgs() {
        return switch (this) {
            case ENHANCE -> new Object[]{number(CONFIG.enhanceAttackDamage), number(CONFIG.enhanceArmor), percent(CONFIG.enhanceRuleDamagePercent), percent(CONFIG.enhanceBodyDamageReduction)};
            case SOUL -> new Object[]{percent(1.0D - CONFIG.soulDamageMultiplier)};
            case REBEL -> new Object[]{CONFIG.rebelSeconds};
            case UNSEEN -> new Object[]{number(CONFIG.unseenConcealment)};
            case SEE_ALL -> new Object[]{number(CONFIG.seeAllPresenceSense)};
            case HEAL -> new Object[]{percent(CONFIG.healCostReduction)};
            case WEAKNESS -> new Object[]{percent(CONFIG.weaknessDamageReduction)};
            case CRUMBLE -> new Object[]{percent(CONFIG.crumbleArmorReduction)};
            case NO_MOVE -> new Object[]{CONFIG.noMoveSeconds};
            case WILL_DIE -> new Object[]{number(CONFIG.willDieEpMultiplier)};
            case SHIELD -> new Object[]{number(CONFIG.shieldRange)};
            case FIST -> new Object[]{percent(CONFIG.fistDamageMultiplier)};
            case HARDEN -> new Object[]{percent(CONFIG.hardenDamageMultiplier)};
            case AIR_CONTROL -> new Object[]{CONFIG.restrainSeconds, number(CONFIG.explodeSize), number(CONFIG.explodeDamage), number(CONFIG.protectHealth), number(CONFIG.protectHealthMastered),
                    CONFIG.weaponArms, percent(CONFIG.weaponDamagePercent)};
            case SLIPPERY -> new Object[]{number(CONFIG.slipperySize)};
            case ELASTIC -> new Object[]{number(CONFIG.elasticSize)};
            case NO_FALL -> new Object[]{number(CONFIG.noFallSize)};
            case SPIKY -> new Object[]{number(CONFIG.spikyDamage), CONFIG.spikyCountMastered, number(CONFIG.spikyDamageMastered)};
            default -> new Object[0];
        };
    }

    static String number(double value) {
        return NUMBER.format(value);
    }

    static String percent(double share) {
        return NUMBER.format(share * 100.0D);
    }

    public static @Nullable NewOrderRule byId(String id) {
        for (NewOrderRule rule : values()) {
            if (rule.getId().equals(id)) {
                return rule;
            }
        }
        return null;
    }

    public static @Nullable NewOrderRule byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : null;
    }

}
