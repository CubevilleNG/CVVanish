package org.cubeville.cvvanish;

import java.io.File;
import java.io.IOException;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.score.Team;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import org.cubeville.cvipc.CVIPC;
import org.cubeville.cvipc.IPCInterface;

import org.cubeville.cvplayerdata.PlayerDataManager;
import org.cubeville.cvvanish.teams.TeamHandler;
import org.cubeville.cvvanish.teams.TeamManager;

public class CVVanish extends Plugin implements IPCInterface, Listener {

    private HashMap<UUID, Long> loginTime = new HashMap<>();
    private Set<UUID> connectedPlayers = new CopyOnWriteArraySet<>();

    private Set<UUID> invisiblePlayers = new CopyOnWriteArraySet<>();
    private Set<UUID> unlistedPlayers = new CopyOnWriteArraySet<>();

    private Set<UUID> pickupDisabledPlayers = new CopyOnWriteArraySet<>();
    private Set<UUID> interactDisabledPlayers = new CopyOnWriteArraySet<>();
    private Set<UUID> nightvisionEnabledPlayers = new CopyOnWriteArraySet<>();

    private Set<UUID> godPlayers = new CopyOnWriteArraySet<>();
    private Map<UUID, Set<UUID>> showPlayers = new HashMap<>();

    private Set<UUID> actionBarPlayers = new CopyOnWriteArraySet<>();
    
    private List<List<String>> tabListPrefixes;

    private CVIPC ipc;
    private ActionBarNotifier actionBarNotifier;

    private static CVVanish instance;
    public static CVVanish getInstance() { return instance; }

    public TeamManager teamManager;
    public TeamHandler teamHandler;
    public PlayerDataManager playerDataManager;

    public List<String> teamEnabledServers;

    public Map<UUID, String[]> playerSkinTextures;
    
    @Override
    public void onEnable() {
        PluginManager pm = getProxy().getPluginManager();
        pm.registerCommand(this, new VCommand(this));
        teamManager = new TeamManager(this);
        teamHandler = new TeamHandler(this, teamManager);
        pm.registerCommand(this, new VReloadCommand(teamHandler));
        pm.registerCommand(this, new TeamOverrideCommand(teamHandler));
        pm.registerListener(this, this);

        UserConnection.setTabListFactory(new CVTabListFactory());
        CVTabList.setPlugin(this);
        CVTabList.setTeamManager(teamManager);
        CVTabList.setTeamHandler(teamHandler);

        ipc = (CVIPC) pm.getPlugin("CVIPC");
        ipc.registerInterface("vanish", this);
        ipc.registerInterface("serverconnect", this);

        try {
            File configFile = new File(getDataFolder(), "config.yml");
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            tabListPrefixes = (List<List<String>>) config.get("prefixes");
        }
        catch(IOException e) {
            System.out.println("CVVanish could not load configuration");
        }

        actionBarNotifier = new ActionBarNotifier(this);
        actionBarNotifier.start();

        instance = this;

        teamEnabledServers = new ArrayList<>();
        teamEnabledServers.add("cv7amongus");
        teamEnabledServers.add("cv7bedwars");
        teamEnabledServers.add("ptown");

        playerSkinTextures = new HashMap<>();

        playerDataManager = PlayerDataManager.getInstance();
    }

    public TeamHandler getTeamHandler() {
        return this.teamHandler;
    }

    public List<String> getTeamEnabledServers() {
        return this.teamEnabledServers;
    }

    public PlayerDataManager getPDM() {
        return this.playerDataManager;
    }

    /*public void initPDM() {
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
    }*/

    public String getPlayerVisibleName(UUID uuid) {
        //ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        //if(player == null) return "";
        //return player.getDisplayName();
        return PlayerDataManager.getInstance().getPlayerVisibleName(uuid);
    }

