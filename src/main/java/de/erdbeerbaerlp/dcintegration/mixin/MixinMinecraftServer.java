package de.erdbeerbaerlp.dcintegration.mixin;

import de.erdbeerbaerlp.dcintegration.metrics.Metrics;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

import static de.erdbeerbaerlp.dcintegration.DiscordIntegrationMod.bstats;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {

        Metrics.capturedServer.set((MinecraftServer) (Object) this);
        bstats = new Metrics(9765);
        bstats.addCustomChart(new Metrics.SimplePie("webhook_mode", () -> Configuration.instance().webhook.enable ? "Enabled" : "Disabled"));
        bstats.addCustomChart(new Metrics.SimplePie("command_log", () -> isCommandLogEnabled() ? "Enabled" : "Disabled"));
        bstats.addCustomChart(new Metrics.SimplePie("loader",()->Metrics.capturedServer.get().getServerModName()));

    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onShutdown(CallbackInfo info) {
        Metrics.capturedServer.compareAndSet((MinecraftServer) (Object) this, null);
    }

    @Unique
    private static boolean isCommandLogEnabled() {
        final Object advanced = Configuration.instance().advanced;
        final Integer newValue = getIntField(advanced, "commandLogMinimumPermissionLevel");
        if (newValue != null) {
            return newValue >= 0;
        }

        final Integer legacyValue = getIntField(advanced, "minimumPermissionLevelForLogging");
        if (legacyValue != null) {
            return legacyValue >= 0;
        }

        return !"0".equals(Configuration.instance().commandLog.channelID);
    }

    @Unique
    private static Integer getIntField(Object target, String fieldName) {
        try {
            final Field field = target.getClass().getField(fieldName);
            return (Integer) field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

}