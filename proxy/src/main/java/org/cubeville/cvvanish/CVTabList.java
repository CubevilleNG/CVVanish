package org.cubeville.cvvanish;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.connection.LoginResult;
import net.md_5.bungee.protocol.Property;
import net.md_5.bungee.protocol.packet.PlayerListItem;
import net.md_5.bungee.tab.TabList;
import org.cubeville.cvvanish.teams.TeamHandler;
import org.cubeville.cvvanish.teams.TeamManager;

public class CVTabList extends TabList
{
    private static Map<UUID, PlayerListItem.Item> playerAddPackets = new HashMap<>();
    private static Lock playerAddPacketsLock = new ReentrantLock();

    private static Set<UUID> playerList = new HashSet<>();

    private static CVVanish plugin;
    public static void setPlugin(CVVanish plugins) {
        plugin = plugins;
    }

    private static TeamManager teamManager;
    public static void setTeamManager(TeamManager teamManagers) {
        teamManager = teamManagers;
    }

    private static TeamHandler teamHandler;
    public static void setTeamHandler(TeamHandler teamHandlers) {
        teamHandler = teamHandlers;
    }

    private static ConcurrentHashMap<UUID, CVTabList> instances = new ConcurrentHashMap<>();
    public static CVTabList getInstanceFor(UUID uuid) {
        return instances.get(uuid);
    }

    public static void updatePlayerAddPacket(UUID uuid) {
        playerAddPacketsLock.lock();
        boolean lck = true;
        try {
            if(playerAddPackets.containsKey(uuid)) {
                PlayerListItem.Item item = playerAddPackets.get(uuid);
                String name = plugin.getPrefix(item.getUuid()) + plugin.getPlayerVisibleName(item.getUuid());
                if(name.length() > 16) name = name.substring(0, 16);
                item.setUsername(name);
                
                playerAddPackets.put(item.getUuid(), item);
                
                playerAddPacketsLock.unlock();
                lck = false;

               plugin.sendUpdatedPacket(uuid);
            }
        }
        finally {
            if(lck) playerAddPacketsLock.unlock();
        }
    }