    public void process(String server, String channel, String message) {
        if(channel.equals("serverconnect")) {
            for(UUID connectedPlayer: connectedPlayers) {
                sendPlayerVisibilityInvertedStatusIPCMessage(connectedPlayer, server);
                sendPlayerPickupInvertedStatusIPCMessage(connectedPlayer, server);
                sendPlayerInteractInvertedStatusIPCMessage(connectedPlayer, server);
                sendPlayerNightvisionEnabledStatusIPCMessage(connectedPlayer, server);
            }
        }
    }
    
    @EventHandler
    public void onPostLogin(final PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        connectedPlayers.add(uuid);
        if(!player.hasPermission("cvvanish.disable.actionbar")) actionBarPlayers.add(uuid);
        
        if(player.hasPermission("cvvanish.default.invisible")) {
            invisiblePlayers.add(uuid);
            setPlayerNightvisionEnabledStatus(uuid, true);
        }
        
        if(player.hasPermission("cvvanish.default.unlisted"))
            unlistedPlayers.add(uuid);

        if(player.hasPermission("cvvanish.default.pickupdisabled"))
            pickupDisabledPlayers.add(uuid);

        if(player.hasPermission("cvvanish.default.interactdisabled"))
            interactDisabledPlayers.add(uuid);

        loginTime.put(uuid, System.currentTimeMillis() / 1000L);
    }

    public void showExistingPlayers(UUID uuid) {
        for(UUID connectedPlayer: connectedPlayers) {
            if(!connectedPlayer.equals(uuid) &&
                    (!unlistedPlayers.contains(connectedPlayer) || teamHandler.canSenderSeePlayerState(uuid, connectedPlayer))) {
                CVTabList.getInstanceFor(uuid).showPlayer(connectedPlayer);
            }
        }
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer p = event.getPlayer();
        if(CVTabList.getInstanceFor(p.getUniqueId()) != null) CVTabList.getInstanceFor(p.getUniqueId()).clearReceivedPLIs();
        teamHandler.init(p);
        if(teamEnabledServers.contains(p.getServer().getInfo().getName())) {
            CVTabList.getInstanceFor(p.getUniqueId()).sendRealNamesToPlayer();
            for(UUID uuid : connectedPlayers) {
                ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
                if(teamEnabledServers.contains(player.getServer().getInfo().getName())) {
                    CVTabList.getInstanceFor(player.getUniqueId()).sendRealNameToPlayer(p.getUniqueId());
                }
            }
        } else if(event.getFrom() != null && teamEnabledServers.contains(event.getFrom().getName().toLowerCase())) {
            CVTabList.getInstanceFor(p.getUniqueId()).sendFakeNamesToPlayer();
            for(UUID uuid : connectedPlayers) {
                ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
                if(teamEnabledServers.contains(player.getServer().getInfo().getName())) {
                    CVTabList.getInstanceFor(player.getUniqueId()).sendFakeNameToPlayer(p.getUniqueId());
                }
            }
        }

        for(UUID source : showPlayers.keySet()) {
            if(showPlayers.get(source).contains(p.getUniqueId())) {
                sendPlayerShowListIPCMessage(source, p.getUniqueId(), event.getPlayer().getServer().getInfo().getName());
            }
        }

        //TODO this ensures the player has been connected for less than 10 seconds
        if((System.currentTimeMillis() / 1000L) - loginTime.get(event.getPlayer().getUniqueId()) < 10) addPacketAvailable(event.getPlayer().getUniqueId());
    }

