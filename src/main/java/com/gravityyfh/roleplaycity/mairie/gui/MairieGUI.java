package com.gravityyfh.roleplaycity.mairie.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
import com.gravityyfh.roleplaycity.identity.data.Identity;
import com.gravityyfh.roleplaycity.identity.manager.IdentityManager;
import com.gravityyfh.roleplaycity.identity.util.IdentityDisplayHelper;
import com.gravityyfh.roleplaycity.mairie.service.AppointmentManager;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import com.gravityyfh.roleplaycity.town.manager.TownPoliceManager;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;

/**
 * GUI principal de la Mairie - Hub pour tous les services municipaux
 */
public class MairieGUI {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final IdentityManager identityManager;
    private final AppointmentManager appointmentManager;
    private final String townName;

    public MairieGUI(RoleplayCity plugin, TownManager townManager, IdentityManager identityManager,
                     AppointmentManager appointmentManager, String townName) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.identityManager = identityManager;
        this.appointmentManager = appointmentManager;
        this.townName = townName;
    }

    public void open(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Le systeme d'interface n'est pas encore initialise.");
            return;
        }

        // Vérifier si le joueur a une carte d'identité
        Identity identity = identityManager.getIdentity(player.getUniqueId());

        if (identity == null) {
            // Pas d'identité → Ouvrir le formulaire de création
            openIdentityCreationMenu(player);
        } else {
            // Identité valide → Animation de scan RP
            openScanAnimation(player, identity);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION DE SCAN DE CARTE D'IDENTITE (RP)
    // ═══════════════════════════════════════════════════════════════════════════

    private void openScanAnimation(Player player, Identity identity) {
        SmartInventory.builder()
                .id("mairieScan")
                .provider(new ScanAnimationProvider(identity))
                .size(3, 9)
                .title(ChatColor.DARK_GRAY + "Borne Automatique - Mairie")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class ScanAnimationProvider implements InventoryProvider {
        private final Identity identity;
        private int tick = 0;
        private int phase = 0;

        private final String[] scanMessages = {
            "Insertion de la carte...",
            "Lecture de la puce...",
            "Verification de l'identite...",
            "Connexion au serveur municipal...",
            "Authentification reussie!"
        };

        public ScanAnimationProvider(Identity identity) {
            this.identity = identity;
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            // Carte d'identité au centre
            List<String> cardLore = new ArrayList<>();
            cardLore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━");
            cardLore.add(ChatColor.WHITE + "Nom: " + ChatColor.AQUA + player.getName());
            cardLore.add(ChatColor.WHITE + "Sexe: " + ChatColor.AQUA + identity.getSex());
            cardLore.add(ChatColor.WHITE + "Age: " + ChatColor.AQUA + identity.getAge() + " ans");
            cardLore.add(ChatColor.WHITE + "Taille: " + ChatColor.AQUA + identity.getHeight() + " cm");
            cardLore.add(ChatColor.WHITE + "Ville: " + ChatColor.AQUA +
                    (identity.getResidenceCity() != null ? identity.getResidenceCity() : "Aucune"));
            cardLore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━");

            contents.set(1, 4, ClickableItem.empty(
                    createItem(Material.PAPER, ChatColor.GOLD + "Carte d'Identite", cardLore)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {
            tick++;

            if (tick % 10 == 0) { // Toutes les 0.5 secondes
                if (phase < scanMessages.length) {
                    // Afficher le message de scan
                    String message = scanMessages[phase];
                    ChatColor color = phase < 4 ? ChatColor.YELLOW : ChatColor.GREEN;

                    // Barre de progression
                    StringBuilder progressBar = new StringBuilder();
                    for (int i = 0; i < 9; i++) {
                        if (i <= phase * 2) {
                            progressBar.append("§a█");
                        } else {
                            progressBar.append("§8█");
                        }
                    }

                    contents.set(0, 4, ClickableItem.empty(
                            createItem(phase < 4 ? Material.YELLOW_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                                    color + message)));

                    // Barre de progression en bas
                    for (int i = 0; i < 9; i++) {
                        Material mat = i <= phase * 2 ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
                        contents.set(2, i, ClickableItem.empty(createItem(mat, " ")));
                    }

                    // Son
                    if (phase < 4) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f + (phase * 0.1f));
                    } else {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }

                    phase++;
                } else if (phase == scanMessages.length) {
                    // Animation terminée - ouvrir le menu principal
                    phase++;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            openMainMenu(player);
                        }
                    }.runTask(plugin);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATION DE CARTE D'IDENTITE (FORMULAIRE COMPLET)
    // ═══════════════════════════════════════════════════════════════════════════

    // Données temporaires pour la création d'identité
    private final Map<UUID, IdentityCreationData> creationData = new HashMap<>();

    private static class IdentityCreationData {
        String sexe;
        int age;
        int taille;
        String villeResidence;
        int step = 0; // 0=sexe, 1=age, 2=taille, 3=ville, 4=confirmation
    }

    private void openIdentityCreationMenu(Player player) {
        // Initialiser les données de création SEULEMENT si elles n'existent pas
        if (!creationData.containsKey(player.getUniqueId())) {
            creationData.put(player.getUniqueId(), new IdentityCreationData());
        }

        SmartInventory.builder()
                .id("mairieCreateIdentity")
                .provider(new IdentityCreationProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Creation Carte d'Identite")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class IdentityCreationProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            IdentityCreationData data = creationData.get(player.getUniqueId());
            if (data == null) {
                data = new IdentityCreationData();
                creationData.put(player.getUniqueId(), data);
            }

            // En-tête
            List<String> headerLore = new ArrayList<>();
            headerLore.add(ChatColor.RED + "⚠ CARTE D'IDENTITE REQUISE");
            headerLore.add("");
            headerLore.add(ChatColor.GRAY + "Pour acceder aux services de la");
            headerLore.add(ChatColor.GRAY + "mairie, vous devez creer votre");
            headerLore.add(ChatColor.GRAY + "carte d'identite.");
            headerLore.add("");
            headerLore.add(ChatColor.YELLOW + "Remplissez les informations ci-dessous:");

            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.BOOK, ChatColor.GOLD + "Borne Automatique", headerLore)));

            // === INFO NOM (automatique) ===
            List<String> nomLore = new ArrayList<>();
            nomLore.add(ChatColor.GREEN + "✔ " + player.getName());
            nomLore.add("");
            nomLore.add(ChatColor.GRAY + "(Nom Minecraft automatique)");

            contents.set(1, 2, ClickableItem.empty(
                    createItem(Material.LIME_DYE, ChatColor.AQUA + "Nom", nomLore)));

            // === ETAPE 1: SEXE ===
            List<String> sexeLore = new ArrayList<>();
            if (data.sexe != null) {
                sexeLore.add(ChatColor.GREEN + "✔ " + data.sexe);
            } else {
                sexeLore.add(ChatColor.RED + "Non renseigne");
                sexeLore.add("");
                sexeLore.add(ChatColor.YELLOW + "Cliquez pour choisir");
            }

            contents.set(1, 3, ClickableItem.of(
                    createItem(data.sexe != null ? Material.LIME_DYE : Material.RED_DYE,
                            ChatColor.AQUA + "1. Sexe", sexeLore),
                    e -> openSexSelectionMenu(player)));

            // === ETAPE 2: AGE ===
            List<String> ageLore = new ArrayList<>();
            if (data.age > 0) {
                ageLore.add(ChatColor.GREEN + "✔ " + data.age + " ans");
            } else {
                ageLore.add(ChatColor.RED + "Non renseigne");
                ageLore.add("");
                ageLore.add(ChatColor.YELLOW + "Cliquez pour entrer");
            }

            contents.set(1, 4, ClickableItem.of(
                    createItem(data.age > 0 ? Material.LIME_DYE : Material.RED_DYE,
                            ChatColor.AQUA + "2. Age", ageLore),
                    e -> askForAge(player)));

            // === ETAPE 3: TAILLE ===
            List<String> tailleLore = new ArrayList<>();
            if (data.taille > 0) {
                tailleLore.add(ChatColor.GREEN + "✔ " + data.taille + " cm");
            } else {
                tailleLore.add(ChatColor.RED + "Non renseigne");
                tailleLore.add("");
                tailleLore.add(ChatColor.YELLOW + "Cliquez pour entrer");
            }

            contents.set(1, 5, ClickableItem.of(
                    createItem(data.taille > 0 ? Material.LIME_DYE : Material.RED_DYE,
                            ChatColor.AQUA + "3. Taille", tailleLore),
                    e -> askForTaille(player)));

            // === ETAPE 4: VILLE DE RESIDENCE ===
            List<String> villeLore = new ArrayList<>();
            if (data.villeResidence != null) {
                villeLore.add(ChatColor.GREEN + "✔ " + data.villeResidence);
            } else {
                villeLore.add(ChatColor.RED + "Non renseigne");
                villeLore.add("");
                villeLore.add(ChatColor.YELLOW + "Cliquez pour choisir");
            }

            contents.set(1, 6, ClickableItem.of(
                    createItem(data.villeResidence != null ? Material.LIME_DYE : Material.RED_DYE,
                            ChatColor.AQUA + "4. Ville", villeLore),
                    e -> openVilleSelectionMenu(player)));

            // === RESUME ===
            List<String> resumeLore = new ArrayList<>();
            resumeLore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━");
            resumeLore.add(ChatColor.WHITE + "Nom: " + ChatColor.GREEN + player.getName());
            resumeLore.add(ChatColor.WHITE + "Sexe: " + (data.sexe != null ? ChatColor.GREEN + data.sexe : ChatColor.RED + "..."));
            resumeLore.add(ChatColor.WHITE + "Age: " + (data.age > 0 ? ChatColor.GREEN + "" + data.age + " ans" : ChatColor.RED + "..."));
            resumeLore.add(ChatColor.WHITE + "Taille: " + (data.taille > 0 ? ChatColor.GREEN + "" + data.taille + " cm" : ChatColor.RED + "..."));
            resumeLore.add(ChatColor.WHITE + "Ville: " + (data.villeResidence != null ? ChatColor.GREEN + data.villeResidence : ChatColor.RED + "..."));
            resumeLore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━");

            contents.set(2, 4, ClickableItem.empty(
                    createItem(Material.PAPER, ChatColor.GOLD + "Apercu Carte d'Identite", resumeLore)));

            // === VALIDER ===
            boolean isComplete = data.sexe != null && data.age > 0 && data.taille > 0 && data.villeResidence != null;

            List<String> validateLore = new ArrayList<>();
            if (isComplete) {
                validateLore.add(ChatColor.GREEN + "Toutes les informations sont");
                validateLore.add(ChatColor.GREEN + "renseignees!");
                validateLore.add("");
                validateLore.add(ChatColor.YELLOW + "Cliquez pour creer votre carte");
            } else {
                validateLore.add(ChatColor.RED + "Remplissez tous les champs");
                validateLore.add(ChatColor.RED + "avant de valider.");
            }

            IdentityCreationData finalData = data;
            contents.set(3, 4, ClickableItem.of(
                    createItem(isComplete ? Material.EMERALD_BLOCK : Material.GRAY_DYE,
                            isComplete ? ChatColor.GREEN + "✔ VALIDER ET CREER" : ChatColor.GRAY + "Incomplet...", validateLore),
                    e -> {
                        if (isComplete) {
                            createIdentityFromData(player, finalData);
                        } else {
                            player.sendMessage(ChatColor.RED + "Veuillez remplir tous les champs!");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        }
                    }));

            // Fermer
            contents.set(3, 0, ClickableItem.of(
                    createItem(Material.BARRIER, ChatColor.RED + "Annuler"),
                    e -> {
                        creationData.remove(player.getUniqueId());
                        player.closeInventory();
                        player.sendMessage(ChatColor.RED + "Creation de carte annulee.");
                    }));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private void askForAge(Player player) {
        player.closeInventory();
        plugin.getChatInputListener().requestInput(
                player,
                ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                ChatColor.AQUA + "        CREATION CARTE D'IDENTITE\n" +
                ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                ChatColor.YELLOW + "Entrez votre AGE (18-99):",
                (input) -> {
                    IdentityCreationData data = creationData.get(player.getUniqueId());
                    if (data != null) {
                        data.age = Integer.parseInt(input);
                        player.sendMessage(ChatColor.GREEN + "Age enregistre: " + data.age + " ans");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    }
                    openIdentityCreationMenu(player);
                },
                (input) -> {
                    try {
                        int a = Integer.parseInt(input);
                        return a >= 18 && a <= 99;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                ChatColor.RED + "L'age doit etre un nombre entre 18 et 99.",
                120
        );
    }

    private void askForTaille(Player player) {
        player.closeInventory();
        plugin.getChatInputListener().requestInput(
                player,
                ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                ChatColor.AQUA + "        CREATION CARTE D'IDENTITE\n" +
                ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                ChatColor.YELLOW + "Entrez votre TAILLE en cm (140-220):",
                (input) -> {
                    IdentityCreationData data = creationData.get(player.getUniqueId());
                    if (data != null) {
                        data.taille = Integer.parseInt(input);
                        player.sendMessage(ChatColor.GREEN + "Taille enregistree: " + data.taille + " cm");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    }
                    openIdentityCreationMenu(player);
                },
                (input) -> {
                    try {
                        int h = Integer.parseInt(input);
                        return h >= 140 && h <= 220;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                ChatColor.RED + "La taille doit etre un nombre entre 140 et 220 cm.",
                120
        );
    }

    private void openSexSelectionMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieSelectSex")
                .provider(new SexSelectionProvider())
                .size(3, 9)
                .title(ChatColor.DARK_GRAY + "Choisissez votre sexe")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class SexSelectionProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.ARMOR_STAND, ChatColor.GOLD + "Choisissez votre sexe")));

            // Homme
            contents.set(1, 3, ClickableItem.of(
                    createItem(Material.LIGHT_BLUE_DYE, ChatColor.AQUA + "Homme",
                            "",
                            ChatColor.YELLOW + "Cliquez pour selectionner"),
                    e -> {
                        IdentityCreationData data = creationData.get(player.getUniqueId());
                        if (data != null) {
                            data.sexe = "Homme";
                            player.sendMessage(ChatColor.GREEN + "Sexe selectionne: Homme");
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        }
                        openIdentityCreationMenu(player);
                    }));

            // Femme
            contents.set(1, 5, ClickableItem.of(
                    createItem(Material.PINK_DYE, ChatColor.LIGHT_PURPLE + "Femme",
                            "",
                            ChatColor.YELLOW + "Cliquez pour selectionner"),
                    e -> {
                        IdentityCreationData data = creationData.get(player.getUniqueId());
                        if (data != null) {
                            data.sexe = "Femme";
                            player.sendMessage(ChatColor.GREEN + "Sexe selectionne: Femme");
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        }
                        openIdentityCreationMenu(player);
                    }));

            // Retour
            contents.set(2, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openIdentityCreationMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private void openVilleSelectionMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieSelectVille")
                .provider(new VilleSelectionProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Ville de Residence")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class VilleSelectionProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.COMPASS, ChatColor.GOLD + "Choisissez votre ville de residence")));

            List<Town> towns = new ArrayList<>(townManager.getTowns().values());

            int slot = 0;
            for (Town town : towns) {
                if (slot >= 18) break;

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Population: " + ChatColor.WHITE + town.getMembers().size());
                lore.add("");
                lore.add(ChatColor.YELLOW + "Cliquez pour selectionner");

                final Town selectedTown = town;
                contents.set(1 + (slot / 9), slot % 9, ClickableItem.of(
                        createItem(Material.COMPASS, ChatColor.AQUA + town.getName(), lore),
                        e -> {
                            IdentityCreationData data = creationData.get(player.getUniqueId());
                            if (data != null) {
                                data.villeResidence = selectedTown.getName();
                                player.sendMessage(ChatColor.GREEN + "Ville selectionnee: " + selectedTown.getName());
                                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                            }
                            openIdentityCreationMenu(player);
                        }));
                slot++;
            }

            if (towns.isEmpty()) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Aucune ville disponible")));
            }

            // Retour
            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openIdentityCreationMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private void createIdentityFromData(Player player, IdentityCreationData data) {
        player.closeInventory();

        // Créer l'identité via IdentityManager
        // Utilise le nom Minecraft du joueur (pas de prénom/nom personnalisé)
        identityManager.createIdentity(
                player.getUniqueId(),
                player.getName(),
                "",
                data.sexe,
                data.age,
                data.taille
        );

        // Mettre à jour la ville de résidence
        Identity identity = identityManager.getIdentity(player.getUniqueId());
        if (identity != null) {
            identity.setResidenceCity(data.villeResidence);
            identityManager.updateIdentity(identity);
        }

        // Nettoyer les données temporaires
        creationData.remove(player.getUniqueId());

        // Messages de confirmation
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GREEN + "  ✔ CARTE D'IDENTITE CREEE!");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.WHITE + "  Nom: " + ChatColor.AQUA + player.getName());
        player.sendMessage(ChatColor.WHITE + "  Sexe: " + ChatColor.AQUA + data.sexe);
        player.sendMessage(ChatColor.WHITE + "  Age: " + ChatColor.AQUA + data.age + " ans");
        player.sendMessage(ChatColor.WHITE + "  Taille: " + ChatColor.AQUA + data.taille + " cm");
        player.sendMessage(ChatColor.WHITE + "  Ville: " + ChatColor.AQUA + data.villeResidence);
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "  Vous pouvez maintenant acceder");
        player.sendMessage(ChatColor.YELLOW + "  aux services de la mairie!");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        // Rouvrir la borne avec le scan
        new BukkitRunnable() {
            @Override
            public void run() {
                open(player);
            }
        }.runTaskLater(plugin, 40L); // 2 secondes
    }

    public void openMainMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Le systeme d'interface n'est pas encore initialise.");
            return;
        }

        SmartInventory.builder()
                .id("mairieMain")
                .provider(new MainMenuProvider())
                .size(6, 9)
                .title(ChatColor.DARK_GRAY + "Mairie de " + ChatColor.GOLD + townName)
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ═══════════════════════════════════════════════════════════════════════════

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVIDERS
    // ═══════════════════════════════════════════════════════════════════════════

    private class MainMenuProvider implements InventoryProvider {

        @Override
        public void init(Player player, InventoryContents contents) {
            ItemStack blackPane = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
            contents.fill(ClickableItem.empty(blackPane));

            Town town = townManager.getTown(townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
                player.closeInventory();
                return;
            }

            UUID playerUuid = player.getUniqueId();
            TownRole role = town.getMemberRole(playerUuid);
            boolean isCitizen = town.isMember(playerUuid);
            boolean isAdmin = (role == TownRole.MAIRE || role == TownRole.ADJOINT);

            // ═══════════════════════════════════════════════════════════
            // LIGNE 0 - EN-TETE
            // ═══════════════════════════════════════════════════════════
            ItemStack goldPane = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 9; i++) {
                contents.set(0, i, ClickableItem.empty(goldPane));
            }

            List<String> headerLore = new ArrayList<>();
            headerLore.add(ChatColor.GRAY + "Bienvenue a la Mairie de " + townName);
            headerLore.add("");
            headerLore.add(ChatColor.GRAY + "Services disponibles:");
            headerLore.add(ChatColor.WHITE + " - Identite et documents");
            headerLore.add(ChatColor.WHITE + " - Gestion immobiliere");
            headerLore.add(ChatColor.WHITE + " - Services fiscaux");
            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.ENCHANTED_BOOK, ChatColor.GOLD + "Mairie de " + townName, headerLore)));

            // ═══════════════════════════════════════════════════════════
            // LIGNE 1-2 - SERVICES IDENTITE ET DOCUMENTS
            // ═══════════════════════════════════════════════════════════
            ItemStack cyanPane = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");
            contents.set(1, 0, ClickableItem.empty(cyanPane));
            contents.set(1, 8, ClickableItem.empty(cyanPane));

            // === CARTE D'IDENTITE ===
            Identity identity = identityManager.getIdentity(playerUuid);
            boolean hasIdentity = identity != null;

            List<String> idLore = new ArrayList<>();
            if (hasIdentity) {
                idLore.add(ChatColor.GREEN + "Carte d'identite valide");
                idLore.add("");
                idLore.add(ChatColor.GRAY + "Nom: " + ChatColor.WHITE + player.getName());
                idLore.add(ChatColor.GRAY + "Sexe: " + ChatColor.WHITE + identity.getSex());
                idLore.add(ChatColor.GRAY + "Age: " + ChatColor.WHITE + identity.getAge() + " ans");
                idLore.add(ChatColor.GRAY + "Taille: " + ChatColor.WHITE + identity.getHeight() + " cm");
                idLore.add(ChatColor.GRAY + "Ville: " + ChatColor.WHITE +
                        (identity.getResidenceCity() != null ? identity.getResidenceCity() : "Aucune"));
                idLore.add("");
                idLore.add(ChatColor.YELLOW + "Cliquez pour voir votre carte");
            } else {
                idLore.add(ChatColor.RED + "Aucune carte d'identite");
                idLore.add("");
                idLore.add(ChatColor.YELLOW + "Cliquez pour creer votre carte");
            }

            contents.set(1, 2, ClickableItem.of(
                    createItem(hasIdentity ? Material.PAPER : Material.WRITABLE_BOOK,
                            ChatColor.AQUA + "Carte d'Identite", idLore),
                    e -> {
                        player.closeInventory();
                        if (hasIdentity) {
                            // Afficher la carte via IdentityGUI
                            if (plugin.getMainMenuGUI() != null && plugin.getMainMenuGUI().getIdentityGUI() != null) {
                                plugin.getMainMenuGUI().getIdentityGUI().openMainMenu(player);
                            } else {
                                player.sendMessage(ChatColor.RED + "Système d'identité non disponible.");
                            }
                        } else {
                            // Créer via MairieGUI
                            openIdentityCreationMenu(player);
                        }
                    }));

            // === MODIFIER IDENTITE (25E) ===
            List<String> modifyLore = new ArrayList<>();
            if (hasIdentity) {
                modifyLore.add(ChatColor.GRAY + "Modifier votre identite");
                modifyLore.add("");
                modifyLore.add(ChatColor.GOLD + "Cout: 25E");
                modifyLore.add(ChatColor.GRAY + "(verse a la nouvelle ville)");
                modifyLore.add("");
                modifyLore.add(ChatColor.YELLOW + "Cliquez pour modifier");
            } else {
                modifyLore.add(ChatColor.RED + "Creez d'abord votre carte");
            }

            contents.set(1, 4, ClickableItem.of(
                    createItem(hasIdentity ? Material.NAME_TAG : Material.GRAY_DYE,
                            ChatColor.AQUA + "Modifier Identite " + ChatColor.GRAY + "(25E)", modifyLore),
                    e -> {
                        if (hasIdentity) {
                            player.closeInventory();
                            openIdentityModificationMenu(player);
                        } else {
                            player.sendMessage(ChatColor.RED + "Vous devez d'abord creer votre carte d'identite.");
                        }
                    }));

            // === RENDEZ-VOUS ===
            int pendingRdv = appointmentManager.countPlayerPendingAppointments(playerUuid);
            int remainingSlots = appointmentManager.getRemainingAppointmentSlots(playerUuid);

            List<String> rdvLore = new ArrayList<>();
            rdvLore.add(ChatColor.GRAY + "Prendre rendez-vous avec");
            rdvLore.add(ChatColor.GRAY + "le Maire ou ses adjoints");
            rdvLore.add("");
            rdvLore.add(ChatColor.GRAY + "RDV en attente: " + ChatColor.WHITE + pendingRdv + "/3");
            rdvLore.add(ChatColor.GRAY + "Places restantes: " + ChatColor.WHITE + remainingSlots);
            rdvLore.add("");
            if (remainingSlots > 0) {
                rdvLore.add(ChatColor.YELLOW + "Cliquez pour demander un RDV");
            } else {
                rdvLore.add(ChatColor.RED + "Limite de 3 RDV atteinte");
            }

            contents.set(1, 6, ClickableItem.of(
                    createItem(remainingSlots > 0 ? Material.CLOCK : Material.GRAY_DYE,
                            ChatColor.AQUA + "Rendez-vous", rdvLore),
                    e -> {
                        player.closeInventory();
                        openAppointmentMenu(player);
                    }));

            // ═══════════════════════════════════════════════════════════
            // LIGNE 2 - GESTION IMMOBILIERE
            // ═══════════════════════════════════════════════════════════
            ItemStack greenPane = createItem(Material.LIME_STAINED_GLASS_PANE, " ");
            contents.set(2, 0, ClickableItem.empty(greenPane));
            contents.set(2, 8, ClickableItem.empty(greenPane));

            // === MES PARCELLES ===
            List<Plot> ownedPlots = getPlayerOwnedPlots(player, town);
            List<Plot> rentedPlots = getPlayerRentedPlots(player, town);
            int totalPlots = ownedPlots.size() + rentedPlots.size();

            List<String> plotsLore = new ArrayList<>();
            plotsLore.add(ChatColor.GRAY + "Consultez vos proprietes");
            plotsLore.add("");
            plotsLore.add(ChatColor.GREEN + "Parcelles possedees: " + ChatColor.WHITE + ownedPlots.size());
            plotsLore.add(ChatColor.YELLOW + "Parcelles louees: " + ChatColor.WHITE + rentedPlots.size());
            plotsLore.add("");
            plotsLore.add(ChatColor.YELLOW + "Cliquez pour voir le detail");

            contents.set(2, 2, ClickableItem.of(
                    createItem(totalPlots > 0 ? Material.GRASS_BLOCK : Material.GRAY_DYE,
                            ChatColor.GREEN + "Mes Parcelles", plotsLore),
                    e -> {
                        player.closeInventory();
                        openMyPlotsMenu(player);
                    }));

            // === TAXES ===
            double taxRate = town.getCitizenTax();

            List<String> taxLore = new ArrayList<>();
            taxLore.add(ChatColor.GRAY + "Gerez vos impots municipaux");
            taxLore.add("");
            taxLore.add(ChatColor.GRAY + "Taux citoyen: " + ChatColor.WHITE + String.format("%.2fE", taxRate));
            taxLore.add("");
            taxLore.add(ChatColor.YELLOW + "Cliquez pour acceder");

            contents.set(2, 4, ClickableItem.of(
                    createItem(Material.GOLD_INGOT, ChatColor.GREEN + "Taxes et Impots", taxLore),
                    e -> {
                        player.closeInventory();
                        openTaxMenu(player);
                    }));

            // === DETTES ===
            List<Town.PlayerDebt> debts = town.getPlayerDebts(playerUuid);
            double totalDebt = town.getTotalPlayerDebt(playerUuid);

            List<String> debtLore = new ArrayList<>();
            if (!debts.isEmpty()) {
                debtLore.add(ChatColor.RED + "ATTENTION: " + debts.size() + " dette(s)");
                debtLore.add("");
                debtLore.add(ChatColor.GRAY + "Total du: " + ChatColor.RED + String.format("%.2fE", totalDebt));
                debtLore.add("");
                debtLore.add(ChatColor.YELLOW + "Cliquez pour payer");
            } else {
                debtLore.add(ChatColor.GREEN + "Aucune dette");
                debtLore.add("");
                debtLore.add(ChatColor.GRAY + "Situation financiere saine");
            }

            contents.set(2, 6, ClickableItem.of(
                    createItem(!debts.isEmpty() ? Material.REDSTONE_BLOCK : Material.LIME_DYE,
                            (!debts.isEmpty() ? ChatColor.RED : ChatColor.GREEN) + "Mes Dettes", debtLore),
                    e -> {
                        player.closeInventory();
                        openDebtMenu(player);
                    }));

            // ═══════════════════════════════════════════════════════════
            // LIGNE 3 - INFORMATIONS CITOYENNES
            // ═══════════════════════════════════════════════════════════
            ItemStack bluePane = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
            contents.set(3, 0, ClickableItem.empty(bluePane));
            contents.set(3, 8, ClickableItem.empty(bluePane));

            // === ENTREPRISES DE LA VILLE ===
            List<Entreprise> townEnterprises = getTownEnterprises();

            List<String> entLore = new ArrayList<>();
            entLore.add(ChatColor.GRAY + "Entreprises installees a " + townName);
            entLore.add("");
            entLore.add(ChatColor.WHITE + "" + townEnterprises.size() + " entreprise(s)");
            entLore.add("");
            entLore.add(ChatColor.YELLOW + "Cliquez pour voir la liste");

            contents.set(3, 2, ClickableItem.of(
                    createItem(Material.CHEST, ChatColor.BLUE + "Entreprises de la Ville", entLore),
                    e -> {
                        player.closeInventory();
                        openTownEnterprisesMenu(player);
                    }));

            // === STATUT CITOYEN ===
            List<String> statusLore = new ArrayList<>();
            if (isCitizen) {
                TownMember member = town.getMember(playerUuid);
                long memberSinceMs = member != null ?
                    member.getJoinDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() :
                    System.currentTimeMillis();
                long daysAsCitizen = (System.currentTimeMillis() - memberSinceMs) / (1000 * 60 * 60 * 24);

                statusLore.add(ChatColor.GREEN + "Citoyen de " + townName);
                statusLore.add("");
                statusLore.add(ChatColor.GRAY + "Role: " + ChatColor.WHITE + (role != null ? role.getDisplayName() : "Citoyen"));
                statusLore.add(ChatColor.GRAY + "Anciennete: " + ChatColor.WHITE + daysAsCitizen + " jours");
            } else {
                statusLore.add(ChatColor.YELLOW + "Visiteur");
            }
            statusLore.add("");
            statusLore.add(ChatColor.YELLOW + "Cliquez pour plus de details");

            contents.set(3, 4, ClickableItem.of(
                    createItem(isCitizen ? Material.PLAYER_HEAD : Material.SKELETON_SKULL,
                            ChatColor.BLUE + "Statut Citoyen", statusLore),
                    e -> {
                        player.closeInventory();
                        openCitizenStatusMenu(player);
                    }));

            // === CLASSEMENT CITOYENS ===
            List<String> rankLore = new ArrayList<>();
            rankLore.add(ChatColor.GRAY + "Classement des citoyens");
            rankLore.add("");
            rankLore.add(ChatColor.WHITE + "Par richesse (Banque + Poche)");
            rankLore.add(ChatColor.WHITE + "Par anciennete");
            rankLore.add("");
            rankLore.add(ChatColor.YELLOW + "Cliquez pour voir");

            contents.set(3, 6, ClickableItem.of(
                    createItem(Material.GOLD_BLOCK, ChatColor.BLUE + "Classement Citoyens", rankLore),
                    e -> {
                        player.closeInventory();
                        openCitizenRankingMenu(player);
                    }));

            // ═══════════════════════════════════════════════════════════
            // LIGNE 4 - JUSTICE
            // ═══════════════════════════════════════════════════════════
            ItemStack purplePane = createItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
            contents.set(4, 0, ClickableItem.empty(purplePane));
            contents.set(4, 8, ClickableItem.empty(purplePane));

            // === CASIER JUDICIAIRE ===
            TownPoliceManager policeManager = plugin.getTownPoliceManager();
            List<Fine> playerFines = policeManager != null ?
                    policeManager.getPlayerFines(playerUuid) : new ArrayList<>();
            long unpaidCount = playerFines.stream().filter(Fine::isPending).count();

            List<String> casierLore = new ArrayList<>();
            casierLore.add(ChatColor.GRAY + "Consultez votre casier judiciaire");
            casierLore.add("");
            casierLore.add(ChatColor.WHITE + "Total amendes: " + playerFines.size());
            if (unpaidCount > 0) {
                casierLore.add(ChatColor.RED + "Impayees: " + unpaidCount);
            }
            casierLore.add("");
            casierLore.add(ChatColor.YELLOW + "Cliquez pour consulter");

            contents.set(4, 2, ClickableItem.of(
                    createItem(unpaidCount > 0 ? Material.IRON_BARS : Material.BOOK,
                            ChatColor.LIGHT_PURPLE + "Casier Judiciaire", casierLore),
                    e -> {
                        player.closeInventory();
                        openCriminalRecordMenu(player);
                    }));

            // === PAYER AMENDES ===
            double unpaidTotal = playerFines.stream()
                    .filter(Fine::isPending)
                    .mapToDouble(Fine::getAmount)
                    .sum();

            List<String> finesLore = new ArrayList<>();
            if (unpaidCount > 0) {
                finesLore.add(ChatColor.RED + "" + unpaidCount + " amende(s) a payer");
                finesLore.add("");
                finesLore.add(ChatColor.GRAY + "Total: " + ChatColor.RED + String.format("%.2fE", unpaidTotal));
                finesLore.add("");
                finesLore.add(ChatColor.YELLOW + "Cliquez pour payer");
            } else {
                finesLore.add(ChatColor.GREEN + "Aucune amende a payer");
            }

            contents.set(4, 4, ClickableItem.of(
                    createItem(unpaidCount > 0 ? Material.GOLD_INGOT : Material.LIME_DYE,
                            ChatColor.LIGHT_PURPLE + "Payer Amendes", finesLore),
                    e -> {
                        player.closeInventory();
                        openPayFinesMenu(player);
                    }));

            // === GESTION RDV ADMIN ===
            if (isAdmin) {
                int townPendingRdv = appointmentManager.countTownPendingAppointments(townName);

                List<String> adminRdvLore = new ArrayList<>();
                adminRdvLore.add(ChatColor.GRAY + "Gerer les rendez-vous");
                adminRdvLore.add("");
                if (townPendingRdv > 0) {
                    adminRdvLore.add(ChatColor.YELLOW + "" + townPendingRdv + " RDV en attente");
                } else {
                    adminRdvLore.add(ChatColor.GREEN + "Aucun RDV en attente");
                }
                adminRdvLore.add("");
                adminRdvLore.add(ChatColor.YELLOW + "Cliquez pour gerer");

                contents.set(4, 6, ClickableItem.of(
                        createItem(townPendingRdv > 0 ? Material.BELL : Material.GRAY_DYE,
                                ChatColor.LIGHT_PURPLE + "Gestion RDV " + ChatColor.GRAY + "(Admin)", adminRdvLore),
                        e -> {
                            player.closeInventory();
                            openAdminAppointmentMenu(player);
                        }));
            }

            // ═══════════════════════════════════════════════════════════
            // LIGNE 5 - NAVIGATION
            // ═══════════════════════════════════════════════════════════
            ItemStack redPane = createItem(Material.RED_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 9; i++) {
                contents.set(5, i, ClickableItem.empty(redPane));
            }

            contents.set(5, 4, ClickableItem.of(
                    createItem(Material.BARRIER, ChatColor.RED + "Fermer"),
                    e -> player.closeInventory()));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // METHODES UTILITAIRES
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Plot> getPlayerOwnedPlots(Player player, Town town) {
        List<Plot> owned = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (player.getUniqueId().equals(plot.getOwnerUuid())) {
                owned.add(plot);
            }
        }
        return owned;
    }

    private List<Plot> getPlayerRentedPlots(Player player, Town town) {
        List<Plot> rented = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (player.getUniqueId().equals(plot.getRenterUuid())) {
                rented.add(plot);
            }
        }
        return rented;
    }

    private List<Entreprise> getTownEnterprises() {
        try {
            return plugin.getEntrepriseManagerLogic().getEntreprisesByVille(townName);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SOUS-MENUS
    // ═══════════════════════════════════════════════════════════════════════════

    private void openMyPlotsMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairiePlots")
                .provider(new MyPlotsProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Mairie - Mes Parcelles")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openTaxMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieTaxes")
                .provider(new TaxMenuProvider())
                .size(3, 9)
                .title(ChatColor.DARK_GRAY + "Mairie - Taxes")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openDebtMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieDebts")
                .provider(new DebtMenuProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Mairie - Mes Dettes")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openTownEnterprisesMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieEnterprises")
                .provider(new TownEnterprisesProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Entreprises de " + townName)
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openCitizenStatusMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieStatus")
                .provider(new CitizenStatusProvider())
                .size(3, 9)
                .title(ChatColor.DARK_GRAY + "Mairie - Statut Citoyen")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openCitizenRankingMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieRanking")
                .provider(new CitizenRankingProvider())
                .size(6, 9)
                .title(ChatColor.DARK_GRAY + "Classement - " + townName)
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openCriminalRecordMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieCriminal")
                .provider(new CriminalRecordProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Mairie - Casier Judiciaire")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openPayFinesMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairiePayFines")
                .provider(new PayFinesProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Mairie - Payer Amendes")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openIdentityModificationMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieModifyIdentity")
                .provider(new IdentityModificationProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Modifier Identite " + ChatColor.GRAY + "(25E)")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openAppointmentMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieAppointments")
                .provider(new AppointmentMenuProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Mairie - Rendez-vous")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private void openAdminAppointmentMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieAdminAppointments")
                .provider(new AdminAppointmentProvider())
                .size(5, 9)
                .title(ChatColor.DARK_GRAY + "Gestion RDV - " + townName)
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVIDERS DES SOUS-MENUS
    // ═══════════════════════════════════════════════════════════════════════════

    private class MyPlotsProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            Town town = townManager.getTown(townName);
            if (town == null) return;

            List<Plot> owned = getPlayerOwnedPlots(player, town);
            List<Plot> rented = getPlayerRentedPlots(player, town);

            int slot = 0;
            for (Plot plot : owned) {
                if (slot >= 18) break;
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GREEN + "PROPRIETAIRE");
                lore.add("");
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName());

                contents.set(slot / 9, slot % 9, ClickableItem.empty(
                        createItem(Material.GRASS_BLOCK,
                                ChatColor.GREEN + "Parcelle " + (slot + 1), lore)));
                slot++;
            }

            for (Plot plot : rented) {
                if (slot >= 18) break;
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.YELLOW + "LOCATAIRE");
                lore.add("");
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName());

                contents.set(slot / 9, slot % 9, ClickableItem.empty(
                        createItem(Material.OAK_DOOR,
                                ChatColor.YELLOW + "Location " + (slot + 1 - owned.size()), lore)));
                slot++;
            }

            if (slot == 0) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Aucune propriete")));
            }

            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class TaxMenuProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            Town town = townManager.getTown(townName);
            if (town == null) return;

            List<String> infoLore = new ArrayList<>();
            infoLore.add(ChatColor.GRAY + "Taux citoyen: " + ChatColor.WHITE + String.format("%.2fE", town.getCitizenTax()));
            infoLore.add(ChatColor.GRAY + "Taux entreprise: " + ChatColor.WHITE + String.format("%.2fE", town.getCompanyTax()));

            contents.set(1, 4, ClickableItem.empty(
                    createItem(Material.GOLD_INGOT, ChatColor.GOLD + "Taxes Municipales", infoLore)));

            contents.set(2, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class DebtMenuProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            Town town = townManager.getTown(townName);
            if (town == null) return;

            List<Town.PlayerDebt> debts = town.getPlayerDebts(player.getUniqueId());

            if (debts.isEmpty()) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.LIME_DYE, ChatColor.GREEN + "Aucune dette")));
            } else {
                int slot = 0;
                for (Town.PlayerDebt debt : debts) {
                    if (slot >= 14) break;
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Montant: " + ChatColor.RED + String.format("%.2fE", debt.amount()));
                    lore.add(ChatColor.GRAY + "Parcelle: " + ChatColor.WHITE + debt.plot().getPlotNumber());
                    lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + debt.plot().getType().getDisplayName());
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Cliquez pour payer");

                    final Town.PlayerDebt debtToPay = debt;
                    contents.set(slot / 7, (slot % 7) + 1, ClickableItem.of(
                            createItem(Material.PAPER, ChatColor.RED + "Dette #" + (slot + 1), lore),
                            e -> {
                                double balance = RoleplayCity.getEconomy().getBalance(player);
                                if (balance >= debtToPay.amount()) {
                                    RoleplayCity.getEconomy().withdrawPlayer(player, debtToPay.amount());
                                    // Réinitialiser la dette sur la parcelle
                                    Plot plot = debtToPay.plot();
                                    if (plot.getCompanyDebtAmount() > 0) {
                                        plot.setCompanyDebtAmount(0);
                                    } else {
                                        plot.setParticularDebtAmount(0);
                                    }
                                    town.deposit(debtToPay.amount());
                                    player.sendMessage(ChatColor.GREEN + "Dette payee!");
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                                    openDebtMenu(player);
                                } else {
                                    player.sendMessage(ChatColor.RED + "Fonds insuffisants!");
                                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                }
                            }));
                    slot++;
                }
            }

            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class TownEnterprisesProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            List<Entreprise> enterprises = getTownEnterprises();

            if (enterprises.isEmpty()) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Aucune entreprise")));
            } else {
                int slot = 0;
                for (Entreprise ent : enterprises) {
                    if (slot >= 18) break;
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Gerant: " + ChatColor.WHITE + ent.getGerant());
                    lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + ent.getType());
                    lore.add(ChatColor.GRAY + "Capital: " + ChatColor.GOLD + String.format("%.2fE", ent.getSolde()));

                    contents.set(slot / 9, slot % 9, ClickableItem.empty(
                            createItem(Material.CHEST, ChatColor.BLUE + ent.getNom(), lore)));
                    slot++;
                }
            }

            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class CitizenStatusProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            Town town = townManager.getTown(townName);
            if (town == null) return;

            UUID playerUuid = player.getUniqueId();
            boolean isCitizen = town.isMember(playerUuid);

            if (!isCitizen) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Non citoyen")));
            } else {
                TownMember member = town.getMember(playerUuid);
                TownRole role = town.getMemberRole(playerUuid);

                List<String> statusLore = new ArrayList<>();
                statusLore.add(ChatColor.GRAY + "Ville: " + ChatColor.WHITE + townName);
                statusLore.add(ChatColor.GRAY + "Role: " + ChatColor.WHITE + (role != null ? role.getDisplayName() : "Citoyen"));
                if (member != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                    Date joinDate = Date.from(member.getJoinDate().atZone(ZoneId.systemDefault()).toInstant());
                    statusLore.add(ChatColor.GRAY + "Membre depuis: " + ChatColor.WHITE + sdf.format(joinDate));
                }

                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.PLAYER_HEAD, ChatColor.GREEN + player.getName(), statusLore)));
            }

            contents.set(2, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class CitizenRankingProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            Town town = townManager.getTown(townName);
            if (town == null) return;

            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.ENCHANTED_BOOK,
                            ChatColor.GOLD + "Classement par Richesse (BANQUE+POCHE)")));

            List<TownMember> members = new ArrayList<>(town.getMembers().values());

            // Classement par richesse
            List<Map.Entry<TownMember, Double>> wealthList = new ArrayList<>();

            for (TownMember member : members) {
                org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(member.getPlayerUuid());
                double wallet = RoleplayCity.getEconomy().getBalance(offlinePlayer);
                double bank = 0;
                try {
                    de.lightplugins.economy.database.querys.BankTableAsync bankTable =
                            new de.lightplugins.economy.database.querys.BankTableAsync(
                                    de.lightplugins.economy.master.Main.getInstance);
                    Double bankBalance = bankTable.playerBankBalance(member.getPlayerName()).join();
                    if (bankBalance != null) bank = bankBalance;
                } catch (Exception ignored) {}
                wealthList.add(new AbstractMap.SimpleEntry<>(member, wallet + bank));
            }

            wealthList.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            int slot = 0;
            int rank = 1;
            for (Map.Entry<TownMember, Double> entry : wealthList) {
                if (slot >= 36) break;
                TownMember member = entry.getKey();
                double wealth = entry.getValue();

                String medal = rank <= 3 ? (rank == 1 ? "§6#1" : (rank == 2 ? "§7#2" : "§c#3")) : "§f#" + rank;
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Richesse totale:");
                lore.add(ChatColor.GOLD + String.format("%.2fE", wealth));
                lore.add("");
                lore.add(ChatColor.GRAY + "(Banque + Poche)");

                // Utiliser le nom d'identité au lieu du pseudo Minecraft
                String displayName = IdentityDisplayHelper.getDisplayName(member.getPlayerUuid());
                contents.set(1 + (slot / 9), slot % 9, ClickableItem.empty(
                        createItem(rank <= 3 ? Material.PLAYER_HEAD : Material.SKELETON_SKULL,
                                medal + " " + ChatColor.WHITE + displayName, lore)));
                slot++;
                rank++;
            }

            contents.set(5, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class CriminalRecordProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            TownPoliceManager policeManager = plugin.getTownPoliceManager();
            if (policeManager == null) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Systeme indisponible")));
                contents.set(3, 4, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                        e -> openMainMenu(player)));
                return;
            }

            List<Fine> fines = policeManager.getPlayerFines(player.getUniqueId());

            if (fines.isEmpty()) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.LIME_DYE, ChatColor.GREEN + "Casier vierge")));
            } else {
                int slot = 0;
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
                for (Fine fine : fines) {
                    if (slot >= 18) break;

                    boolean isPaid = fine.getStatus() == Fine.FineStatus.PAID;
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Montant: " + (isPaid ? ChatColor.GREEN : ChatColor.RED) +
                            String.format("%.2fE", fine.getAmount()));
                    lore.add(ChatColor.GRAY + "Motif: " + ChatColor.WHITE + fine.getReason());
                    Date issueDate = Date.from(fine.getIssueDate().atZone(ZoneId.systemDefault()).toInstant());
                    lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + sdf.format(issueDate));
                    lore.add(ChatColor.GRAY + "Par: " + ChatColor.WHITE + fine.getPolicierName());
                    lore.add("");
                    lore.add(isPaid ? ChatColor.GREEN + "PAYEE" : ChatColor.RED + fine.getStatus().getDisplayName());

                    contents.set(slot / 9, slot % 9, ClickableItem.empty(
                            createItem(isPaid ? Material.LIME_DYE : Material.RED_DYE,
                                    ChatColor.YELLOW + "Amende #" + (slot + 1), lore)));
                    slot++;
                }
            }

            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class PayFinesProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            TownPoliceManager policeManager = plugin.getTownPoliceManager();
            if (policeManager == null) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Systeme indisponible")));
                contents.set(3, 4, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                        e -> openMainMenu(player)));
                return;
            }

            List<Fine> unpaidFines = policeManager.getPlayerFines(player.getUniqueId()).stream()
                    .filter(Fine::isPending)
                    .collect(java.util.stream.Collectors.toList());

            if (unpaidFines.isEmpty()) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.LIME_DYE, ChatColor.GREEN + "Aucune amende a payer")));
            } else {
                int slot = 0;
                for (Fine fine : unpaidFines) {
                    if (slot >= 14) break;

                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Montant: " + ChatColor.RED + String.format("%.2fE", fine.getAmount()));
                    lore.add(ChatColor.GRAY + "Motif: " + ChatColor.WHITE + fine.getReason());
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Cliquez pour payer");

                    final Fine fineToPay = fine;
                    contents.set(slot / 7, (slot % 7) + 1, ClickableItem.of(
                            createItem(Material.GOLD_INGOT, ChatColor.RED + "Amende: " +
                                    String.format("%.2fE", fine.getAmount()), lore),
                            e -> {
                                double balance = RoleplayCity.getEconomy().getBalance(player);
                                if (balance >= fineToPay.getAmount()) {
                                    RoleplayCity.getEconomy().withdrawPlayer(player, fineToPay.getAmount());
                                    fineToPay.markAsPaid();

                                    Town town = townManager.getTown(fineToPay.getTownName());
                                    if (town != null) {
                                        town.deposit(fineToPay.getAmount());
                                    }

                                    player.sendMessage(ChatColor.GREEN + "Amende payee!");
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                                    openPayFinesMenu(player);
                                } else {
                                    player.sendMessage(ChatColor.RED + "Fonds insuffisants!");
                                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                }
                            }));
                    slot++;
                }

                double total = unpaidFines.stream().mapToDouble(Fine::getAmount).sum();
                contents.set(2, 4, ClickableItem.of(
                        createItem(Material.GOLD_BLOCK, ChatColor.GOLD + "Payer Tout: " + String.format("%.2fE", total)),
                        e -> {
                            double balance = RoleplayCity.getEconomy().getBalance(player);
                            if (balance >= total) {
                                for (Fine fine : unpaidFines) {
                                    RoleplayCity.getEconomy().withdrawPlayer(player, fine.getAmount());
                                    fine.markAsPaid();
                                    Town town = townManager.getTown(fine.getTownName());
                                    if (town != null) {
                                        town.deposit(fine.getAmount());
                                    }
                                }
                                player.sendMessage(ChatColor.GREEN + "Toutes les amendes payees!");
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                                openPayFinesMenu(player);
                            } else {
                                player.sendMessage(ChatColor.RED + "Fonds insuffisants!");
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            }
                        }));
            }

            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MODIFICATION IDENTITE (25€)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final double IDENTITY_MODIFICATION_COST = 25.0;

    private class IdentityModificationProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            Identity identity = identityManager.getIdentity(player.getUniqueId());
            if (identity == null) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Aucune identite")));
                contents.set(3, 4, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                        e -> openMainMenu(player)));
                return;
            }

            // En-tête avec les infos actuelles
            List<String> currentLore = new ArrayList<>();
            currentLore.add(ChatColor.GRAY + "Informations actuelles:");
            currentLore.add("");
            currentLore.add(ChatColor.GRAY + "Nom: " + ChatColor.WHITE + player.getName());
            currentLore.add(ChatColor.GRAY + "Sexe: " + ChatColor.WHITE + identity.getSex());
            currentLore.add(ChatColor.GRAY + "Age: " + ChatColor.WHITE + identity.getAge() + " ans");
            currentLore.add(ChatColor.GRAY + "Taille: " + ChatColor.WHITE + identity.getHeight() + " cm");
            currentLore.add(ChatColor.GRAY + "Ville: " + ChatColor.WHITE +
                    (identity.getResidenceCity() != null ? identity.getResidenceCity() : "Aucune"));
            currentLore.add("");
            currentLore.add(ChatColor.GOLD + "Cout modification: 25E");

            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.PAPER, ChatColor.AQUA + "Votre Identite", currentLore)));

            // === MODIFIER SEXE ===
            contents.set(1, 1, ClickableItem.of(
                    createItem(Material.ARMOR_STAND, ChatColor.YELLOW + "Modifier Sexe",
                            ChatColor.GRAY + "Actuel: " + ChatColor.WHITE + identity.getSex(),
                            "",
                            ChatColor.GOLD + "Cout: 25E",
                            ChatColor.YELLOW + "Cliquez pour modifier"),
                    e -> openSexModificationMenu(player)));

            // === MODIFIER AGE ===
            contents.set(1, 5, ClickableItem.of(
                    createItem(Material.CLOCK, ChatColor.YELLOW + "Modifier Age",
                            ChatColor.GRAY + "Actuel: " + ChatColor.WHITE + identity.getAge() + " ans",
                            "",
                            ChatColor.GOLD + "Cout: 25E",
                            ChatColor.YELLOW + "Cliquez pour modifier"),
                    e -> startModification(player, "AGE")));

            // === MODIFIER TAILLE ===
            contents.set(1, 7, ClickableItem.of(
                    createItem(Material.IRON_BOOTS, ChatColor.YELLOW + "Modifier Taille",
                            ChatColor.GRAY + "Actuel: " + ChatColor.WHITE + identity.getHeight() + " cm",
                            "",
                            ChatColor.GOLD + "Cout: 25E",
                            ChatColor.YELLOW + "Cliquez pour modifier"),
                    e -> startModification(player, "TAILLE")));

            // === MODIFIER VILLE DE RESIDENCE ===
            List<String> villesDisponibles = new ArrayList<>();
            for (Town t : townManager.getTowns().values()) {
                villesDisponibles.add(t.getName());
            }

            List<String> residenceLore = new ArrayList<>();
            residenceLore.add(ChatColor.GRAY + "Actuel: " + ChatColor.WHITE +
                    (identity.getResidenceCity() != null ? identity.getResidenceCity() : "Non definie"));
            residenceLore.add("");
            residenceLore.add(ChatColor.GOLD + "Cout: 25E");
            residenceLore.add(ChatColor.GRAY + "(verse a la nouvelle ville)");
            residenceLore.add("");
            residenceLore.add(ChatColor.YELLOW + "Cliquez pour changer");

            contents.set(2, 4, ClickableItem.of(
                    createItem(Material.COMPASS, ChatColor.YELLOW + "Changer Ville de Residence", residenceLore),
                    e -> openResidenceCityMenu(player)));

            // Retour
            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private void startModification(Player player, String type) {
        player.closeInventory();

        // Vérifier le solde
        double balance = RoleplayCity.getEconomy().getBalance(player);
        if (balance < IDENTITY_MODIFICATION_COST) {
            player.sendMessage(ChatColor.RED + "Fonds insuffisants! Il vous faut 25E.");
            return;
        }

        switch (type) {
            case "PRENOM":
                plugin.getChatInputListener().requestInput(
                        player,
                        ChatColor.YELLOW + "Entrez votre nouveau prenom:",
                        (input) -> {
                            if (processPaymentAndModify(player, () -> {
                                Identity identity = identityManager.getIdentity(player.getUniqueId());
                                if (identity != null) {
                                    identity.setFirstName(input);
                                    identityManager.updateIdentity(identity);
                                }
                            })) {
                                player.sendMessage(ChatColor.GREEN + "Prenom modifie en: " + input);
                            }
                        },
                        (input) -> input != null && input.length() >= 2 && input.length() <= 20,
                        ChatColor.RED + "Le prenom doit faire entre 2 et 20 caracteres.",
                        60
                );
                break;

            case "AGE":
                plugin.getChatInputListener().requestInput(
                        player,
                        ChatColor.YELLOW + "Entrez votre nouvel age (18-99):",
                        (input) -> {
                            int newAge = Integer.parseInt(input);
                            if (processPaymentAndModify(player, () -> {
                                Identity identity = identityManager.getIdentity(player.getUniqueId());
                                if (identity != null) {
                                    identity.setAge(newAge);
                                    identityManager.updateIdentity(identity);
                                }
                            })) {
                                player.sendMessage(ChatColor.GREEN + "Age modifie en: " + newAge + " ans");
                            }
                        },
                        (input) -> {
                            try {
                                int a = Integer.parseInt(input);
                                return a >= 18 && a <= 99;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        },
                        ChatColor.RED + "L'age doit etre entre 18 et 99.",
                        60
                );
                break;

            case "TAILLE":
                plugin.getChatInputListener().requestInput(
                        player,
                        ChatColor.YELLOW + "Entrez votre nouvelle taille en cm (140-220):",
                        (input) -> {
                            int newHeight = Integer.parseInt(input);
                            if (processPaymentAndModify(player, () -> {
                                Identity identity = identityManager.getIdentity(player.getUniqueId());
                                if (identity != null) {
                                    identity.setHeight(newHeight);
                                    identityManager.updateIdentity(identity);
                                }
                            })) {
                                player.sendMessage(ChatColor.GREEN + "Taille modifiee en: " + newHeight + " cm");
                            }
                        },
                        (input) -> {
                            try {
                                int h = Integer.parseInt(input);
                                return h >= 140 && h <= 220;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        },
                        ChatColor.RED + "La taille doit etre entre 140 et 220 cm.",
                        60
                );
                break;
        }
    }

    private boolean processPaymentAndModify(Player player, Runnable modification) {
        double balance = RoleplayCity.getEconomy().getBalance(player);
        if (balance < IDENTITY_MODIFICATION_COST) {
            player.sendMessage(ChatColor.RED + "Fonds insuffisants!");
            return false;
        }

        // Prélever l'argent
        RoleplayCity.getEconomy().withdrawPlayer(player, IDENTITY_MODIFICATION_COST);

        // Verser à la ville de la mairie
        Town town = townManager.getTown(townName);
        if (town != null) {
            town.deposit(IDENTITY_MODIFICATION_COST);
        }

        // Effectuer la modification
        modification.run();

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    private void openSexModificationMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieModifySex")
                .provider(new SexModificationProvider())
                .size(3, 9)
                .title(ChatColor.DARK_GRAY + "Modifier Sexe " + ChatColor.GRAY + "(25E)")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class SexModificationProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            // Homme
            contents.set(1, 3, ClickableItem.of(
                    createItem(Material.LIGHT_BLUE_DYE, ChatColor.AQUA + "Homme",
                            "",
                            ChatColor.GOLD + "Cout: 25E",
                            ChatColor.YELLOW + "Cliquez pour selectionner"),
                    e -> {
                        if (processPaymentAndModify(player, () -> {
                            Identity identity = identityManager.getIdentity(player.getUniqueId());
                            if (identity != null) {
                                identity.setSex("Homme");
                                identityManager.updateIdentity(identity);
                            }
                        })) {
                            player.sendMessage(ChatColor.GREEN + "Sexe modifie en: Homme");
                            player.closeInventory();
                        }
                    }));

            // Femme
            contents.set(1, 5, ClickableItem.of(
                    createItem(Material.PINK_DYE, ChatColor.LIGHT_PURPLE + "Femme",
                            "",
                            ChatColor.GOLD + "Cout: 25E",
                            ChatColor.YELLOW + "Cliquez pour selectionner"),
                    e -> {
                        if (processPaymentAndModify(player, () -> {
                            Identity identity = identityManager.getIdentity(player.getUniqueId());
                            if (identity != null) {
                                identity.setSex("Femme");
                                identityManager.updateIdentity(identity);
                            }
                        })) {
                            player.sendMessage(ChatColor.GREEN + "Sexe modifie en: Femme");
                            player.closeInventory();
                        }
                    }));

            // Retour
            contents.set(2, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openIdentityModificationMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private void openResidenceCityMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieModifyResidence")
                .provider(new ResidenceCityProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Changer Residence " + ChatColor.GRAY + "(25E)")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class ResidenceCityProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            Identity identity = identityManager.getIdentity(player.getUniqueId());
            String currentCity = identity != null ? identity.getResidenceCity() : null;

            List<Town> towns = new ArrayList<>(townManager.getTowns().values());

            int slot = 0;
            for (Town town : towns) {
                if (slot >= 18) break;

                boolean isCurrentCity = town.getName().equalsIgnoreCase(currentCity);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Population: " + ChatColor.WHITE + town.getMembers().size());
                lore.add("");
                if (isCurrentCity) {
                    lore.add(ChatColor.GREEN + "Residence actuelle");
                } else {
                    lore.add(ChatColor.GOLD + "Cout: 25E");
                    lore.add(ChatColor.GRAY + "(verse a " + town.getName() + ")");
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Cliquez pour choisir");
                }

                final Town targetTown = town;
                contents.set(slot / 9, slot % 9, ClickableItem.of(
                        createItem(isCurrentCity ? Material.LIME_DYE : Material.COMPASS,
                                (isCurrentCity ? ChatColor.GREEN : ChatColor.AQUA) + town.getName(), lore),
                        e -> {
                            if (isCurrentCity) {
                                player.sendMessage(ChatColor.YELLOW + "C'est deja votre ville de residence!");
                                return;
                            }

                            double balance = RoleplayCity.getEconomy().getBalance(player);
                            if (balance < IDENTITY_MODIFICATION_COST) {
                                player.sendMessage(ChatColor.RED + "Fonds insuffisants! Il vous faut 25E.");
                                return;
                            }

                            // Prélever l'argent
                            RoleplayCity.getEconomy().withdrawPlayer(player, IDENTITY_MODIFICATION_COST);

                            // Verser à la NOUVELLE ville (pas l'ancienne!)
                            targetTown.deposit(IDENTITY_MODIFICATION_COST);

                            // Modifier la résidence
                            if (identity != null) {
                                identity.setResidenceCity(targetTown.getName());
                                identityManager.updateIdentity(identity);
                            }

                            player.sendMessage(ChatColor.GREEN + "Ville de residence changee en: " + targetTown.getName());
                            player.sendMessage(ChatColor.GRAY + "(25E verses a " + targetTown.getName() + ")");
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            player.closeInventory();
                        }));
                slot++;
            }

            if (towns.isEmpty()) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Aucune ville disponible")));
            }

            // Retour
            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openIdentityModificationMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYSTEME DE RENDEZ-VOUS
    // ═══════════════════════════════════════════════════════════════════════════

    private class AppointmentMenuProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            UUID playerUuid = player.getUniqueId();
            int pendingCount = appointmentManager.countPlayerPendingAppointments(playerUuid);
            int remainingSlots = appointmentManager.getRemainingAppointmentSlots(playerUuid);

            // En-tête
            List<String> headerLore = new ArrayList<>();
            headerLore.add(ChatColor.GRAY + "Gerez vos rendez-vous avec");
            headerLore.add(ChatColor.GRAY + "le Maire ou ses adjoints");
            headerLore.add("");
            headerLore.add(ChatColor.GRAY + "RDV en attente: " + ChatColor.WHITE + pendingCount + "/3");
            headerLore.add(ChatColor.GRAY + "Places restantes: " + ChatColor.WHITE + remainingSlots);

            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.CLOCK, ChatColor.GOLD + "Mes Rendez-vous", headerLore)));

            // === DEMANDER UN RDV ===
            List<String> newRdvLore = new ArrayList<>();
            if (remainingSlots > 0) {
                newRdvLore.add(ChatColor.GRAY + "Demander un nouveau");
                newRdvLore.add(ChatColor.GRAY + "rendez-vous a la mairie");
                newRdvLore.add("");
                newRdvLore.add(ChatColor.YELLOW + "Cliquez pour demander");
            } else {
                newRdvLore.add(ChatColor.RED + "Limite de 3 RDV atteinte");
                newRdvLore.add("");
                newRdvLore.add(ChatColor.GRAY + "Attendez qu'un RDV soit traite");
            }

            contents.set(1, 2, ClickableItem.of(
                    createItem(remainingSlots > 0 ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                            ChatColor.GREEN + "Nouveau Rendez-vous", newRdvLore),
                    e -> {
                        if (remainingSlots > 0) {
                            startAppointmentRequest(player);
                        } else {
                            player.sendMessage(ChatColor.RED + "Vous avez deja 3 rendez-vous en attente!");
                        }
                    }));

            // === MES RDV EN ATTENTE ===
            List<com.gravityyfh.roleplaycity.mairie.data.Appointment> myAppointments =
                    appointmentManager.getPlayerAppointments(playerUuid);

            List<String> myRdvLore = new ArrayList<>();
            myRdvLore.add(ChatColor.GRAY + "Voir vos rendez-vous");
            myRdvLore.add(ChatColor.GRAY + "en attente de traitement");
            myRdvLore.add("");
            myRdvLore.add(ChatColor.WHITE + "" + pendingCount + " RDV en attente");

            contents.set(1, 6, ClickableItem.of(
                    createItem(pendingCount > 0 ? Material.BOOK : Material.GRAY_DYE,
                            ChatColor.AQUA + "Mes RDV en attente", myRdvLore),
                    e -> {
                        player.closeInventory();
                        openMyAppointmentsMenu(player);
                    }));

            // Liste rapide des RDV en attente
            int slot = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
            for (com.gravityyfh.roleplaycity.mairie.data.Appointment appt : myAppointments) {
                if (slot >= 7 || !appt.isPending()) continue;

                List<String> apptLore = new ArrayList<>();
                apptLore.add(ChatColor.GRAY + "Sujet: " + ChatColor.WHITE + appt.getSubject());
                Date requestDate = Date.from(appt.getRequestDate().atZone(ZoneId.systemDefault()).toInstant());
                apptLore.add(ChatColor.GRAY + "Demande: " + ChatColor.WHITE + sdf.format(requestDate));
                apptLore.add(ChatColor.GRAY + "Jours restants: " + ChatColor.WHITE + (15 - appt.getDaysSinceRequest()));
                apptLore.add("");
                apptLore.add(ChatColor.YELLOW + "En attente de traitement");

                contents.set(2, slot + 1, ClickableItem.empty(
                        createItem(Material.PAPER, ChatColor.YELLOW + "RDV #" + (slot + 1), apptLore)));
                slot++;
            }

            // Retour
            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private void startAppointmentRequest(Player player) {
        player.closeInventory();

        plugin.getChatInputListener().requestInput(
                player,
                ChatColor.YELLOW + "Entrez le sujet de votre rendez-vous:",
                (input) -> {
                    com.gravityyfh.roleplaycity.mairie.data.Appointment appointment =
                            appointmentManager.createAppointment(player.getUniqueId(), player.getName(), townName, input);

                    if (appointment != null) {
                        player.sendMessage(ChatColor.GREEN + "Rendez-vous demande avec succes!");
                        player.sendMessage(ChatColor.GRAY + "Sujet: " + ChatColor.WHITE + input);
                        player.sendMessage(ChatColor.GRAY + "Le Maire ou un adjoint traitera votre demande.");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    } else {
                        player.sendMessage(ChatColor.RED + "Impossible de creer le rendez-vous (limite atteinte?)");
                    }
                },
                (input) -> input != null && input.length() >= 5 && input.length() <= 100,
                ChatColor.RED + "Le sujet doit faire entre 5 et 100 caracteres.",
                120
        );
    }

    private void openMyAppointmentsMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) return;

        SmartInventory.builder()
                .id("mairieMyAppointments")
                .provider(new MyAppointmentsProvider())
                .size(4, 9)
                .title(ChatColor.DARK_GRAY + "Mes Rendez-vous")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class MyAppointmentsProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            List<com.gravityyfh.roleplaycity.mairie.data.Appointment> appointments =
                    appointmentManager.getPlayerAppointments(player.getUniqueId());

            if (appointments.isEmpty()) {
                contents.set(1, 4, ClickableItem.empty(
                        createItem(Material.BARRIER, ChatColor.RED + "Aucun rendez-vous")));
            } else {
                int slot = 0;
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
                for (com.gravityyfh.roleplaycity.mairie.data.Appointment appt : appointments) {
                    if (slot >= 18) break;

                    boolean isPending = appt.isPending();
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Ville: " + ChatColor.WHITE + appt.getTownName());
                    lore.add(ChatColor.GRAY + "Sujet: " + ChatColor.WHITE + appt.getSubject());
                    Date requestDate = Date.from(appt.getRequestDate().atZone(ZoneId.systemDefault()).toInstant());
                    lore.add(ChatColor.GRAY + "Demande le: " + ChatColor.WHITE + sdf.format(requestDate));
                    lore.add("");

                    if (isPending) {
                        long daysRemaining = 15 - appt.getDaysSinceRequest();
                        lore.add(ChatColor.YELLOW + "En attente");
                        lore.add(ChatColor.GRAY + "Expire dans: " + ChatColor.WHITE + daysRemaining + " jour(s)");
                    } else {
                        lore.add(ChatColor.GREEN + "Traite");
                        if (appt.getTreatedByName() != null) {
                            lore.add(ChatColor.GRAY + "Par: " + ChatColor.WHITE + appt.getTreatedByName());
                        }
                        if (appt.getTreatedDate() != null) {
                            Date treatedDate = Date.from(appt.getTreatedDate().atZone(ZoneId.systemDefault()).toInstant());
                            lore.add(ChatColor.GRAY + "Le: " + ChatColor.WHITE + sdf.format(treatedDate));
                        }
                    }

                    contents.set(slot / 9, slot % 9, ClickableItem.empty(
                            createItem(isPending ? Material.CLOCK : Material.LIME_DYE,
                                    (isPending ? ChatColor.YELLOW : ChatColor.GREEN) + "RDV: " + appt.getSubject(), lore)));
                    slot++;
                }
            }

            contents.set(3, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openAppointmentMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private class AdminAppointmentProvider implements InventoryProvider {
        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

            List<com.gravityyfh.roleplaycity.mairie.data.Appointment> townAppointments =
                    appointmentManager.getTownAppointments(townName);

            // En-tête
            long pendingCount = townAppointments.stream()
                    .filter(com.gravityyfh.roleplaycity.mairie.data.Appointment::isPending)
                    .count();

            List<String> headerLore = new ArrayList<>();
            headerLore.add(ChatColor.GRAY + "Gestion des rendez-vous");
            headerLore.add(ChatColor.GRAY + "de la ville de " + townName);
            headerLore.add("");
            headerLore.add(ChatColor.GRAY + "En attente: " + ChatColor.YELLOW + pendingCount);
            headerLore.add(ChatColor.GRAY + "Total: " + ChatColor.WHITE + townAppointments.size());

            contents.set(0, 4, ClickableItem.empty(
                    createItem(Material.BELL, ChatColor.GOLD + "Gestion RDV - " + townName, headerLore)));

            if (townAppointments.isEmpty()) {
                contents.set(2, 4, ClickableItem.empty(
                        createItem(Material.LIME_DYE, ChatColor.GREEN + "Aucun rendez-vous en attente")));
            } else {
                int slot = 0;
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

                // Trier: en attente d'abord
                townAppointments.sort((a, b) -> {
                    if (a.isPending() != b.isPending()) return a.isPending() ? -1 : 1;
                    return a.getRequestDate().compareTo(b.getRequestDate());
                });

                for (com.gravityyfh.roleplaycity.mairie.data.Appointment appt : townAppointments) {
                    if (slot >= 27) break;

                    boolean isPending = appt.isPending();
                    // Utiliser le nom d'identité au lieu du pseudo Minecraft
                    String requesterDisplayName = IdentityDisplayHelper.getDisplayName(appt.getPlayerUuid());

                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Demandeur: " + ChatColor.WHITE + requesterDisplayName);
                    lore.add(ChatColor.GRAY + "Sujet: " + ChatColor.WHITE + appt.getSubject());
                    Date requestDate = Date.from(appt.getRequestDate().atZone(ZoneId.systemDefault()).toInstant());
                    lore.add(ChatColor.GRAY + "Demande: " + ChatColor.WHITE + sdf.format(requestDate));
                    lore.add("");

                    if (isPending) {
                        long daysRemaining = 15 - appt.getDaysSinceRequest();
                        lore.add(ChatColor.YELLOW + "EN ATTENTE");
                        lore.add(ChatColor.GRAY + "Expire dans: " + ChatColor.WHITE + daysRemaining + " jour(s)");
                        lore.add("");
                        lore.add(ChatColor.GREEN + "Clic gauche: Marquer traite");
                    } else {
                        lore.add(ChatColor.GREEN + "TRAITE");
                        if (appt.getTreatedByName() != null) {
                            // Nom d'identité de celui qui a traité
                            lore.add(ChatColor.GRAY + "Par: " + ChatColor.WHITE + appt.getTreatedByName());
                        }
                    }

                    final com.gravityyfh.roleplaycity.mairie.data.Appointment finalAppt = appt;
                    final String finalRequesterName = requesterDisplayName;
                    contents.set(1 + (slot / 9), slot % 9, ClickableItem.of(
                            createItem(isPending ? Material.PAPER : Material.LIME_DYE,
                                    (isPending ? ChatColor.YELLOW : ChatColor.GREEN) + requesterDisplayName, lore),
                            e -> {
                                if (finalAppt.isPending()) {
                                    // Utiliser le nom d'identité de l'admin qui traite
                                    String adminDisplayName = IdentityDisplayHelper.getDisplayName(player.getUniqueId());
                                    appointmentManager.markAsTreated(finalAppt.getAppointmentId(),
                                            player.getUniqueId(), adminDisplayName);

                                    player.sendMessage(ChatColor.GREEN + "RDV de " + finalRequesterName + " marque comme traite!");
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

                                    // Notifier le joueur si en ligne
                                    Player target = org.bukkit.Bukkit.getPlayer(finalAppt.getPlayerUuid());
                                    if (target != null) {
                                        target.sendMessage(ChatColor.GREEN + "[Mairie] Votre rendez-vous a ete traite par " + adminDisplayName);
                                        target.sendMessage(ChatColor.GRAY + "Sujet: " + finalAppt.getSubject());
                                    }

                                    // Rafraîchir le menu
                                    openAdminAppointmentMenu(player);
                                }
                            }));
                    slot++;
                }
            }

            // Retour
            contents.set(4, 4, ClickableItem.of(
                    createItem(Material.ARROW, ChatColor.YELLOW + "Retour"),
                    e -> openMainMenu(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }
}
