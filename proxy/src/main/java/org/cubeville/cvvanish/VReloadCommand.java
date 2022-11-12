package org.cubeville.cvvanish;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class VReloadCommand extends Command {

    private TeamHandler teamHandler;

    public VReloadCommand(TeamHandler teamHandler) {
        super("vreload", "cvvanish.vreload");
        this.teamHandler = teamHandler;
    }

    public void execute(CommandSender commandSender, String[] args) {
        if(args.length > 0) {
            commandSender.sendMessage(ChatColor.DARK_RED + "Incorrect syntax! Use just /vreload");
            return;
        }
        teamHandler.updateServerTeamConfig();
        for(ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
            teamHandler.sendCreatePackets(p);
        }
        commandSender.sendMessage(ChatColor.GREEN + "Proxy Team Config Reloaded");
    }
}
