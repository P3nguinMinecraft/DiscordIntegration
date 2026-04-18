package de.erdbeerbaerlp.dcintegration.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import net.dv8tion.jda.api.EmbedBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;

@Mixin(Commands.class)
public class CommandsMixin {
    @Unique
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss", Locale.ROOT);
    @Unique
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);



    @Inject(method = "performCommand", at = @At("HEAD"))
    private void onCommand(ParseResults<CommandSourceStack> command, String commandString, CallbackInfo ci) {
        if (DiscordIntegration.INSTANCE == null) {
            return;
        }

        final int minimumPermissionLevel = getCommandLogMinimumPermissionLevel();
        if (minimumPermissionLevel < 0) {
            return;
        }

        final int requiredPermissionLevel = getRequiredPermissionLevel(command);
        if (requiredPermissionLevel < minimumPermissionLevel) {
            return;
        }

        final String sourceName = command.getContext().getSource().getEntity() instanceof ServerPlayer p ? p.getName().getString() : "Console";
        final String fullCommand = Commands.trimOptionalPrefix(commandString);
        final String commandNoArgs = fullCommand.split(" ")[0];
        final String commandArgs = fullCommand.contains(" ") ? fullCommand.substring(fullCommand.indexOf(' ') + 1).trim() : "";
        final String day_time = LocalDateTime.now().format(DAY_TIME);
        final String time = LocalDateTime.now().format(TIME);

        if (isIgnoredCommand(commandNoArgs)) {
            return;
        }

        final String plainText = applyPlaceholders(getPlainTemplate(), sourceName, requiredPermissionLevel, fullCommand, commandNoArgs, commandArgs, day_time, time)
                .replace("\\n", "\n");
        final String channelId = getCommandLogChannelId();

        final Object commandLogEmbedEntry = getFieldValue(Configuration.instance().embedMode, "commandLogMessage");
        if (Configuration.instance().embedMode.enabled && commandLogEmbedEntry != null && Boolean.TRUE.equals(getFieldValue(commandLogEmbedEntry, "asEmbed"))) {
            final String customJson = String.valueOf(getFieldValue(commandLogEmbedEntry, "customJSON"));
            if (!customJson.isBlank()) {
                final EmbedBuilder b = invokeEmbedBuilder(commandLogEmbedEntry, "toEmbedJson", new Class[]{String.class},
                        new Object[]{applyPlaceholders(customJson, sourceName, requiredPermissionLevel, fullCommand, commandNoArgs, commandArgs, day_time, time)});
                DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()), DiscordIntegration.INSTANCE.getChannel(channelId));
            } else {
                final EmbedBuilder b = invokeEmbedBuilder(commandLogEmbedEntry, "toEmbed", new Class[0], new Object[0])
                        .setDescription(plainText);
                DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()), DiscordIntegration.INSTANCE.getChannel(channelId));
            }
            return;
        }

        DiscordIntegration.INSTANCE.sendMessage(plainText, DiscordIntegration.INSTANCE.getChannel(channelId));
    }

    @Unique
    private static int getRequiredPermissionLevel(ParseResults<CommandSourceStack> command) {
        final List<ParsedCommandNode<CommandSourceStack>> nodes = command.getContext().getNodes();
        for (PermissionLevel level : PermissionLevel.values()) {
            final CommandSourceStack source = Commands.createCompilationContext(LevelBasedPermissionSet.forLevel(level));
            boolean allowed = true;
            for (ParsedCommandNode<CommandSourceStack> node : nodes) {
                if (!node.getNode().getRequirement().test(source)) {
                    allowed = false;
                    break;
                }
            }
            if (allowed) {
                return level.id();
            }
        }
        return PermissionLevel.OWNERS.id();
    }

    @Unique
    private static String applyPlaceholders(String input, String source, int level, String cmd, String cmdNoArgs, String args, String day_time, String time) {
        return input
                .replace("%source%", source)
                .replace("%level%", String.valueOf(level))
                .replace("%cmd%", cmd)
                .replace("%cmd-no-args%", cmdNoArgs)
                .replace("%args%", args)
                .replace("%day_time%", day_time)
                .replace("%time%", time);
    }

    @Unique
    private static int getCommandLogMinimumPermissionLevel() {
        final Object advanced = Configuration.instance().advanced;
        final Object minNew = getFieldValue(advanced, "commandLogMinimumPermissionLevel");
        if (minNew instanceof Integer i) return i;

        final Object minLegacy = getFieldValue(advanced, "minimumPermissionLevelForLogging");
        if (minLegacy instanceof Integer i) return i;

        final String legacyChannel = String.valueOf(getFieldValue(Configuration.instance().commandLog, "channelID"));
        return "0".equals(legacyChannel) ? -1 : 0;
    }

    @Unique
    private static String getCommandLogChannelId() {
        final Object advancedChannel = getFieldValue(Configuration.instance().advanced, "commandLogChannelID");
        if (advancedChannel instanceof String s && !s.isBlank()) return s;

        final Object legacyChannel = getFieldValue(Configuration.instance().commandLog, "channelID");
        if (legacyChannel instanceof String s && !s.isBlank()) return s;
        return "default";
    }

    @Unique
    private static String getPlainTemplate() {
        final Object localizationTemplate = getFieldValue(Localization.instance(), "commandLogMessage");
        if (localizationTemplate instanceof String s && !s.isBlank()) return s;

        final Object legacyTemplate = getFieldValue(Configuration.instance().commandLog, "message");
        if (legacyTemplate instanceof String s && !s.isBlank()) return s;
        return "%source% executed command `%cmd%`";
    }

    @Unique
    private static boolean isIgnoredCommand(String commandNoArgs) {
        final Object commandLogCfg = Configuration.instance().commandLog;
        final Object ignored = getFieldValue(commandLogCfg, "ignoredCommands");
        final Object whitelist = getFieldValue(commandLogCfg, "commandWhitelist");

        if (!(ignored instanceof String[] ignoredCommands) || !(whitelist instanceof Boolean isWhitelist)) {
            return false;
        }

        final boolean listed = ArrayUtils.contains(ignoredCommands, commandNoArgs);
        return isWhitelist != listed;
    }

    @Unique
    private static Object getFieldValue(Object target, String fieldName) {
        try {
            final Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static EmbedBuilder invokeEmbedBuilder(Object target, String methodName, Class<?>[] signature, Object[] args) {
        try {
            final Method method = target.getClass().getMethod(methodName, signature);
            return (EmbedBuilder) method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to build command-log embed", e);
        }
    }
}
