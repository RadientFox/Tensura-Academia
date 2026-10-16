package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.WaterGunQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class WaterGunClient {

    private WaterGunClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isPassenger() || player.getAbilities().flying) {
            return;
        }

        CompoundTag tag = WaterGunQuirk.getWaterGun(player).map(instance -> instance.getTag()).orElse(null);
        if (tag != null && tag.getBoolean(WaterGunQuirk.JETTING_TAG)) {
            player.setDeltaMovement(WaterGunQuirk.lift(player.getDeltaMovement(), tag.getDouble(WaterGunQuirk.JET_LIFT_TAG) / 20.0D));
            player.resetFallDistance();
        }
    }

}
