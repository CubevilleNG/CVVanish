package org.cubeville.cvvanish;

import java.util.*;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.cubeville.cvipc.CVIPC;
import org.cubeville.cvipc.IPCInterface;

import com.lishid.openinv.IOpenInv;

public class CVVanish extends JavaPlugin implements IPCInterface, Listener {

    private CVIPC ipc;
    private IOpenInv openInv;
    private ProtocolManager protocolManager;
    
    private Set<UUID> invertedVisibility = new HashSet<>();
    private Set<UUID> pickupInvertedPlayers = new HashSet<>();
    private Set<UUID> interactInvertedPlayers = new HashSet<>();
    private Set<UUID> nightvisionEnabledPlayers = new HashSet<>();

    private Set<UUID> godEnabledPlayers = new HashSet<>();

    private Set<Material> interactDisallowedMaterials = new HashSet<>();

    private Runnable nightVisionUpdater;

    public void onEnable() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        
        ipc = (CVIPC) pm.getPlugin("CVIPC");
        ipc.registerInterface("vanish", this);

        openInv = (IOpenInv) pm.getPlugin("OpenInv");
	if(openInv == null) {
	    System.out.println("OpenInv not loaded!");
	}

        protocolManager = ProtocolLibrary.getProtocolManager();
        petListener();
	
        interactDisallowedMaterials.add(Material.ACACIA_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.BIRCH_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.CRIMSON_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.DARK_OAK_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.JUNGLE_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.OAK_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.SPRUCE_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.STONE_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.WARPED_PRESSURE_PLATE);

        interactDisallowedMaterials.add(Material.TRIPWIRE);
        