    /*public void teamsServerLogic(ServerInfo from, ProxiedPlayer player) {
        if(from != null) { //todo player is switching servers and not first login
            if(teamEnabledServers.contains(from.getName().toLowerCase())) { //todo server coming from was teams enabled
                if(teamEnabledServers.contains(player.getServer().getInfo().getName().toLowerCase())) { //todo server going to is teams enabled
                    initTeamsServer(player);
                } else { //todo server going to is not teams enabled
                    initNonTeamsServer(player);
                }
            } else if(teamEnabledServers.contains(player.getServer().getInfo().getName().toLowerCase())) { //todo server going to is teams enabled
                initTeamsServer(player);
            }
        } else { //todo player is on first login
            if(teamEnabledServers.contains(player.getServer().getInfo().getName().toLowerCase())) { //todo server going to is teams enabled
                initTeamsServer(player);
            }
        }
    }

    public void initNonTeamsServer(ProxiedPlayer player) {
        CVTabList.getInstanceFor(player.getUniqueId()).sendFakeNamesToPlayer();
        for(UUID uuid : connectedPlayers) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            if(teamEnabledServers.contains(p.getServer().getInfo().getName().toLowerCase())) {
                CVTabList.getInstanceFor(p.getUniqueId()).sendFakeNameToPlayer(player.getUniqueId());
            }
        }
    }

    public void initTeamsServer(ProxiedPlayer player) {
        CVTabList.getInstanceFor(player.getUniqueId()).sendFakeNamesToPlayer();
        CVTabList.getInstanceFor(player.getUniqueId()).sendRealNamesToPlayer();
        for(UUID uuid : connectedPlayers) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            if(player.getServer().getInfo().getName().equalsIgnoreCase(p.getServer().getInfo().getName())) {
                CVTabList.getInstanceFor(p.getUniqueId()).sendRealNameToPlayer(player.getUniqueId());
            }
        }
    }*/

    @EventHandler
    public void onDisconnect(final PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        connectedPlayers.remove(uuid);
        actionBarPlayers.remove(uuid);
        boolean unlisted = unlistedPlayers.contains(uuid);
        if(unlisted) unlistedPlayers.remove(uuid);
        invisiblePlayers.remove(uuid);
        for(UUID targetPlayer: connectedPlayers) {
            if(!unlisted || teamHandler.canSenderSeePlayerState(targetPlayer, uuid)) {
                CVTabList.getInstanceFor(targetPlayer).hidePlayer(uuid);
            }
        }
        
        CVTabList.getInstanceFor(event.getPlayer().getUniqueId()).removeInstance();

        ipc.broadcastMessage("vanish|dcreset:" + uuid);
        godPlayers.remove(uuid);
        nightvisionEnabledPlayers.remove(uuid);
        pickupDisabledPlayers.remove(uuid);
        interactDisabledPlayers.remove(uuid);
        Team team = teamManager.getPlayerTeam(event.getPlayer().getUniqueId());
        if(team != null) {
            teamHandler.sendRemovePacketToServer(teamHandler.getTeamPacket(team), unlisted, event.getPlayer().getUniqueId());
        }
    }

    public void updatePlayer(UUID uuid) {
        CVTabList.updatePlayerAddPacket(uuid);
    }
    
    public void addPacketAvailable(UUID uuid) { // called by tablist as soon as the first player tablist packet is available, show to other players who are able to see them
        // TODO: Seems to be a bit too slow sometimes cause we create new packets which might get sent by netty after the player spawn packet was sent to a client. It would
        //       be better to have the original packets forwarded. (Current workaround: The mc vanish plugin sends an additional player spawn packet after a few seconds)
        boolean unlisted = unlistedPlayers.contains(uuid);
        for(UUID targetPlayer: connectedPlayers) {
            if(teamHandler.canSenderSeePlayerState(targetPlayer, uuid) || !unlisted || targetPlayer.equals(uuid)) {
                CVTabList.getInstanceFor(targetPlayer).showPlayer(uuid);
            }
        }
        /*if(ProxyServer.getInstance().getPlayer(uuid) != null) {
            teamsServerLogic(null, ProxyServer.getInstance().getPlayer(uuid));
        }*/
    }

    public void sendUpdatedPacket(UUID uuid) {
        boolean unlisted = unlistedPlayers.contains(uuid);
        for(UUID targetPlayer: connectedPlayers) {
            if(teamHandler.canSenderSeePlayerState(targetPlayer, uuid) || !unlisted || targetPlayer.equals(uuid)) {
                CVTabList.getInstanceFor(targetPlayer).hidePlayer(uuid);
                CVTabList.getInstanceFor(targetPlayer).showPlayer(uuid);
            }
        }
    }
    
