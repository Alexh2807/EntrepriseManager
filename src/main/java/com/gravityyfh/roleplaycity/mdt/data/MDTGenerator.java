package com.gravityyfh.roleplaycity.mdt.data;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

public class MDTGenerator {
    private final Location location;
    private final List<ResourceSpawner> resources;
    private final String type; // "TEAM" ou "MID"

    public MDTGenerator(Location location, List<ResourceSpawner> resourcesTemplate, String type) {
        this.location = location;
        this.type = type;
        this.resources = new ArrayList<>();
        
        // On clone les ressources pour que chaque générateur ait son propre timer
        for (ResourceSpawner res : resourcesTemplate) {
            this.resources.add(res.clone());
        }
    }

    public Location getLocation() {
        return location;
    }

    public List<ResourceSpawner> getResources() {
        return resources;
    }
    
    public String getType() {
        return type;
    }
}