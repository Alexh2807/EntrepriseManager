package com.gravityyfh.roleplaycity.mdt.data;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;

/**
 * Représente un lit (Équipe ou Neutre)
 */
public class MDTBed {
    public enum Type { TEAM, NEUTRAL }

    private final String id;
    private final Location location;
    private final Type type;
    
    // Pour les lits d'équipe
    private final MDTTeam ownerTeam;
    
    // Pour les lits neutres
    private final int bonusHearts;
    private MDTTeam claimedBy; // L'équipe qui a capturé ce lit

    private boolean destroyed;

    // Constructeur Lit Équipe
    public MDTBed(String id, Location location, MDTTeam ownerTeam) {
        this.id = id;
        this.location = location;
        this.type = Type.TEAM;
        this.ownerTeam = ownerTeam;
        this.bonusHearts = 0;
        this.destroyed = false;
    }

    // Constructeur Lit Neutre
    public MDTBed(String id, Location location, int bonusHearts) {
        this.id = id;
        this.location = location;
        this.type = Type.NEUTRAL;
        this.ownerTeam = null; // Pas de propriétaire au début
        this.bonusHearts = bonusHearts;
        this.destroyed = false;
    }

    public String getId() { return id; }
    public Location getLocation() { return location; }
    public Type getType() { return type; }
    
    public MDTTeam getOwnerTeam() { return ownerTeam; }
    
    public int getBonusHearts() { return bonusHearts; }
    public MDTTeam getClaimedBy() { return claimedBy; }
    public void setClaimedBy(MDTTeam team) { this.claimedBy = team; }

    public boolean isDestroyed() { return destroyed; }
    public void setDestroyed(boolean destroyed) { this.destroyed = destroyed; }

    public boolean isTeamBed() { return type == Type.TEAM; }
    public boolean isNeutralBed() { return type == Type.NEUTRAL; }

    /**
     * Vérifie si une location correspond à ce lit (vérifie les 2 blocs du lit)
     */
    public boolean isAtLocation(Location loc) {
        if (location.getWorld() == null || loc.getWorld() == null) return false;
        if (!location.getWorld().equals(loc.getWorld())) return false;

        Block block = location.getBlock();
        if (block.getBlockData() instanceof Bed) {
            Location headLoc = location.clone();
            Location footLoc = getOtherHalfLocation(block);
            return locationsMatch(loc, headLoc) || (footLoc != null && locationsMatch(loc, footLoc));
        }
        return locationsMatch(loc, location);
    }

    private boolean locationsMatch(Location loc1, Location loc2) {
        return loc1.getBlockX() == loc2.getBlockX()
                && loc1.getBlockY() == loc2.getBlockY()
                && loc1.getBlockZ() == loc2.getBlockZ();
    }

    private Location getOtherHalfLocation(Block bedBlock) {
        if (!(bedBlock.getBlockData() instanceof Bed bed)) return null;
        return bedBlock.getRelative(bed.getPart() == Bed.Part.HEAD ? 
                bed.getFacing().getOppositeFace() : bed.getFacing()).getLocation();
    }
}
