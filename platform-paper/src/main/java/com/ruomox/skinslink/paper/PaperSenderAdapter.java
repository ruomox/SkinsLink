package com.ruomox.skinslink.paper;

import com.ruomox.skinslink.api.platform.SenderAdapter;
import com.ruomox.skinslink.core.api.CommandBridge;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class PaperSenderAdapter implements SenderAdapter<CommandSender> {
    @Override
    public CommandBridge.CommandSender wrap(CommandSender sender) {
        return new CommandBridge.CommandSender() {
            @Override
            public void sendMessage(String message) {
                sender.sendMessage(message);
            }

            @Override
            public boolean hasPermission(String permission) {
                return sender.hasPermission(permission);
            }

            @Override
            public boolean isConsole() {
                return sender instanceof ConsoleCommandSender;
            }

            @Override
            public java.util.UUID getUUID() {
                return (sender instanceof Player p) ? p.getUniqueId() : null;
            }

            @Override
            public String getName() {
                return sender.getName();
            }
        };
    }
}