package org.cubeville.cvvanish.teams;

import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.score.Team;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.Either;
import net.md_5.bungee.protocol.packet.PlayerListItemUpdate;
import org.cubeville.cvvanish.CVTabList;
import org.cubeville.cvvanish.CVVanish;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TeamHandler {

    public CVVanish plugin;
    public TeamManager teamManager;

    public HashMap<String, HashMap<String, String>> serverTeamConfig;
    public final HashMap<UUID, HashMap<String, String>> playerTeamConfig;

    public HashMap<UUID, HashMap<UUID, List<ScheduledTask>>> queuedTeamsPacketTasks;

    public TeamHandler(CVVanish plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.serverTeamConfig = new HashMap<>();
        this.playerTeamConfig = new HashMap<>();
        this.queuedTeamsPacketTasks = new HashMap<>();
        updateServerTeamConfig();
    }

    public void setPlayerTeamConfig(String key, String value, ProxiedPlayer player) {
        String finalValue = value;
        if(value.equalsIgnoreCase("reset")) {
            finalValue = this.serverTeamConfig.get(player.getServer().getInfo().getName()).get(key);
        }
        HashMap<String, String> config;
        if(this.playerTeamConfig.containsKey(player.getUniqueId())) {
            config = new HashMap<>(this.playerTeamConfig.get(player.getUniqueId()));
        } else {
            config = new HashMap<>(this.serverTeamConfig.get(player.getServer().getInfo().getName()));
        }
        config.put(key, finalValue);
        this.playerTeamConfig.put(player.getUniqueId(), config);
    }

    public void updateServerTeamConfig() {
        this.serverTeamConfig = new HashMap<>();
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            Configuration servers = config.getSection("servers");
            Collection<ServerInfo> realServers = ProxyServer.getInstance().getServers().values();
            for(ServerInfo s : realServers) {
                Configuration server = servers.getSection(s.getName());
                String collision = "always";
                String nametags = "always";
                HashMap<String, String> teamConfig = new HashMap<>();
                if(server != null) {
                    collision = server.getString("collision", "always");
                    nametags = server.getString("nametags", "always");
                }
                teamConfig.put("collision", collision);
                teamConfig.put("nametags", nametags);
                this.serverTeamConfig.put(s.getName(), teamConfig);
            }
        } catch(IOException e) {
            System.out.println("Could not load configuration");
        }
        refreshEntireTab();
    }

    public void refreshEntireTab() {
        for(ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
            init(p);
        }
    }

    public void init(ProxiedPlayer player) {
        //plugin.initPDM();
        String [] rank = getRank(player);
        String currentTeam = teamManager.getPlayerTeam(player.getUniqueId()) == null ? null : teamManager.getPlayerTeam(player.getUniqueId()).getName();
        String newTeam = rank[0] + plugin.getPDM().getPlayerVisibleName(player.getUniqueId());
        Team team;
        Team destroyTeam;
        if(currentTeam == null) {     //proxy doesn't have the player in a team
            team = teamManager.addPlayerTeam(player.getUniqueId(), newTeam, rank[1]);
        } else if(!currentTeam.equalsIgnoreCase(newTeam)) {     //proxy has the player in the wrong team (only use case is probably a promotion?)
            destroyTeam = teamManager.removePlayerTeam(player.getUniqueId());
            sendRemovePacketToServer(getTeamPacket(destroyTeam));
            sendRemovePacketsToPlayer(player);
            team = teamManager.addPlayerTeam(player.getUniqueId(), newTeam, rank[1]);
        } else {
            team = teamManager.getPlayerTeam(player.getUniqueId());
            String pname = team.getPrefix();
            String color = pname.substring(pname.indexOf("#"), pname.indexOf("#") + 7);
            String alias = plugin.getPDM().getPlayerVisibleName(player.getUniqueId());
            if(!pname.equals(alias)) {
                pname = alias;
                team = teamManager.changePlayerTeamName(player.getUniqueId(), color + pname);
            }
        }
        if(team == null) {
            System.out.println("Creation or retrieval of Team failed! Cancelling packet send");
            return;
        }
        sendAllCreatePacketsToPlayer(player);
        sendCreatePacketToServer(getTeamPacket(team), player);
        plugin.showExistingPlayers(player.getUniqueId());
    }

    public void sendAllCreatePacketsToPlayer(ProxiedPlayer player) {
        for(Team team : teamManager.getAllTeams()) {
            net.md_5.bungee.protocol.packet.Team newTeam = getTeamPacket(team);
            HashMap<String, String> teamConfig;
            if(this.playerTeamConfig.containsKey(player.getUniqueId())) {
                teamConfig = this.playerTeamConfig.get(player.getUniqueId());
            } else {
                teamConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
            }
            newTeam.setCollisionRule(teamConfig.get("collision"));
            newTeam.setNameTagVisibility(teamConfig.get("nametags"));
            newTeam.setMode((byte) 0);
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(teamManager.getRealNameUUID((String) team.getPlayers().toArray()[0]));
            if(p != null) {
                String oldPrefix = newTeam.getPrefix().getRight().toLegacyText();
                //String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(newTeam.getPrefix()));
                String color = oldPrefix.substring(oldPrefix.indexOf("#"), oldPrefix.indexOf("#") + 7);
                oldPrefix = oldPrefix.substring(oldPrefix.indexOf("#") + 7);
                if(plugin.isPlayerUnlisted(p.getUniqueId())) {
                    //System.out.println(p.getName() + " is unlisted");
                    if(canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId())) {
                        String newPrefix = ChatColor.of(color) + "§m" + oldPrefix;
                        newTeam.setPrefix(Either.right(TextComponent.fromLegacy(newPrefix)));
                        //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                        executePacket(player, newTeam);
                        //System.out.println("sending strikethrough packet of " + p.getName() + " to " + player.getName());
                    }
                } else if(plugin.isPlayerInvisible(p.getUniqueId())) {
                    //System.out.println(p.getName() + " is invisible");
                    if(canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId())) {
                        newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + "§o" + oldPrefix)));
                        //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + "§o" + oldPrefix)));
                        //System.out.println("sending italics packet of " + p.getName() + " to " + player.getName());
                    } else {
                        newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
                        //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
                        //System.out.println("sending normal packet of " + p.getName() + " to " + player.getName());
                    }
                    executePacket(player, newTeam);
                } else if(p.hasPermission("cvvanish.prefix.helper") && !modOrHigher(p)){
                    if(!player.hasPermission("cvvanish.prefix.helper") && !modOrHigher(player)) {
                        color = "#FFFFFF";
                        newTeam.setName("0" + newTeam.getName().substring(1));
                    }
                    newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
                    //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
                    executePacket(player, newTeam);
                } else {
                    //System.out.println(p.getName() + " is neither invisible nor unlisted");
                    newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
                    //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
                    executePacket(player, newTeam);
                    //System.out.println("sending normal packet of " + p.getName() + " to " + player.getName());
                }
            }
        }
    }

    public void sendCreatePacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 0);
        HashMap<String, String> teamConfig;
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            if(!p.equals(player)) {
                if(this.playerTeamConfig.containsKey(uuid)) {
                    teamConfig = this.playerTeamConfig.get(uuid);
                } else {
                    teamConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
                }
                team.setCollisionRule(teamConfig.get("collision"));
                team.setNameTagVisibility(teamConfig.get("nametags"));
                net.md_5.bungee.protocol.packet.Team newTeam = createNewTeamPacket(team);
                String oldPrefix = newTeam.getPrefix().getRight().toLegacyText();
                //String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(newTeam.getPrefix()));
                String color = oldPrefix.substring(oldPrefix.indexOf("#"), oldPrefix.indexOf("#") + 7);
                oldPrefix = oldPrefix.substring(oldPrefix.indexOf("#") + 7);
                if(plugin.isPlayerUnlisted(player.getUniqueId())) {
                    //System.out.println(player.getName() + " is unlisted");
                    if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                        String newPrefix = ChatColor.of(color) + "§m" + oldPrefix;
                        newTeam.setPrefix(Either.right(TextComponent.fromLegacy(newPrefix)));
                        //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                        executePacket(p, newTeam);
                        //System.out.println("sending strikethrough packet of " + player.getName() + " to " + p.getName());
                    }
                } else if(plugin.isPlayerInvisible(player.getUniqueId())) {
                    //System.out.println(player.getName() + " is invisible");
                    if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                        String newPrefix = ChatColor.of(color) + "§o" + oldPrefix;
                        newTeam.setPrefix(Either.right(TextComponent.fromLegacy(newPrefix)));
                        //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                        executePacket(p, newTeam);
                        //System.out.println("sending italics packet of " + player.getName() + " to " + p.getName());
                    } else {
                        newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
                        //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
                        executePacket(p, newTeam);
                        //System.out.println("sending normal packet of " + player.getName() + " to " + p.getName());
                        //System.out.println("normal packet prefix is " + newTeam.getPrefix());
                    }
                } else if(player.hasPermission("cvvanish.prefix.helper") && !modOrHigher(player)) {
                    if(!p.hasPermission("cvvanish.prefix.helper") && !modOrHigher(p)) {
                        color = "#FFFFFF";
                        newTeam.setName("0" + newTeam.getName().substring(1));
                    }
                    newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
                    //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
                    executePacket(p, newTeam);
                } else {
                    //System.out.println(player.getName() + " is neither invisible nor unlisted");
                    newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
                    //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
                    executePacket(p, newTeam);
                    //System.out.println("sending normal packet of " + player.getName() + " to " + p.getName());
                }
            }
        }
    }

    public void sendRemovePacketsToPlayer(ProxiedPlayer player) {
        for(Team t : teamManager.getAllTeams()) {
            net.md_5.bungee.protocol.packet.Team team = getTeamPacket(t);
            team.setMode((byte) 1);
            executePacket(player, team);
        }
    }

    public void sendRemovePacketToServer(net.md_5.bungee.protocol.packet.Team team) {
        team.setMode((byte) 1);
        for(UUID uuid : plugin.getConnectedPlayers()) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            executePacket(p, team);
        }
    }

    public void sendRemovePacketToServer(net.md_5.bungee.protocol.packet.Team team, boolean unlisted, UUID player) {
        team.setMode((byte) 1);
        for(UUID uuid : plugin.getConnectedPlayers()) {
            if(unlisted) {
                if(canSenderSeePlayerState(uuid, player)) {
                    ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
                    executePacket(p, team);
                }
            } else {
                ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
                executePacket(p, team);
            }
        }
    }

    public void sendVisiblePacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 1);
        HashMap<String, String> teamConfig;
        //System.out.println(player.getName() + " status just changed to visible");
        for(UUID uuid : plugin.getConnectedPlayers()) {
            if(this.playerTeamConfig.containsKey(uuid)) {
                teamConfig = this.playerTeamConfig.get(uuid);
            } else {
                teamConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
            }
            team.setCollisionRule(teamConfig.get("collision"));
            team.setNameTagVisibility(teamConfig.get("nametags"));
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            net.md_5.bungee.protocol.packet.Team newTeam = createNewTeamPacket(team);
            if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                executePacket(p, newTeam);
            }
            String oldPrefix = newTeam.getPrefix().getRight().toLegacyText();
            //String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(newTeam.getPrefix()));
            String color = oldPrefix.substring(oldPrefix.indexOf("#"), oldPrefix.indexOf("#") + 7);
            oldPrefix = oldPrefix.substring(oldPrefix.indexOf("#") + 7);
            newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
            //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
            newTeam.setMode((byte) 0);
            executePacket(p, newTeam);
            //System.out.println("sending normal packet of " + player.getName() + " to " + p.getName());
        }
    }

    public void sendInvisiblePacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 0);
        HashMap<String, String> teamConfig;
        //System.out.println(player.getName() + " status just changed to invisible");
        for(UUID uuid : plugin.getConnectedPlayers()) {
            if(this.playerTeamConfig.containsKey(uuid)) {
                teamConfig = this.playerTeamConfig.get(uuid);
            } else {
                teamConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
            }
            team.setCollisionRule(teamConfig.get("collision"));
            team.setNameTagVisibility(teamConfig.get("nametags"));
            net.md_5.bungee.protocol.packet.Team newTeam = createNewTeamPacket(team);
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            String oldPrefix = team.getPrefix().getRight().toLegacyText();
            //String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(team.getPrefix()));
            String color = oldPrefix.substring(oldPrefix.indexOf("#"), oldPrefix.indexOf("#") + 7);
            oldPrefix = oldPrefix.substring(oldPrefix.indexOf("#") + 7);
            if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                newTeam.setMode((byte) 1);
                executePacket(p, newTeam);
                String newPrefix = ChatColor.of(color) + "§o" + oldPrefix;
                newTeam.setPrefix(Either.right(TextComponent.fromLegacy(newPrefix)));
                //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(newPrefix)));
                //System.out.println("sending italics packet of " + player.getName() + " to " + p.getName());
            } else {
                newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + oldPrefix)));
                //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + oldPrefix)));
                //System.out.println("sending normal packet of " + player.getName() + " to " + p.getName());
            }
            newTeam.setMode((byte) 0);
            executePacket(p, newTeam);
        }
    }

    public void sendVanishPacketToServer(net.md_5.bungee.protocol.packet.Team team, ProxiedPlayer player) {
        team.setMode((byte) 1);
        HashMap<String, String> teamConfig;
        //System.out.println(player.getName() + " status just changed to unlisted");
        for(UUID uuid : plugin.getConnectedPlayers()) {
            if(this.playerTeamConfig.containsKey(uuid)) {
                teamConfig = this.playerTeamConfig.get(uuid);
            } else {
                teamConfig = this.serverTeamConfig.get(player.getServer().getInfo().getName());
            }
            team.setCollisionRule(teamConfig.get("collision"));
            team.setNameTagVisibility(teamConfig.get("nametags"));
            net.md_5.bungee.protocol.packet.Team newTeam = createNewTeamPacket(team);
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            executePacket(p, newTeam);
            if(canSenderSeePlayerState(p.getUniqueId(), player.getUniqueId())) {
                String oldPrefix = team.getPrefix().getRight().toLegacyText();
                //String oldPrefix = TextComponent.toLegacyText(ComponentSerializer.parse(team.getPrefix()));
                String color = oldPrefix.substring(oldPrefix.indexOf("#"), oldPrefix.indexOf("#") + 7);
                oldPrefix = oldPrefix.substring(oldPrefix.indexOf("#") + 7);
                newTeam.setPrefix(Either.right(TextComponent.fromLegacy(ChatColor.of(color) + "§m" + oldPrefix)));
                //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(ChatColor.of(color) + "§m" + oldPrefix)));
                newTeam.setMode((byte) 0);
                executePacket(p, newTeam);
                //System.out.println("sending strikethrough packet of " + player.getName() + " to " + p.getName());
            }
        }
    }

    public net.md_5.bungee.protocol.packet.Team getTeamPacket(Team team) {
        net.md_5.bungee.protocol.packet.Team newTeam = new net.md_5.bungee.protocol.packet.Team();
        newTeam.setName(team.getName());
        newTeam.setDisplayName(Either.right(TextComponent.fromLegacy(team.getDisplayName()).getExtra().get(0)));
        newTeam.setPrefix(Either.right(TextComponent.fromLegacy(team.getPrefix()).getExtra().get(0)));
        newTeam.setSuffix(Either.right(TextComponent.fromLegacy(team.getSuffix()).getExtra().get(0)));

        //newTeam.setDisplayName(ComponentSerializer.toString(TextComponent.fromLegacyText(team.getDisplayName())));
        //newTeam.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(team.getPrefix())));
        //newTeam.setSuffix(ComponentSerializer.toString(TextComponent.fromLegacyText(team.getSuffix())));
        newTeam.setCollisionRule(team.getCollisionRule());
        newTeam.setFriendlyFire(team.getFriendlyFire());
        newTeam.setNameTagVisibility(team.getNameTagVisibility());
        newTeam.setColor(team.getColor());
        Collection<String> teamPlayers = new ArrayList<>(team.getPlayers());
        newTeam.setPlayers(teamPlayers.toArray(new String[0]));
        return newTeam;
    }

    public net.md_5.bungee.protocol.packet.Team createNewTeamPacket(net.md_5.bungee.protocol.packet.Team oldTeam) {
        net.md_5.bungee.protocol.packet.Team newTeam = new net.md_5.bungee.protocol.packet.Team();
        newTeam.setMode(oldTeam.getMode());
        newTeam.setName(oldTeam.getName());
        newTeam.setDisplayName(oldTeam.getDisplayName());
        newTeam.setPrefix(oldTeam.getPrefix());
        newTeam.setSuffix(oldTeam.getSuffix());
        newTeam.setCollisionRule(oldTeam.getCollisionRule());
        newTeam.setFriendlyFire(oldTeam.getFriendlyFire());
        newTeam.setNameTagVisibility(oldTeam.getNameTagVisibility());
        newTeam.setColor(oldTeam.getColor());
        newTeam.setPlayers(oldTeam.getPlayers());
        return newTeam;
    }

    public boolean canSenderSeePlayerState(UUID sender, UUID player) {
        return plugin.isPlayerShownToPlayer(player, sender) || plugin.getPDM().outranksOrEqual(sender, player);
    }

    public String[] getRank(ProxiedPlayer player) {
        if(player.hasPermission("cvvanish.prefix.sa")) {
            return new String[]{"6","#AA0000"};
        } else if(player.hasPermission("cvvanish.prefix.a")) {
            return new String[]{"5","#55FFFF"};
        } else if(player.hasPermission("cvvanish.prefix.smod")) {
            return new String[]{"4","#FF55FF"};
        } else if(player.hasPermission("cvvanish.prefix.mod")) {
            return new String[]{"3","#55FF55"};
        } else if(player.hasPermission("cvvanish.prefix.retired")) {
            return new String[]{"2","#AAAAAA"};
        } else if(player.hasPermission("cvvanish.prefix.helper")) {
            return new String[]{"1","#FFAA00"};
        } else {
            return new String[]{"0","#FFFFFF"};
        }
    }

    public boolean modOrHigher(ProxiedPlayer player) {
        return player.hasPermission("cvvanish.prefix.retired") ||
                player.hasPermission("cvvanish.prefix.mod") ||
                player.hasPermission("cvvanish.prefix.smod") ||
                player.hasPermission("cvvanish.prefix.a") ||
                player.hasPermission("cvvanish.prefix.sa");
    }

    private void executePacket(ProxiedPlayer player, net.md_5.bungee.protocol.packet.Team teamPacket) {
        if(teamPacket.getPlayers().length == 0) return;
        UUID teamUUID = teamManager.getRealNameUUID(teamPacket.getPlayers()[0]);
        if(teamUUID == null) return;
        if(waitForPLI(player, teamUUID)) {
            if(player.getName().equalsIgnoreCase("ToeMan_")) System.out.println("initial waitForPLI for toeman and team packet " + teamPacket.getName());
            UUID uuid = player.getUniqueId();
            for(int i = 1; i <= 5; i++) {
                List<ScheduledTask> tasks;
                if(this.queuedTeamsPacketTasks.containsKey(uuid) && this.queuedTeamsPacketTasks.get(uuid).containsKey(teamUUID)) {
                    tasks = this.queuedTeamsPacketTasks.get(uuid).get(teamUUID);
                } else {
                    tasks = new ArrayList<>();
                }
                ScheduledTask task = ProxyServer.getInstance().getScheduler().schedule(plugin, () -> {
                    if(player.isConnected()) {
                        if(!waitForPLI(player, teamUUID)) {
                            if(player.getName().equalsIgnoreCase("ToeMan_")) System.out.println("sending team packet to toeman and team packet " + teamPacket.getName());
                            sendPacket(player, teamPacket);
                            cancelQueuedTasks(uuid, teamUUID);
                        } else {
                            if(player.getName().equalsIgnoreCase("ToeMan_")) System.out.println("another wait waitForPLI for toeman and team packet " + teamPacket.getName());
                        }
                    }
                }, i, TimeUnit.SECONDS);
                tasks.add(task);
                HashMap<UUID, List<ScheduledTask>> teamTasks;
                if(this.queuedTeamsPacketTasks.containsKey(uuid)) {
                    teamTasks = this.queuedTeamsPacketTasks.get(uuid);
                } else {
                    teamTasks = new HashMap<>();
                }
                teamTasks.put(teamUUID, tasks);
                this.queuedTeamsPacketTasks.put(uuid, teamTasks);
            }
        } else {
            sendPacket(player, teamPacket);
        }
    }

    private void sendPacket(ProxiedPlayer player, DefinedPacket packet) {
        if(player.getPendingConnection().getVersion() >= 764) {
            try {
                ((UserConnection) player).sendPacketQueued(packet);
            } catch (Exception ignored) {

            }
        } else {
            player.unsafe().sendPacket(packet);
        }
    }

    private boolean waitForPLI(ProxiedPlayer player, UUID pliUUID) {
        return !CVTabList.getInstanceFor(player.getUniqueId()).hasReceivedPLI(pliUUID);
    }

    private void cancelQueuedTasks(UUID uuid, UUID teamUUID) {
        if(this.queuedTeamsPacketTasks.containsKey(uuid) && this.queuedTeamsPacketTasks.get(uuid).containsKey(teamUUID)) {
            List<ScheduledTask> teamTasks = new ArrayList<>(this.queuedTeamsPacketTasks.get(uuid).get(teamUUID));
            for(ScheduledTask task : teamTasks) {
                if(task != null) task.cancel();
                this.queuedTeamsPacketTasks.get(uuid).get(teamUUID).remove(task);
            }
        }
    }
}
