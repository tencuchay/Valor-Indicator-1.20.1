package com.tencuchay.valorindicator.network;

import com.tencuchay.valorindicator.client.IndicatorManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client. Position is where the number spawns (top of the entity's hitbox).
 * typeId is the damage type registry id (e.g. "minecraft:on_fire"), or "heal".
 * hud = true: a number for the receiving player's own health bar (position is ignored).
 */
public record IndicatorPacket(double x, double y, double z, float amount,
                              String typeId, boolean crit, boolean heal, boolean hud) {

    public static void encode(IndicatorPacket p, FriendlyByteBuf buf) {
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.amount);
        buf.writeUtf(p.typeId, 128);
        buf.writeBoolean(p.crit);
        buf.writeBoolean(p.heal);
        buf.writeBoolean(p.hud);
    }

    public static IndicatorPacket decode(FriendlyByteBuf buf) {
        return new IndicatorPacket(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(),
                buf.readUtf(128),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(IndicatorPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IndicatorManager.add(p)));
        context.setPacketHandled(true);
    }
}
