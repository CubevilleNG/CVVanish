package org.cubeville.cvvanish;

import java.util.UUID;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class VCommand extends Command
{
    private CVVanish plugin;

    public VCommand(CVVanish plugin) {
        super("v", "cvvanish.use");
        this.plugin = plugin;
    }

    public void execute(CommandSender commandSender, String[] args) {
        ProxiedPlayer player = (ProxiedPlayer) commandSender;
        UUID uuid = player.getUniqueId();

        boolean cc = false;
        
        if(args.length == 0) {
            plugin.switchPlayerVisibility(uuid);
            cc = true;
        }

        else if(args.length == 1) {
            if(args[0].equals("vis")) {
                plugin.setPlayerVisible(uuid);
                cc = true;
            }
            else if(args[0].equals("inv")) {
                plugin.setPlayerInvisible(uuid);
                cc = true;
            }
            else if(args[0].equals("van")) {
                plugin.setPlayerVanished(uuid);
                cc = true;
            }
            else if(args[0].equals("hide")) {
                plugin.setPlayerHidden(uuid);
                cc = true;
            }
            else if(args[0].equals("ioff")) {
                plugin.setPlayerInteractDisabledStatus(uuid, true);
                cc = true;
            }
            else if(args[0].equals("ion")) {
                plugin.setPlayerInteractDisabledStatus(uuid, false);
                cc = true;
            }
            else if(args[0].equals("poff")) {
                plugin.setPlayerPickupDisabledStatus(uuid, true);
                cc = true;
            }
            else if(args[0].equals("pon")) {
                plugin.setPlayerPickupDisabledStatus(uuid, false);
                cc = true;
            }
            else if(args[0].equals("noff") || args[0].equals("non")) {
                if(player.hasPermission("cvvanish.use.nightvision"))
                    plugin.setPlayerNightvisionEnabledStatus(uuid, args[0].equals("non"));
                else
                    player.sendMessage("§cNo permission.");
            }
            else if(args[0].equals("ungod") || args[0].equals("god")) {
                if(player.hasPermission("cvvanish.use.god"))
                    plugin.setPlayerGodStatus(uuid, args[0].equals("god"));
                else
                    player.sendMessage("§cNo permission.");
            }
            else if(args[0].equals("help")) {
                player.sendMessage("§c§lVanish Plugin Commands");
                player.sendMessage("§6/v§r - §6§oToggles the player between visible, and the state they log on with (smod/admin: invisible, pickup/interact off | sa: vanish, pickup/interact off)");
                player.sendMessage("§6/van§r - §6§oPlayer becomes invisible, and will not show on tab.");
                player.sendMessage("§6/inv§r - §6§oPlayer becomes invisible, but will remain on tab.");
                player.sendMessage("§6/hide§r - §6§oPlayer becomes visible, but will not remain on tab.");
                player.sendMessage("§6/vis§r - §6§oPlayer becomes visible, and remains on tab.");
                player.sendMessage("§6/poff /pon§r - §6§oThis will toggle whether you pick items up or not.");
                player.sendMessage("§6/ioff /ion§r - §6§oThis will toggle whether you interact with pressure plates and tripwires.");
                player.sendMessage("§6/god /ungod§r - §6§oThis will toggle invincibility. Being invisible automatically also makes you invincible.");
                if(player.hasPermission("cvvanish.use.nightvision")) player.sendMessage("§6/noff /non§r - §6§oThis will toggle nightvision.");
                player.sendMessage("§6/vfj§r - §6§oSend fake join message, go into /vis mode");
                player.sendMessage("§6/ifj§r - §6§oSend fake join message, go into /inv mode");
                player.sendMessage("§6/vfq§r - §6§oSend fake leave message, go into /van mode");
                player.sendMessage("§eIf you would prefer a status message after entering a command, tell an SA to activate it for you.");
            }
            else {
                player.sendMessage("§cUnknown command!");
            }

        }
        else if(args.length == 2) {
            if(args[0].equals("update")) {
                if(player.hasPermission("cvvanish.updateplayer")) {
                    ProxiedPlayer p = ProxyServer.getInstance().getPlayer(args[1]);
                    if(p == null) {
                        player.sendMessage("§cPlayer not found.");
                    }
                    else {
                        CVTabList.updatePlayerAddPacket(p.getUniqueId());
                        player.sendMessage("§aUpdated.");
                    }
                }
                else
                    player.sendMessage("§cNo permission.");                
            }
            else {
                player.sendMessage("§cUnknown command!");
            }
        }

        else {
            player.sendMessage("§cWrong number of arguments!");
        }

        if(cc && player.hasPermission("cvvanish.showstatusmessage")) {
            String msg = "§aStatus: ";
            msg += plugin.isPlayerUnlisted(uuid) ? "§eUnlisted§a on tab " : "§eListed§a on tab ";
            msg += plugin.isPlayerInvisible(uuid) ? "§aand §einvisible§a." : "and §evisible§a.";
            msg += " Pickup is ";
            msg += plugin.isPlayerItemPickupDisabled(uuid) ? "§edisabled" : "§eenabled";
            msg += "§a. Interact is ";
            msg += plugin.isPlayerInteractDisabled(uuid) ? "§edisabled§a." : "§eenabled§a.";
            msg += " God is ";
            msg += plugin.isPlayerGod(uuid) ? "§eenabled§a." : plugin.isPlayerInvisible(uuid) ? "§eactive§a." : "§edisabled§a.";
            player.sendMessage(msg);
        }
        
    }

}
