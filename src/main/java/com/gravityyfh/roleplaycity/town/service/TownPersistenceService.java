package com.gravityyfh.roleplaycity.town.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.town.data.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service de persistance SQL pour les villes
 * Remplace l'ancien système YAML par SQLite
 *
 * @author Phase 7 - SQLite Migration
 */
public class TownPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public TownPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge toutes les villes depuis SQLite
     */
    public Map<String, Town> loadTowns() {
        Map<String, Town> towns = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM towns")) {

            while (rs.next()) {
                String townName = rs.getString("name");
                try {
                    Town town = loadTown(conn, rs);
                    if (town != null) {
                        towns.put(townName, town);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[Towns] Erreur chargement ville " + townName, e);
                }
            }

            plugin.getLogger().info("[Towns] Chargé " + towns.size() + " villes depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Towns] Erreur chargement villes", e);
        }

        return towns;
    }

    /**
     * Charge une ville depuis un ResultSet
     */
    private Town loadTown(Connection conn, ResultSet rs) throws SQLException {
        String townName = rs.getString("name");
        UUID mayorUuid = UUID.fromString(rs.getString("mayor_uuid"));

        // Trouver le nom du maire depuis les membres
        String mayorName = getMayorName(conn, townName, mayorUuid);

        // Créer la ville
        Town town = new Town(townName, mayorUuid, mayorName);

        // Charger les données de base
        town.setDescription(rs.getString("description"));

        double bankBalance = rs.getDouble("bank_balance");
        if (bankBalance > 0) {
            town.deposit(bankBalance);
        }

        town.setCitizenTax(rs.getDouble("citizen_tax"));
        town.setCompanyTax(rs.getDouble("company_tax"));

        String lastTaxStr = rs.getString("last_tax_collection");
        if (lastTaxStr != null) {
            town.setLastTaxCollection(LocalDateTime.parse(lastTaxStr, DATE_FORMAT));
        }

        // Niveau de la ville
        String levelName = rs.getString("level");
        TownLevel level = TownLevel.fromName(levelName);
        if (level != null) {
            town.setLevel(level);
        }

        // Spawn de la ville
        String spawnWorld = rs.getString("spawn_world");
        if (spawnWorld != null) {
            World world = Bukkit.getWorld(spawnWorld);
            if (world != null) {
                Location spawnLoc = new Location(
                    world,
                    rs.getDouble("spawn_x"),
                    rs.getDouble("spawn_y"),
                    rs.getDouble("spawn_z"),
                    (float) rs.getDouble("spawn_yaw"),
                    (float) rs.getDouble("spawn_pitch")
                );
                town.setSpawnLocation(spawnLoc);
            }
        }

        // Charger les membres
        loadTownMembers(conn, town);

        // Charger les invitations
        loadTownInvites(conn, town);

        // Charger les parcelles
        loadPlots(conn, town);

        return town;
    }

    /**
     * Récupère le nom du maire
     */
    private String getMayorName(Connection conn, String townName, UUID mayorUuid) throws SQLException {
        String sql = "SELECT player_name FROM town_members WHERE town_name = ? AND player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, townName);
            stmt.setString(2, mayorUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("player_name");
                }
            }
        }
        return "Unknown";
    }

    /**
     * Charge les membres d'une ville
     */
    private void loadTownMembers(Connection conn, Town town) throws SQLException {
        String sql = "SELECT * FROM town_members WHERE town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, town.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));

                    // Ignorer le maire (déjà ajouté lors de la création)
                    if (playerUuid.equals(town.getMayorUuid())) {
                        continue;
                    }

                    String playerName = rs.getString("player_name");
                    String rolesStr = rs.getString("roles");

                    // Parser les rôles (format: "ROLE1,ROLE2,ROLE3")
                    Set<TownRole> roles = new HashSet<>();
                    for (String roleName : rolesStr.split(",")) {
                        try {
                            roles.add(TownRole.valueOf(roleName.trim()));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("[Towns] Rôle invalide: " + roleName);
                        }
                    }

                    // Ajouter le membre avec son rôle principal
                    TownRole primaryRole = roles.stream()
                        .max((r1, r2) -> Integer.compare(r1.getPower(), r2.getPower()))
                        .orElse(TownRole.CITOYEN);

                    town.addMember(playerUuid, playerName, primaryRole);

                    // Ajouter les autres rôles
                    TownMember member = town.getMember(playerUuid);
                    if (member != null) {
                        member.setRoles(roles);
                    }
                }
            }
        }
    }

    private void loadTownInvites(Connection conn, Town town) throws SQLException {
        String sql = "SELECT * FROM town_invites WHERE town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, town.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    LocalDateTime date = LocalDateTime.parse(rs.getString("invite_date"), DATE_FORMAT);
                    town.invitePlayer(playerUuid, date);
                }
            }
        }
    }

    /**
     * Charge les parcelles d'une ville
     */
    private void loadPlots(Connection conn, Town town) throws SQLException {
        String sql = "SELECT * FROM plots WHERE town_name = ?";
        
        // Cache pour les plots groupés afin d'éviter de créer plusieurs instances pour le même terrain
        Map<String, Plot> loadedGroups = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, town.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        String groupId = rs.getString("plot_group_id");
                        String worldName = rs.getString("world");
                        int chunkX = rs.getInt("chunk_x");
                        int chunkZ = rs.getInt("chunk_z");

                        // Gestion des Plots Groupés (Multi-Chunks)
                        if (groupId != null && loadedGroups.containsKey(groupId)) {
                            // Le groupe est déjà instancié, on récupère l'objet existant
                            Plot existingPlot = loadedGroups.get(groupId);
                            
                            // Ajouter ce nouveau chunk à la liste des chunks du plot (mémoire interne du plot)
                            String chunkKey = worldName + ":" + chunkX + ":" + chunkZ;
                            existingPlot.addChunk(chunkKey);
                            
                            // Enregistrer ce chunk dans la map de la ville (pointe vers la même instance de Plot)
                            // Utilise addPlotAt pour forcer l'enregistrement à ces coordonnées précises
                            town.addPlotAt(existingPlot, chunkX, chunkZ);
                            continue; 
                        }

                        // Si ce n'est pas un groupe connu, on charge un nouvel objet Plot complet
                        Plot plot = loadPlot(conn, rs);
                        if (plot != null) {
                            // Ajouter à la ville aux coordonnées spécifiques
                            town.addPlotAt(plot, chunkX, chunkZ);
                            
                            // Si c'est le début d'un groupe, on le met en cache pour les prochains chunks
                            if (groupId != null) {
                                loadedGroups.put(groupId, plot);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "[Towns] Erreur chargement parcelle ID " + rs.getInt("id"), e);
                    }
                }
            }
        }
    }

    /**
     * Charge une parcelle depuis un ResultSet
     */
    private Plot loadPlot(Connection conn, ResultSet rs) throws SQLException {
        int plotId = rs.getInt("id");
        String worldName = rs.getString("world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("[Towns] Monde introuvable: " + worldName);
            return null;
        }

        int chunkX = rs.getInt("chunk_x");
        int chunkZ = rs.getInt("chunk_z");
        String townName = rs.getString("town_name");

        Plot plot = new Plot(townName, world.getChunkAt(chunkX, chunkZ));

        // Type et sous-type
        plot.setType(PlotType.valueOf(rs.getString("plot_type")));
        String subType = rs.getString("municipal_subtype");
        if (subType != null) plot.setMunicipalSubType(MunicipalSubType.valueOf(subType));
        
        plot.setPlotNumber(rs.getString("plot_number"));
        
        String claimDateStr = rs.getString("claim_date");
        if (claimDateStr != null) plot.setClaimDate(LocalDateTime.parse(claimDateStr, DATE_FORMAT));

        // Propriétaire
        String ownerUuidStr = rs.getString("owner_uuid");
        if (ownerUuidStr != null) {
            plot.setOwner(UUID.fromString(ownerUuidStr), rs.getString("owner_name"));
        }

        // Company & Debt
        plot.setCompanySiret(rs.getString("company_siret"));
        plot.setCompany(rs.getString("company_name"));
        plot.setCompanyDebtAmount(rs.getDouble("debt_amount"));
        plot.setDebtWarningCount(rs.getInt("debt_warning_count"));
        String lastDebt = rs.getString("last_debt_warning");
        if (lastDebt != null) plot.setLastDebtWarningDate(LocalDateTime.parse(lastDebt, DATE_FORMAT));
        
        plot.setParticularDebtAmount(rs.getDouble("particular_debt_amount"));
        plot.setParticularDebtWarningCount(rs.getInt("particular_debt_warning_count"));
        String lastPartDebt = rs.getString("particular_last_debt_warning");
        if (lastPartDebt != null) plot.setParticularLastDebtWarningDate(LocalDateTime.parse(lastPartDebt, DATE_FORMAT));

        // Prix et vente
        double price = rs.getDouble("price");
        if (rs.getInt("for_sale") == 1) {
            plot.setSalePrice(price);
            plot.setForSale(true);
        }

        // Location
        plot.setRentPricePerDay(rs.getDouble("rent_amount"));
        if (rs.getInt("for_rent") == 1) plot.setForRent(true);
        
        String renterUuidStr = rs.getString("renter_uuid");
        if (renterUuidStr != null) {
            UUID renterUuid = UUID.fromString(renterUuidStr);
            // Initialiser avec une durée bidon, puis update date
            plot.setRenter(renterUuid, 1); 

            String rentStartStr = rs.getString("rent_start_date");
            if (rentStartStr != null) {
                plot.setRentStartDate(LocalDateTime.parse(rentStartStr, DATE_FORMAT));
            }

            String rentEndStr = rs.getString("rent_end_date");
            if (rentEndStr != null) {
                plot.setRentEndDate(LocalDateTime.parse(rentEndStr, DATE_FORMAT));
            }
            plot.setRenterCompanySiret(rs.getString("renter_company_siret"));
        }

        // Prison Spawn
        String prisonWorld = rs.getString("prison_spawn_world");
        if (prisonWorld != null && Bukkit.getWorld(prisonWorld) != null) {
            Location prisonLoc = new Location(Bukkit.getWorld(prisonWorld),
                rs.getDouble("prison_spawn_x"), rs.getDouble("prison_spawn_y"), rs.getDouble("prison_spawn_z"),
                (float)rs.getDouble("prison_spawn_yaw"), (float)rs.getDouble("prison_spawn_pitch"));
            plot.setPrisonSpawn(prisonLoc);
        }

        // Groupe de parcelles
        String groupId = rs.getString("plot_group_id");
        if (groupId != null) {
            plot.setGrouped(true);
            plot.setGroupName(groupId); 
        }
        
        // Load Related Data
        loadPlotFlags(conn, plotId, plot);
        loadPlotPermissions(conn, plotId, plot);
        loadPlotTrusted(conn, plotId, plot);
        loadPlotAuthorizations(conn, plotId, plot);
        loadPlotProtectedBlocks(conn, plotId, plot, world);
        loadRenterBlocks(conn, plotId, plot);
        loadPlotMailbox(conn, plot);
        
        return plot;
    }
    
    private void loadPlotFlags(Connection conn, int plotId, Plot plot) throws SQLException {
        String sql = "SELECT flag_name, value FROM plot_flags WHERE plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        PlotFlag flag = PlotFlag.valueOf(rs.getString("flag_name"));
                        boolean value = rs.getInt("value") == 1;
                        plot.setFlag(flag, value);
                    } catch (Exception e) {}
                }
            }
        }
    }

    private void loadPlotPermissions(Connection conn, int plotId, Plot plot) throws SQLException {
        String sql = "SELECT player_uuid, permission FROM plot_permissions WHERE plot_id = ?";
        Map<UUID, Set<PlotPermission>> permsMap = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID player = UUID.fromString(rs.getString("player_uuid"));
                    PlotPermission perm = PlotPermission.valueOf(rs.getString("permission"));
                    permsMap.computeIfAbsent(player, k -> EnumSet.noneOf(PlotPermission.class)).add(perm);
                }
            }
        }
        for (Map.Entry<UUID, Set<PlotPermission>> entry : permsMap.entrySet()) {
            plot.setPlayerPermissions(entry.getKey(), entry.getValue());
        }
    }

    private void loadPlotTrusted(Connection conn, int plotId, Plot plot) throws SQLException {
        String sql = "SELECT player_uuid FROM plot_trusted WHERE plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    plot.addTrustedPlayer(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        }
    }

    private void loadPlotAuthorizations(Connection conn, int plotId, Plot plot) throws SQLException {
        String sql = "SELECT player_uuid, authorization_type FROM plot_authorizations WHERE plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            try (ResultSet rs = stmt.executeQuery()) {
                Set<UUID> ownerAuthorized = new HashSet<>();
                Set<UUID> renterAuthorized = new HashSet<>();

                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String authType = rs.getString("authorization_type");

                    if ("OWNER".equals(authType)) {
                        ownerAuthorized.add(playerUuid);
                    } else if ("RENTER".equals(authType)) {
                        renterAuthorized.add(playerUuid);
                    }
                }

                plot.setOwnerAuthorizedPlayers(ownerAuthorized);
                plot.setRenterAuthorizedPlayers(renterAuthorized);
            }
        }
    }

    private void loadPlotProtectedBlocks(Connection conn, int plotId, Plot plot, World world) throws SQLException {
        String sql = "SELECT x, y, z FROM plot_protected_blocks WHERE plot_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    plot.addProtectedBlock(new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
                }
            }
        }
    }

    private void loadRenterBlocks(Connection conn, int plotId, Plot plot) throws SQLException {
        String sql = "SELECT renter_uuid, world, x, y, z FROM plot_renter_blocks WHERE plot_id = ?";
        Map<UUID, Set<RenterBlockTracker.BlockPosition>> blocksMap = new HashMap<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, plotId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID renterUuid = UUID.fromString(rs.getString("renter_uuid"));
                        String world = rs.getString("world");
                        int x = rs.getInt("x");
                        int y = rs.getInt("y");
                        int z = rs.getInt("z");
                        
                        blocksMap.computeIfAbsent(renterUuid, k -> new HashSet<>())
                                .add(new RenterBlockTracker.BlockPosition(world, x, y, z));
                    } catch (IllegalArgumentException e) {
                        // Ignorer UUID invalide
                    }
                }
            }
        }
        
        if (!blocksMap.isEmpty() && plot.getRenterBlockTracker() != null) {
            plot.getRenterBlockTracker().loadBlocks(blocksMap);
        }
    }

    private void loadPlotMailbox(Connection conn, Plot plot) throws SQLException {
        // Utiliser plotNumber (R-001, R-002, etc.) - seuls les terrains non-PUBLIC en ont un
        String plotNumber = plot.getPlotNumber();
        if (plotNumber == null) return;

        // FIX: Filtrer aussi par town_name pour éviter les collisions d'IDs entre villes
        String sql = "SELECT id, world, x, y, z, mailbox_type FROM mailboxes WHERE plot_group_id = ? AND town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, plotNumber);
            stmt.setString(2, plot.getTownName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int mailboxId = rs.getInt("id");
                    String worldName = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    String typeStr = rs.getString("mailbox_type");
                    
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) return;
                    
                    Location loc = new Location(world, x, y, z);
                    com.gravityyfh.roleplaycity.postal.data.MailboxType type;
                    try {
                        type = com.gravityyfh.roleplaycity.postal.data.MailboxType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        type = com.gravityyfh.roleplaycity.postal.data.MailboxType.LIGHT_GRAY; // Fallback
                    }

                    // Charger les items
                    Map<Integer, org.bukkit.inventory.ItemStack> items = new HashMap<>();
                    String itemSql = "SELECT item_data FROM mail_items WHERE mailbox_id = ? AND retrieved = 0";
                    try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                        itemStmt.setInt(1, mailboxId);
                        try (ResultSet itemRs = itemStmt.executeQuery()) {
                            int slot = 0;
                            while (itemRs.next()) {
                                String itemData = itemRs.getString("item_data");
                                try {
                                    org.bukkit.inventory.ItemStack item = com.gravityyfh.roleplaycity.util.InventorySerializer.deserializeItem(itemData);
                                    if (item != null) {
                                        items.put(slot++, item);
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("[Towns] Erreur désérialisation item courrier: " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    com.gravityyfh.roleplaycity.postal.data.Mailbox mailbox = new com.gravityyfh.roleplaycity.postal.data.Mailbox(type, loc, items);
                    plot.setMailbox(mailbox);
                }
            }
        }
    }

    /**
     * Sauvegarde toutes les villes (Asynchrone)
     */
    public CompletableFuture<Void> saveTowns(Map<String, Town> towns) {
        return CompletableFuture.runAsync(() -> {
            saveTownsSync(towns);
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[TownPersistence] ❌ ERREUR ASYNC sauvegarde villes", ex);
            return null;
        });
    }

    /**
     * Sauvegarde toutes les villes (Synchrone - pour shutdown)
     */
    public void saveTownsSync(Map<String, Town> towns) {
        plugin.getLogger().info("[TownPersistence] Début sauvegarde de " + towns.size() + " villes...");
        try {
            connectionManager.executeTransaction(conn -> {
                int count = 0;
                for (Town town : towns.values()) {
                    saveTown(conn, town);
                    count++;
                    plugin.getLogger().info("[TownPersistence] Ville " + count + "/" + towns.size() + " sauvegardée: " + town.getName());
                }
            });
            plugin.getLogger().info("[TownPersistence] ✓ Sauvegardé " + towns.size() + " villes dans SQLite");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[TownPersistence] ❌ ERREUR sauvegarde villes", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Sauvegarde une ville
     */
    private void saveTown(Connection conn, Town town) throws SQLException {
        plugin.getLogger().info("[TownPersistence] Sauvegarde ville: " + town.getName() +
            " (Maire: " + town.getMayorUuid() + ", Parcelles: " + town.getPlots().size() +
            ", Membres: " + town.getMembers().size() + ")");

        // INSERT OR REPLACE pour la ville
        String sql = "INSERT OR REPLACE INTO towns (" +
            "name, description, mayor_uuid, foundation_date, level, " +
            "bank_balance, citizen_tax, company_tax, last_tax_collection, " +
            "spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, town.getName());
            stmt.setString(2, town.getDescription());
            stmt.setString(3, town.getMayorUuid().toString());
            stmt.setString(4, town.getFoundationDate().format(DATE_FORMAT));
            stmt.setString(5, town.getLevel().name());
            stmt.setDouble(6, town.getBankBalance());
            stmt.setDouble(7, town.getCitizenTax());
            stmt.setDouble(8, town.getCompanyTax());
            stmt.setString(9, town.getLastTaxCollection().format(DATE_FORMAT));

            // Spawn location
            if (town.hasSpawnLocation()) {
                Location spawn = town.getSpawnLocation();
                stmt.setString(10, spawn.getWorld().getName());
                stmt.setDouble(11, spawn.getX());
                stmt.setDouble(12, spawn.getY());
                stmt.setDouble(13, spawn.getZ());
                stmt.setDouble(14, spawn.getYaw());
                stmt.setDouble(15, spawn.getPitch());
            } else {
                stmt.setNull(10, Types.VARCHAR);
                stmt.setNull(11, Types.DOUBLE);
                stmt.setNull(12, Types.DOUBLE);
                stmt.setNull(13, Types.DOUBLE);
                stmt.setNull(14, Types.DOUBLE);
                stmt.setNull(15, Types.DOUBLE);
            }

            int rowsAffected = stmt.executeUpdate();
            plugin.getLogger().info("[TownPersistence] INSERT town - Lignes affectées: " + rowsAffected);
        }

        // Sauvegarder les membres
        saveTownMembers(conn, town);

        // Sauvegarder les invitations
        saveTownInvites(conn, town);

        // Sauvegarder les parcelles
        savePlots(conn, town);

        plugin.getLogger().info("[TownPersistence] ✓ Ville " + town.getName() + " sauvegardée complètement");
    }

    /**
     * Sauvegarde les membres d'une ville
     */
    private void saveTownMembers(Connection conn, Town town) throws SQLException {
        // Supprimer les anciens membres
        String deleteSql = "DELETE FROM town_members WHERE town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, town.getName());
            stmt.executeUpdate();
        }

        // Insérer les nouveaux membres
        String insertSql = "INSERT INTO town_members (town_name, player_uuid, player_name, join_date, roles) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (TownMember member : town.getMembers().values()) {
                stmt.setString(1, town.getName());
                stmt.setString(2, member.getPlayerUuid().toString());
                // Utiliser getStoredName() pour la persistance, pas getPlayerName() dynamique
                stmt.setString(3, member.getStoredName());
                stmt.setString(4, member.getJoinDate().format(DATE_FORMAT));

                // Sérialiser les rôles en CSV
                List<String> roleNames = new ArrayList<>();
                for (TownRole role : member.getRoles()) {
                    roleNames.add(role.name());
                }
                stmt.setString(5, String.join(",", roleNames));

                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void saveTownInvites(Connection conn, Town town) throws SQLException {
        String deleteSql = "DELETE FROM town_invites WHERE town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, town.getName());
            stmt.executeUpdate();
        }

        String insertSql = "INSERT INTO town_invites (town_name, player_uuid, invite_date) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Map.Entry<UUID, LocalDateTime> entry : town.getPendingInvitations().entrySet()) {
                stmt.setString(1, town.getName());
                stmt.setString(2, entry.getKey().toString());
                stmt.setString(3, entry.getValue().format(DATE_FORMAT));
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Sauvegarde les parcelles d'une ville
     */
    private void savePlots(Connection conn, Town town) throws SQLException {
        // Supprimer d'abord les mail_items liés aux mailboxes de cette ville
        String deleteMailItemsSql = "DELETE FROM mail_items WHERE mailbox_id IN (SELECT id FROM mailboxes WHERE town_name = ?)";
        try (PreparedStatement stmt = conn.prepareStatement(deleteMailItemsSql)) {
            stmt.setString(1, town.getName());
            stmt.executeUpdate();
        }

        // Supprimer les mailboxes de cette ville
        String deleteMailboxesSql = "DELETE FROM mailboxes WHERE town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteMailboxesSql)) {
            stmt.setString(1, town.getName());
            stmt.executeUpdate();
        }

        // Supprimer les anciennes parcelles de cette ville
        String deleteSql = "DELETE FROM plots WHERE town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, town.getName());
            stmt.executeUpdate();
        }

        // FIX: Supprimer et recréer les plot_groups AVANT d'insérer les mailboxes (clé étrangère)
        String deleteGroupsSql = "DELETE FROM plot_groups WHERE town_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteGroupsSql)) {
            stmt.setString(1, town.getName());
            stmt.executeUpdate();
        }

        // Insérer les plot_groups pour chaque terrain avec un plotNumber
        String insertGroupSql = "INSERT OR IGNORE INTO plot_groups (group_id, town_name, owner_uuid, owner_name, company_siret, company_name) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement groupStmt = conn.prepareStatement(insertGroupSql)) {
            Set<String> insertedGroups = new HashSet<>();
            for (Plot plot : new HashSet<>(town.getPlots().values())) {
                String plotNumber = plot.getPlotNumber();
                if (plotNumber != null && !insertedGroups.contains(plotNumber)) {
                    groupStmt.setString(1, plotNumber);
                    groupStmt.setString(2, town.getName());

                    // Propriétaire (peut être null pour terrains municipaux)
                    UUID ownerUuid = plot.getOwnerUuid() != null ? plot.getOwnerUuid() : plot.getRenterUuid();
                    groupStmt.setString(3, ownerUuid != null ? ownerUuid.toString() : town.getMayorUuid().toString());
                    groupStmt.setString(4, plot.getOwnerName() != null ? plot.getOwnerName() :
                                          (plot.getRenterName() != null ? plot.getRenterName() : "Ville"));

                    groupStmt.setString(5, plot.getCompanySiret());
                    groupStmt.setString(6, plot.getCompanyName());
                    groupStmt.executeUpdate();
                    insertedGroups.add(plotNumber);
                }
            }
        }

        String insertSql = "INSERT INTO plots (" +
            "town_name, world, chunk_x, chunk_z, plot_type, municipal_subtype, plot_number, claim_date, " +
            "owner_uuid, owner_name, " +
            "company_siret, company_name, debt_amount, debt_warning_count, last_debt_warning, " +
            "particular_debt_amount, particular_debt_warning_count, particular_last_debt_warning, " +
            "price, for_sale, rent_amount, for_rent, renter_uuid, renter_name, rent_start_date, rent_end_date, renter_company_siret, " +
            "plot_group_id, " +
            "prison_spawn_world, prison_spawn_x, prison_spawn_y, prison_spawn_z, prison_spawn_yaw, prison_spawn_pitch" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            // Dédupliquer les plots - un plot groupé peut apparaître plusieurs fois dans la map
            Set<Plot> uniquePlots = new HashSet<>(town.getPlots().values());
            // FIX: Tracker les mailboxes déjà sauvegardées (une seule fois par plotNumber)
            Set<String> savedMailboxes = new HashSet<>();
            for (Plot plot : uniquePlots) {
                for (String chunkKey : plot.getChunks()) {
                    String[] parts = chunkKey.split(":");
                    if (parts.length < 3) continue;
                    int chunkX = Integer.parseInt(parts[1]);
                    int chunkZ = Integer.parseInt(parts[2]);

                    int i = 1;
                    stmt.setString(i++, town.getName());
                    stmt.setString(i++, plot.getWorldName());
                    stmt.setInt(i++, chunkX);
                    stmt.setInt(i++, chunkZ);
                    stmt.setString(i++, plot.getType().name());
                    stmt.setString(i++, plot.getMunicipalSubType().name());
                    stmt.setString(i++, plot.getPlotNumber());
                    stmt.setString(i++, plot.getClaimDate() != null ? plot.getClaimDate().format(DATE_FORMAT) : LocalDateTime.now().format(DATE_FORMAT));
                    
                    // Owner
                    stmt.setString(i++, plot.getOwnerUuid() != null ? plot.getOwnerUuid().toString() : null);
                    stmt.setString(i++, plot.getOwnerName());
                    
                    // Company
                    stmt.setString(i++, plot.getCompanySiret());
                    stmt.setString(i++, plot.getCompanyName());
                    stmt.setDouble(i++, plot.getCompanyDebtAmount());
                    stmt.setInt(i++, plot.getDebtWarningCount());
                    stmt.setString(i++, plot.getLastDebtWarningDate() != null ? plot.getLastDebtWarningDate().format(DATE_FORMAT) : null);
                    
                    // Particular Debt
                    stmt.setDouble(i++, plot.getParticularDebtAmount());
                    stmt.setInt(i++, plot.getParticularDebtWarningCount());
                    stmt.setString(i++, plot.getParticularLastDebtWarningDate() != null ? plot.getParticularLastDebtWarningDate().format(DATE_FORMAT) : null);
                    
                    // Sale
                    stmt.setDouble(i++, plot.getSalePrice());
                    stmt.setInt(i++, plot.isForSale() ? 1 : 0);
                    
                    // Rent
                    stmt.setDouble(i++, plot.getRentPricePerDay());
                    stmt.setInt(i++, plot.isForRent() ? 1 : 0);
                    stmt.setString(i++, plot.getRenterUuid() != null ? plot.getRenterUuid().toString() : null);
                    stmt.setString(i++, plot.getRenterUuid() != null ? Bukkit.getOfflinePlayer(plot.getRenterUuid()).getName() : null); 
                    stmt.setString(i++, plot.getRentStartDate() != null ? plot.getRentStartDate().format(DATE_FORMAT) : null);
                    stmt.setString(i++, plot.getRentEndDate() != null ? plot.getRentEndDate().format(DATE_FORMAT) : null);
                    stmt.setString(i++, plot.getRenterCompanySiret());
                    
                    // Group
                    stmt.setString(i++, plot.getGroupName());
                    
                    // Prison
                    if (plot.hasPrisonSpawn()) {
                        Location loc = plot.getPrisonSpawn();
                        stmt.setString(i++, loc.getWorld().getName());
                        stmt.setDouble(i++, loc.getX());
                        stmt.setDouble(i++, loc.getY());
                        stmt.setDouble(i++, loc.getZ());
                        stmt.setDouble(i++, loc.getYaw());
                        stmt.setDouble(i++, loc.getPitch());
                    } else {
                        stmt.setNull(i++, Types.VARCHAR);
                        stmt.setNull(i++, Types.DOUBLE);
                        stmt.setNull(i++, Types.DOUBLE);
                        stmt.setNull(i++, Types.DOUBLE);
                        stmt.setNull(i++, Types.DOUBLE);
                        stmt.setNull(i++, Types.DOUBLE);
                    }

                    stmt.executeUpdate();
                    
                    // Get Generated ID
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int plotId = rs.getInt(1);
                            saveRelatedPlotData(conn, plotId, plot, savedMailboxes);
                        }
                    }
                }
            }
        }
    }

    private void saveRelatedPlotData(Connection conn, int plotId, Plot plot, Set<String> savedMailboxes) throws SQLException {
        // Flags
        String flagSql = "INSERT INTO plot_flags (plot_id, flag_name, value) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(flagSql)) {
            for (Map.Entry<PlotFlag, Boolean> entry : plot.getAllFlags().entrySet()) {
                stmt.setInt(1, plotId);
                stmt.setString(2, entry.getKey().name());
                stmt.setInt(3, entry.getValue() ? 1 : 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        // Permissions
        String permSql = "INSERT INTO plot_permissions (plot_id, player_uuid, permission) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(permSql)) {
            for (Map.Entry<UUID, Set<PlotPermission>> entry : plot.getAllPlayerPermissions().entrySet()) {
                for (PlotPermission perm : entry.getValue()) {
                    stmt.setInt(1, plotId);
                    stmt.setString(2, entry.getKey().toString());
                    stmt.setString(3, perm.name());
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        }

        // Trusted
        String trustSql = "INSERT INTO plot_trusted (plot_id, player_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(trustSql)) {
            for (UUID uuid : plot.getTrustedPlayers()) {
                stmt.setInt(1, plotId);
                stmt.setString(2, uuid.toString());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        // Authorizations (Owner and Renter)
        String authSql = "INSERT INTO plot_authorizations (plot_id, player_uuid, authorization_type) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(authSql)) {
            // Owner authorized players
            for (UUID uuid : plot.getOwnerAuthorizedPlayers()) {
                stmt.setInt(1, plotId);
                stmt.setString(2, uuid.toString());
                stmt.setString(3, "OWNER");
                stmt.addBatch();
            }
            // Renter authorized players
            for (UUID uuid : plot.getRenterAuthorizedPlayers()) {
                stmt.setInt(1, plotId);
                stmt.setString(2, uuid.toString());
                stmt.setString(3, "RENTER");
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        // Protected Blocks
        String blockSql = "INSERT INTO plot_protected_blocks (plot_id, world, x, y, z) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(blockSql)) {
            for (String locStr : plot.getProtectedBlocks()) {
                String[] parts = locStr.split(":");
                if (parts.length < 3) continue;
                stmt.setInt(1, plotId);
                stmt.setString(2, plot.getWorldName());
                stmt.setInt(3, Integer.parseInt(parts[0]));
                stmt.setInt(4, Integer.parseInt(parts[1]));
                stmt.setInt(5, Integer.parseInt(parts[2]));
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        // Renter Blocks (Blocs placés par les locataires)
        if (plot.getRenterBlockTracker() != null) {
            String renterSql = "INSERT INTO plot_renter_blocks (plot_id, renter_uuid, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(renterSql)) {
                for (Map.Entry<UUID, Set<RenterBlockTracker.BlockPosition>> entry : plot.getRenterBlockTracker().getAllBlocks().entrySet()) {
                    String renterUuid = entry.getKey().toString();
                    for (RenterBlockTracker.BlockPosition pos : entry.getValue()) {
                        stmt.setInt(1, plotId);
                        stmt.setString(2, renterUuid);
                        stmt.setString(3, pos.getWorldName());
                        stmt.setInt(4, pos.getX());
                        stmt.setInt(5, pos.getY());
                        stmt.setInt(6, pos.getZ());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
        }

        // Mailbox (Boîte aux lettres) - Utiliser plotNumber (R-001, R-002, etc.)
        // Note: Seuls les terrains non-PUBLIC ont un plotNumber et peuvent avoir une mailbox
        // FIX: Ne sauvegarder la mailbox qu'UNE SEULE FOIS par plotNumber (évite UNIQUE constraint pour terrains multi-chunks)
        String plotNumber = plot.getPlotNumber();
        if (plot.hasMailbox() && plotNumber != null && !savedMailboxes.contains(plotNumber)) {
            savedMailboxes.add(plotNumber); // Marquer comme sauvegardée
            com.gravityyfh.roleplaycity.postal.data.Mailbox mailbox = plot.getMailbox();
            Location loc = mailbox.headLocation();

            // Simplifié: pas besoin de owner_uuid/owner_name car le terrain a déjà ces infos
            String mailboxSql = "INSERT INTO mailboxes (town_name, plot_group_id, world, x, y, z, mailbox_type, creation_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(mailboxSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, plot.getTownName());
                stmt.setString(2, plot.getPlotNumber());
                stmt.setString(3, loc.getWorld().getName());
                stmt.setDouble(4, loc.getX());
                stmt.setDouble(5, loc.getY());
                stmt.setDouble(6, loc.getZ());
                stmt.setString(7, mailbox.type().name());
                stmt.setString(8, LocalDateTime.now().format(DATE_FORMAT));
                stmt.executeUpdate();

                // Récupérer l'ID généré pour insérer les items
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int mailboxId = rs.getInt(1);

                        // Sauvegarder les items du courrier
                        Map<Integer, org.bukkit.inventory.ItemStack> items = mailbox.items();
                        if (!items.isEmpty()) {
                            String itemSql = "INSERT INTO mail_items (mailbox_id, item_data, retrieved) VALUES (?, ?, 0)";
                            try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                                for (org.bukkit.inventory.ItemStack item : items.values()) {
                                    if (item != null) {
                                        String itemData = com.gravityyfh.roleplaycity.util.InventorySerializer.serializeItem(item);
                                        itemStmt.setInt(1, mailboxId);
                                        itemStmt.setString(2, itemData);
                                        itemStmt.addBatch();
                                    }
                                }
                                itemStmt.executeBatch();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Supprime une ville et toutes ses données associées de la base de données
     * Cela inclut: parcelles, membres, invitations, entreprises, amendes, prison,
     * boîtes aux lettres, rendez-vous, traitements médicaux, etc.
     * @param townName Le nom de la ville à supprimer
     */
    public void deleteTownFromDB(String townName) {
        plugin.getLogger().info("[TownPersistence] Suppression COMPLÈTE de la ville '" + townName + "' de la base de données...");

        try {
            connectionManager.executeTransaction(conn -> {
                // ========================================
                // ÉTAPE 1: Récupérer les IDs nécessaires
                // ========================================

                // 1a. Récupérer tous les plot IDs
                List<Integer> plotIds = new ArrayList<>();
                String getPlotIdsSql = "SELECT id FROM plots WHERE town_name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(getPlotIdsSql)) {
                    stmt.setString(1, townName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            plotIds.add(rs.getInt("id"));
                        }
                    }
                }

                // 1b. Récupérer tous les mailbox IDs pour supprimer les mail_items
                List<Integer> mailboxIds = new ArrayList<>();
                String getMailboxIdsSql = "SELECT id FROM mailboxes WHERE town_name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(getMailboxIdsSql)) {
                    stmt.setString(1, townName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            mailboxIds.add(rs.getInt("id"));
                        }
                    }
                }

                // 1c. Récupérer tous les SIRET des entreprises de cette ville
                List<String> entrepriseSirets = new ArrayList<>();
                String getEntreprisesSql = "SELECT siret FROM entreprises WHERE ville = ?";
                try (PreparedStatement stmt = conn.prepareStatement(getEntreprisesSql)) {
                    stmt.setString(1, townName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            entrepriseSirets.add(rs.getString("siret"));
                        }
                    }
                }

                // ========================================
                // ÉTAPE 2: Supprimer les données liées aux parcelles
                // ========================================
                for (int plotId : plotIds) {
                    deleteRelatedPlotData(conn, plotId);
                }

                // ========================================
                // ÉTAPE 3: Supprimer les données liées aux boîtes aux lettres
                // ========================================
                for (int mailboxId : mailboxIds) {
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM mail_items WHERE mailbox_id = ?")) {
                        stmt.setInt(1, mailboxId);
                        stmt.executeUpdate();
                    }
                }

                // ========================================
                // ÉTAPE 4: Supprimer les données liées aux entreprises
                // ========================================
                for (String siret : entrepriseSirets) {
                    deleteEntrepriseRelatedData(conn, siret);
                }

                // ========================================
                // ÉTAPE 5: Supprimer toutes les tables liées à town_name
                // ========================================

                // Parcelles
                executeDeleteByTownName(conn, "DELETE FROM plots WHERE town_name = ?", townName, "parcelles");

                // Membres
                executeDeleteByTownName(conn, "DELETE FROM town_members WHERE town_name = ?", townName, "membres");

                // Invitations
                executeDeleteByTownName(conn, "DELETE FROM town_invites WHERE town_name = ?", townName, "invitations");

                // Groupes de parcelles
                executeDeleteByTownName(conn, "DELETE FROM plot_groups WHERE town_name = ?", townName, "groupes de parcelles");

                // Boîtes aux lettres
                executeDeleteByTownName(conn, "DELETE FROM mailboxes WHERE town_name = ?", townName, "boîtes aux lettres");

                // Amendes
                executeDeleteByTownName(conn, "DELETE FROM fines WHERE town_name = ?", townName, "amendes");

                // Prison
                executeDeleteByTownName(conn, "DELETE FROM prisons WHERE town_name = ?", townName, "prisons");

                // Joueurs emprisonnés
                executeDeleteByTownName(conn, "DELETE FROM imprisoned_players WHERE town_name = ?", townName, "joueurs emprisonnés");

                // Rendez-vous mairie
                executeDeleteByTownName(conn, "DELETE FROM appointments WHERE town_name = ?", townName, "rendez-vous");

                // Entreprises de cette ville
                executeDeleteByTownName(conn, "DELETE FROM entreprises WHERE ville = ?", townName, "entreprises");

                // Traitements médicaux (SET NULL dans le schéma, mais on nettoie quand même)
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE medical_treatments SET town_name = NULL WHERE town_name = ?")) {
                    stmt.setString(1, townName);
                    stmt.executeUpdate();
                }

                // Mettre à jour les identités (ville de résidence)
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE identities SET residence_city = NULL WHERE residence_city = ?")) {
                    stmt.setString(1, townName);
                    stmt.executeUpdate();
                }

                // ========================================
                // ÉTAPE 6: Supprimer la ville elle-même
                // ========================================
                String deleteTownSql = "DELETE FROM towns WHERE name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteTownSql)) {
                    stmt.setString(1, townName);
                    int deleted = stmt.executeUpdate();
                    if (deleted > 0) {
                        plugin.getLogger().info("[TownPersistence] ✓ Ville '" + townName + "' supprimée COMPLÈTEMENT de la base de données");
                    } else {
                        plugin.getLogger().warning("[TownPersistence] Ville '" + townName + "' non trouvée dans la base de données");
                    }
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[TownPersistence] ❌ Erreur lors de la suppression de la ville '" + townName + "'", e);
        }
    }

    /**
     * Exécute une requête DELETE par town_name et log le résultat
     */
    private void executeDeleteByTownName(Connection conn, String sql, String townName, String tableName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, townName);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("[TownPersistence] Supprimé " + deleted + " " + tableName);
            }
        }
    }

    /**
     * Supprime toutes les données liées à une entreprise (employés, activités, transactions, etc.)
     */
    private void deleteEntrepriseRelatedData(Connection conn, String siret) throws SQLException {
        // Récupérer les IDs d'activité pour supprimer les actions détaillées
        List<Integer> activityIds = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM employee_activities WHERE entreprise_siret = ?")) {
            stmt.setString(1, siret);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    activityIds.add(rs.getInt("id"));
                }
            }
        }

        // Supprimer les actions et production détaillée
        for (int activityId : activityIds) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM employee_actions WHERE activity_id = ?")) {
                stmt.setInt(1, activityId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM detailed_production WHERE activity_id = ?")) {
                stmt.setInt(1, activityId);
                stmt.executeUpdate();
            }
        }

        // Supprimer les autres tables liées à l'entreprise
        String[] entrepriseTables = {
            "employes",
            "employee_activities",
            "global_production",
            "transactions",
            "production_log",
            "service_sessions",
            "shops"
        };

        for (String table : entrepriseTables) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE entreprise_siret = ?")) {
                stmt.setString(1, siret);
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Supprime les données liées à une parcelle (flags, permissions, trusted, protected blocks, renter blocks)
     */
    private void deleteRelatedPlotData(Connection conn, int plotId) throws SQLException {
        // Supprimer les flags
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM plot_flags WHERE plot_id = ?")) {
            stmt.setInt(1, plotId);
            stmt.executeUpdate();
        }

        // Supprimer les permissions
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM plot_permissions WHERE plot_id = ?")) {
            stmt.setInt(1, plotId);
            stmt.executeUpdate();
        }

        // Supprimer les trusted players
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM plot_trusted WHERE plot_id = ?")) {
            stmt.setInt(1, plotId);
            stmt.executeUpdate();
        }

        // Supprimer les autorisations (owner/renter)
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM plot_authorizations WHERE plot_id = ?")) {
            stmt.setInt(1, plotId);
            stmt.executeUpdate();
        }

        // Supprimer les protected blocks
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM plot_protected_blocks WHERE plot_id = ?")) {
            stmt.setInt(1, plotId);
            stmt.executeUpdate();
        }

        // Supprimer les renter blocks
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM plot_renter_blocks WHERE plot_id = ?")) {
            stmt.setInt(1, plotId);
            stmt.executeUpdate();
        }
    }

    /**
     * Migration depuis YAML vers SQLite
     */
    public void migrateFromYAML(Map<String, Town> townsFromYAML) {
        plugin.getLogger().info("[Towns] Migration de " + townsFromYAML.size() + " villes depuis YAML...");

        try {
            connectionManager.executeTransaction(conn -> {
                for (Town town : townsFromYAML.values()) {
                    saveTown(conn, town);
                }
            });

            plugin.getLogger().info("[Towns] Migration terminée avec succès");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Towns] Erreur migration YAML", e);
        }
    }
}