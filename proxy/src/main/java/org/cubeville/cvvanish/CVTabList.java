package org.cubeville.cvvanish;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.protocol.Property;
import net.md_5.bungee.protocol.packet.PlayerListItem;
import net.md_5.bungee.protocol.packet.PlayerListItemRemove;
import net.md_5.bungee.protocol.packet.PlayerListItemUpdate;
import net.md_5.bungee.tab.TabList;
import org.cubeville.cvvanish.teams.TeamHandler;
import org.cubeville.cvvanish.teams.TeamManager;

public class CVTabList extends TabList
{
    public void destroyReceivedPLIs() {
        Set<UUID> receivedPLIsCopy = new HashSet<>(receivedPLIs);
        for(UUID uuid : receivedPLIsCopy) {
            sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, createUuidItem(uuid));
        }
        clearReceivedPLIs();
    }

    private final Set<UUID> receivedPLIs = new HashSet<>();
    public boolean hasReceivedPLI(UUID uuid) { return this.receivedPLIs.contains(uuid); }
    public void addReceivedPLI(UUID uuid) { this.receivedPLIs.add(uuid); }
    public void removeReceivedPLI(UUID uuid) { this.receivedPLIs.remove(uuid); }
    public void clearReceivedPLIs() { this.receivedPLIs.clear(); }

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
        /*if(playerAddPackets.get(uuid) != null && player.getServer() != null && ProxyServer.getInstance().getPlayer(uuid) != null && ProxyServer.getInstance().getPlayer(uuid).getServer() != null) {*/
        if(player.getServer() != null && ProxyServer.getInstance().getPlayer(uuid) != null && ProxyServer.getInstance().getPlayer(uuid).getServer() != null) {
            if(!(plugin.getTeamEnabledServers().contains(player.getServer().getInfo().getName()) &&
                    plugin.getTeamEnabledServers().contains(ProxyServer.getInstance().getPlayer(uuid).getServer().getInfo().getName()))) {
                /*playerAddPacketsLock.lock();*/
                //try {
                PlayerListItem.Item item = createUuidItem(uuid);
                if(teamManager.getFakeName(item.getUuid()) == null) {
                    System.out.println("fake name null in showplayer for player: " + ProxyServer.getInstance().getPlayer(uuid).getName() + " to player: " + player.getName());
                    return;
                }
                item.setUsername(teamManager.getFakeName(item.getUuid()));
                sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, item);
                if(player.getName().equalsIgnoreCase("toeman_")) {
                    System.out.println("sending PLI ADD_PLAYER fake name packet to toeman_ for name: " + item.getUsername() + " displayname: " +
                            item.getDisplayName() + " uuid: " + item.getUuid());
                }
            } else {
                PlayerListItem.Item item = createUuidItem(uuid);
                item.setUsername(ProxyServer.getInstance().getPlayer(uuid).getName());
                sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, item);
                if(player.getName().equalsIgnoreCase("toeman_")) {
                    System.out.println("sending PLI ADD_PLAYER fake name packet to toeman_ for name: " + item.getUsername() + " displayname: " +
                            item.getDisplayName() + " uuid: " + item.getUuid());
                }
            }
        }
    }

    public void sendRealNamesToPlayer() {
        for(ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
            if(p != null && plugin.getTeamEnabledServers().contains(p.getServer().getInfo().getName())) {
            /*if(p.getServer().getInfo().getName().equalsIgnoreCase(player.getServer().getInfo().getName())) {*/
                if(!plugin.isPlayerUnlisted(p.getUniqueId()) || teamHandler.canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId())) {
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
            if( p!= null && (!plugin.isPlayerUnlisted(p.getUniqueId()) || teamHandler.canSenderSeePlayerState(player.getUniqueId(), p.getUniqueId()))) {
                PlayerListItem.Item itemremove = createUuidItem(p.getUniqueId());
                sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, itemremove);
                PlayerListItem.Item itemadd = createUuidItem(p.getUniqueId());
                if(teamManager.getFakeName(itemadd.getUuid()) == null) {
                    System.out.println("fake name null in sendfakenameStoplayer for player: " + ProxyServer.getInstance().getPlayer(p.getUniqueId()).getName() + " to player: " + player.getName());
                    return;
                }
                itemadd.setUsername(teamManager.getFakeName(itemadd.getUuid()));
                sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, itemadd);
                /*if(playerAddPackets.get(p.getUniqueId()) != null) {
                    sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, playerAddPackets.get(p.getUniqueId()));
                }*/
            }
        }
    }

    public void sendRealNameToPlayer(UUID uuid) {
        if(!plugin.isPlayerUnlisted(uuid) || teamHandler.canSenderSeePlayerState(player.getUniqueId(), uuid)) {
            PlayerListItem.Item item = createUuidItem(uuid);
            sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, item);
            item.setUsername(ProxyServer.getInstance().getPlayer(uuid).getName());
            sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, item);
        }
    }

    public void sendFakeNameToPlayer(UUID uuid) {
        if(!plugin.isPlayerUnlisted(uuid) || teamHandler.canSenderSeePlayerState(player.getUniqueId(), uuid)) {
            PlayerListItem.Item itemremove = createUuidItem(uuid);
            sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, itemremove);
            //item.setUsername(ProxyServer.getInstance().getPlayer(uuid).getName());
            PlayerListItem.Item itemadd = createUuidItem(uuid);
            if(teamManager.getFakeName(itemadd.getUuid()) == null) {
                System.out.println("fake name null in sendfakenametoplayer for player: " + ProxyServer.getInstance().getPlayer(uuid).getName() + " to player: " + player.getName());
                return;
            }
            itemadd.setUsername(teamManager.getFakeName(itemadd.getUuid()));
            sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, itemadd);
            /*if(playerAddPackets.get(uuid) != null) {
                sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, playerAddPackets.get(uuid));
            }*/
        }
    }

    public CVTabList(ProxiedPlayer player)
    {
        super( player );
    }

    public void sendSingleItemPacket(PlayerListItem.Action action, PlayerListItem.Item item) {

        //System.out.println("PLI packet being sent to: " + player.getName() + " Action: " + action + " Username: " + item.getUsername() + " UUID: " + item.getUuid());

        int serverVersion = player.getPendingConnection().getVersion();
        if(serverVersion <= 758) {
            /*PlayerListItem playerListItem = new PlayerListItem();
            playerListItem.setAction(action);
            item.setPublicKey(null);
            if(!item.getUuid().equals(player.getUniqueId())) {
                item.setGamemode(1);
            } else if(item.getGamemode() != null) {

            *//*} else if(playerAddPackets.get(item.getUuid()) != null) {
                item.setGamemode(playerAddPackets.get(item.getUuid()).getGamemode());*//*
            } else {
                item.setGamemode(1);
            }
            item.setPing(0);
            //if(plugin.playerSkins.containsKey(item.getUuid())) item.setProperties(plugin.playerSkins.get(item.getUuid()));
            PlayerListItem.Item items[] = new PlayerListItem.Item[1];
            items[0] = item;
            playerListItem.setItems(items);
            boolean sent = teamHandler.finalSendPacket(player, playerListItem);
            if(sent) {
                this.receivedPLIs.add(item.getUuid());
            } else {
                this.queuedPackets.add(playerListItem);
            }*/
        } else {
            if(action.equals(PlayerListItem.Action.ADD_PLAYER)) {
                if(!plugin.getConnectedPlayers().contains(item.getUuid()) || plugin.playerSkinTextures.containsKey(item.getUuid())) {
                    PlayerListItemUpdate playerListItemUpdate = new PlayerListItemUpdate();
                    PlayerListItem.Item items[] = new PlayerListItem.Item[1];
                    if(item.getListed() == null) {
                        item.setListed(true);
                    }
                    item.setPublicKey(null);
                    if(!item.getUuid().equals(player.getUniqueId())) {
                        item.setGamemode(1);
                    } else if(item.getGamemode() != null) {

                /*} else if(playerAddPackets.get(item.getUuid()) != null) {
                    item.setGamemode(playerAddPackets.get(item.getUuid()).getGamemode());*/
                    } else {
                        item.setGamemode(1);
                    }
                    item.setPing(0);
                    if(plugin.playerSkinTextures.containsKey(item.getUuid())) {
                        Property[] propArray = new Property[1];
                        String[] props = plugin.playerSkinTextures.get(item.getUuid());
                        propArray[0] = new Property("textures", props[0], props[1]);
                        item.setProperties(propArray);
                    }
                    //System.out.println("property being sent via pli construction for " + item.getUuid() + " to player " + player.getName());
                    //System.out.println(item.getProperties()[0]);
                    items[0] = item;
                    playerListItemUpdate.setItems(items);
                    playerListItemUpdate.setActions(EnumSet.of(PlayerListItemUpdate.Action.ADD_PLAYER, PlayerListItemUpdate.Action.UPDATE_LISTED));
                    boolean sent = teamHandler.finalSendPacket(player, playerListItemUpdate);
                    if(sent) addReceivedPLI(item.getUuid());
                }
            } else if(action.equals(PlayerListItem.Action.REMOVE_PLAYER)) {
                PlayerListItemRemove playerListItemRemove = new PlayerListItemRemove();
                UUID uuids[] = new UUID[1];
                item.setPublicKey(null);
                //if(plugin.playerSkins.containsKey(item.getUuid())) item.setProperties(plugin.playerSkins.get(item.getUuid()));
                uuids[0] = item.getUuid();
                playerListItemRemove.setUuids(uuids);
                boolean sent = teamHandler.finalSendPacket(player, playerListItemRemove);
                if(sent) removeReceivedPLI(item.getUuid());
            } else if(action.equals(PlayerListItem.Action.UPDATE_GAMEMODE)) {
                if(item.getUuid().equals(player.getUniqueId())) {
                    PlayerListItemUpdate playerListItemUpdate = new PlayerListItemUpdate();
                    PlayerListItem.Item items[] = new PlayerListItem.Item[1];
                    if(item.getListed() == null) {
                        item.setListed(true);
                    }
                    item.setPublicKey(null);
                    //if(plugin.playerSkins.containsKey(item.getUuid())) item.setProperties(plugin.playerSkins.get(item.getUuid()));
                    if(!item.getUuid().equals(player.getUniqueId())) {
                        item.setGamemode(1);
                    } else if(item.getGamemode() != null) {

                    /*} else if(playerAddPackets.get(item.getUuid()) != null) {
                        item.setGamemode(playerAddPackets.get(item.getUuid()).getGamemode());*/
                    } else {
                        item.setGamemode(1);
                    }
                    item.setPing(0);
                    items[0] = item;
                    playerListItemUpdate.setItems(items);
                    playerListItemUpdate.setActions(EnumSet.of(PlayerListItemUpdate.Action.UPDATE_GAMEMODE));
                    teamHandler.finalSendPacket(player, playerListItemUpdate);
                }
            }
        }
    }

    public PlayerListItem.Item createUuidItem(UUID uuid) {
        PlayerListItem.Item ret = new PlayerListItem.Item();
        ret.setUuid(uuid);
        return ret;
    }
    
    @Override
    public void onUpdate(PlayerListItem playerListItem) //todo old?
    {
        System.out.println("this is firing? ig it still does");
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
                    boolean bad = item.getUsername().contains("§");
                    if(bad) System.out.println("PLI contained §! UUID:" + item.getUuid() + " username:" + item.getUsername());
                    if(plugin.isConnectedPlayer(item.getUuid())) {
                        //playerAddPacketsLock.lock();
                        boolean lck = true;
                        try {
                            //if(!playerAddPackets.containsKey(item.getUuid())) {

                                //item.setGamemode(1);

                                String fakeName = teamManager.getFakeName(item.getUuid());
                                if(fakeName != null) {
                                    item.setUsername(fakeName);
                                    //playerAddPackets.put(item.getUuid(), item);

                                    //playerAddPacketsLock.unlock();
                                    lck = false;

                                    plugin.addPacketAvailable(item.getUuid());
                                }
                            //}
                        }
                        finally {
                            //if(lck) playerAddPacketsLock.unlock();
                        }
                    }
                    //else { TODO: How to handle this if at all?
                    //    System.out.println("Ignoring add player packet cause plugin doesn't think it's a connected player.");
                    //}
                    if(bad) System.out.println("PLI contained §! UUID:" + item.getUuid() + " username:" + item.getUsername());
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
            for(PlayerListItem.Item item : playerListItem.getItems()) {
                //System.out.println(playerListItem.getAction());
                sendSingleItemPacket(playerListItem.getAction(), item);
            }
            //player.unsafe().sendPacket(playerListItem);
        }
    }

    @Override
    public void onUpdate(PlayerListItemRemove playerListItemRemove) {
        List<PlayerListItemRemove> updatedItemList = new ArrayList<>();
        for(UUID uuid : playerListItemRemove.getUuids()) {
            if(!playerList.contains(uuid)) {
                PlayerListItemRemove item = new PlayerListItemRemove();
                UUID[] uuid1 = new UUID[1];
                uuid1[0] = uuid;
                item.setUuids(uuid1);
                updatedItemList.add(item);
            }
        }
        if(updatedItemList.size() > 0) {
            for(PlayerListItemRemove item : updatedItemList) {
                PlayerListItem.Item item1 = new PlayerListItem.Item();
                item1.setUuid(item.getUuids()[0]);
                sendSingleItemPacket(PlayerListItem.Action.REMOVE_PLAYER, item1);
            }
        }
    }

    @Override
    public void onUpdate(PlayerListItemUpdate playerListItemUpdate) {
        List<PlayerListItem.Item> updatedItemList = new ArrayList<>();
        for(PlayerListItem.Item item : playerListItemUpdate.getItems()) {
            boolean isPlayer = false;
            synchronized(playerList) { if(playerList.contains(item.getUuid())) isPlayer = true; }
            if(!isPlayer) { // NPC
                updatedItemList.add(item);
            }
            else { // Player
                if(plugin.isConnectedPlayer(item.getUuid())) {
                    if(playerListItemUpdate.getActions().contains(PlayerListItemUpdate.Action.ADD_PLAYER)) {
                        boolean bad = item.getUsername().contains("§");
                        if(bad) System.out.println("PLI contained §! UUID:" + item.getUuid() + " username:" + item.getUsername());
                        //playerAddPacketsLock.lock();
                        boolean lck = true;
                        try {
                            //if(!playerAddPackets.containsKey(item.getUuid())) {

                            //item.setGamemode(1);

                            String fakeName = teamManager.getFakeName(item.getUuid());
                            if (fakeName != null) {
                                item.setUsername(fakeName);
                                //playerAddPackets.put(item.getUuid(), item);

                                //playerAddPacketsLock.unlock();
                                lck = false;

                                if(item.getProperties() != null) {
                                    //System.out.println("pli passthrough for " + item.getUuid() + " to player " + player.getName());
                                    //System.out.println(item.getProperties()[0]);
                                    String[] propArray = new String[2];
                                    propArray[0] = item.getProperties()[0].getValue();
                                    propArray[1] = item.getProperties()[0].getSignature();
                                    plugin.playerSkinTextures.put(item.getUuid(), propArray);
                                }
                                plugin.addPacketAvailable(item.getUuid());
                            }
                                //}
                        } finally {
                            //if (lck) playerAddPacketsLock.unlock();
                        }
                        if(bad) System.out.println("PLI contained §! UUID:" + item.getUuid() + " username:" + item.getUsername());
                    } else if(playerListItemUpdate.getActions().contains(PlayerListItemUpdate.Action.UPDATE_GAMEMODE) && playerListItemUpdate.getActions().size() == 1) {
                        if(item.getUuid().equals(getUniqueId())) {
                            updatedItemList.add(item);
                        }
                    }
                }
                    //else { TODO: How to handle this if at all?
                    //    System.out.println("Ignoring add player packet cause plugin doesn't think it's a connected player.");
                    //}
            }
        }
        if(updatedItemList.size() > 0) {
            PlayerListItem.Item items[] = new PlayerListItem.Item[updatedItemList.size()];
            updatedItemList.toArray(items);
            playerListItemUpdate.setItems(items);
            for(PlayerListItem.Item item : playerListItemUpdate.getItems()) {
                //System.out.println(playerListItemUpdate.getActions().iterator().next());
                if(playerListItemUpdate.getActions().iterator().next().equals(PlayerListItemUpdate.Action.UPDATE_GAMEMODE)) {
                    sendSingleItemPacket(PlayerListItem.Action.UPDATE_GAMEMODE, item);
                } else if(playerListItemUpdate.getActions().iterator().next().equals(PlayerListItemUpdate.Action.ADD_PLAYER)) {
                    sendSingleItemPacket(PlayerListItem.Action.ADD_PLAYER, item);
                }
            }
            //player.unsafe().sendPacket(playerListItem);
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
        //playerAddPackets.remove(getUniqueId());
    }

    public UUID getUniqueId()
    {
        return player.getUniqueId();
    }

}
