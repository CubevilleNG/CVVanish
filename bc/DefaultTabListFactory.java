package net.md_5.bungee.tab;

// To use exchangeable tab list factories patch UserConnection as follows:
// - remove ServerUnique import
// - add DefaultTabListFactory import
// - add static member "DefaultTabListFactory tabListFactory" and initialize with default list factory object
// - add public static method setTabListFactory that replaces the default object
// - in function init() replace the object assigned to tabListHandler with tavbListFactory.createTabList(this)

import net.md_5.bungee.api.connection.ProxiedPlayer;

public class DefaultTabListFactory
{
    public TabList createTabList(ProxiedPlayer player) {
        return new ServerUnique(player);
    }
}
