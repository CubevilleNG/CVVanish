package org.cubeville.cvvanish;

import com.comphenix.protocol.ProtocolManager;
import org.bukkit.entity.Player;
import org.cubeville.cvvanish.SAFunOptions.SAFunBats;
import org.cubeville.cvvanish.SAFunOptions.SAFunLightning;

public class SAFunManager {

    CVVanish plugin;
    ProtocolManager protocolManager;

    public SAFunManager(CVVanish plugin, ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
        this.plugin = plugin;
    }

    public void executeBats(Player player) {
        SAFunBats.spawnBats(player, plugin, protocolManager);
    }

    public void executeLightning(Player player) {
        SAFunLightning.spawnLightning(player, plugin, protocolManager);
    }
}
