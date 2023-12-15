package org.cubeville.cvvanish.SAFunOptions;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.cubeville.cvvanish.CVVanish;

import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

public class SAFunLightning {

    public static void spawnLightning(Player player, CVVanish plugin, ProtocolManager protocolManager) {
        Location pLoc = player.getLocation();
        LinkedList<Location> strikeLocs = new LinkedList<>();
        strikeLocs.add(pLoc);
        strikeLocs.add(pLoc.clone().add(0.5, 0, 0));
        strikeLocs.add(pLoc.clone().add(0.25, 0, 0));
        strikeLocs.add(pLoc.clone().add(0.5, 0, 0.5));
        strikeLocs.add(pLoc.clone().add(0.25, 0, 0.25));
        strikeLocs.add(pLoc.clone().add(0, 0, 0.5));
        strikeLocs.add(pLoc.clone().add(0, 0, 0.25));
        strikeLocs.add(pLoc.clone().add(-0.5, 0, 0.5));
        strikeLocs.add(pLoc.clone().add(-0.25, 0, 0.25));
        strikeLocs.add(pLoc.clone().add(0.5, 0, -0.5));
        strikeLocs.add(pLoc.clone().add(0.25, 0, -0.25));
        strikeLocs.add(pLoc.clone().add(0, 0, -0.5));
        strikeLocs.add(pLoc.clone().add(0, 0, -0.25));
        strikeLocs.add(pLoc.clone().add(-0.5, 0, 0));
        strikeLocs.add(pLoc.clone().add(-0.25, 0, 0));
        strikeLocs.add(pLoc.clone().add(-0.5, 0, -0.5));
        strikeLocs.add(pLoc.clone().add(-0.25, 0, -0.25));

        Random r = new Random(System.currentTimeMillis());
        int entityID = 10000 + r.nextInt(20000);
        int time = 1;
        for(Location loc : strikeLocs) {
            int finalEntityID = entityID;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawnLightningStrike(pLoc, loc, finalEntityID, protocolManager), time);
            entityID++;
            time++;
        }
        time = 1;
        for(int i = 0; i <= 9; i++) {
            int finalEntityID = entityID;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawnLightningStrike(pLoc, strikeLocs.get(0), finalEntityID, protocolManager), time);
            entityID++;
            time++;
        }
        time = 1;
        for(Location loc : strikeLocs) {
            int finalEntityID = entityID;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawnLightningStrike(pLoc, loc, finalEntityID, protocolManager), time);
            entityID++;
            time++;
        }
    }

    private static void spawnLightningStrike(Location pLoc, Location loc, int entityID, ProtocolManager protocolManager) {
        PacketContainer spawnPacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityID);
        spawnPacket.getUUIDs().write(0, UUID.randomUUID());
        spawnPacket.getEntityTypeModifier().write(0, EntityType.LIGHTNING);
        spawnPacket.getDoubles()
                .write(0, loc.getX())
                .write(1, loc.getY())
                .write(2, loc.getZ());

        for(Player p : Bukkit.getOnlinePlayers()) {
            if(p.getWorld().equals(pLoc.getWorld())) {
                if(pLoc.distance(p.getLocation()) <= 50) protocolManager.sendServerPacket(p, spawnPacket);
            }
        }
    }
}
