package org.cubeville.cvvanish.SAFunOptions;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.cubeville.cvvanish.CVVanish;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SAFunBats {

    public static void spawnBats(Player player, CVVanish plugin, ProtocolManager protocolManager) {
        Location pLoc = player.getLocation();
        List<Location> batLocs = new ArrayList<>();
        batLocs.add(pLoc.clone().add(0.5, 0, 0));
        batLocs.add(pLoc.clone().add(0.25, 0, 0));
        batLocs.add(pLoc.clone().add(0.5, 0, 0.5));
        batLocs.add(pLoc.clone().add(0.25, 0, 0.25));
        batLocs.add(pLoc.clone().add(0, 0.25, 0.5));
        batLocs.add(pLoc.clone().add(0, 0.25, 0.25));
        batLocs.add(pLoc.clone().add(-0.5, 0.25, 0.5));
        batLocs.add(pLoc.clone().add(-0.25, 0.25, 0.25));
        batLocs.add(pLoc.clone().add(0.5, 0.5, -0.5));
        batLocs.add(pLoc.clone().add(0.25, 0.5, -0.25));
        batLocs.add(pLoc.clone().add(0, 0.5, -0.5));
        batLocs.add(pLoc.clone().add(0, 0.5, -0.25));
        batLocs.add(pLoc.clone().add(-0.5, 0.75, 0));
        batLocs.add(pLoc.clone().add(-0.25, 0.75, 0));
        batLocs.add(pLoc.clone().add(-0.5, 0.75, -0.5));
        batLocs.add(pLoc.clone().add(-0.25, 0.75, -0.25));

        Random r = new Random(System.currentTimeMillis());
        int entityID = 10000 + r.nextInt(20000);
        List<Integer> entityIDs = new ArrayList<>();
        for(Location loc : batLocs) {
            spawnBat(pLoc, loc, entityID, protocolManager);
            entityIDs.add(entityID);
            entityID++;
        }
        for(int i = 1; i <= 60; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> rotateBats(entityIDs, pLoc, protocolManager), i);
        }
        for(int i = 1; i <= 30; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> moveBats(entityIDs, pLoc, protocolManager), 2 * i);
        }
        for(int i = 0; i <= 2; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> playBatSounds(pLoc, entityIDs.size() / 2, plugin), 20 * i);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> despawnBats(entityIDs, pLoc, protocolManager), 60);
    }

    private static void playBatSounds(Location pLoc, int amount, CVVanish plugin) {
        for(Player p : Bukkit.getOnlinePlayers()) {
            if(p.getWorld().equals(pLoc.getWorld())) {
                if(pLoc.distance(p.getLocation()) <= 50) {
                    for(int i = 0; i <= amount; i++) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(p.getLocation(), Sound.ENTITY_BAT_AMBIENT, SoundCategory.MASTER, 0.5F, 1.0F), 4L * i);
                    }
                }
            }
        }
    }

    private static void spawnBat(Location pLoc, Location loc, int entityID, ProtocolManager protocolManager) {
        Vector vector = new Vector();
        PacketContainer spawnPacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityID);
        spawnPacket.getUUIDs().write(0, UUID.randomUUID());
        spawnPacket.getEntityTypeModifier().write(0, EntityType.BAT);
        spawnPacket.getDoubles()
                .write(0, loc.getX())
                .write(1, loc.getY())
                .write(2, loc.getZ());
        spawnPacket.getIntegers()
                .write(2, convertVelocity(vector.getX()))
                .write(3, convertVelocity(vector.getY()))
                .write(4, convertVelocity(vector.getZ()));
        spawnPacket.getBytes()
                .write(0, (byte) 0) //pitch
                .write(1, (byte) (getLookAwayYaw(loc, pLoc) * 256.0F / 360.0F)) //yaw
                .write(2, (byte) (getLookAwayYaw(loc, pLoc) * 256.0F / 360.0F)); //head yaw

        for(Player p : Bukkit.getOnlinePlayers()) {
            if(p.getWorld().equals(pLoc.getWorld())) {
                if(pLoc.distance(p.getLocation()) <= 50) protocolManager.sendServerPacket(p, spawnPacket);
            }
        }
    }

    private static void despawnBats(List<Integer> entityIDs, Location pLoc, ProtocolManager protocolManager) {
        PacketContainer despawnPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        despawnPacket.getIntLists().write(0, entityIDs);
        for(Player p : Bukkit.getOnlinePlayers()) {
            if(p.getWorld().equals(pLoc.getWorld())) protocolManager.sendServerPacket(p, despawnPacket);
        }
    }

    private static void moveBats(List<Integer> entityIDs, Location pLoc, ProtocolManager protocolManager) {
        for(int entityID : entityIDs) {
            Random r = new Random();
            int ranX = r.nextBoolean() ? 1 : -1;
            int ranZ = r.nextBoolean() ? 1 : -1;
            PacketContainer movePacket = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE);
            movePacket.getIntegers().write(0, entityID);
            movePacket.getShorts() //32 = 1
                    .write(0, (short) (((32 * .2) * ranX) * 128))
                    .write(1, (short) ((32 * .1) * 128))
                    .write(2, (short) (((32 * .2) * ranZ) * 128));
            movePacket.getBooleans().write(0, false);
            for(Player p : Bukkit.getOnlinePlayers()) {
                if(p.getWorld().equals(pLoc.getWorld())) {
                    if(pLoc.distance(p.getLocation()) <= 50) protocolManager.sendServerPacket(p, movePacket);
                }
            }
        }
    }

    private static void rotateBats(List<Integer> entityIDs, Location pLoc, ProtocolManager protocolManager) {
        for(int entityID : entityIDs) {
            byte yaw = (byte) ((Math.random() * 360) - 180);
            PacketContainer lookPacket = new PacketContainer(PacketType.Play.Server.ENTITY_LOOK);
            lookPacket.getIntegers().write(0, entityID);
            lookPacket.getBytes()
                    .write(0, yaw)
                    .write(1, (byte) 0);
            lookPacket.getBooleans().write(0, false);

            PacketContainer headPacket = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            headPacket.getIntegers().write(0, entityID);
            headPacket.getBytes().write(0, yaw);

            for(Player p : Bukkit.getOnlinePlayers()) {
                if(p.getWorld().equals(pLoc.getWorld())) {
                    if(pLoc.distance(p.getLocation()) <= 50) {
                        protocolManager.sendServerPacket(p, headPacket);
                        protocolManager.sendServerPacket(p, lookPacket);
                    }
                }
            }
        }
    }

    private static int convertVelocity(double velocity) {
        return (int) (clamp(velocity) * 8000);
    }

    private static double clamp(double targetNum) {
        return Math.max(-3.9, Math.min(targetNum, 3.9));
    }

    private static float getLookAwayYaw(Location source, Location target) {
        double xDiff = target.getX() - source.getX();
        double zDiff = target.getZ() - source.getZ();

        double DistanceXZ = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
        double newYaw = Math.acos(xDiff / DistanceXZ) * 180 / Math.PI;
        if (zDiff < 0.0)
            newYaw = newYaw + Math.abs(180 - newYaw) * 2;
        newYaw = (newYaw - 90);

        source.setYaw((float) newYaw);
        source.setDirection(source.getDirection().multiply(-1));
        return source.getYaw();
    }
}
