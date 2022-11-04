package org.cubeville.cvvanish;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.tab.DefaultTabListFactory;
import net.md_5.bungee.tab.TabList;

public class CVTabListFactory extends DefaultTabListFactory
{
    public TabList createTabList(ProxiedPlayer player) {
        return new CVTabList(player);
    }
}
