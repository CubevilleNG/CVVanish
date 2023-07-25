package org.cubeville.cvvanish;

import java.util.UUID;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
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
                if(player.hasPermission("cvvanish.use.god")) {
                    plugin.setPlayerGodStatus(uuid, args[0].equals("god"));
                    cc = true;
                }
                else
                    player.sendMessage("§cNo permission.");
            }
            else if(args[0].equals("showlist")) {
                if(player.hasPermission("cvvanish.use.show")) {
                    player.sendMessage(new TextComponent("§aPlayers that can see you:"));
                    for(String p : plugin.getPlayersShownToPlayer(uuid)) {
                        if(p != null) {
                            player.sendMessage(new TextComponent("§a - §e" + p));
                        }
                    }
                }
            }
            else if(args[0].equals("help")) {
                player.sendMessage("§c§lVanish Plugin Commands");
                player.sendMessage("§6/v§r - §6§oToggles the player between visible, and the state they log on with (smod/admin: invisible, pickup/interact off | sa: vanish, pickup/interact off)");
                player.sendMessage("§6/van§r - §6§oPlayer becomes invisible, and will not show on tab.");
                player.sendMessage("§6/inv§r - §6§oPlayer becomes invisible, but will remain on tab.");
                player.sendMessage("§6/vis§r - §6§oPlayer becomes visible, and remains on tab.");
                player.sendMessage("§6/poff /pon§r - §6§oThis will toggle whether you pick items up or not.");
                player.sendMessage("§6/ioff /ion§r - §6§oThis will toggle whether you interact with pressure plates and tripwires.");
                if(player.hasPermission("cvvanish.use.god")) player.sendMessage("§6/god /ungod§r - §6§oThis will toggle invincibility. Being invisible automatically also makes you invincible.");
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
            else if(args[0].equals("ungod") || args[0].equals("god")) {
                if(player.hasPermission("cvvanish.use.godother")) {
                    ProxiedPlayer p = ProxyServer.getInstance().getPlayer(args[1]);
                    if (p == null) {
                        player.sendMessage("§cInvalid player.");
                        return;
                    }
                    UUID pu = p.getUniqueId();
                    String message;
                    if (args[0].equals("god")) {
                        if (!plugin.isPlayerGod(pu)) {
                            message = "§aApplied god to §e" + p.getName() + "§a.";
                        }
                        else {
                            message = "§e" + p.getName() + " §aalready has god enabled.";
                        }
                    }
                    else {
                        if (plugin.isPlayerGod(pu)) {
                            message = "§aRemoved god from §e" + p.getName() + "§a.";
                        }
                        else {
                            message = "§e" + p.getName() + " §aalready has god disabled.";
                        }
                    }
                    plugin.setPlayerGodStatus(pu, args[0].equals("god"));
                    player.sendMessage(message);
                }
                else
                    player.sendMessage("§cNo permission.");
            }
            else if(args[0].equals("showto") || args[0].equals("hidefrom")) {
                if(player.hasPermission("cvvanish.use.show")) {
                    UUID t = plugin.getPDM().getPlayerId(args[1]);
                    if(t == null) {
                        player.sendMessage(new TextComponent("§cInvalid player."));
                        return;
                    }
                    UUID source = player.getUniqueId();
                    UUID target = t;
                    String message;
                    if(args[0].equals("showto")) {
                        if(!plugin.isPlayerShownToPlayer(source, target)) {
                            message = "§aNow visible to §e" + args[1] + "§a.";
                            plugin.addToPlayerShowList(source, target);
                        } else {
                            message = "§e" + args[1] + " §acan already see you.";
                        }
                    } else {
                        if(plugin.isPlayerShownToPlayer(source, target)) {
                            message = "§aYou are now hidden from §e" + args[1] + "§a.";
                            plugin.removeFromPlayerShowList(source, target);
                        } else {
                            message = "§eYou are already hidden from §e" + args[1] + "§a.";
                        }
                    }
                    player.sendMessage(new TextComponent(message));
                }
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
            msg += plugin.isPlayerGod(uuid) ? "§eenabled§a." : plugin.isPlayerInvisible(uuid) ? "§eenabled§a." : "§edisabled§a.";
            player.sendMessage(msg);
        }
        
    }

}
