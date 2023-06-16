package org.cubeville.cvvanish;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import org.cubeville.cvvanish.teams.TeamHandler;

public class TeamOverrideCommand extends Command {

    private TeamHandler teamHandler;

    public TeamOverrideCommand(TeamHandler teamHandler) {
        super("teamoverride", "cvvanish.worldteams");
        this.teamHandler = teamHandler;
    }

    public void execute(CommandSender commandSender, String[] args) {
        if(args.length != 2) {
            commandSender.sendMessage(new TextComponent(ChatColor.RED + "Incorrect syntax! Use /teamoverride <key>:<value> player:<player>"));
            commandSender.sendMessage(new TextComponent(ChatColor.RED + "Example: /teamoverride collision:never player:ToeMan_"));
            return;
        }
        String configKeyValue = args[0].toLowerCase();
        String configKey;
        String configValue;
        String playerKeyValue = args[1];
        ProxiedPlayer player;
        if(configKeyValue.equals("collision:never") || configKeyValue.equals("collision:always") || configKeyValue.equals("collision:reset") || configKeyValue.equals("nametags:never") || configKeyValue.equals("nametags:always") || configKeyValue.equals("nametags:reset")) {
            configKey = configKeyValue.substring(0, configKeyValue.indexOf(":"));
            configValue = configKeyValue.substring(configKeyValue.indexOf(":") + 1);
        } else {
            commandSender.sendMessage(new TextComponent(ChatColor.RED + "Please only use nametags and collision with always, never, or reset!"));
            return;
        }
        if(playerKeyValue.contains("player:") && ProxyServer.getInstance().getPlayer(playerKeyValue.substring(playerKeyValue.indexOf(":") + 1)) != null) {
            player = ProxyServer.getInstance().getPlayer(playerKeyValue.substring(playerKeyValue.indexOf(":") + 1));
        } else {
            commandSender.sendMessage(new TextComponent(ChatColor.RED + playerKeyValue.substring(playerKeyValue.indexOf(":") + 1) + " is not online!"));
            return;
        }
        teamHandler.setPlayerTeamConfig(configKey, configValue, player);
        teamHandler.init(player);
        commandSender.sendMessage(new TextComponent(configKey + ":" + configValue + " set for player:" + player.getName()));
    }
}
