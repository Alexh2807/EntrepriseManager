package com.gravityyfh.roleplaycity.Listener;// Dans ton fichier ChatListener.java

import com.gravityyfh.roleplaycity.EntrepriseGUI;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.Shop.Shop;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {

    private final RoleplayCity plugin;
    private final EntrepriseGUI entrepriseGUI; // Assure-toi que tu as bien ce champ

    // Map pour suivre les joueurs en attente d'une saisie
    private final Map<UUID, PlayerInputContext> playersWaitingForInput = new HashMap<>();

    // Énumération pour les différents types de saisie
    private enum InputType {
        // Pour les entreprises
        DEPOSIT_AMOUNT,
        WITHDRAW_AMOUNT,
        RENAME_ENTREPRISE_NEW_NAME,
        // Pour les boutiques
        SHOP_CREATION_DETAILS, // Un seul type pour prix ET quantité
        SHOP_NEW_PRICE,
        SHOP_NEW_QUANTITY,
        // Pour les villes
        CREATE_TOWN_NAME,
        RENAME_TOWN_NAME
    }

    // Classe interne pour stocker le contexte de la demande
    private static class PlayerInputContext {
        final InputType inputType;
        final Object data; // Peut stocker un nom d'entreprise, un objet Shop, etc.
        final Object secondaryData; // Pour les contextes plus complexes (ex: Location)
        final Object tertiaryData; // Pour les contextes encore plus complexes (ex: ItemStack)


        PlayerInputContext(InputType inputType, Object data) {
            this(inputType, data, null, null);
        }

        PlayerInputContext(InputType inputType, Object data, Object secondaryData, Object tertiaryData) {
            this.inputType = inputType;
            this.data = data;
            this.secondaryData = secondaryData;
            this.tertiaryData = tertiaryData;
        }
    }

    // Ton constructeur
    public ChatListener(RoleplayCity plugin, EntrepriseGUI entrepriseGUI) {
        this.plugin = plugin;
        this.entrepriseGUI = entrepriseGUI;
    }

    // --- Les méthodes que tu appelles depuis le GUI ---

    public void attendreMontantDepot(Player p, String nomEnt) {
        playersWaitingForInput.put(p.getUniqueId(), new PlayerInputContext(InputType.DEPOSIT_AMOUNT, nomEnt));
        p.sendMessage(ChatColor.GOLD + "Entrez le montant à déposer. Tapez 'annuler' pour annuler.");
    }

    public void attendreMontantRetrait(Player p, String nomEnt) {
        playersWaitingForInput.put(p.getUniqueId(), new PlayerInputContext(InputType.WITHDRAW_AMOUNT, nomEnt));
        p.sendMessage(ChatColor.GOLD + "Entrez le montant à retirer. Tapez 'annuler' pour annuler.");
    }

    public void attendreNouveauNomEntreprise(Player p, String nomEnt) {
        playersWaitingForInput.put(p.getUniqueId(), new PlayerInputContext(InputType.RENAME_ENTREPRISE_NEW_NAME, nomEnt));
        p.sendMessage(ChatColor.GOLD + "Entrez le nouveau nom de l'entreprise. Tapez 'annuler' pour annuler.");
    }

    public void requestShopCreationDetails(Player p, EntrepriseManagerLogic.Entreprise e, Location loc, ItemStack item) {
        playersWaitingForInput.put(p.getUniqueId(), new PlayerInputContext(InputType.SHOP_CREATION_DETAILS, e, loc, item));
        p.sendMessage(ChatColor.GOLD + "Veuillez entrer la QUANTITÉ par vente, suivie du PRIX total pour cette quantité.");
        p.sendMessage(ChatColor.GRAY + "Exemple : " + ChatColor.YELLOW + "16 350.50" + ChatColor.GRAY + " (pour vendre 16 objets à 350.50€)");
        p.sendMessage(ChatColor.RED + "Utilisez un point '.' pour les décimales. Tapez 'annuler' pour annuler.");
    }

    public void requestNewPriceForShop(Player p, Shop shop) {
        playersWaitingForInput.put(p.getUniqueId(), new PlayerInputContext(InputType.SHOP_NEW_PRICE, shop));
        p.sendMessage(ChatColor.GOLD + "Entrez le nouveau prix pour la boutique. Tapez 'annuler' pour annuler.");
    }

    public void requestNewQuantityForShop(Player p, Shop shop) {
        playersWaitingForInput.put(p.getUniqueId(), new PlayerInputContext(InputType.SHOP_NEW_QUANTITY, shop));
        p.sendMessage(ChatColor.GOLD + "Entrez la nouvelle quantité par vente. Tapez 'annuler' pour annuler.");
    }

    // Méthode générique pour attendre une saisie avec callback
    public void waitForInput(UUID playerUUID, java.util.function.Consumer<String> callback) {
        // On utilise un objet Runnable qui contient le callback
        playersWaitingForInput.put(playerUUID, new PlayerInputContext(InputType.CREATE_TOWN_NAME, callback));
    }

    public boolean isPlayerWaitingForInput(UUID uuid) {
        return playersWaitingForInput.containsKey(uuid);
    }


    // --- LA MÉTHODE onPlayerChat ENTIÈREMENT RÉÉCRITE ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Si le joueur n'est pas en attente d'une saisie, on ne fait rien.
        if (!playersWaitingForInput.containsKey(playerUUID)) {
            return;
        }

        // Annule l'événement pour que le message n'apparaisse pas dans le chat public.
        event.setCancelled(true);

        String message = event.getMessage();
        PlayerInputContext context = playersWaitingForInput.remove(playerUUID);

        // Gère le cas où l'utilisateur veut annuler l'action.
        if (message.equalsIgnoreCase("annuler")) {
            player.sendMessage(ChatColor.YELLOW + "Action annulée.");
            // On le replace dans le menu précédent pour une meilleure expérience
            new BukkitRunnable() {
                @Override
                public void run() {
                    reopenPreviousMenu(player, context);
                }
            }.runTask(plugin);
            return;
        }

        // Exécute la logique de traitement sur le thread principal du serveur.
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    switch (context.inputType) {
                        case DEPOSIT_AMOUNT:
                            handleDepositAmount(player, context, message);
                            break;
                        case WITHDRAW_AMOUNT:
                            handleWithdrawAmount(player, context, message);
                            break;
                        case RENAME_ENTREPRISE_NEW_NAME:
                            handleRenameEnterprise(player, context, message);
                            break;
                        case SHOP_CREATION_DETAILS:
                            handleShopCreationDetails(player, context, message);
                            break;
                        case SHOP_NEW_PRICE:
                            handleShopNewPrice(player, context, message);
                            break;
                        case SHOP_NEW_QUANTITY:
                            handleShopNewQuantity(player, context, message);
                            break;
                        case CREATE_TOWN_NAME:
                        case RENAME_TOWN_NAME:
                            handleGenericCallback(player, context, message);
                            break;
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Une erreur est survenue lors du traitement de votre saisie. Veuillez réessayer.");
                    plugin.getLogger().severe("Erreur lors du traitement de la saisie chat pour " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTask(plugin);
    }

    // --- MÉTHODES DE TRAITEMENT PRIVÉES ---

    private void handleDepositAmount(Player player, PlayerInputContext context, String message) {
        String nomEntreprise = (String) context.data;
        try {
            double amount = Double.parseDouble(message);
            plugin.getEntrepriseManagerLogic().deposerArgent(player, nomEntreprise, amount);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Montant invalide. Veuillez entrer un nombre.");
        }
    }

    private void handleWithdrawAmount(Player player, PlayerInputContext context, String message) {
        String nomEntreprise = (String) context.data;
        try {
            double amount = Double.parseDouble(message);
            plugin.getEntrepriseManagerLogic().retirerArgent(player, nomEntreprise, amount);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Montant invalide. Veuillez entrer un nombre.");
        }
    }

    private void handleRenameEnterprise(Player player, PlayerInputContext context, String message) {
        String oldName = (String) context.data;
        // Le nouveau nom est le message entier, ce qui permet les espaces
        plugin.getEntrepriseManagerLogic().renameEntreprise(player, oldName, message);
    }

    private void handleShopCreationDetails(Player player, PlayerInputContext context, String message) {
        EntrepriseManagerLogic.Entreprise entreprise = (EntrepriseManagerLogic.Entreprise) context.data;
        Location location = (Location) context.secondaryData;
        ItemStack itemStack = (ItemStack) context.tertiaryData;

        String[] parts = message.split("\\s+");
        if (parts.length != 2) {
            player.sendMessage(ChatColor.RED + "Format invalide. Veuillez entrer la QUANTITÉ puis le PRIX, séparés par un espace.");
            requestShopCreationDetails(player, entreprise, location, itemStack); // Redemande la saisie
            return;
        }

        try {
            int quantity = Integer.parseInt(parts[0]);
            double price = Double.parseDouble(parts[1].replace(',', '.')); // Remplace virgule par point

            if (quantity <= 0 || price <= 0) {
                player.sendMessage(ChatColor.RED + "La quantité et le prix doivent être des nombres positifs.");
                requestShopCreationDetails(player, entreprise, location, itemStack);
                return;
            }

            plugin.getShopManager().finalizeShopCreation(player, entreprise, location, itemStack, quantity, price);

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Entrée invalide. Assurez-vous d'entrer des nombres corrects.");
            requestShopCreationDetails(player, entreprise, location, itemStack);
        }
    }

    private void handleShopNewPrice(Player player, PlayerInputContext context, String message) {
        Shop shop = (Shop) context.data;
        try {
            double newPrice = Double.parseDouble(message.replace(',', '.'));
            if (newPrice <= 0) {
                player.sendMessage(ChatColor.RED + "Le prix doit être un nombre positif.");
            } else {
                plugin.getShopManager().changeShopPrice(shop, newPrice);
                player.sendMessage(ChatColor.GREEN + "Le prix de la boutique a été mis à jour à " + String.format("%,.2f", newPrice) + "€.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Entrée invalide. Veuillez entrer un nombre (ex: 150.50).");
        }
        reopenPreviousMenu(player, context);
    }

    private void handleShopNewQuantity(Player player, PlayerInputContext context, String message) {
        Shop shop = (Shop) context.data;
        try {
            int newQuantity = Integer.parseInt(message);
            if (newQuantity <= 0) {
                player.sendMessage(ChatColor.RED + "La quantité doit être un nombre entier positif.");
            } else {
                plugin.getShopManager().changeShopQuantity(shop, newQuantity);
                player.sendMessage(ChatColor.GREEN + "La quantité par vente a été mise à jour à " + newQuantity + ".");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Entrée invalide. Veuillez entrer un nombre entier (ex: 16).");
        }
        reopenPreviousMenu(player, context);
    }

    private void handleGenericCallback(Player player, PlayerInputContext context, String message) {
        // Le callback est stocké dans context.data
        if (context.data instanceof java.util.function.Consumer) {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<String> callback = (java.util.function.Consumer<String>) context.data;
            callback.accept(message);
        }
    }

    // Méthode utilitaire pour ré-ouvrir le menu précédent
    private void reopenPreviousMenu(Player player, PlayerInputContext context) {
        if (context.inputType == InputType.SHOP_NEW_PRICE || context.inputType == InputType.SHOP_NEW_QUANTITY) {
            Shop shop = (Shop) context.data;
            if (shop != null) {
                plugin.getShopGUI().openManageShopMenu(player, shop);
            }
        }
        // Ajoute ici d'autres logiques pour ré-ouvrir d'autres menus si nécessaire
        // Par exemple, pour la gestion d'entreprise
        else if (context.inputType == InputType.DEPOSIT_AMOUNT || context.inputType == InputType.WITHDRAW_AMOUNT || context.inputType == InputType.RENAME_ENTREPRISE_NEW_NAME) {
            String nomEntreprise = (String) context.data;
            EntrepriseManagerLogic.Entreprise entreprise = plugin.getEntrepriseManagerLogic().getEntreprise(nomEntreprise);
            if (entreprise != null) {
                entrepriseGUI.openManageSpecificEntrepriseMenu(player, entreprise);
            }
        }
    }
}