package com.gravityyfh.roleplaycity.mdt.setup;

import org.bukkit.Location;

import java.util.*;

/**
 * Session de configuration pour un joueur
 */
public class MDTSetupSession {
    private final UUID playerUuid;
    private MDTSetupMode currentMode;
    private final long startTime;

    // Données configurées pendant la session
    private Location lobbySpawn;
    private Location redTeamSpawn;
    private Location blueTeamSpawn;
    private Location redBedLocation;
    private Location blueBedLocation;
    private final List<Location> neutralBedLocations = new ArrayList<>();
    private final Map<String, List<Location>> generatorLocations = new HashMap<>();

    // 4 marchands différents (blocks, weapons, armor, special)
    private final Map<String, Location> merchantLocations = new HashMap<>();

    public MDTSetupSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.currentMode = MDTSetupMode.LOBBY;
        this.startTime = System.currentTimeMillis();

        // Initialiser les listes de générateurs
        generatorLocations.put("brick", new ArrayList<>());
        generatorLocations.put("iron", new ArrayList<>());
        generatorLocations.put("gold", new ArrayList<>());
        generatorLocations.put("diamond", new ArrayList<>());
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public MDTSetupMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(MDTSetupMode mode) {
        this.currentMode = mode;
    }

    public long getStartTime() {
        return startTime;
    }

    // === Getters & Setters pour les locations ===

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public Location getRedTeamSpawn() {
        return redTeamSpawn;
    }

    public void setRedTeamSpawn(Location redTeamSpawn) {
        this.redTeamSpawn = redTeamSpawn;
    }

    public Location getBlueTeamSpawn() {
        return blueTeamSpawn;
    }

    public void setBlueTeamSpawn(Location blueTeamSpawn) {
        this.blueTeamSpawn = blueTeamSpawn;
    }

    public Location getRedBedLocation() {
        return redBedLocation;
    }

    public void setRedBedLocation(Location redBedLocation) {
        this.redBedLocation = redBedLocation;
    }

    public Location getBlueBedLocation() {
        return blueBedLocation;
    }

    public void setBlueBedLocation(Location blueBedLocation) {
        this.blueBedLocation = blueBedLocation;
    }

    public List<Location> getNeutralBedLocations() {
        return neutralBedLocations;
    }

    public void addNeutralBed(Location location) {
        neutralBedLocations.add(location);
    }

    public boolean removeNeutralBed(Location location) {
        return neutralBedLocations.removeIf(loc ->
            loc.getBlockX() == location.getBlockX() &&
            loc.getBlockY() == location.getBlockY() &&
            loc.getBlockZ() == location.getBlockZ());
    }

    public Map<String, List<Location>> getGeneratorLocations() {
        return generatorLocations;
    }

    public void addGenerator(String type, Location location) {
        generatorLocations.computeIfAbsent(type.toLowerCase(), k -> new ArrayList<>()).add(location);
    }

    public boolean removeGenerator(Location location) {
        for (List<Location> locs : generatorLocations.values()) {
            if (locs.removeIf(loc ->
                loc.getBlockX() == location.getBlockX() &&
                loc.getBlockY() == location.getBlockY() &&
                loc.getBlockZ() == location.getBlockZ())) {
                return true;
            }
        }
        return false;
    }

    // === 4 MARCHANDS ===

    public Map<String, Location> getMerchantLocations() {
        return merchantLocations;
    }

    public Location getMerchantLocation(String type) {
        return merchantLocations.get(type.toLowerCase());
    }

    public void setMerchantLocation(String type, Location location) {
        merchantLocations.put(type.toLowerCase(), location);
    }

    public boolean removeMerchant(String type) {
        return merchantLocations.remove(type.toLowerCase()) != null;
    }

    /**
     * Supprime un élément à une position donnée
     */
    public boolean removeElementAt(Location location) {
        // Vérifier les spawns
        if (isSameBlock(lobbySpawn, location)) {
            lobbySpawn = null;
            return true;
        }
        if (isSameBlock(redTeamSpawn, location)) {
            redTeamSpawn = null;
            return true;
        }
        if (isSameBlock(blueTeamSpawn, location)) {
            blueTeamSpawn = null;
            return true;
        }

        // Vérifier les lits
        if (isSameBlock(redBedLocation, location)) {
            redBedLocation = null;
            return true;
        }
        if (isSameBlock(blueBedLocation, location)) {
            blueBedLocation = null;
            return true;
        }
        if (removeNeutralBed(location)) {
            return true;
        }

        // Vérifier les générateurs
        if (removeGenerator(location)) {
            return true;
        }

        // Vérifier les marchands
        Iterator<Map.Entry<String, Location>> it = merchantLocations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Location> entry = it.next();
            if (isSameBlock(entry.getValue(), location)) {
                it.remove();
                return true;
            }
        }

        return false;
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getBlockX() == b.getBlockX() &&
               a.getBlockY() == b.getBlockY() &&
               a.getBlockZ() == b.getBlockZ();
    }

    /**
     * Compte le nombre total d'éléments configurés
     */
    public int getTotalConfiguredElements() {
        int count = 0;
        if (lobbySpawn != null) count++;
        if (redTeamSpawn != null) count++;
        if (blueTeamSpawn != null) count++;
        if (redBedLocation != null) count++;
        if (blueBedLocation != null) count++;
        count += neutralBedLocations.size();
        for (List<Location> locs : generatorLocations.values()) {
            count += locs.size();
        }
        count += merchantLocations.size();
        return count;
    }

    /**
     * Vérifie si la configuration minimale est complète
     */
    public boolean isMinimalConfigComplete() {
        return lobbySpawn != null &&
               redTeamSpawn != null &&
               blueTeamSpawn != null &&
               redBedLocation != null &&
               blueBedLocation != null;
    }

    /**
     * Retourne une liste des éléments manquants
     */
    public List<String> getMissingElements() {
        List<String> missing = new ArrayList<>();
        if (lobbySpawn == null) missing.add("Spawn Lobby");
        if (redTeamSpawn == null) missing.add("Spawn Équipe Rouge");
        if (blueTeamSpawn == null) missing.add("Spawn Équipe Bleue");
        if (redBedLocation == null) missing.add("Lit Équipe Rouge");
        if (blueBedLocation == null) missing.add("Lit Équipe Bleue");
        return missing;
    }
}
