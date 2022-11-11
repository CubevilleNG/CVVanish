package org.cubeville.cvvanish;

import java.io.File;
import java.io.IOException;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.score.Team;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import net.md_5.bungee.protocol.packet.Chat;
import org.cubeville.cvipc.CVIPC;
import org.cubeville.cvipc.IPCInterface;

import org.cubeville.cvchat.playerdata.PlayerDataManager;

public class CVVanish extends Plugin implements IPCInterface, Listener {

    private Set<UUID> connectedPlayers = new CopyOnWriteArraySet<>();

    private Set<UUID> invisiblePlayers = new CopyOnWriteArraySet<>();
    private Set<UUID> unlistedPlayers = new CopyOnWriteArraySet<>();

    private Set<UUID> pickupDisabledPlayers = new CopyOnWriteArraySet<>();
    private Set<UUID> interactDisabledPlayers = new CopyOnWriteArraySet<>();
    private Set<UUID> nightvisionEnabledPlayers = new CopyOnWriteArraySet<>();

    private Set<UUID> actionBarPlayers = new CopyOnWriteArraySet<>();
    
    private List<List<String>> tabListPrefixes;

    private CVIPC ipc;
    private ActionBarNotifier actionBarNotifier;

    private static CVVanish instance;
    public static CVVanish getInstance() { return instance; }

    private PlayerDataManager playerDataManager;

    public HashMap<String, Team> teams;
    
    @Override
    public void onEnable() {
        PluginManager pm = getProxy().getPluginManager();
        pm.registerCommand(this, new VCommand(this));
        pm.registerListener(this, this);

        UserConnection.setTabListFactory(new CVTabListFactory());
        CVTabList.setPlugin(this);

        ipc = (CVIPC) pm.getPlugin("CVIPC");
        ipc.registerInterface("vanish", this);
        ipc.registerInterface("serverconnect", this);

        try {
            File configFile = new File(getDataFolder(), "config.yml");
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            tabListPrefixes = (List<List<String>>) config.get("prefixes");
        }
        catch(IOException e) {
            System.out.println("Could not load configuration");
        }

        actionBarNotifier = new ActionBarNotifier(this);
        actionBarNotifier.start();

        instance = this;
        teams = new HashMap<>();
        Team saTeam = new net.md_5.bungee.api.score.Team("5sa");
        saTeam.setDisplayName("sa");
        saTeam.setNameTagVisibility("always");
        saTeam.setCollisionRule("always");
        saTeam.setFriendlyFire((byte) 0);
        saTeam.setColor(4);
        saTeam.setPrefix("[SA] ");
        saTeam.setSuffix("");
        this.teams.put("sa", saTeam);
        Team aTeam = new Team("4a");
        aTeam.setDisplayName("a");
        aTeam.setNameTagVisibility("always");
        aTeam.setCollisionRule("always");
        aTeam.setFriendlyFire((byte) 0);
        aTeam.setColor(11);
        aTeam.setPrefix("[A] ");
        aTeam.setSuffix("");
        this.teams.put("a", aTeam);
        Team smTeam = new Team("2sm");
        smTeam.setDisplayName("sm");
        smTeam.setNameTagVisibility("always");
        smTeam.setCollisionRule("always");
        smTeam.setFriendlyFire((byte) 0);
        smTeam.setColor(13);
        smTeam.setPrefix("[SM] ");
        smTeam.setSuffix("");
        this.teams.put("sm", smTeam);
        Team mTeam = new Team("1m");
        mTeam.setDisplayName("m");
        mTeam.setNameTagVisibility("always");
        mTeam.setCollisionRule("always");
        mTeam.setFriendlyFire((byte) 0);
        mTeam.setColor(10);
        mTeam.setPrefix("[M] ");
        mTeam.setSuffix("");
        this.teams.put("m", mTeam);
        Team rTeam = new Team("1r");
        rTeam.setDisplayName("r");
        rTeam.setNameTagVisibility("always");
        rTeam.setCollisionRule("always");
        rTeam.setFriendlyFire((byte) 0);
        rTeam.setColor(7);
        rTeam.setPrefix("[R] ");
        rTeam.setSuffix("");
        this.teams.put("r", rTeam);
        Team cTeam = new Team("0c");
        cTeam.setDisplayName("c");
        cTeam.setNameTagVisibility("always");
        cTeam.setCollisionRule("always");
        cTeam.setFriendlyFire((byte) 0);
        cTeam.setColor(15);
        cTeam.setPrefix("[C] ");
        cTeam.setSuffix("");
        this.teams.put("c", cTeam);
    }

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

