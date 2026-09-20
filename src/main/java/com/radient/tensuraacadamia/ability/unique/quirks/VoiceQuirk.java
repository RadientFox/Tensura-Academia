package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.jetbrains.annotations.Nullable;

/** A chat-powered sound quirk with two forward sonic-cannon attacks. */
public final class VoiceQuirk extends Skill {
    private static final int MAX_MASTERY = 5_000;
    private static final int MODE_COUNT = 2;
    private static final int MIN_CANNON_WAVES = 4;
    private static final int MAX_CANNON_WAVES = 10;
    private static final double CHAT_RADIUS = 15.0D;
    private static final float MASTERY_MULTIPLIER = 1.5F;
    private static final String LOUD_TAG = "tracadamia_voice_loud";
    private static final String LOUD_CHAT_MESSAGE = "YOOOOOOOOOOOOOOOOOO";
    private static final String LOUD_OUT_CHAT_MESSAGE = "YEAAAAAAHHHHHHHHHHHH";

    public VoiceQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/voice_icon.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        instance.getOrCreateTag().putBoolean(LOUD_TAG, true);
        instance.markDirty();
        if (entity instanceof ServerPlayer player)
            player.displayClientMessage(Component.translatable("tracadamia.skill.voice.loud_on"), true);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        instance.getOrCreateTag().putBoolean(LOUD_TAG, false);
        instance.markDirty();
        if (entity instanceof ServerPlayer player)
            player.displayClientMessage(Component.translatable("tracadamia.skill.voice.loud_off"), true);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_COUNT;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "voice.loud_voice";
            case 1 -> "voice.loud_out_shout";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == 0 ? 500.0D : mode == 1 ? 750.0D : 0.0D;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || mode < 0 || mode >= MODE_COUNT
                || instance.onCoolDown(mode)) return;
        double cost = getAuraCost(player, instance, mode);
        if (!payAura(player, cost)) return;

        if (mode == 0) loudVoice(instance, player);
        else loudOutShout(instance, player);

        // From Birth!: every Voice action grants two points instead of one.
        instance.addMasteryPoint(player);
        instance.addMasteryPoint(player);
        instance.setCoolDown(5, mode);
    }

    /** Loud Voice: a 40 x 5 x 5 forward sonic cannon. */
    private static void loudVoice(ManasSkillInstance instance, ServerPlayer player) {
        fireCannon(instance, player, 40.0D, 5.0D, 5.0D, 200.0F);
        if (instance.isToggled()) forceShout(instance, player, LOUD_CHAT_MESSAGE);
    }

    /** Loud-Out Shout: a wider 50 x 15 x 15 forward sonic cannon. */
    private static void loudOutShout(ManasSkillInstance instance, ServerPlayer player) {
        fireCannon(instance, player, 50.0D, 15.0D, 15.0D, 100.0F);
        if (instance.isToggled()) forceShout(instance, player, LOUD_OUT_CHAT_MESSAGE);
    }

    private static void fireCannon(ManasSkillInstance instance, ServerPlayer owner, double length,
                                   double height, double width, float baseDamage) {
        ServerLevel level = owner.serverLevel();
        Vec3 origin = owner.getEyePosition();
        Vec3 forward = owner.getLookAngle().normalize();
        Vec3 right = horizontalRight(forward);
        boolean mastered = instance.isMastered(owner);
        float damage = baseDamage * (mastered ? MASTERY_MULTIPLIER : 1.0F);
        AABB candidates = owner.getBoundingBox().expandTowards(forward.scale(length))
                .inflate(width * 0.5D, height * 0.5D, width * 0.5D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, candidates,
                target -> target != owner && target.isAlive())) {
            if (insideCannon(target.getBoundingBox(), origin, forward, right, length, height, width))
                soundDamage(level, owner, target, damage);
        }
        renderCannon(level, origin, forward, length, Math.max(height, width));
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 2.2F, height > 10.0D ? 0.82F : 1.05F);
    }

    private static boolean insideCannon(AABB bounds, Vec3 origin, Vec3 forward, Vec3 right,
                                        double length, double height, double width) {
        Vec3 relative = bounds.getCenter().subtract(origin);
        double forwardDistance = relative.dot(forward);
        double sideways = Math.abs(relative.dot(right));
        double horizontalExtent = Math.abs(forward.x) * bounds.getXsize() * 0.5D
                + Math.abs(forward.z) * bounds.getZsize() * 0.5D;
        double lateralExtent = Math.abs(right.x) * bounds.getXsize() * 0.5D
                + Math.abs(right.z) * bounds.getZsize() * 0.5D;
        return forwardDistance + horizontalExtent >= 0.0D && forwardDistance - horizontalExtent <= length
                && sideways <= width * 0.5D + lateralExtent
                && bounds.maxY >= origin.y - height * 0.5D && bounds.minY <= origin.y + height * 0.5D;
    }

    private static void renderCannon(ServerLevel level, Vec3 origin, Vec3 forward, double length, double diameter) {
        // A readable series of actual Warden waves. Spawning one billboard for every
        // block at the same instant makes them overlap into a solid cone at Voice's
        // 40- and 50-block ranges.
        int waves = Math.min(MAX_CANNON_WAVES, Math.max(MIN_CANNON_WAVES, (int) Math.ceil(length / 4.0D)));
        SimpleParticleType particle = diameter > 5.0D ? MHAParticles.VOICE_CANNON_WIDE.get()
                : MHAParticles.VOICE_CANNON.get();
        for (int wave = 1; wave <= waves; wave++) {
            double distance = length * wave / (waves + 1.0D);
            Vec3 position = origin.add(forward.scale(distance));
            level.sendParticles(particle, position.x, position.y, position.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onChatMessage(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        var voice = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.VOICE.get().getRegistryName());
        if (voice.isEmpty()) return;
        ManasSkillInstance instance = voice.get();
        if (!instance.isToggled() && !instance.getOrCreateTag().getBoolean(LOUD_TAG)) return;

        // Raw text is the actual character sequence the player sent. The formatted
        // chat component may be replaced or filtered by other server chat handlers.
        String message = event.getRawText();
        if (message.isEmpty() || message.equals(LOUD_CHAT_MESSAGE) || message.equals(LOUD_OUT_CHAT_MESSAGE)) return;
        applyChatDamage(player, instance, message);
    }

    private static void applyChatDamage(ServerPlayer player, ManasSkillInstance instance, String message) {
        int characters = message.length();
        if (!payAura(player, characters)) return;
        float damage = characters / 5.0F * (instance.isMastered(player) ? MASTERY_MULTIPLIER : 1.0F);
        ServerLevel level = player.serverLevel();
        Vec3 position = player.position();
        AABB area = new AABB(position, position).inflate(CHAT_RADIUS);
        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                target -> target != player && target.isAlive())) {
            soundDamage(level, player, target, damage);
            hits++;
        }
        instance.addMasteryPoint(player);
        instance.addMasteryPoint(player);
        player.displayClientMessage(Component.literal("Loud! " + Math.round(damage * 10.0F) / 10.0F
                + " sound damage to " + hits + " target(s)."), true);
    }

    private static boolean payAura(ServerPlayer player, double cost) {
        var existence = TensuraStorages.getExistenceFrom(player);
        if (existence.getAura() < cost) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.voice.not_enough_aura"), true);
            return false;
        }
        existence.setAura(existence.getAura() - cost);
        existence.markDirty();
        return true;
    }

    private static void soundDamage(ServerLevel level, ServerPlayer owner, LivingEntity target, float amount) {
        DamageSource source = level.damageSources().source(DamageTypes.SONIC_BOOM, owner);
        ((TensuraDamageSource) source).tensura$setSkillType(SkillType.UNIQUE);
        target.invulnerableTime = 0;
        target.hurt(source, amount);
        Vec3 push = target.position().subtract(owner.position()).normalize();
        target.push(push.x * 1.5D, 0.35D, push.z * 1.5D);
        target.hurtMarked = true;
    }

    private static Vec3 horizontalRight(Vec3 forward) {
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return right.lengthSqr() < 1.0E-5D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
    }

    private static void forceShout(ManasSkillInstance instance, ServerPlayer player, String message) {
        player.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + player.getName().getString() + "> " + message), false);
        // Server-authored chat does not raise ServerChatEvent, so apply Loud! here.
        applyChatDamage(player, instance, message);
    }
}
