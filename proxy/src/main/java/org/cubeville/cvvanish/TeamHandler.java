package org.cubeville.cvvanish;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.score.Team;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class TeamHandler {

    public CVVanish plugin;

    public HashMap<String, Team> teams;
    public HashMap<String, HashMap<String, String>> serverTeamConfig;
    public HashMap<UUID, String> playerTeam;

    public TeamHandler(CVVanish plugin) {
        this.plugin = plugin;
        this.teams = new HashMap<>();
        this.serverTeamConfig = new HashMap<>();
        this.playerTeam = new HashMap<>();

        Team saTeam = new net.md_5.bungee.api.score.Team("5sa");
        saTeam.setDisplayName("sa");
        saTeam.setNameTagVisibility("always");
        saTeam.setCollisionRule("always");
        saTeam.setFriendlyFire((byte) 0);
        saTeam.setColor(4);
        saTeam.setPrefix("");
        saTeam.setSuffix("");
        this.teams.put("sa", saTeam);
        Team aTeam = new Team("4a");
        aTeam.setDisplayName("a");
        aTeam.setNameTagVisibility("always");
        aTeam.setCollisionRule("always");
        aTeam.setFriendlyFire((byte) 0);
        aTeam.setColor(11);
        aTeam.setPrefix("");
        aTeam.setSuffix("");
        this.teams.put("a", aTeam);
        Team smTeam = new Team("2sm");
        smTeam.setDisplayName("sm");
        smTeam.setNameTagVisibility("always");
        smTeam.setCollisionRule("always");
        smTeam.setFriendlyFire((byte) 0);
        smTeam.setColor(13);
        smTeam.setPrefix("");
        smTeam.setSuffix("");
        this.teams.put("sm", smTeam);
        Team mTeam = new Team("1m");
        mTeam.setDisplayName("m");
        mTeam.setNameTagVisibility("always");
        mTeam.setCollisionRule("always");
        mTeam.setFriendlyFire((byte) 0);
        mTeam.setColor(10);
        mTeam.setPrefix("");
        mTeam.setSuffix("");
        this.teams.put("m", mTeam);
        Team rTeam = new Team("1r");
        rTeam.setDisplayName("r");
        rTeam.setNameTagVisibility("always");
        rTeam.setCollisionRule("always");
        rTeam.setFriendlyFire((byte) 0);
        rTeam.setColor(7);
        rTeam.setPrefix("");
        rTeam.setSuffix("");
        this.teams.put("r", rTeam);
        Team cTeam = new Team("0c");
        cTeam.setDisplayName("c");
        cTeam.setNameTagVisibility("always");
        cTeam.setCollisionRule("always");
        cTeam.setFriendlyFire((byte) 0);
        cTeam.setColor(15);
        cTeam.setPrefix("");
        cTeam.setSuffix("");
        this.teams.put("c", cTeam);

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
        String currentTeam = getCurrentTeam(player.getUniqueId());
        String newTeam = getNewTeam(player);
        if(currentTeam == null) {     //proxy doesn't have the player in a team
            addPlayerToTeam(newTeam, player);
        } else if(!currentTeam.equalsIgnoreCase(newTeam)) {     //proxy has the player in the wrong team (only use case is probably a promotion?)
            removePlayerFromTeam(currentTeam, player);
            addPlayerToTeam(newTeam, player);
        }
        sendCreatePackets(player);
        updateServerWithPlayer(newTeam, player);
    }

    public void updateServerWithPlayer(String t, ProxiedPlayer player) {
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            if(!p.equals(player)) {
                sendPlayerUpdatePacket(t, p);
            }
        }
    }

    public String getCurrentTeam(UUID uuid) {
        return this.playerTeam.get(uuid);
    }

    public String getNewTeam(ProxiedPlayer player) {
        if(player.hasPermission("cvvanish.prefix.sa")) {
            return "sa";
        } else if(player.hasPermission("cvvanish.prefix.a")) {
            return "a";
        } else if(player.hasPermission("cvvanish.prefix.smod")) {
            return "sm";
        } else if(player.hasPermission("cvvanish.prefix.mod")) {
            return "m";
        } else if(player.hasPermission("cvvanish.prefix.retired")) {
            return "r";
        } else {
            return "c";
        }
    }

    public void addPlayerToTeam(String team, ProxiedPlayer player) {
        this.teams.get(team).addPlayer(player.getName());
        this.playerTeam.put(player.getUniqueId(), team);
    }

    public void removePlayerFromTeam(String team, ProxiedPlayer player) {
        this.teams.get(team).removePlayer(player.getName());
    }

    public void sendCreatePackets(ProxiedPlayer p) {
        System.out.println("sending create packets to " + p.getName());
        for(String t : this.teams.keySet()) {
            Team realTeam = this.teams.get(t);
            net.md_5.bungee.protocol.packet.Team team = new net.md_5.bungee.protocol.packet.Team();
            team.setName(realTeam.getName());
            team.setDisplayName(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getDisplayName())));
            team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getPrefix())));
            team.setSuffix(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getSuffix())));
            team.setCollisionRule(this.serverTeamConfig.get(p.getServer().getInfo().getName().toLowerCase()).get("collision"));
            team.setFriendlyFire(realTeam.getFriendlyFire());
            team.setNameTagVisibility(this.serverTeamConfig.get(p.getServer().getInfo().getName().toLowerCase()).get("nametags"));
            team.setColor(realTeam.getColor());
            Collection<String> newPlayerList = new ArrayList<>();
            for(String oldPlayer : realTeam.getPlayers()) {
                UUID oldPlayerUUID = ProxyServer.getInstance().getPlayer(oldPlayer).getUniqueId();
                if(!plugin.isPlayerListed(oldPlayerUUID) && p.hasPermission("cvvanish.override")) {
                    newPlayerList.add(oldPlayer); //todo do strike
                    //team.setColor(18);
                } else if(plugin.isPlayerListed(oldPlayerUUID) && plugin.isPlayerInvisible(oldPlayerUUID) && p.hasPermission("cvvanish.override")) {
                    newPlayerList.add(oldPlayer); //todo do italic
                    //team.setColor(20);
                } else if(plugin.isPlayerListed(oldPlayerUUID)) {
                    newPlayerList.add(oldPlayer);
                }
            }
            team.setPlayers(newPlayerList.toArray(new String[0]));
            team.setMode((byte) 0);
            p.unsafe().sendPacket(team);
        }
    }

    public void sendPlayerUpdatePacket(String t, ProxiedPlayer p) {
        System.out.println("sending update packet for team " + t + " to " + p.getName());
        Team realTeam = this.teams.get(t);
        net.md_5.bungee.protocol.packet.Team team = new net.md_5.bungee.protocol.packet.Team();
        team.setName(realTeam.getName());
        team.setDisplayName(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getDisplayName())));
        team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getPrefix())));
        team.setSuffix(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getSuffix())));
        team.setCollisionRule(this.serverTeamConfig.get(p.getServer().getInfo().getName().toLowerCase()).get("collision"));
        team.setFriendlyFire(realTeam.getFriendlyFire());
        team.setNameTagVisibility(this.serverTeamConfig.get(p.getServer().getInfo().getName().toLowerCase()).get("nametags"));
        team.setColor(realTeam.getColor());
        Collection<String> newPlayerList = new ArrayList<>();
        for(String oldPlayer : realTeam.getPlayers()) {
            UUID oldPlayerUUID = ProxyServer.getInstance().getPlayer(oldPlayer).getUniqueId();
            if(!plugin.isPlayerListed(oldPlayerUUID) && p.hasPermission("cvvanish.override")) {
                newPlayerList.add(oldPlayer); //todo do strike
                //team.setColor(18);
            } else if(plugin.isPlayerListed(oldPlayerUUID) && plugin.isPlayerInvisible(oldPlayerUUID) && p.hasPermission("cvvanish.override")) {
                newPlayerList.add(oldPlayer); //todo do italic
                //team.setColor(20);
            } else if(plugin.isPlayerListed(oldPlayerUUID)) {
                newPlayerList.add(oldPlayer);
            }
        }
        team.setPlayers(newPlayerList.toArray(new String[0]));
        team.setMode((byte) 3);
        p.unsafe().sendPacket(team);
    }
}
