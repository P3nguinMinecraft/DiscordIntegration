package de.erdbeerbaerlp.dcintegration.util.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static de.erdbeerbaerlp.dcintegration.DiscordIntegrationMod.server;

public class ServerInterfaceImpl {
    public static String getLoaderNameX() {
        return "Fabric";
    }

    public static String getLoaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader").get().getMetadata().getVersion().getFriendlyString() + " (MC: " + FabricLoader.getInstance().getModContainer("minecraft").get().getMetadata().getVersion().getFriendlyString() + ")";
    }

    public static boolean playerHasPermissionsX(UUID player, String... permissions) {
        if (server == null) {
            return false;
        }
        final ServerPlayer target = server.getPlayerList().getPlayer(player);
        if (target == null) {
            return false;
        }
        return playerHasPermissionsX(target, permissions);
    }

    // Try Fabric Permissions API reflectively, then fall back to vanilla operator level.
    private static boolean hasPermission(ServerPlayer player, String permission) {
        final boolean vanillaFallback = false; //player.hasPermissions(2);
        try {
            final Class<?> perms = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            for (Method method : perms.getMethods()) {
                if (!method.getName().equals("check") || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 3) {
                    continue;
                }

                final Class<?> firstParam = method.getParameterTypes()[0];
                if (firstParam.isInstance(player)) {
                    return (boolean) method.invoke(null, player, permission, vanillaFallback);
                }
                if (firstParam.isInstance(player.createCommandSourceStack())) {
                    return (boolean) method.invoke(null, player.createCommandSourceStack(), permission, vanillaFallback);
                }
            }
        } catch (Throwable ignored) {
        }
        return vanillaFallback;
    }

    public static boolean playerHasPermissionsX(Player player, String... permissions) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        for (String permission : permissions) {
            if (!hasPermission(serverPlayer, permission)) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkVanish(UUID player) {
        if (server == null) {
            return false;
        }
        final ServerPlayer target = server.getPlayerList().getPlayer(player);
        if (target == null) {
            return false;
        }

        final String[] apiClasses = {
                "de.myzelyam.vanish.api.VanishAPI",
                "de.myzelyam.api.vanish.VanishAPI",
                "org.kilocraft.essentials.api.vanish.VanishApi"
        };
        final String[] methodNames = {"isVanished", "isInvisible", "isInvisibleOffline"};

        for (String className : apiClasses) {
            try {
                final Class<?> api = Class.forName(className);
                for (String methodName : methodNames) {
                    for (Method method : api.getMethods()) {
                        if (!method.getName().equals(methodName) || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                            continue;
                        }
                        final Class<?> param = method.getParameterTypes()[0];
                        if (param.isAssignableFrom(UUID.class)) {
                            return (boolean) method.invoke(null, player);
                        }
                        if (param.isInstance(target)) {
                            return (boolean) method.invoke(null, target);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
