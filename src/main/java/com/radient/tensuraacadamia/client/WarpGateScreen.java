package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.ability.unique.quirks.WarpGateQuirk;
import com.radient.tensuraacadamia.network.WarpGateDestinationPayload;
import com.radient.tensuraacadamia.network.WarpGateOpenPayload;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.tensura.client.screen.SpatialMovementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class WarpGateScreen extends SpatialMovementScreen<WarpGateQuirk> {
    private final long token;

    private WarpGateScreen(long token, List<String> dimensions) {
        super(QuirkSkills.WARP_GATE.get(), dimensions);
        this.token = token;
    }

    public static void open(WarpGateOpenPayload payload) {
        Minecraft.getInstance().setScreen(new WarpGateScreen(payload.token(), payload.dimensions()));
    }

    @Override protected void performAction(int action) {
        if (action != 4) {
            if (action <= 3) super.performAction(action);
            return;
        }
        Double x = getFieldValue(fieldX);
        Double y = getFieldValue(fieldY);
        Double z = getFieldValue(fieldZ);
        if (x == null || y == null || z == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
            player.displayClientMessage(Component.literal("Enter valid Gate coordinates."), true);
            return;
        }
        String dimension = getDimensionLocation(fieldD.getValue());
        if (dimension.isEmpty()) dimension = player.level().dimension().location().toString();
        PacketDistributor.sendToServer(new WarpGateDestinationPayload(token, dimension,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        minecraft.setScreen(null);
    }
}