    public String getPrefix(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if(player == null) return "";
        for(List<String> prefix: tabListPrefixes) {
            if(player.hasPermission(prefix.get(0))) {
                return prefix.get(1);
            }
        }
        return "";
    }

    public String getTabListColour(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if(player == null) return "";
        for(List<String> prefix: tabListPrefixes) {
            if(player.hasPermission(prefix.get(0))) {
                return prefix.get(2);
            }
        }
        return "white";
    }
    
    public void setPlayerVisible(UUID uuid) {
        setPlayerUnlistedStatus(uuid, false);
        setPlayerInvisibilityStatus(uuid, false);
        if(hasPermission(uuid, "cvvanish.disable.autopickupdisable") == false)
            setPlayerPickupDisabledStatus(uuid, false);
        if(hasPermission(uuid, "cvvanish.disable.autointeractdisable") == false)
            setPlayerInteractDisabledStatus(uuid, false);
        setPlayerNightvisionEnabledStatus(uuid, false);
        teamHandler.sendVisiblePacketToServer(teamHandler.getTeamPacket(teamManager.getPlayerTeam(uuid)), ProxyServer.getInstance().getPlayer(uuid));
    }

    public void setPlayerInvisible(UUID uuid) { 
        if(isPlayerVisible(uuid)) {
            if(hasPermission(uuid, "cvvanish.disable.autopickupdisable") == false)
                setPlayerPickupDisabledStatus(uuid, true);
            if(hasPermission(uuid, "cvvanish.disable.autointeractdisable") == false)
                setPlayerInteractDisabledStatus(uuid, true);
            setPlayerNightvisionEnabledStatus(uuid, true);
        }
        setPlayerUnlistedStatus(uuid, false);
        setPlayerInvisibilityStatus(uuid, true);
        teamHandler.sendInvisiblePacketToServer(teamHandler.getTeamPacket(teamManager.getPlayerTeam(uuid)), ProxyServer.getInstance().getPlayer(uuid));
    }

    public void setPlayerVanished(UUID uuid) {
        if(isPlayerVisible(uuid)) {
            if(hasPermission(uuid, "cvvanish.disable.autopickupdisable") == false)
                setPlayerPickupDisabledStatus(uuid, true);
            if(hasPermission(uuid, "cvvanish.disable.autointeractdisable") == false)
                setPlayerInteractDisabledStatus(uuid, true);
            setPlayerNightvisionEnabledStatus(uuid, true);
        }
        setPlayerUnlistedStatus(uuid, true);
        setPlayerInvisibilityStatus(uuid, true);
        teamHandler.sendVanishPacketToServer(teamHandler.getTeamPacket(teamManager.getPlayerTeam(uuid)), ProxyServer.getInstance().getPlayer(uuid));
    }

    public void switchPlayerVisibility(UUID uuid) {
        if(invisiblePlayers.contains(uuid)) {
            setPlayerVisible(uuid);
        }
        else {
            if(hasPermission(uuid, "cvvanish.default.unlisted"))
                setPlayerVanished(uuid);
            else
                setPlayerInvisible(uuid);
        }
    }
    
    public void setPlayerUnlistedStatus(UUID uuid, boolean unlisted) {
        if(unlisted != unlistedPlayers.contains(uuid)) {
            if(unlisted)
                unlistedPlayers.add(uuid);
            else
                unlistedPlayers.remove(uuid);
            for(UUID targetPlayer: connectedPlayers) {
                if(!targetPlayer.equals(uuid) && !teamHandler.canSenderSeePlayerState(targetPlayer, uuid)) {
                    if(unlisted)
                        CVTabList.getInstanceFor(targetPlayer).hidePlayer(uuid);
                    else
                        CVTabList.getInstanceFor(targetPlayer).showPlayer(uuid);
                }
            }
        }
    }

