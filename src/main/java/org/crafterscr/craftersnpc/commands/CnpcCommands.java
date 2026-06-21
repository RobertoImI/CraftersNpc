package org.crafterscr.craftersnpc.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class CnpcCommands {
    private CnpcCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cnpc")
            .requires(source -> source.hasPermission(2))
            .then(NpcManagementCommands.register())
            .then(NpcManagementCommands.registerSkinLooked())
            .then(NpcManagementCommands.registerEdit())
            .then(Commands.literal("npc")
                .then(NpcDialogueCommands.register())
                .then(NpcManagementCommands.registerNpcSkin())
                .then(NpcRouteCommands.registerNpcRouteAssignment())
                .then(NpcScheduleCommands.register())
                .then(NpcSettingsCommands.registerTemperament())
                .then(NpcSettingsCommands.registerSpeed())
                .then(NpcManagementCommands.registerDebug())
                .then(NpcManagementCommands.registerUnstick())
                .then(NpcSettingsCommands.registerNightMode())
                .then(NpcSettingsCommands.registerNightRefuge())
                .then(NpcManagementCommands.registerExport())
                .then(NpcManagementCommands.registerImport())
                .then(NpcManagementCommands.registerRemove())
                .then(NpcManagementCommands.registerList())
                .then(NpcSettingsCommands.registerDamage()))
            .then(NpcRouteCommands.registerRouteCommands())
            .then(NpcRouteCommands.registerWandCommands()));
    }
}
