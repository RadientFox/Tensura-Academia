package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.regestry.OFAEntitys;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.data.TensuraBlockTags;
import io.github.manasmods.tensura.entity.magic.beam.BeamProjectile;
import io.github.manasmods.tensura.entity.projectile.WebBulletProjectile;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.item.weapon.ranged.WebCartridgeItem;
import io.github.manasmods.tensura.item.weapon.ranged.WebGunItem;
import io.github.manasmods.tensura.registry.block.TensuraBlocks;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.entity.MonsterEntityTypes;
import io.github.manasmods.tensura.registry.item.TensuraToolItems;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public  class BlackwhipProjectile extends AbstractArrow {
    private static final EntityDataAccessor<Boolean> SLINGER;
    private static final EntityDataAccessor<ItemStack> SOURCE_ITEM;
    private static final EntityDataAccessor<ItemStack> AMMO;

    public BlackwhipProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    public BlackwhipProjectile(Level pLevel, double pX, double pY, double pZ) {
        super((EntityType) OFAEntitys.BLACKWHIP_PROJECTILE.get(), pLevel);
        this.setPos(pX, pY, pZ);
    }


    public BlackwhipProjectile(Level worldIn, LivingEntity shooter, boolean right, ItemStack sourceItem, ItemStack ammo) {
        this((EntityType)OFAEntitys.BLACKWHIP_PROJECTILE.get(), worldIn);
        this.setOwner(shooter);
        this.setSourceItem(sourceItem.copy());
        this.setAmmo(ammo);
        float rot = shooter.yHeadRot + (float)(right ? 60 : -60);
        this.setPos(shooter.getX() - (double)shooter.getBbWidth() * 0.5 * (double)Mth.sin(rot * 0.017453292F), shooter.getEyeY() - 0.20000000298023224, shooter.getZ() + (double)shooter.getBbWidth() * 0.5 * (double)Mth.cos(rot * 0.017453292F));
    }

    public BlackwhipProjectile(Level worldIn, LivingEntity shooter, boolean right, ItemStack ammo) {
        this((EntityType)OFAEntitys.BLACKWHIP_PROJECTILE.get(), worldIn);
        this.setOwner(shooter);
        this.setAmmo(ammo);
        float rot = shooter.yHeadRot + (float)(right ? 60 : -60);
        this.setPos(shooter.getX() - (double)shooter.getBbWidth() * 0.5 * (double)Mth.sin(rot * 0.017453292F), shooter.getEyeY() - 0.20000000298023224, shooter.getZ() + (double)shooter.getBbWidth() * 0.5 * (double)Mth.cos(rot * 0.017453292F));
    }

    public void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLINGER, false);
        builder.define(SOURCE_ITEM, ((WebGunItem) TensuraToolItems.WEB_GUN.get()).getDefaultInstance());
        builder.define(AMMO, this.getDefaultPickupItem());
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Slinger", this.isSlinger());
        compound.put("SourceItem", this.getSourceItem().save(this.registryAccess(), new CompoundTag()));
        compound.put("Ammo", this.getAmmo().save(this.registryAccess(), new CompoundTag()));
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setSlinger(compound.getBoolean("Slinger"));
        if (compound.contains("SourceItem", 10)) {
            this.setSourceItem(ItemStack.parseOptional(this.registryAccess(), compound.getCompound("SourceItem")));
        }

        if (compound.contains("Ammo", 10)) {
            this.setAmmo(ItemStack.parseOptional(this.registryAccess(), compound.getCompound("Ammo")));
        }

    }

    public ItemStack getSourceItem() {
        return (ItemStack)this.entityData.get(SOURCE_ITEM);
    }

    public void setSourceItem(ItemStack pStack) {
        this.entityData.set(SOURCE_ITEM, pStack);
    }

    public ItemStack getAmmo() {
        return (ItemStack)this.entityData.get(AMMO);
    }

    public void setAmmo(ItemStack pStack) {
        this.entityData.set(AMMO, pStack);
    }

    public boolean isSlinger() {
        return (Boolean)this.entityData.get(SLINGER);
    }

    public void setSlinger(boolean saddled) {
        this.entityData.set(SLINGER, saddled);
    }

    public @NotNull ItemStack getPickupItem() {
        return this.getAmmo();
    }

    public @NotNull ItemStack getPickupItemStackOrigin() {
        return this.getAmmo();
    }

    protected @NotNull ItemStack getDefaultPickupItem() {
        return ((Item)TensuraToolItems.WEB_CARTRIDGE.get()).getDefaultInstance();
    }

    public boolean isInGround() {
        return this.inGround;
    }

    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        Vec3 vector3d = (new Vec3(x, y, z)).normalize().add(this.random.nextGaussian() * 0.007499999832361937 * (double)inaccuracy, this.random.nextGaussian() * 0.007499999832361937 * (double)inaccuracy, this.random.nextGaussian() * 0.007499999832361937 * (double)inaccuracy).scale((double)velocity);
        this.setDeltaMovement(vector3d);
        float f = Mth.sqrt((float)(vector3d.x * vector3d.x + vector3d.z * vector3d.z));
        this.setYRot((float)(Mth.atan2(vector3d.x, vector3d.z) * 57.2957763671875));
        this.setXRot((float)(Mth.atan2(vector3d.y, (double)f) * 57.2957763671875));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void tick() {
        super.tick();
        if (this.isInLava()) {
            this.remove(RemovalReason.DISCARDED);
        }

        if (this.isSlinger()) {
            Entity entity = this.getOwner();
            if (entity instanceof LivingEntity) {
                LivingEntity owner = (LivingEntity)entity;
                if (!entity.isRemoved() && entity.isAlive()) {
                    Entity vehicle = this.getVehicle();
                    double f;
                    double d0;
                    double d1;
                    double d2;
                    if (vehicle != null) {
                        f = (double)vehicle.distanceTo(owner);
                        if (f > 30.0) {
                            this.discard();
                            return;
                        }

                        if (this.canPull(vehicle, owner)) {
                            if (owner.getY() + 5.0 > vehicle.getY()) {
                                vehicle.resetFallDistance();
                            }

                            if (f > 10.0 || owner.isShiftKeyDown() && f > 2.0) {
                                d0 = (entity.getX() - vehicle.getX()) / f;
                                d1 = (entity.getY() - vehicle.getY()) / f;
                                d2 = (entity.getZ() - vehicle.getZ()) / f;
                                vehicle.setDeltaMovement(vehicle.getDeltaMovement().add(Math.copySign(d0 * d0 * 0.2, d0), Math.copySign(d1 * d1 * 0.2, d1), Math.copySign(d2 * d2 * 0.2, d2)));
                                vehicle.hurtMarked = true;
                                return;
                            }
                        } else {
                            if (owner.getY() <= vehicle.getY()) {
                                owner.resetFallDistance();
                            }

                            if (f > 10.0 || owner.isShiftKeyDown() && f > 2.0) {
                                d0 = (vehicle.getX() - owner.getX()) / f;
                                d1 = (vehicle.getY() - owner.getY()) / f;
                                d2 = (vehicle.getZ() - owner.getZ()) / f;
                                owner.setDeltaMovement(owner.getDeltaMovement().add(Math.copySign(d0 * d0 * 0.2, d0), Math.copySign(d1 * d1 * 0.2, d1), Math.copySign(d2 * d2 * 0.2, d2)));
                                owner.hurtMarked = true;
                                return;
                            }
                        }

                        return;
                    } else {
                        if (this.isInGround()) {
                            f = (double)owner.distanceTo(this);
                            if (owner.isShiftKeyDown()) {
                                d0 = (this.getX() - entity.getX()) / f;
                                d1 = (this.getY() - entity.getY()) / f;
                                d2 = (this.getZ() - entity.getZ()) / f;
                                entity.setDeltaMovement(entity.getDeltaMovement().add(Math.copySign(d0 * d0 * 0.25, d0), Math.copySign(d1 * d1 * 0.25, d1), Math.copySign(d2 * d2 * 0.25, d2)));
                                entity.hurtMarked = true;
                            }

                            if (f > 50.0 || f <= 1.0 && owner.getY() > this.getY()) {
                                this.discard();
                            }

                            if (owner.getY() < this.getY() - 5.0) {
                                owner.resetFallDistance();
                            }

                            return;
                        }

                        return;
                    }
                }
            }

            this.discard();
        }
    }

    private boolean canPull(Entity entity, LivingEntity owner) {
        if (owner.hasInfiniteMaterials()) {
            return true;
        } else {
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity)entity;
                if (TensuraStorages.getExistenceFrom(living).getEP() > TensuraStorages.getExistenceFrom(owner).getEP()) {
                    return false;
                }
            }

            float entitySize = Math.max(entity.getBbHeight(), entity.getBbWidth());
            float ownerSize = Math.max(owner.getBbHeight(), owner.getBbWidth());
            return entitySize < ownerSize * 3.0F;
        }
    }

    public void playerTouch(Player pEntity) {
    }

    public void recreateFromPacket(ClientboundAddEntityPacket pPacket) {
        super.recreateFromPacket(pPacket);
        double d0 = pPacket.getXa();
        double d1 = pPacket.getYa();
        double d2 = pPacket.getZa();

        for(int i = 0; i < 12; ++i) {
            double d3 = 0.4 + 0.1 * (double)i;
            this.level().addParticle(ParticleTypes.SPIT, this.getX(), this.getY(), this.getZ(), d0 * d3, d1, d2 * d3);
        }

        this.setDeltaMovement(d0, d1, d2);
    }

    protected void onHitEntity(EntityHitResult pResult) {
        Entity entity = pResult.getEntity();
        if (this.isSlinger() && entity instanceof LivingEntity target) {
            TensuraDamageHelper.markHurt(target, this.getOwner());
            this.inGround = true;
            this.startRiding(entity, true);
        } else {
            if (entity instanceof LivingEntity target) {
                TensuraDamageHelper.markHurt(target, this.getOwner());
                Item var5 = this.getAmmo().getItem();
                if (!(var5 instanceof WebCartridgeItem)) {
                    return;
                }

                WebCartridgeItem webCartridgeItem = (WebCartridgeItem)var5;
                if (target.getBbHeight() <= 3.0F || target.getBbWidth() <= 3.0F) {
                    boolean var10000;
                    label31: {
                        Entity var7 = this.getOwner();
                        if (var7 instanceof LivingEntity) {
                            LivingEntity owner = (LivingEntity)var7;
                            if (EnergyHelper.getMaxEP(target) > EnergyHelper.getMaxEP(owner)) {
                                var10000 = true;
                                break label31;
                            }
                        }

                        var10000 = false;
                    }

                    boolean epHigh = var10000;
                    if (!epHigh) {
                        MobEffectInstance webbed = new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.WEBBED), webCartridgeItem.getWebbedDuration(), 0, true, false, true);
                        target.addEffect(webbed, this.getOwner());
                    }

                    if ((double)target.getRandom().nextFloat() <= webCartridgeItem.getSilenceChance()) {
                        target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.SILENCE), webCartridgeItem.getSilenceDuration(), 0, true, false, true), this.getOwner());
                    }
                }
            }

            this.discard();
            this.playSound(SoundEvents.WOOL_BREAK, 0.5F, 0.75F);
        }
    }

    protected void onHitBlock(BlockHitResult pResult) {
        super.onHitBlock(pResult);
        if (!this.level().isClientSide) {
            if (this.shouldPlaceWeb()) {
                this.placeWeb(this);
                this.remove(RemovalReason.DISCARDED);
                this.playSound(SoundEvents.WOOL_BREAK, 0.1F, 0.1F);
            } else if (!this.isSlinger()) {
                this.remove(RemovalReason.DISCARDED);
            }
        } else {
            this.level().addParticle(ParticleTypes.WHITE_ASH, this.getX(), this.getY(), this.getZ(), 0.0, 0.05, 0.0);
        }

    }

    private boolean shouldPlaceWeb() {
        if (this.getOwner() != null) {
            if (this.getOwner().getType().equals(MonsterEntityTypes.HELL_CATERPILLAR.get())) {
                return false;
            }

            if (this.getOwner().getType().equals(MonsterEntityTypes.HELL_MOTH.get())) {
                return false;
            }
        }

        return !this.isSlinger();
    }

    protected boolean shouldGrief() {
        return this.getOwner() != null && !this.getOwner().getType().equals(EntityType.PLAYER) ? this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) : TensuraGameRules.canSkillGrief(this.level());
    }

    protected void placeWeb(Entity entity) {
        if (this.shouldGrief()) {
            Item var3 = this.getAmmo().getItem();
            if (var3 instanceof WebCartridgeItem) {
                WebCartridgeItem webCartridgeItem = (WebCartridgeItem)var3;
                int yPos = Mth.floor(entity.getY()) - 1;
                int xPos = Mth.floor(entity.getX());
                int zPos = Mth.floor(entity.getZ());
                boolean destroyBlock = false;
                boolean placeWeb = false;
                boolean webbedStones = false;

                for(int j = -1; j <= 1; ++j) {
                    for(int k = -1; k <= 1; ++k) {
                        for(int i = -1; i <= 2; ++i) {
                            int newYPos = yPos + i;
                            int newXPos = xPos;
                            int newZPos = zPos;
                            if (i == 1 || i == 0) {
                                newXPos = xPos + j;
                                newZPos = zPos + k;
                            }

                            BlockPos blockpos = new BlockPos(newXPos, newYPos, newZPos);
                            BlockState blockstate = this.level().getBlockState(blockpos);
                            if ((k != j && k != -j || j == 0) && !((TensuraSkillEvents.SkillGriefEvent)TensuraSkillEvents.SKILL_GRIEF_PRE.invoker()).grief((ManasSkillInstance)null, this.level(), this.getOwner(), (double)newXPos, (double)newYPos, (double)newZPos).isFalse()) {
                                BlockState webbedStone;
                                if (blockstate.isAir() || blockstate.is(TensuraBlockTags.WEB_REPLACEABLE)) {
                                    webbedStone = webCartridgeItem.getWebBlock().defaultBlockState();
                                    destroyBlock = this.level().destroyBlock(blockpos, true, this) || destroyBlock;
                                    placeWeb = this.level().setBlockAndUpdate(blockpos, webbedStone) || placeWeb;
                                    this.level().scheduleTick(blockpos, webbedStone.getBlock(), webCartridgeItem.getDissolvingDuration());
                                }

                                if (this.getAmmo().is((Item)TensuraToolItems.WEB_CARTRIDGE.get()) && blockstate.is(TensuraBlockTags.WEBBED_AVAILABLE)) {
                                    webbedStone = ((Block) TensuraBlocks.WEBBED_COBBLESTONE.get()).defaultBlockState();
                                    if (blockstate.is(Blocks.STONE_BRICKS)) {
                                        webbedStone = ((Block)TensuraBlocks.WEBBED_STONE_BRICKS.get()).defaultBlockState();
                                    }

                                    webbedStones = this.level().setBlockAndUpdate(blockpos, webbedStone) || webbedStones;
                                }

                                ((TensuraSkillEvents.SkillGriefEvent)TensuraSkillEvents.SKILL_GRIEF_POS.invoker()).grief((ManasSkillInstance)null, this.level(), this.getOwner(), (double)newXPos, (double)newYPos, (double)newZPos);
                            }
                        }
                    }
                }

                if (destroyBlock) {
                    this.level().playSound((Player)null, this.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.2F, 1.0F);
                    if (this.getOwner() != null) {
                        this.level().gameEvent(this.getOwner(), GameEvent.BLOCK_DESTROY, this.blockPosition());
                    }
                }

                if (placeWeb || webbedStones) {
                    this.level().playSound((Player)null, this.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.2F, 1.0F);
                    if (this.getOwner() != null) {
                        this.level().gameEvent(this.getOwner(), GameEvent.BLOCK_CHANGE, this.blockPosition());
                    }
                }

            }
        }
    }

    static {
        SLINGER = SynchedEntityData.defineId(WebBulletProjectile.class, EntityDataSerializers.BOOLEAN);
        SOURCE_ITEM = SynchedEntityData.defineId(WebBulletProjectile.class, EntityDataSerializers.ITEM_STACK);
        AMMO = SynchedEntityData.defineId(WebBulletProjectile.class, EntityDataSerializers.ITEM_STACK);
    }
}




