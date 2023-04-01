package org.cubeville.cvvanish;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;

public class ActionBarNotifier implements Runnable
{
    private CVVanish plugin;

    public ActionBarNotifier(CVVanish plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getProxy().getScheduler().schedule(plugin, this, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        Set<UUID> connectedPlayers = plugin.getConnectedPlayers();
        for(UUID uuid: connectedPlayers) {
            if(plugin.isActionBarActive(uuid) == false) continue;
            String message = "";
            if(!plugin.isPlayerListed(uuid) && plugin.isPlayerInvisible(uuid))
                message = "Vanished. ";
            else if(plugin.isPlayerInvisible(uuid) && plugin.isPlayerListed(uuid))
                message = "Invisible. ";
            else if (!plugin.isPlayerListed(uuid))
                message = "Hidden. ";
            if(plugin.isPlayerGod(uuid) && !plugin.isPlayerInvisible(uuid) && !ProxyServer.getInstance().getPlayer(uuid).hasPermission("cvvanish.disableactionbar.god"))
                message += "God Enabled.";
            if(plugin.isPlayerItemPickupDisabled(uuid) && plugin.isPlayerInteractDisabled(uuid))
                message += "PU, IA off.";
            else {
                if(plugin.isPlayerItemPickupDisabled(uuid))
                    message += "PU off. ";
                if(plugin.isPlayerInteractDisabled(uuid))
                    message += "IA off. ";
            }
            if(message.length() > 0) {
                TextComponent messagetc = new TextComponent();
                messagetc.setText(message);
                messagetc.setColor(ChatColor.GREEN);
                ProxyServer.getInstance().getPlayer(uuid).sendMessage(ChatMessageType.ACTION_BAR, messagetc);
            }
        }
    }
    
}