    public void setPlayerUnlistedStatus(UUID source, UUID target, boolean unlisted) {
        ProxiedPlayer sourceP = ProxyServer.getInstance().getPlayer(source);
        ProxiedPlayer targetP = ProxyServer.getInstance().getPlayer(target);
        if(sourceP != null && targetP != null) {
            if(unlisted) {
                if(teamHandler.canSenderSeePlayerState(source, target)) {
                    CVTabList.getInstanceFor(target).showPlayer(source);
                } else {
                    CVTabList.getInstanceFor(target).hidePlayer(source);
                }
            } else {
                CVTabList.getInstanceFor(target).showPlayer(source);
            }
        }
    }
    
    public void setPlayerInvisibilityStatus(UUID uuid, boolean invisible) {
        if(invisible != invisiblePlayers.contains(uuid)) {
            if(invisible)
                invisiblePlayers.add(uuid);
            else
                invisiblePlayers.remove(uuid);
            sendPlayerVisibilityInvertedStatusIPCMessage(uuid, null);
        }
    }
    
    public void setPlayerPickupDisabledStatus(UUID uuid, boolean disabled) {
        if(disabled != pickupDisabledPlayers.contains(uuid)) {
            if(disabled)
                pickupDisabledPlayers.add(uuid);
            else
                pickupDisabledPlayers.remove(uuid);
            sendPlayerPickupInvertedStatusIPCMessage(uuid, null);
        }
    }

    public void setPlayerInteractDisabledStatus(UUID uuid, boolean disabled) {
        if(disabled != interactDisabledPlayers.contains(uuid)) {
            if(disabled)
                interactDisabledPlayers.add(uuid);
            else
                interactDisabledPlayers.remove(uuid);
            sendPlayerInteractInvertedStatusIPCMessage(uuid, null);
        }
    }

    public void setPlayerNightvisionEnabledStatus(UUID uuid, boolean enabled) {
        if(enabled != nightvisionEnabledPlayers.contains(uuid)) {
            if(enabled)
                nightvisionEnabledPlayers.add(uuid);
            else
                nightvisionEnabledPlayers.remove(uuid);
            sendPlayerNightvisionEnabledStatusIPCMessage(uuid, null);
        }
    }

    public void setPlayerGodStatus(UUID uuid, boolean disabled) {
        if(disabled != godPlayers.contains(uuid)) {
            if(disabled)
                godPlayers.add(uuid);
            else
                godPlayers.remove(uuid);
            sendPlayerGodStatusIPCMessage(uuid, null);
        }
    }

    public void showListSendTeams(UUID source) {
        if(unlistedPlayers.contains(source)) {
            teamHandler.sendVanishPacketToServer(teamHandler.getTeamPacket(teamManager.getPlayerTeam(source)), ProxyServer.getInstance().getPlayer(source));
        } else if(invisiblePlayers.contains(source)) {
            teamHandler.sendInvisiblePacketToServer(teamHandler.getTeamPacket(teamManager.getPlayerTeam(source)), ProxyServer.getInstance().getPlayer(source));
        }
    }

    public void addToPlayerShowList(UUID source, UUID target) {
        Set<UUID> showList;
        if(showPlayers.containsKey(source)) {
            showList = showPlayers.get(source);
        } else {
            showList = new HashSet<>();
        }
        showList.add(target);
        showPlayers.put(source, showList);
        if(connectedPlayers.contains(target)) {
            sendPlayerShowListIPCMessage(source, target, null);
            setPlayerUnlistedStatus(source, target, unlistedPlayers.contains(source));
            showListSendTeams(source);
            CVTabList.getInstanceFor(target).showPlayer(source);
        }
    }

    public void removeFromPlayerShowList(UUID source, UUID target) {
        if(showPlayers.containsKey(source)) {
            Set<UUID> showList = showPlayers.get(source);
            showList.remove(target);
            showPlayers.put(source, showList);
            if(connectedPlayers.contains(target)) {
                sendPlayerShowListIPCMessage(source, target, null);
                setPlayerUnlistedStatus(source, target, unlistedPlayers.contains(source));
                showListSendTeams(source);
                CVTabList.getInstanceFor(target).hidePlayer(source);
            }
        }
    }

