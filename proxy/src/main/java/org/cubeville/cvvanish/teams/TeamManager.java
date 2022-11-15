package org.cubeville.cvvanish.teams;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.score.Team;

import java.util.*;

public class TeamManager {

    private final String[] codes = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f","k","l","m","n","o","r"};

    public HashMap<UUID, String> fakeNames;
    public HashMap<String, Team> allTeams;

    public TeamManager() {
        this.fakeNames = new HashMap<>();
        this.allTeams = new HashMap<>();
    }

    private boolean isFakeNameInUse(String s) {
        return this.fakeNames.containsValue(s);
    }

    private boolean doesPlayerHaveFakeName(UUID uuid) {
        return this.fakeNames.containsKey(uuid);
    }

    public String getFakeName(UUID uuid) {
        if(doesPlayerHaveFakeName(uuid)) {
            return this.fakeNames.get(uuid);
        }
        return null;
    }

    public UUID getRealNameUUID(String s) {
        if(this.fakeNames.containsValue(s)) {
            for(UUID uuid : this.fakeNames.keySet()) {
                if(this.fakeNames.get(uuid).equals(s)) {
                    return uuid;
                }
            }
        }
        return null;
    }

    private String addFakeName(UUID uuid) {
        Random random;
        String fakeName = "";
        for(int i = 1; i <= 8; i++) {
            random = new Random();
            fakeName = fakeName.concat("§" + codes[random.nextInt(codes.length)]);
        }
        if(isFakeNameInUse(fakeName)) {
            addFakeName(uuid);
        } else {
            this.fakeNames.put(uuid, fakeName);
            return fakeName;
        }
        return null;
    }

    private boolean doesPlayerHaveTeam(String s) {
        return this.allTeams.containsKey(s);
    }

    public Team getPlayerTeam(UUID uuid) {
        if(getFakeName(uuid) != null && doesPlayerHaveTeam(getFakeName(uuid))) {
            return this.allTeams.get(getFakeName(uuid));
        }
        return null;
    }

    public Collection<Team> getAllTeams() {
        return this.allTeams.values();
    }

    public Team addPlayerTeam(UUID uuid, String teamName, String color) {
        ProxiedPlayer p = ProxyServer.getInstance().getPlayer(uuid);
        if(p == null || getPlayerTeam(uuid) != null) return null;
        Team team = new Team(teamName);
        team.setDisplayName("");
        team.setNameTagVisibility("always");
        team.setCollisionRule("always");
        team.setFriendlyFire((byte) 0);
        team.setColor(15);
        team.setPrefix("§" + color + p.getName());
        team.setSuffix("");
        String fakeName = addFakeName(uuid);
        team.setPlayers(Collections.singleton(fakeName));
        this.allTeams.put(fakeName, team);
        return team;
    }

    public Team removePlayerTeam(UUID uuid) {
        Team team = getPlayerTeam(uuid);
        if(team == null) return null;
        this.allTeams.remove(this.fakeNames.get(uuid));
        this.fakeNames.remove(uuid);
        return team;
    }
}