        nightVisionUpdater = new Runnable() {
                public void run() {
                    for(UUID uuid: nightvisionEnabledPlayers) {
                        addNightvisionEffectToPlayer(uuid);
                    }
                }
            };
        getServer().getScheduler().runTaskTimer(this, nightVisionUpdater, 20, 20);
    }
    
    public void onDisable() {
        ipc.deregisterInterface("vanish");
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    public void petListener() {
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if(event.isCancelled()) return;
                if(event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

                final PacketContainer packet = event.getPacket();
                final Entity entity = packet.getEntityModifier(event).read(0);

                if(entity == null) return;
                if(!(entity instanceof Tameable)) return;

                final WrappedDataWatcher dataWatcher = WrappedDataWatcher.getEntityWatcher(entity).deepClone();

                final WrappedDataWatcher.Serializer uuidSerializer = WrappedDataWatcher.Registry.getUUIDSerializer(true);

                final WrappedDataWatcher.WrappedDataWatcherObject optUUIDFieldWatcher = new WrappedDataWatcher.WrappedDataWatcherObject(18, uuidSerializer);

                final Optional<Object> optUUIDField = Optional.empty();

                dataWatcher.setObject(optUUIDFieldWatcher, optUUIDField);

                final List<WrappedDataValue> wrappedDataValueList = new ArrayList<>();

                for(final WrappedWatchableObject entry : dataWatcher.getWatchableObjects()) {
                    if(entry == null) continue;

                    final WrappedDataWatcher.WrappedDataWatcherObject watcherObject = entry.getWatcherObject();
                    wrappedDataValueList.add(
                            new WrappedDataValue(
                                    watcherObject.getIndex(),
                                    watcherObject.getSerializer(),
                                    entry.getRawValue()
                            )
                    );
                }

                packet.getDataValueCollectionModifier().write(0, wrappedDataValueList);

                event.setPacket(packet);
            }
        });
    }

    public void process(String channel, String message) {
        if(channel.equals("vanish")) {
            int separator = message.indexOf(":");
            if(separator == -1) throw new RuntimeException("Unparseable IPC message: " + message);
            String prefix = message.substring(0, separator);
            UUID uuid = UUID.fromString(message.substring(separator + 1));

            if(prefix.equals("vi")) {
                if(!invertedVisibility.contains(uuid)) {
                    invertedVisibility.add(uuid);
                    updatePlayer(uuid);
                }
            }
            else if(prefix.equals("vr")) {
                if(invertedVisibility.contains(uuid)) {
                    invertedVisibility.remove(uuid);
                    updatePlayer(uuid);
                }
            }
            else if(prefix.equals("pi")) {
                pickupInvertedPlayers.add(uuid);
            }
            else if(prefix.equals("pr")) {
                pickupInvertedPlayers.remove(uuid);
            }
            else if(prefix.equals("ii")) {
                interactInvertedPlayers.add(uuid);
            }
            else if(prefix.equals("ir")) {
                interactInvertedPlayers.remove(uuid);
            }
            else if(prefix.equals("non")) {
                nightvisionEnabledPlayers.add(uuid);
                addNightvisionEffectToPlayer(uuid);
            }
            else if(prefix.equals("noff")) {
                nightvisionEnabledPlayers.remove(uuid);
                removeNightvisionEffectFromPlayer(uuid);
            }
            else if(prefix.equals("god")) {
                godEnabledPlayers.add(uuid);
            }
            else if(prefix.equals("ungod")) {
                godEnabledPlayers.remove(uuid);
            }
            else if(prefix.equals("dcreset")) {
                godEnabledPlayers.remove(uuid);
                nightvisionEnabledPlayers.remove(uuid);
                interactInvertedPlayers.remove(uuid);
                pickupInvertedPlayers.remove(uuid);
                invertedVisibility.remove(uuid);
            }
            else {
                throw new RuntimeException("Unparseable IPC message: " + message);
            }
        }
    }

    public void removeNightvisionEffectFromPlayer(UUID uuid) {
        Player player = getServer().getPlayer(uuid);
        if(player != null) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }
    
    public void addNightvisionEffectToPlayer(UUID uuid) {
        Player player = getServer().getPlayer(uuid);
        if(player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, false, false), true);
        }
    }
    
    public void updatePlayer(UUID uuid) {
        Player player = Bukkit.getServer().getPlayer(uuid);
        if(player != null) {
            boolean invis = isPlayerInvisible(player);
            for(Player p: Bukkit.getServer().getOnlinePlayers()) {
                if(!p.getUniqueId().equals(uuid)) {
                    if(invis) {
                        if(!p.hasPermission("cvvanish.override")) {
                            p.hidePlayer(this, player); // TODO: different signature in 1.15
                        }
                    }
                    else {
                        p.showPlayer(this, player); // TODO: different signature in 1.15
                    }
                }
            }
            if(openInv != null) openInv.setPlayerSilentChestStatus(player, invis);
            if(invis)
                player.setMetadata("vanished", new FixedMetadataValue(this, true));
            else
                if(player.hasMetadata("vanished")) player.removeMetadata("vanished", this);
        }
    }

    public boolean isPlayerInvisible(Player player) {
        boolean ret = invertedVisibility.contains(player.getUniqueId());
        ret ^= player.hasPermission("cvvanish.default.invisible");
        return ret;
    }

    public boolean isPlayerPickupDisabled(Player player) {
        boolean ret = pickupInvertedPlayers.contains(player.getUniqueId());
        ret ^= player.hasPermission("cvvanish.default.pickupdisabled");
        return ret;
    }

    public boolean isPlayerInteractDisabled(Player player) {
        boolean ret = interactInvertedPlayers.contains(player.getUniqueId());
        ret ^= player.hasPermission("cvvanish.default.interactdisabled");
        return ret;
    }

    public boolean isPlayerGod(Player player) {
        return godEnabledPlayers.contains(player.getUniqueId());
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        for(Player p: Bukkit.getServer().getOnlinePlayers()) { 
            if(!p.getUniqueId().equals(player.getUniqueId())) {
                if(isPlayerInvisible(player) &&
                   p.hasPermission("cvvanish.override") == false) {
                    p.hidePlayer(this, player);
                }
                if(player.hasPermission("cvvanish.override") == false &&
                   isPlayerInvisible(p)) {
                    player.hidePlayer(this, p);
                }
            }
        }
        if(isPlayerInvisible(player)) {
            player.setMetadata("vanished", new FixedMetadataValue(this, true));
            if(openInv != null) openInv.setPlayerSilentChestStatus(player, true);
        }
        else  {
            if(player.hasMetadata("vanished")) player.removeMetadata("vanished", this);
            if(openInv != null) openInv.setPlayerSilentChestStatus(player, false);
        }
        if(nightvisionEnabledPlayers.contains(player.getUniqueId())) {
            addNightvisionEffectToPlayer(player.getUniqueId());
        }

        // send an additional player spawn packet after a few seconds, sometimes the tab list is too slow to have the player info available in time
        Runnable runnable = new Runnable() {
                public void run() {
                    for(Player p: Bukkit.getServer().getOnlinePlayers()) {
                        if(!p.getUniqueId().equals(player.getUniqueId())) {
                            if(p.canSee(player)) {
                                p.hidePlayer(player);
                                p.showPlayer(player);
                            }
                        }
                    }
                }
            };
        getServer().getScheduler().runTaskLater(this, runnable, 70);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if(!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if(isPlayerPickupDisabled(player))
           event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickupArrow(PlayerPickupArrowEvent event) {
        if(isPlayerPickupDisabled(event.getPlayer()))
            event.setCancelled(true);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if(!(event.getTarget() instanceof Player)) return;
        Player player = (Player) event.getTarget();
        if(isPlayerInvisible(player) || isPlayerGod(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if(!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if(isPlayerInvisible(player) || isPlayerGod(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(CreatureSpawnEvent event) {
        if(event.isCancelled()) return;
        if(!(event.getEntity() instanceof Phantom)) return;

        Location loc = event.getLocation();
        
        for(Player player: loc.getWorld().getPlayers()) {
            if(isPlayerInvisible(player) || isPlayerGod(player)) {
                Location ynl = player.getLocation().clone();
                ynl.setY(loc.getY());
                if(ynl.distance(loc) < 20) {
                    event.setCancelled(true);
                    System.out.println("Phantom spawn denied at hidden player " + player.getName());
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if(!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if(isPlayerInvisible(player) || isPlayerGod(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityCombust(EntityCombustEvent event) {
        if(!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if(isPlayerInvisible(player) || isPlayerGod(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getAction() != Action.PHYSICAL) return;
        if(event.getClickedBlock() == null) return;
        if(!interactDisallowedMaterials.contains(event.getClickedBlock().getType())) return;
        if(isPlayerInteractDisabled(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if(event.getEntityType() != EntityType.PLAYER) return;
        Player player = (Player) event.getEntity();
        if(!nightvisionEnabledPlayers.contains(player.getUniqueId())) return;
        if(event.getAction() == EntityPotionEffectEvent.Action.ADDED ||
           event.getAction() == EntityPotionEffectEvent.Action.CHANGED) {
            if(event.getCause() == EntityPotionEffectEvent.Cause.BEACON) {
                PotionEffect org = event.getNewEffect();
                PotionEffect n = new PotionEffect(org.getType(), org.getDuration(), org.getAmplifier(), false, false, true);
                player.addPotionEffect(n, true);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSculkSensorActivate(GenericGameEvent event) {
        if(event.getEntity() == null) return;
        if(!(event.getEntity() instanceof Player)) return;
        if(isPlayerInteractDisabled((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
}
