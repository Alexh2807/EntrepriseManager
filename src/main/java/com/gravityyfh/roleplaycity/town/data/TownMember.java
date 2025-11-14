package com.gravityyfh.roleplaycity.town.data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TownMember {
    private final UUID playerUuid;
    private final String playerName;
    private final Set<TownRole> roles;
    private final LocalDateTime joinDate;
    private LocalDateTime lastOnline;

    public TownMember(UUID playerUuid, String playerName, TownRole role) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.roles = new HashSet<>();
        this.roles.add(role);
        this.joinDate = LocalDateTime.now();
        this.lastOnline = LocalDateTime.now();
    }

    // Constructor pour le chargement depuis la BDD
    public TownMember(UUID playerUuid, String playerName, TownRole role, LocalDateTime joinDate, LocalDateTime lastOnline) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.roles = new HashSet<>();
        this.roles.add(role);
        this.joinDate = joinDate;
        this.lastOnline = lastOnline;
    }

    // Nouveau constructeur pour multi-rôles
    public TownMember(UUID playerUuid, String playerName, Set<TownRole> roles, LocalDateTime joinDate, LocalDateTime lastOnline) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.roles = new HashSet<>(roles);
        this.joinDate = joinDate;
        this.lastOnline = lastOnline;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    /**
     * FIX BASSE #5: Méthode utilitaire conservée (non-deprecated)
     * Retourne le rôle principal (celui avec le plus haut pouvoir)
     *
     * @return Le rôle avec le plus haut niveau de pouvoir, ou CITOYEN par défaut
     */
    public TownRole getRole() {
        // Retourne le rôle avec le plus haut pouvoir pour compatibilité
        return roles.stream()
                .max((r1, r2) -> Integer.compare(r1.getPower(), r2.getPower()))
                .orElse(TownRole.CITOYEN);
    }

    public Set<TownRole> getRoles() {
        return new HashSet<>(roles);
    }

    public void addRole(TownRole role) {
        // SYSTÈME UN SEUL RÔLE : Remplacer le rôle actuel par le nouveau
        // Chaque membre ne peut avoir qu'un seul rôle à la fois
        roles.clear();
        roles.add(role);
    }

    public void removeRole(TownRole role) {
        roles.remove(role);
        // Si plus aucun rôle, devenir CITOYEN par défaut
        if (roles.isEmpty()) {
            roles.add(TownRole.CITOYEN);
        }
    }

    // FIX BASSE #6: Méthode setRole() deprecated supprimée - utiliser setRoles(), addRole() ou removeRole()

    public void setRoles(Set<TownRole> newRoles) {
        roles.clear();
        if (newRoles == null || newRoles.isEmpty()) {
            roles.add(TownRole.CITOYEN);
        } else {
            roles.addAll(newRoles);
        }
    }

    public boolean hasRole(TownRole role) {
        return roles.contains(role);
    }

    public boolean isMaire() {
        return roles.contains(TownRole.MAIRE);
    }

    public LocalDateTime getJoinDate() {
        return joinDate;
    }

    public LocalDateTime getLastOnline() {
        return lastOnline;
    }

    public void updateLastOnline() {
        this.lastOnline = LocalDateTime.now();
    }

    public boolean hasPermission(TownRole.TownPermission permission) {
        // Un membre a la permission si AU MOINS UN de ses rôles la possède
        return roles.stream().anyMatch(r -> r.hasPermission(permission));
    }

    public boolean canManage(TownMember other) {
        // Peut gérer si le pouvoir maximum de ses rôles est supérieur au pouvoir maximum de l'autre
        int thisMaxPower = roles.stream()
                .mapToInt(TownRole::getPower)
                .max()
                .orElse(0);
        int otherMaxPower = other.roles.stream()
                .mapToInt(TownRole::getPower)
                .max()
                .orElse(0);
        return thisMaxPower > otherMaxPower;
    }

    /**
     * Retourne le niveau de pouvoir maximum parmi tous les rôles
     */
    public int getMaxPower() {
        return roles.stream()
                .mapToInt(TownRole::getPower)
                .max()
                .orElse(1);
    }
}