    public void hidePlayer(UUID uuid) {
        if(!teamHandler.canSenderSeePlayerState(player.getUniqueId(), uuid) || !plugin.getConnectedPlayers().contains(uuid)) {
            sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, createUuidItem(uuid));
        }
    }

    public void showPlayer(UUID uuid) {
        if(playerAddPackets.get(uuid) != null) {
            if(!(plugin.getTeamEnabledServers().contains(player.getServer().getInfo().getName().toLowerCase()) &&
                    plugin.getTeamEnabledServers().contains(ProxyServer.getInstance().getPlayer(uuid).getServer().getInfo().getName().toLowerCase()))) {
                playerAddPacketsLock.lock();
                try {
                    sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, playerAddPackets.get(uuid));
                }
                finally {
                    playerAddPacketsLock.unlock();
                }
            } else {
                PlayerListItem.Item item = createUuidItem(uuid);
                item.setUsername(ProxyServer.getInstance().getPlayer(uuid).getName());
                sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, item);
            }
        }
    }

    public void sendRealNamesToPlayer() {
        for(ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
            if(plugin.getTeamEnabledServers().contains(p.getServer().getInfo().getName().toLowerCase())) {
                if(teamHandler.canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId()) && plugin.getConnectedPlayers().contains(p.getUniqueId())) {
                    PlayerListItem.Item item = createUuidItem(p.getUniqueId());
                    sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, item);
                    item.setUsername(p.getName());
                    sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, item);
                }
            }
        }
    }

    public void sendFakeNamesToPlayer() {
        for(ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
            if(teamHandler.canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId())) {
                PlayerListItem.Item item = createUuidItem(p.getUniqueId());
                sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, item);
                sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, playerAddPackets.get(p.getUniqueId()));
            }
        }
    }

    public void sendRealNameToPlayer(UUID uuid) {
        if(teamHandler.canSenderSeePlayerState(player.getUniqueId(), uuid)) {
            PlayerListItem.Item item = createUuidItem(uuid);
            sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, item);
            item.setUsername(ProxyServer.getInstance().getPlayer(uuid).getName());
            sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, item);
        }
    }

    public void sendFakeNameToPlayer(UUID uuid) {
        if(teamHandler.canSenderSeePlayerState(player.getUniqueId(), uuid)) {
            PlayerListItem.Item item = createUuidItem(uuid);
            sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, item);
            item.setUsername(ProxyServer.getInstance().getPlayer(uuid).getName());
            sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, playerAddPackets.get(uuid));
        }
    }

    public CVTabList(ProxiedPlayer player)
    {
        super( player );
    }

    public void sendSingleItemPacket(PlayerListItem.Action action, PlayerListItem.Item item) {
        PlayerListItem playerListItem = new PlayerListItem();
        playerListItem.setAction(action);
        PlayerListItem.Item items[] = new PlayerListItem.Item[1];
        items[0] = item;
        playerListItem.setItems(items);
        player.unsafe().sendPacket(playerListItem);
    }

    public PlayerListItem.Item createUuidItem(UUID uuid) {
        PlayerListItem.Item ret = new PlayerListItem.Item();
        ret.setUuid(uuid);
        return ret;
    }
    
    @Override
    public void onUpdate(PlayerListItem playerListItem)
    {
        if(playerListItem.getAction() == PlayerListItem.Action.UPDATE_LATENCY) return; // TODO ... well, or not, kinda more convenient
        //         playerListItem.getAction() == PlayerListItem.Action.UPDATE_GAMEMODE) return;

        List<PlayerListItem.Item> updatedItemList = new ArrayList<>();
        for(PlayerListItem.Item item : playerListItem.getItems()) {
            boolean isPlayer = false;
            synchronized(playerList) { if(playerList.contains(item.getUuid())) isPlayer = true; }
            if(!isPlayer) { // NPC
                updatedItemList.add(item);
            }
            else { // Player
                if(playerListItem.getAction() == PlayerListItem.Action.ADD_PLAYER) { //PlayerListItem.Action.ADD_PLAYER
                    if(plugin.isConnectedPlayer(item.getUuid())) {
                        playerAddPacketsLock.lock();
                        boolean lck = true;
                        try {
                            if(!playerAddPackets.containsKey(item.getUuid())) {
                                
                                item.setGamemode(1);

                                String fakeName = teamManager.getFakeName(item.getUuid());
                                if(fakeName == null) {
                                    System.out.println("fake name was null! Cannot set username. User should relog");
                                } else {
                                    item.setUsername(fakeName);
                                    playerAddPackets.put(item.getUuid(), item);

                                    playerAddPacketsLock.unlock();
                                    lck = false;

                                    plugin.addPacketAvailable(item.getUuid());
                                }
                            }
                        }
                        finally {
                            if(lck) playerAddPacketsLock.unlock();
                        }
                    }
                    //else { TODO: How to handle this if at all?
                    //    System.out.println("Ignoring add player packet cause plugin doesn't think it's a connected player.");
                    //}
                }
                else if(playerListItem.getAction() == PlayerListItem.Action.UPDATE_GAMEMODE) {
                    if(item.getUuid().equals(getUniqueId())) {
                        updatedItemList.add(item);
                    }
                }
            }
        }

        if(updatedItemList.size() > 0) {
            PlayerListItem.Item items[] = new PlayerListItem.Item[updatedItemList.size()];
            updatedItemList.toArray(items);
            playerListItem.setItems(items);
            player.unsafe().sendPacket(playerListItem);
        }
    }

    @Override
    public void onPingChange(int ping)
    {
    }

    @Override
    public void onServerChange()
    {
    }

    @Override
    public void onConnect()
    {
        instances.put(player.getUniqueId(), this);
        synchronized(playerList) { playerList.add(player.getUniqueId()); }
    }

    @Override
    public void onDisconnect()
    {
    }

    public void removeInstance()
    {
        instances.remove(getUniqueId());
        playerAddPackets.remove(getUniqueId());
    }

    public UUID getUniqueId()
    {
        return player.getUniqueId();
    }

}
