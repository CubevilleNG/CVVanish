package org.cubeville.cvvanish;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.scheduler.BukkitScheduler;
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
    private BukkitScheduler scheduler;

    public HashMap<String, HashMap<String, String>> worldTeamConfig;
    public HashMap<UUID, Long> playerTeamConfigQueue;

    public Map<Material, DyeColor> collarMappings = new HashMap<>();

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
        interactDisallowedMaterials.add(Material.MANGROVE_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.BAMBOO_PRESSURE_PLATE);
        interactDisallowedMaterials.add(Material.CHERRY_PRESSURE_PLATE);

        collarMappings.put(Material.WHITE_DYE, DyeColor.WHITE);
        collarMappings.put(Material.LIGHT_GRAY_DYE, DyeColor.LIGHT_GRAY);
        collarMappings.put(Material.GRAY_DYE, DyeColor.GRAY);
        collarMappings.put(Material.BLACK_DYE, DyeColor.BLACK);
        collarMappings.put(Material.BROWN_DYE, DyeColor.BROWN);
        collarMappings.put(Material.RED_DYE, DyeColor.RED);
        collarMappings.put(Material.ORANGE_DYE, DyeColor.ORANGE);
        collarMappings.put(Material.YELLOW_DYE, DyeColor.YELLOW);
        collarMappings.put(Material.LIME_DYE, DyeColor.LIME);
        collarMappings.put(Material.GREEN_DYE, DyeColor.GREEN);
        collarMappings.put(Material.CYAN_DYE, DyeColor.CYAN);
        collarMappings.put(Material.LIGHT_BLUE_DYE, DyeColor.LIGHT_BLUE);
        collarMappings.put(Material.BLUE_DYE, DyeColor.BLUE);
        collarMappings.put(Material.PURPLE_DYE, DyeColor.PURPLE);
        collarMappings.put(Material.MAGENTA_DYE, DyeColor.MAGENTA);
        collarMappings.put(Material.PINK_DYE, DyeColor.PINK);

        interactDisallowedMaterials.add(Material.TRIPWIRE);
        
        nightVisionUpdater = new Runnable() {
                public void run() {
                    for(UUID uuid: nightvisionEnabledPlayers) {
                        addNightvisionEffectToPlayer(uuid);
                    }
                }
            };
        this.scheduler = getServer().getScheduler();
        this.scheduler.runTaskTimer(this, nightVisionUpdater, 20, 20);

        this.worldTeamConfig = new HashMap<>();
        this.playerTeamConfigQueue = new HashMap<>();
        final File dataDir = getDataFolder();
        if(!dataDir.exists()) dataDir.mkdirs();
        File configFile = new File(dataDir, "config.yml");
        if(!configFile.exists()) {
            try {
                configFile.createNewFile();
                final InputStream inputStream = this.getResource(configFile.getName());
                final FileOutputStream fileOutputStream = new FileOutputStream(configFile);
                final byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = Objects.requireNonNull(inputStream).read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                fileOutputStream.flush();
                fileOutputStream.close();
            } catch(IOException e) {
                System.out.println("Unable to generate config file! " + e);
            }
        }
        YamlConfiguration mainConfig = new YamlConfiguration();
        try {
            mainConfig.load(configFile);
            ConfigurationSection worlds = mainConfig.getConfigurationSection("worlds");
            if(worlds != null) {
                for(World w : Bukkit.getWorlds()) {
                    ConfigurationSection world = worlds.getConfigurationSection(w.getName().toLowerCase());
                    if(world != null) {
                        HashMap<String, String> teamConfig = new HashMap<>();
                        String collision = world.getString("collision", "always");
                        String nametags = world.getString("nametags", "always");
                        teamConfig.put("collision", collision);
                        teamConfig.put("nametags", nametags);
                        this.worldTeamConfig.put(w.getName().toLowerCase(), teamConfig);
                    }
                }
            }
        } catch(IOException | InvalidConfigurationException e) {
            System.out.println("Unable to load config file! " + e);
        }
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
                if(WrappedDataWatcher.getEntityWatcher(entity).getObject(18) == null) return;
                if(!(WrappedDataWatcher.getEntityWatcher(entity).getObject(18) instanceof Optional)) return;

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPetInteract(PlayerInteractEntityEvent e) {
        if(e.isCancelled()) return;
        Entity entity = e.getRightClicked();
        if(!entity.getType().equals(EntityType.WOLF) && !entity.getType().equals(EntityType.PARROT) && !entity.getType().equals(EntityType.CAT)) return;
        if(!((Tameable) entity).isTamed()) return;
        if(((Tameable) entity).getOwner() == null || !((Tameable) entity).getOwner().getUniqueId().equals(e.getPlayer().getUniqueId())) return;
        if(e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.LEAD)) return;
        e.setCancelled(true);
        if(e.getHand().equals(EquipmentSlot.OFF_HAND)) return;
        Material item = e.getPlayer().getInventory().getItemInMainHand().getType();
        if(entity.getType().equals(EntityType.WOLF)) {
            if(item.equals(Material.AIR)) {
                ((Wolf) entity).setSitting(!((Wolf) entity).isSitting());
            } else if(collarMappings.containsKey(item)) {
                ((Wolf) entity).setCollarColor(collarMappings.get(item));
                ItemStack dye = e.getPlayer().getInventory().getItemInMainHand();
                dye.setAmount(dye.getAmount() - 1);
                e.getPlayer().getInventory().setItemInMainHand(dye);
            } else if(((Wolf) entity).isBreedItem(item) && !((Wolf) entity).isLoveMode()) {
                ((Wolf) entity).setLoveModeTicks(600);
                ItemStack food = e.getPlayer().getInventory().getItemInMainHand();
                food.setAmount(food.getAmount() - 1);
                e.getPlayer().getInventory().setItemInMainHand(food);
            }
        } else if(entity.getType().equals(EntityType.CAT)) {
            if(item.equals(Material.AIR)) {
                ((Cat) entity).setSitting(!((Cat) entity).isSitting());
            } else if(collarMappings.containsKey(item)) {
                ((Cat) entity).setCollarColor(collarMappings.get(item));
                ItemStack dye = e.getPlayer().getInventory().getItemInMainHand();
                dye.setAmount(dye.getAmount() - 1);
                e.getPlayer().getInventory().setItemInMainHand(dye);
            } else if(((Cat) entity).isBreedItem(item) && !((Cat) entity).isLoveMode()) {
                ((Cat) entity).setLoveModeTicks(600);
                ItemStack food = e.getPlayer().getInventory().getItemInMainHand();
                food.setAmount(food.getAmount() - 1);
                e.getPlayer().getInventory().setItemInMainHand(food);
            }
        } else if(entity.getType().equals(EntityType.PARROT)) {
            if(item.equals(Material.AIR)) {
                if(entity.isOnGround()) ((Parrot) entity).setSitting(!((Parrot) entity).isSitting());
            }
        }
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
            if(invis) {
                player.setMetadata("vanished", new FixedMetadataValue(this, true));
                player.setSleepingIgnored(true);
            } else {
                if(player.hasMetadata("vanished")) player.removeMetadata("vanished", this);
                player.setSleepingIgnored(false);
            }

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
        if(isPlayerInvisible(player)) player.setSleepingIgnored(true);
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

        worldTeamConfigCheck(player);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if(this.worldTeamConfig.isEmpty()) return;
        worldTeamConfigCheck(event.getPlayer());
    }

    public void worldTeamConfigCheck(Player player) {
        String to = player.getWorld().getName().toLowerCase();
        if(this.worldTeamConfig.containsKey(to)) {
            if(this.worldTeamConfig.get(to).containsKey("collision")) {
                sendIPCWorldTeamConfig("collision:", this.worldTeamConfig.get(to).get("collision"), player);
            }
            if(this.worldTeamConfig.get(to).containsKey("nametags")) {
                sendIPCWorldTeamConfig("nametags:", this.worldTeamConfig.get(to).get("nametags"), player);
            }
        } else {
            sendIPCWorldTeamConfig("collision:", "reset", player);
            sendIPCWorldTeamConfig("nametags:", "reset", player);
        }
    }

    public void sendIPCWorldTeamConfig(String key, String value, Player player) {
        long i = 1;
        if(this.playerTeamConfigQueue.get(player.getUniqueId()) != null) {
            i = this.playerTeamConfigQueue.get(player.getUniqueId()) + i;
        }
        this.playerTeamConfigQueue.put(player.getUniqueId(), i);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            long c = this.playerTeamConfigQueue.get(player.getUniqueId()) - 1;
            this.playerTeamConfigQueue.put(player.getUniqueId(), c);
            //System.out.println("Executing command: " + "pcmd teamoverride " + key + value + " player:" + player.getName());
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "pcmd teamoverride " + key + value + " player:" + player.getName());
        }, 20 * i);
    }
}