    public boolean isPlayerShownToPlayer(UUID source, UUID target) {
        return showPlayers.containsKey(source) && showPlayers.get(source).contains(target);
    }

    public List<String> getPlayersShownToPlayer(UUID source) {
        List<String> showList = new ArrayList<>();
        if(showPlayers.containsKey(source)) {
            for(UUID uuid : showPlayers.get(source)) {
                String p = playerDataManager.getPlayerName(uuid);
                if(p != null) showList.add(p);
            }
        }
        return showList;
    }

    private void sendPlayerShowListIPCMessage(UUID source, UUID target, String server) {
        boolean show = showPlayers.containsKey(source) && showPlayers.get(source).contains(target);
        sendPlayerStatusIPCMessage(source, (show ? "showto:" : "hidefrom:") + target, server);
    }
    
    private void sendPlayerVisibilityInvertedStatusIPCMessage(UUID uuid, String server) {
        boolean inverted = invisiblePlayers.contains(uuid);
        inverted ^= hasPermission(uuid, "cvvanish.default.invisible");
        sendPlayerStatusIPCMessage(uuid, inverted ? "vi" : "vr", server);
    }

    private void sendPlayerPickupInvertedStatusIPCMessage(UUID uuid, String server) {
        boolean inverted = pickupDisabledPlayers.contains(uuid);
        inverted ^= hasPermission(uuid, "cvvanish.default.pickupdisabled");
        sendPlayerStatusIPCMessage(uuid, inverted ? "pi" : "pr", server);
    }

    private void sendPlayerInteractInvertedStatusIPCMessage(UUID uuid, String server) {
        boolean inverted = interactDisabledPlayers.contains(uuid);
        inverted ^= hasPermission(uuid, "cvvanish.default.interactdisabled");
        sendPlayerStatusIPCMessage(uuid, inverted ? "ii" : "ir", server);
    }

    private void sendPlayerNightvisionEnabledStatusIPCMessage(UUID uuid, String server) {
        sendPlayerStatusIPCMessage(uuid, nightvisionEnabledPlayers.contains(uuid) ? "non" : "noff", server);
    }

    private void sendPlayerGodStatusIPCMessage(UUID uuid, String server) {
        sendPlayerStatusIPCMessage(uuid, godPlayers.contains(uuid) ? "god" : "ungod", server);
    }
    
    private void sendPlayerStatusIPCMessage(UUID uuid, String prefix, String server) {
        String message = "vanish|" + prefix + ":" + uuid;
        if(server == null)
            ipc.broadcastMessage(message);
        else
            ipc.sendMessage(server, message);
    }
    
    public boolean isPlayerVisible(UUID uuid) {
        return(!invisiblePlayers.contains(uuid));
    }

    public boolean isPlayerInvisible(UUID uuid) {
        return invisiblePlayers.contains(uuid);
    }
    
    public boolean isPlayerListed(UUID uuid) {
        return(!unlistedPlayers.contains(uuid));
    }

    public boolean isPlayerUnlisted(UUID uuid) {
        return(unlistedPlayers.contains(uuid));
    }
    
    public boolean isPlayerItemPickupDisabled(UUID uuid) {
        return pickupDisabledPlayers.contains(uuid);
    }

    public boolean isPlayerInteractDisabled(UUID uuid) {
        return interactDisabledPlayers.contains(uuid);
    }

    public boolean isPlayerGod(UUID uuid) {
        return godPlayers.contains(uuid);
    }

    public boolean isConnectedPlayer(UUID uuid) {
        boolean ret;
        ret = connectedPlayers.contains(uuid);
        return ret;
    }

    public boolean isActionBarActive(UUID uuid) {
        return actionBarPlayers.contains(uuid);
    }
    
    public boolean hasPermission(UUID uuid, String permission) {
        return ProxyServer.getInstance().getPlayer(uuid).hasPermission(permission);
    }

    public Set<UUID> getConnectedPlayers() {
        return connectedPlayers;
    }
}
