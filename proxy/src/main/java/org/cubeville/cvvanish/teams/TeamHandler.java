package org.cubeville.cvvanish.teams;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.score.Team;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.cubeville.cvchat.playerdata.PlayerDataManager;
import org.cubeville.cvvanish.CVVanish;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamHandler {

    public CVVanish plugin;
    public TeamManager teamManager;
    public PlayerDataManager playerDataManager;

    public HashMap<String, HashMap<String, String>> serverTeamConfig;

    public TeamHandler(CVVanish plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.serverTeamConfig = new HashMap<>();
        updateServerTeamConfig();
    }

    public void updateServerTeamConfig() {
        this.serverTeamConfig = new HashMap<>();
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            Configuration servers = config.getSection("servers");
            Collection<ServerInfo> realServers = ProxyServer.getInstance().getServers().values();
            for(ServerInfo s : realServers) {
                Configuration server = servers.getSection(s.getName().toLowerCase());
                String collision = "always";
                String nametags = "always";
                HashMap<String, String> teamConfig = new HashMap<>();
                if(server != null) {
                    collision = server.getString("collision", "always");
                    nametags = server.getString("nametags", "always");
                }
                teamConfig.put("collision", collision);
                teamConfig.put("nametags", nametags);
                this.serverTeamConfig.put(s.getName().toLowerCase(), teamConfig);
            }
        } catch(IOException e) {
            System.out.println("Could not load configuration");
        }
    }

    public void init(ProxiedPlayer player) {
        if(this.playerDataManager == null) {
            System.out.println("PDM was null...attempting to get instance");
            try {
                this.playerDataManager = PlayerDataManager.getInstance();
                System.out.println("PDM instance successfully retrieved");
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("Getting PDM instance failed");
            }
        }
        String [] rank = getRank(player);
        String currentTeam = teamManager.getPlayerTeam(player.getUniqueId()) == null ? null : teamManager.getPlayerTeam(player.getUniqueId()).getName();
        String newTeam = rank[0] + player.getName();
        Team team;
        Team destroyTeam;
        if(currentTeam == null) {     //proxy doesn't have the player in a team
            team = teamManager.addPlayerTeam(player.getUniqueId(), newTeam, rank[1]);
        } else if(!currentTeam.equalsIgnoreCase(newTeam)) {     //proxy has the player in the wrong team (only use case is probably a promotion?)
            destroyTeam = teamManager.removePlayerTeam(player.getUniqueId());
            sendRemovePacketToServer(getTeamPacket(destroyTeam));
            team = teamManager.addPlayerTeam(player.getUniqueId(), newTeam, rank[1]);
        } else {
            team = teamManager.getPlayerTeam(player.getUniqueId());
        }
        if(team == null) {
            System.out.println("Creation or retrieval of Team failed! Cancelling packet send");
            return;
        }
        sendAllCreatePacketsToPlayer(player);
        sendCreatePacketToServer(getTeamPacket(team), player);
    }

    public void sendAllCreatePacketsToPlayer(ProxiedPlayer player) {
        for(Team team : teamManager.getAllTeams()) {
            net.md_5.bungee.protocol.packet.Team newTeam = getTeamPacket(team);
            HashMap<String, String> serverConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
            newTeam.setCollisionRule(serverConfig.get("collision"));
            newTeam.setNameTagVisibility(serverConfig.get("nametags"));
            newTeam.setMode((byte) 0);
            System.out.println(teamManager.getRealNameUUID((String) team.getPlayers().toArray()[0]));
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(teamManager.getRealNameUUID((String) team.getPlayers().toArray()[0]));
            String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(newTeam.getPrefix()));
            String color = oldPrefix.substring(0, oldPrefix.indexOf("§") + 2);
            while(oldPrefix.contains("§")) {
                oldPrefix = oldPrefix.substring(oldPrefix.indexOf("§") + 2);
            }
            if(plugin.isPlayerUnlisted(p.getUniqueId()) && canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId())) {
                String newPrefix = color + "§m" + oldPrefix;
                newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                player.unsafe().sendPacket(newTeam);
            } else if(plugin.isPlayerInvisible(p.getUniqueId())) {
                if(canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId())) {
                    String newPrefix = color + "§o" + oldPrefix;
                    newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                    player.unsafe().sendPacket(newTeam);
                } else {
                    player.unsafe().sendPacket(newTeam);
                }
            } else {
                player.unsafe().sendPacket(newTeam);
            }
        }
    }

    public void sendCreatePacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 0);
        HashMap<String, String> serverConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
        team.setCollisionRule(serverConfig.get("collision"));
        team.setNameTagVisibility(serverConfig.get("nametags"));
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            if(!p.equals(player)) {
                String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(team.getPrefix()));
                String color = oldPrefix.substring(0, oldPrefix.indexOf("§") + 2);
                while(oldPrefix.contains("§")) {
                    oldPrefix = oldPrefix.substring(oldPrefix.indexOf("§") + 2);
                }
                if(plugin.isPlayerUnlisted(player.getUniqueId()) && canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                    String newPrefix = color + "§m" + oldPrefix;
                    team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                    p.unsafe().sendPacket(team);
                } else if(plugin.isPlayerInvisible(player.getUniqueId())) {
                    if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                        String newPrefix = color + "§o" + oldPrefix;
                        team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                        p.unsafe().sendPacket(team);
                    } else {
                        p.unsafe().sendPacket(team);
                    }
                } else {
                    p.unsafe().sendPacket(team);
                }
            }
        }
    }

    public void sendRemovePacketToServer(net.md_5.bungee.protocol.packet.Team team) {
        team.setMode((byte) 1);
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            p.unsafe().sendPacket(team);
        }
    }

    public void sendVisiblePacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 1);
        HashMap<String, String> serverConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
        team.setCollisionRule(serverConfig.get("collision"));
        team.setNameTagVisibility(serverConfig.get("nametags"));
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                p.unsafe().sendPacket(team);
            }
            String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(team.getPrefix()));
            String color = oldPrefix.substring(0, oldPrefix.indexOf("§") + 2);
            while(oldPrefix.contains("§")) {
                oldPrefix = oldPrefix.substring(oldPrefix.indexOf("§") + 2);
            }
            String newPrefix = color + oldPrefix;
            team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
            team.setMode((byte) 0);
            p.unsafe().sendPacket(team);
        }
    }

    public void sendInvisiblePacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 1);
        HashMap<String, String> serverConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
        team.setCollisionRule(serverConfig.get("collision"));
        team.setNameTagVisibility(serverConfig.get("nametags"));
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            p.unsafe().sendPacket(team);
            String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(team.getPrefix()));
            String color = oldPrefix.substring(0, oldPrefix.indexOf("§") + 2);
            while(oldPrefix.contains("§")) {
                oldPrefix = oldPrefix.substring(oldPrefix.indexOf("§") + 2);
            }
            String newPrefix = color + "§o" + oldPrefix;
            team.setMode((byte) 0);
            if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                p.unsafe().sendPacket(team);
            } else {
                team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(color + oldPrefix)));
                p.unsafe().sendPacket(team);
            }
        }
    }

    public void sendVanishPacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 1);
        HashMap<String, String> serverConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
        team.setCollisionRule(serverConfig.get("collision"));
        team.setNameTagVisibility(serverConfig.get("nametags"));
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            p.unsafe().sendPacket(team);
            if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(team.getPrefix()));
                String color = oldPrefix.substring(0, oldPrefix.indexOf("§") + 2);
                while(oldPrefix.contains("§")) {
                    oldPrefix = oldPrefix.substring(oldPrefix.indexOf("§") + 2);
                }
                String newPrefix = color + "§m" + oldPrefix;
                team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                team.setMode((byte) 0);
                p.unsafe().sendPacket(team);
            }
        }
    }

    public net.md_5.bungee.protocol.packet.Team getTeamPacket(Team team) {
        net.md_5.bungee.protocol.packet.Team newTeam = new net.md_5.bungee.protocol.packet.Team();
        newTeam.setName(team.getName());
        newTeam.setDisplayName(ComponentSerializer.toString(TextComponent.fromLegacyText(team.getDisplayName())));
        newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(team.getPrefix())));
        newTeam.setSuffix(ComponentSerializer.toString(TextComponent.fromLegacyText(team.getSuffix())));
        newTeam.setCollisionRule(team.getCollisionRule());
        newTeam.setFriendlyFire(team.getFriendlyFire());
        newTeam.setNameTagVisibility(team.getNameTagVisibility());
        newTeam.setColor(team.getColor());
        Collection<String> teamPlayers = new ArrayList<>(team.getPlayers());
        newTeam.setPlayers(teamPlayers.toArray(new String[0]));
        return newTeam;
    }

    public boolean canSenderSeePlayerState(UUID sender, UUID player) {
        return this.playerDataManager.outranksOrEqual(sender, player);
    }

    public String[] getRank(ProxiedPlayer player) {
        if(player.hasPermission("cvvanish.prefix.sa")) {
            return new String[]{"5","4"};
        } else if(player.hasPermission("cvvanish.prefix.a")) {
            return new String[]{"4","b"};
        } else if(player.hasPermission("cvvanish.prefix.smod")) {
            return new String[]{"3","d"};
        } else if(player.hasPermission("cvvanish.prefix.mod")) {
            return new String[]{"2","a"};
        } else if(player.hasPermission("cvvanish.prefix.retired")) {
            return new String[]{"1","7"};
        } else {
            return new String[]{"0","f"};
        }
    }
}
