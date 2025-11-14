package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI pour afficher et gérer les entreprises d'un joueur
 */
public class MyCompaniesGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final TownMainGUI mainGUI;

    public MyCompaniesGUI(RoleplayCity plugin, TownManager townManager, TownMainGUI mainGUI) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.entrepriseLogic = plugin.getEntrepriseManagerLogic();
        this.mainGUI = mainGUI;
    }

    /**
     * Ouvre le menu "Mes Entreprises" pour le joueur
     */
    public void openCompaniesMenu(Player player) {
        List<EntrepriseManagerLogic.Entreprise> companies = entrepriseLogic.getEntreprisesGereesPar(player.getName());

        if (companies.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous ne gérez aucune entreprise.");
            return;
        }

        // Calculer la taille de l'inventaire
        int rows = Math.min(6, Math.max(3, (companies.size() + 9) / 9));
        int invSize = rows * 9;

        Inventory inv = Bukkit.createInventory(null, invSize, "Mes Entreprises");

        int slot = 0;

        for (EntrepriseManagerLogic.Entreprise company : companies) {
            if (slot >= invSize - 9) break; // Garder dernière ligne pour boutons

            ItemStack item = createCompanyItem(company, player);
            inv.setItem(slot, item);
            slot++;
        }

        // Bouton "Retour"
        int lastRow = invSize - 9;
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour au menu principal");
        backButton.setItemMeta(backMeta);
        inv.setItem(lastRow + 8, backButton);

        player.openInventory(inv);
    }

    /**
     * Crée un ItemStack représentant une entreprise
     */
    private ItemStack createCompanyItem(EntrepriseManagerLogic.Entreprise company, Player player) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "💼 " + company.getNom());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "─────────────────");
        lore.add(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + company.getType());
        lore.add(ChatColor.YELLOW + "SIRET: " + ChatColor.WHITE + company.getSiret());
        lore.add(ChatColor.YELLOW + "Solde: " + ChatColor.GOLD + String.format("%.2f€", company.getSolde()));
        lore.add(ChatColor.YELLOW + "Employés: " + ChatColor.WHITE + company.getEmployes().size());

        // SYSTÈME UNIFIÉ : Compter terrains PROFESSIONNEL (tous dans town.getPlots())
        String townName = townManager.getPlayerTown(player.getUniqueId());
        int companyPlots = 0;
        int companyGroups = 0;
        int totalChunks = 0;
        double totalDebt = 0.0;
        int plotsWithDebt = 0;
        int minDaysUntilSeizure = Integer.MAX_VALUE;

        if (townName != null) {
            List<Plot> plots = townManager.getPlotsByCompanySiret(company.getSiret(), townName);

            for (Plot plot : plots) {
                if (plot.isGrouped()) {
                    // Terrain groupé - compter une seule fois par groupe
                    companyGroups++;
                    totalChunks += plot.getChunks().size();
                } else {
                    // Terrain individuel
                    companyPlots++;
                    totalChunks++;
                }

                // Analyser les dettes
                if (plot.getCompanyDebtAmount() > 0) {
                    totalDebt += plot.getCompanyDebtAmount();
                    plotsWithDebt++;

                    // Calculer jours restants avant saisie
                    if (plot.getLastDebtWarningDate() != null) {
                        LocalDateTime warningDate = plot.getLastDebtWarningDate();
                        // ✅ FIX: Utiliser ChronoUnit.DAYS pour compter les jours calendaires
                        long daysPassed = ChronoUnit.DAYS.between(warningDate.toLocalDate(), LocalDateTime.now().toLocalDate());
                        int daysRemaining = (int) (7 - daysPassed);
                        if (daysRemaining < minDaysUntilSeizure) {
                            minDaysUntilSeizure = daysRemaining;
                        }
                    }
                }
            }
        }

        // Affichage amélioré avec groupes
        if (companyGroups > 0) {
            lore.add(ChatColor.YELLOW + "Terrains PRO: " + ChatColor.WHITE + totalChunks + " chunks " +
                    ChatColor.GRAY + "(" + companyPlots + " plots + " + companyGroups + " groupes)");
        } else {
            lore.add(ChatColor.YELLOW + "Terrains PRO: " + ChatColor.WHITE + companyPlots);
        }

        // Afficher dette si existante
        if (totalDebt > 0) {
            lore.add("");
            lore.add(ChatColor.RED + "⚠ DETTE ACTIVE");
            lore.add(ChatColor.YELLOW + "Montant total: " + ChatColor.RED + String.format("%.2f€", totalDebt));
            lore.add(ChatColor.YELLOW + "Terrains endettés: " + ChatColor.RED + plotsWithDebt);
            if (minDaysUntilSeizure < Integer.MAX_VALUE) {
                String daysText = minDaysUntilSeizure <= 0 ? "IMMINENT" : "J-" + minDaysUntilSeizure;
                lore.add(ChatColor.YELLOW + "Saisie: " + ChatColor.RED + ChatColor.BOLD + daysText);
            }
        } else {
            lore.add("");
            lore.add(ChatColor.GREEN + "✓ Aucune dette");
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Actions disponibles:");
        lore.add(ChatColor.GREEN + "▶ Clic gauche: " + ChatColor.WHITE + "Voir les terrains PRO");
        lore.add(ChatColor.AQUA + "▶ Clic droit: " + ChatColor.WHITE + "Gérer l'entreprise");
        if (totalDebt > 0) {
            lore.add(ChatColor.YELLOW + "▶ Shift + Clic: " + ChatColor.WHITE + "Payer les dettes");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Gère les clics dans le menu
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("Mes Entreprises")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;

        // Bouton "Retour"
        if (clicked.getType() == Material.ARROW) {
            player.closeInventory();
            mainGUI.openMainMenu(player);
            return;
        }

        // Clic sur une entreprise
        if (clicked.getType() == Material.CHEST) {
            EntrepriseManagerLogic.Entreprise company = findCompanyFromItem(clicked, player);
            if (company == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                return;
            }

            boolean isShiftClick = event.isShiftClick();
            boolean isRightClick = event.isRightClick();

            if (isShiftClick) {
                // Payer les dettes
                payCompanyDebts(player, company);
            } else if (isRightClick) {
                // Gérer l'entreprise (ouvrir menu /entreprise classique)
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Pour gérer votre entreprise, utilisez: " + ChatColor.WHITE + "/entreprise");
                player.sendMessage(ChatColor.GRAY + "Utilisez " + ChatColor.WHITE + "/entreprise menu " + ChatColor.GRAY + "pour ouvrir le menu.");
            } else {
                // Voir les terrains PRO de cette entreprise
                showCompanyPlots(player, company);
            }
        }
    }

    /**
     * Trouve l'entreprise correspondant à l'item cliqué
     */
    private EntrepriseManagerLogic.Entreprise findCompanyFromItem(ItemStack item, Player player) {
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        // Retirer le préfixe "💼 "
        String companyName = displayName.replace("💼 ", "").trim();

        List<EntrepriseManagerLogic.Entreprise> companies = entrepriseLogic.getEntreprisesGereesPar(player.getName());
        for (EntrepriseManagerLogic.Entreprise company : companies) {
            if (company.getNom().equals(companyName)) {
                return company;
            }
        }
        return null;
    }

    /**
     * SYSTÈME UNIFIÉ : Affiche les terrains PROFESSIONNEL de l'entreprise
     */
    private void showCompanyPlots(Player player, EntrepriseManagerLogic.Entreprise company) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        List<Plot> plots = townManager.getPlotsByCompanySiret(company.getSiret(), townName);

        // Séparer plots individuels et groupés
        List<Plot> individualPlots = new ArrayList<>();
        List<Plot> groupedPlots = new ArrayList<>();

        for (Plot plot : plots) {
            if (plot.isGrouped()) {
                groupedPlots.add(plot);
            } else {
                individualPlots.add(plot);
            }
        }

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GOLD + "  Terrains de " + ChatColor.WHITE + company.getNom());
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (individualPlots.isEmpty() && groupedPlots.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Cette entreprise ne possède aucun terrain PRO.");
        } else {
            // Afficher les plots individuels
            if (!individualPlots.isEmpty()) {
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "▼ Terrains individuels (" + individualPlots.size() + "):");
                for (Plot plot : individualPlots) {
                    String status = plot.isForSale() ? ChatColor.GREEN + "[EN VENTE]" :
                                   plot.isForRent() ? ChatColor.AQUA + "[EN LOCATION]" :
                                   ChatColor.WHITE + "[LIBRE]";

                    player.sendMessage("");
                    player.sendMessage(status + ChatColor.YELLOW + " Terrain " + ChatColor.WHITE +
                        "(" + (plot.getChunkX() * 16) + ", " + (plot.getChunkZ() * 16) + ")");
                    player.sendMessage(ChatColor.GRAY + "  • Type: " + ChatColor.WHITE + "PROFESSIONNEL");
                    player.sendMessage(ChatColor.GRAY + "  • Taxe: " + ChatColor.GOLD + String.format("%.2f€/jour", plot.getDailyTax()));

                    if (plot.getCompanyDebtAmount() > 0) {
                        player.sendMessage(ChatColor.GRAY + "  • Dette: " + ChatColor.RED +
                            String.format("%.2f€", plot.getCompanyDebtAmount()));
                        if (plot.getLastDebtWarningDate() != null) {
                            // ✅ FIX: Utiliser ChronoUnit.DAYS pour compter les jours calendaires
                            long daysPassed = ChronoUnit.DAYS.between(plot.getLastDebtWarningDate().toLocalDate(), LocalDateTime.now().toLocalDate());
                            int daysRemaining = (int) (7 - daysPassed);
                            player.sendMessage(ChatColor.GRAY + "  • Saisie dans: " + ChatColor.RED + daysRemaining + " jours");
                        }
                    }
                }
            }

            // Afficher les terrains groupés
            if (!groupedPlots.isEmpty()) {
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "▼ Groupes de terrains (" + groupedPlots.size() + "):");
                for (Plot plot : groupedPlots) {
                    String status = plot.isForSale() ? ChatColor.GREEN + "[EN VENTE]" :
                                   plot.isForRent() ? ChatColor.AQUA + "[EN LOCATION]" :
                                   ChatColor.WHITE + "[LIBRE]";

                    player.sendMessage("");
                    player.sendMessage(status + ChatColor.AQUA + " Groupe: " + ChatColor.WHITE + plot.getGroupName());
                    player.sendMessage(ChatColor.GRAY + "  • Chunks: " + ChatColor.WHITE + plot.getChunks().size());
                    player.sendMessage(ChatColor.GRAY + "  • Surface: " + ChatColor.WHITE + (plot.getChunks().size() * 256) + "m²");
                    player.sendMessage(ChatColor.GRAY + "  • Type: " + ChatColor.WHITE + "PROFESSIONNEL");

                    if (plot.getCompanyDebtAmount() > 0) {
                        player.sendMessage(ChatColor.GRAY + "  • Dette: " + ChatColor.RED +
                            String.format("%.2f€", plot.getCompanyDebtAmount()));
                        if (plot.getLastDebtWarningDate() != null) {
                            // ✅ FIX: Utiliser ChronoUnit.DAYS pour compter les jours calendaires
                            long daysPassed = ChronoUnit.DAYS.between(plot.getLastDebtWarningDate().toLocalDate(), LocalDateTime.now().toLocalDate());
                            int daysRemaining = (int) (7 - daysPassed);
                            player.sendMessage(ChatColor.GRAY + "  • Saisie dans: " + ChatColor.RED + daysRemaining + " jours");
                        }
                    }
                }
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GRAY + "Tapez " + ChatColor.WHITE + "/ville" + ChatColor.GRAY + " pour revenir au menu");
        player.sendMessage("");
    }

    /**
     * SYSTÈME UNIFIÉ : Paye les dettes de l'entreprise
     */
    private void payCompanyDebts(Player player, EntrepriseManagerLogic.Entreprise company) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        List<Plot> plots = townManager.getPlotsByCompanySiret(company.getSiret(), townName);

        double totalDebt = 0.0;
        int plotsWithDebt = 0;
        int groupsWithDebt = 0;

        // Calculer dette totale
        for (Plot plot : plots) {
            if (plot.getCompanyDebtAmount() > 0) {
                totalDebt += plot.getCompanyDebtAmount();
                if (plot.isGrouped()) {
                    groupsWithDebt++;
                } else {
                    plotsWithDebt++;
                }
            }
        }

        if (totalDebt == 0) {
            player.sendMessage(ChatColor.GREEN + "Cette entreprise n'a aucune dette !");
            return;
        }

        // Vérifier si l'entreprise a assez d'argent
        if (company.getSolde() < totalDebt) {
            player.sendMessage(ChatColor.RED + "Fonds insuffisants !");
            player.sendMessage(ChatColor.YELLOW + "Dette totale: " + ChatColor.RED + String.format("%.2f€", totalDebt));
            player.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", company.getSolde()));
            return;
        }

        // Payer toutes les dettes
        for (Plot plot : plots) {
            if (plot.getCompanyDebtAmount() > 0) {
                plot.resetDebt();
            }
        }

        // Prélever de l'entreprise
        company.setSolde(company.getSolde() - totalDebt);

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✓ Dettes payées avec succès !");
        player.sendMessage(ChatColor.YELLOW + "Montant payé: " + ChatColor.GOLD + String.format("%.2f€", totalDebt));

        if (groupsWithDebt > 0) {
            player.sendMessage(ChatColor.YELLOW + "Terrains libérés: " + ChatColor.WHITE +
                (plotsWithDebt + groupsWithDebt) +
                ChatColor.GRAY + " (" + plotsWithDebt + " plots + " + groupsWithDebt + " groupes)");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Terrains libérés: " + ChatColor.WHITE + plotsWithDebt);
        }

        player.sendMessage(ChatColor.YELLOW + "Nouveau solde: " + ChatColor.GOLD + String.format("%.2f€", company.getSolde()));
        player.sendMessage("");

        plugin.getLogger().info(String.format(
            "[MyCompaniesGUI] %s a payé %.2f€ de dettes pour l'entreprise %s (SIRET: %s) - %d plots + %d groupes",
            player.getName(), totalDebt, company.getNom(), company.getSiret(), plotsWithDebt, groupsWithDebt
        ));
    }
}
