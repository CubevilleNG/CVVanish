package org.cubeville.cvvanish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.protocol.packet.PlayerListItem;
import net.md_5.bungee.tab.TabList;

public class CVTabList extends TabList
{
    private static Map<UUID, PlayerListItem.Item> playerAddPackets = new HashMap<>();
    private static Lock playerAddPacketsLock = new ReentrantLock();

    private static Set<UUID> playerList = new HashSet<>();

    private static CVVanish plugin;
    public static void setPlugin(CVVanish plugins) {
        plugin = plugins;
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
        sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, createUuidItem(uuid));
    }

    public void showPlayer(UUID uuid) {
        playerAddPacketsLock.lock();
        try {
            sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, playerAddPackets.get(uuid));
        }
        finally {
            playerAddPacketsLock.unlock();
        }
    }

    public CVTabList(ProxiedPlayer player)
    {
        super( player );
    }

    private void sendSingleItemPacket(PlayerListItem.Action action, PlayerListItem.Item item) {
        PlayerListItem playerListItem = new PlayerListItem();
        playerListItem.setAction(action);
        PlayerListItem.Item items[] = new PlayerListItem.Item[1];
        items[0] = item;
        playerListItem.setItems(items);
        player.unsafe().sendPacket(playerListItem);
    }

    private PlayerListItem.Item createUuidItem(UUID uuid) {
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
                if(playerListItem.getAction() == PlayerListItem.Action.ADD_PLAYER) {
                    if(plugin.isConnectedPlayer(item.getUuid())) {
                        playerAddPacketsLock.lock();
                        boolean lck = true;
                        try {
                            if(!playerAddPackets.containsKey(item.getUuid())) {
                                
                                item.setGamemode(1);
                                
                                //String name = plugin.getPrefix(item.getUuid()) + item.getUsername();  todo was already commented
                                /*String name = plugin.getPlayerVisibleName(item.getUuid());
                                if(plugin.isPlayerUnlisted(item.getUuid())) {
                                    name = "§m" + name;
                                } else if(plugin.isPlayerInvisible(item.getUuid())) {
                                    name = "§o" + name;
                                }
                                //String name = plugin.getPrefix(item.getUuid()) + plugin.getPlayerVisibleName(item.getUuid());
                                name = plugin.getPrefix(item.getUuid()) + name;
                                if(name.length() > 16) name = name.substring(0, 16);
                                item.setUsername(name);*/
                                
                                playerAddPackets.put(item.getUuid(), item);

                                playerAddPacketsLock.unlock();
                                lck = false;
                                
                                plugin.addPacketAvailable(item.getUuid());
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