        // show the players already on the server to the new player
        for(UUID connectedPlayer: connectedPlayers) {
            if(connectedPlayer.equals(event.getPlayer().getUniqueId()) == false &&
               (unlistedPlayers.contains(connectedPlayer) == false ||
                event.getPlayer().hasPermission("cvvanish.override") == true)) {
                CVTabList.getInstanceFor(event.getPlayer().getUniqueId()).showPlayer(connectedPlayer);
            }
        }
        ProxyServer.getInstance().getScheduler().schedule(this, () -> updateTeams(player.getUniqueId()), 1, TimeUnit.SECONDS);
    }

    public void updateTeams(UUID updatedPlayerUUID) {
        ProxiedPlayer updatedPlayer = ProxyServer.getInstance().getPlayer(updatedPlayerUUID);
        if(updatedPlayer.isConnected()) {
            if(updatedPlayer.hasPermission("cvvanish.prefix.sa")) {
                if(!this.teams.get("sa").getPlayers().contains(updatedPlayer.getName())) this.teams.get("sa").addPlayer(updatedPlayer.getName());
            } else if(updatedPlayer.hasPermission("cvvanish.prefix.a")) {
                if(!this.teams.get("a").getPlayers().contains(updatedPlayer.getName())) this.teams.get("a").addPlayer(updatedPlayer.getName());
            } else if(updatedPlayer.hasPermission("cvvanish.prefix.smod")) {
                if(!this.teams.get("sm").getPlayers().contains(updatedPlayer.getName())) this.teams.get("sm").addPlayer(updatedPlayer.getName());
            } else if(updatedPlayer.hasPermission("cvvanish.prefix.mod")) {
                if(!this.teams.get("m").getPlayers().contains(updatedPlayer.getName())) this.teams.get("m").addPlayer(updatedPlayer.getName());
            } else if(updatedPlayer.hasPermission("cvvanish.prefix.retired")) {
                if(!this.teams.get("r").getPlayers().contains(updatedPlayer.getName())) this.teams.get("r").addPlayer(updatedPlayer.getName());
            } else {
                if(!this.teams.get("c").getPlayers().contains(updatedPlayer.getName())) this.teams.get("c").addPlayer(updatedPlayer.getName());
            }
        } else {
            for(String teamName : this.teams.keySet()) {
                this.teams.get(teamName).removePlayer(updatedPlayer.getName());
            }
        }
        for(UUID uuid : connectedPlayers) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
            for(String s : this.teams.keySet()) {
                Team realTeam = this.teams.get(s);
                net.md_5.bungee.protocol.packet.Team team = new net.md_5.bungee.protocol.packet.Team();
                team.setName(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getName())));
                team.setDisplayName(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getDisplayName())));
                team.setPrefix(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getPrefix())));
                team.setSuffix(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getSuffix())));
                team.setCollisionRule(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getCollisionRule())));
                team.setFriendlyFire(realTeam.getFriendlyFire());
                team.setNameTagVisibility(ComponentSerializer.toString(TextComponent.fromLegacyText(realTeam.getNameTagVisibility())));
                team.setColor(realTeam.getColor());
                Collection<String> newPlayerList = new ArrayList<>();
                for(String oldPlayer : realTeam.getPlayers()) {
                    UUID oldPlayerUUID = ProxyServer.getInstance().getPlayer(oldPlayer).getUniqueId();
                    if(!isPlayerListed(oldPlayerUUID) && p.hasPermission("cvvanish.override")) {
                        newPlayerList.add(oldPlayer); //todo do strike
                        //team.setColor(18);
                    } else if(isPlayerListed(oldPlayerUUID) && isPlayerInvisible(oldPlayerUUID) && p.hasPermission("cvvanish.override")) {
                        newPlayerList.add(oldPlayer); //todo do italic
                        //team.setColor(20);
                    } else if(isPlayerListed(oldPlayerUUID)) {
                        newPlayerList.add(oldPlayer);
                    }
                }
                team.setPlayers(newPlayerList.toArray(new String[0]));
                team.setMode((byte) 0);
                p.unsafe().sendPacket(team);
            }
        }
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        updateTeams(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDisconnect(final PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        connectedPlayers.remove(uuid);
        actionBarPlayers.remove(uuid);
        boolean unlisted = unlistedPlayers.contains(uuid);
        if(unlisted) unlistedPlayers.remove(uuid);
        invisiblePlayers.remove(uuid);
        for(UUID targetPlayer: connectedPlayers) {
            if(unlisted == false ||
               hasPermission(targetPlayer, "cvvanish.override")) {
                CVTabList.getInstanceFor(targetPlayer).hidePlayer(uuid);
            }
        }
        
        CVTabList.getInstanceFor(event.getPlayer().getUniqueId()).removeInstance();

        ipc.broadcastMessage("vanish|dcreset:" + uuid);
        nightvisionEnabledPlayers.remove(uuid);
        pickupDisabledPlayers.remove(uuid);
        interactDisabledPlayers.remove(uuid);
        updateTeams(uuid);
    }

    public void updatePlayer(UUID uuid) {
        CVTabList.updatePlayerAddPacket(uuid);
    }
    
    public void addPacketAvailable(UUID uuid) { // called by tablist as soon as the first player tablist packet is available, show to other players who are able to see them
        // TODO: Seems to be a bit too slow sometimes cause we create new packets which might get sent by netty after the player spawn packet was sent to a client. It would
        //       be better to have the original packets forwarded. (Current workaround: The mc vanish plugin sends an additional player spawn packet after a few seconds)
        boolean unlisted = unlistedPlayers.contains(uuid);
        for(UUID targetPlayer: connectedPlayers) {
            if(hasPermission(targetPlayer, "cvvanish.override") ||
               unlisted == false ||
               targetPlayer.equals(uuid)) {
                CVTabList.getInstanceFor(targetPlayer).showPlayer(uuid);
            }
        }
    }

    public void sendUpdatedPacket(UUID uuid) {
        boolean unlisted = unlistedPlayers.contains(uuid);
        for(UUID targetPlayer: connectedPlayers) {
            if(hasPermission(targetPlayer, "cvvanish.override") ||
               unlisted == false ||
               targetPlayer.equals(uuid)) {
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
        updateTeams(uuid);
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
        updateTeams(uuid);
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
                if(targetPlayer.equals(uuid) == false &&
                   hasPermission(targetPlayer, "cvvanish.override") == false) {
                    if(unlisted)
                        CVTabList.getInstanceFor(targetPlayer).hidePlayer(uuid);
                    else
                        CVTabList.getInstanceFor(targetPlayer).showPlayer(uuid);
                }
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
