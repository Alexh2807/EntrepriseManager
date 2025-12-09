package com.gravityyfh.roleplaycity.phone.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.gui.PhoneMainGUI;
import com.gravityyfh.roleplaycity.phone.model.PhoneAccount;
import com.gravityyfh.roleplaycity.phone.model.PlanType;
import com.gravityyfh.roleplaycity.phone.model.PhoneType;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande principale du systeme de telephone.
 * Usage:
 * - /phone : Ouvre le GUI (si telephone en main)
 * - /phone call <numero> : Appeler
 * - /phone sms <numero> <message> : Envoyer un SMS
 * - /phone contacts : Liste des contacts
 * - /phone contact add <nom> <numero> : Ajouter un contact
 * - /phone inbox : Messages recus
 * - /phone give <joueur> phone <type> [credits] : Donner un telephone (admin)
 * - /phone give <joueur> plan <type> : Donner un forfait (admin)
 * - /phone reload : Recharger la configuration (admin)
 */
public class PhoneCommand implements CommandExecutor, TabCompleter {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    public PhoneCommand(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Ouvrir le GUI si c'est un joueur
            if (sender instanceof Player player) {
                if (phoneManager.hasPhoneInHand(player)) {
                    new PhoneMainGUI(plugin).open(player);
                } else {
                    sendHelp(sender);
                }
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "call" -> {
                return handleCall(sender, args);
            }
            case "sms" -> {
                return handleSms(sender, args);
            }
            case "contacts" -> {
                return handleContacts(sender, args);
            }
            case "contact" -> {
                return handleContactSubCommand(sender, args);
            }
            case "inbox" -> {
                return handleInbox(sender, args);
            }
            case "hangup", "end" -> {
                return handleHangup(sender);
            }
            case "accept" -> {
                return handleAccept(sender);
            }
            case "reject", "decline" -> {
                return handleReject(sender);
            }
            case "give" -> {
                return handleGive(sender, args);
            }
            case "reload" -> {
                return handleReload(sender);
            }
            case "help", "?" -> {
                sendHelp(sender);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Commande inconnue: " + subCommand);
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleCall(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /phone call <numero>");
            return true;
        }

        String number = normalizePhoneNumber(args[1]);
        if (number == null) {
            sender.sendMessage(ChatColor.RED + "Format de numero invalide. Utilisez XXX-XXXX");
            return true;
        }

        // initiateCall gere les messages d'erreur
        phoneService.initiateCall(player, number);
        return true;
    }

    private boolean handleSms(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /phone sms <numero> <message>");
            return true;
        }

        String number = normalizePhoneNumber(args[1]);
        if (number == null) {
            sender.sendMessage(ChatColor.RED + "Format de numero invalide. Utilisez XXX-XXXX");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // sendSms gere les messages d'erreur
        phoneService.sendSms(player, number, message);
        return true;
    }

    private boolean handleContacts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }

        var contacts = phoneService.getContacts(player);
        if (contacts.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Votre repertoire est vide.");
            sender.sendMessage(ChatColor.GRAY + "Ajoutez un contact: /phone contact add <nom> <numero>");
        } else {
            sender.sendMessage(ChatColor.GOLD + "=== Vos contacts ===");
            for (var contact : contacts) {
                sender.sendMessage(ChatColor.WHITE + contact.getContactName() + ChatColor.GRAY + " - " + ChatColor.AQUA + contact.getContactNumber());
            }
        }
        return true;
    }

    private boolean handleContactSubCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /phone contact <add|remove> ...");
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("add")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /phone contact add <nom> <numero>");
                return true;
            }
            String name = args[2];
            String number = normalizePhoneNumber(args[3]);
            if (number == null) {
                sender.sendMessage(ChatColor.RED + "Format de numero invalide.");
                return true;
            }
            phoneService.addContact(player, number, name);
        } else if (action.equals("remove") || action.equals("del")) {
            sender.sendMessage(ChatColor.YELLOW + "Pour supprimer un contact, utilisez le menu /phone contacts");
        } else {
            sender.sendMessage(ChatColor.RED + "Action inconnue: " + action);
        }
        return true;
    }

    private boolean handleInbox(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }

        var messages = phoneService.getReceivedMessages(player);
        if (messages.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Aucun message recu.");
        } else {
            sender.sendMessage(ChatColor.GOLD + "=== Boite de reception ===");
            int count = 0;
            for (var msg : messages) {
                if (count >= 10) {
                    sender.sendMessage(ChatColor.GRAY + "... et " + (messages.size() - 10) + " autres messages");
                    break;
                }
                String read = msg.isRead() ? "" : ChatColor.RED + "[NEW] ";
                String displayName = phoneService.getContactDisplayName(player.getUniqueId(), msg.getSenderNumber());
                if (displayName == null) displayName = msg.getSenderNumber();
                sender.sendMessage(read + ChatColor.AQUA + displayName + ChatColor.GRAY + ": " + ChatColor.WHITE + msg.getPreview());
                count++;
            }
        }
        return true;
    }

    private boolean handleHangup(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }
        phoneService.hangUp(player);
        return true;
    }

    private boolean handleAccept(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }
        if (!phoneService.acceptCall(player)) {
            sender.sendMessage(ChatColor.RED + "Aucun appel entrant a accepter.");
        }
        return true;
    }

    private boolean handleReject(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }
        if (!phoneService.rejectCall(player)) {
            sender.sendMessage(ChatColor.RED + "Aucun appel entrant a rejeter.");
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("roleplaycity.phone.give")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        // /phone give <joueur> phone <type> [credits]
        // /phone give <joueur> plan <type>
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage:");
            sender.sendMessage(ChatColor.RED + "/phone give <joueur> phone <type> [credits]");
            sender.sendMessage(ChatColor.RED + "/phone give <joueur> plan <type>");
            sender.sendMessage(ChatColor.GRAY + "Types de telephones: " + String.join(", ", phoneManager.getPhoneTypes().keySet()));
            sender.sendMessage(ChatColor.GRAY + "Types de forfaits: " + String.join(", ", phoneManager.getPlanTypes().keySet()));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable: " + args[1]);
            return true;
        }

        String itemType = args[2].toLowerCase();
        String typeId = args[3].toLowerCase();

        if (itemType.equals("phone") || itemType.equals("telephone")) {
            PhoneType phoneType = phoneManager.getPhoneTypes().get(typeId);
            if (phoneType == null) {
                sender.sendMessage(ChatColor.RED + "Type de telephone inconnu: " + typeId);
                sender.sendMessage(ChatColor.GRAY + "Types disponibles: " + String.join(", ", phoneManager.getPhoneTypes().keySet()));
                return true;
            }

            int credits = 0;
            if (args.length >= 5) {
                try {
                    credits = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Nombre de credits invalide: " + args[4]);
                    return true;
                }
            }

            ItemStack phone = phoneManager.createPhoneItem(typeId, credits);
            if (phone != null) {
                target.getInventory().addItem(phone);
                sender.sendMessage(ChatColor.GREEN + "Telephone " + typeId + " donne a " + target.getName() + " avec " + credits + " credits.");
                target.sendMessage(ChatColor.GREEN + "Vous avez recu un telephone!");
            } else {
                sender.sendMessage(ChatColor.RED + "Erreur lors de la creation du telephone.");
            }

        } else if (itemType.equals("plan") || itemType.equals("forfait")) {
            PlanType planType = phoneManager.getPlanTypes().get(typeId);
            if (planType == null) {
                sender.sendMessage(ChatColor.RED + "Type de forfait inconnu: " + typeId);
                sender.sendMessage(ChatColor.GRAY + "Types disponibles: " + String.join(", ", phoneManager.getPlanTypes().keySet()));
                return true;
            }

            ItemStack plan = phoneManager.createPlanItem(typeId);
            if (plan != null) {
                target.getInventory().addItem(plan);
                sender.sendMessage(ChatColor.GREEN + "Forfait " + typeId + " (" + planType.getCredits() + " credits) donne a " + target.getName() + ".");
                target.sendMessage(ChatColor.GREEN + "Vous avez recu un forfait telephonique!");
            } else {
                sender.sendMessage(ChatColor.RED + "Erreur lors de la creation du forfait.");
            }

        } else {
            sender.sendMessage(ChatColor.RED + "Type d'item inconnu: " + itemType);
            sender.sendMessage(ChatColor.GRAY + "Utilisez 'phone' ou 'plan'");
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("roleplaycity.phone.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        phoneManager.loadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "Configuration du telephone rechargee.");
        return true;
    }

    /**
     * Normalise un numero de telephone au format XXX-XXXX.
     * Retourne null si le format est invalide.
     */
    private String normalizePhoneNumber(String number) {
        if (number == null || number.isEmpty()) {
            return null;
        }

        // Supprimer tout sauf les chiffres
        String cleaned = number.replaceAll("[^\\d]", "");

        // Doit avoir exactement 7 chiffres
        if (cleaned.length() != 7) {
            return null;
        }

        // Formater en XXX-XXXX
        return cleaned.substring(0, 3) + "-" + cleaned.substring(3);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Commandes Telephone ===");
        sender.sendMessage(ChatColor.YELLOW + "/phone" + ChatColor.GRAY + " - Ouvrir le menu (telephone en main)");
        sender.sendMessage(ChatColor.YELLOW + "/phone call <numero>" + ChatColor.GRAY + " - Appeler");
        sender.sendMessage(ChatColor.YELLOW + "/phone sms <numero> <msg>" + ChatColor.GRAY + " - Envoyer un SMS");
        sender.sendMessage(ChatColor.YELLOW + "/phone contacts" + ChatColor.GRAY + " - Voir vos contacts");
        sender.sendMessage(ChatColor.YELLOW + "/phone contact add <nom> <numero>" + ChatColor.GRAY + " - Ajouter un contact");
        sender.sendMessage(ChatColor.YELLOW + "/phone inbox" + ChatColor.GRAY + " - Voir vos messages");
        sender.sendMessage(ChatColor.YELLOW + "/phone hangup" + ChatColor.GRAY + " - Raccrocher");
        sender.sendMessage(ChatColor.YELLOW + "/phone accept" + ChatColor.GRAY + " - Accepter un appel");
        sender.sendMessage(ChatColor.YELLOW + "/phone reject" + ChatColor.GRAY + " - Rejeter un appel");
        if (sender.hasPermission("roleplaycity.phone.give")) {
            sender.sendMessage(ChatColor.RED + "/phone give <joueur> phone <type> [credits]" + ChatColor.GRAY + " - Donner un telephone");
            sender.sendMessage(ChatColor.RED + "/phone give <joueur> plan <type>" + ChatColor.GRAY + " - Donner un forfait");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("call", "sms", "contacts", "contact", "inbox", "hangup", "accept", "reject", "help"));
            if (sender.hasPermission("roleplaycity.phone.give")) {
                completions.add("give");
            }
            if (sender.hasPermission("roleplaycity.phone.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("roleplaycity.phone.give")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("contact")) {
                completions.addAll(Arrays.asList("add", "remove"));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("roleplaycity.phone.give")) {
                completions.addAll(Arrays.asList("phone", "plan"));
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("roleplaycity.phone.give")) {
                if (args[2].equalsIgnoreCase("phone")) {
                    completions.addAll(phoneManager.getPhoneTypes().keySet());
                } else if (args[2].equalsIgnoreCase("plan")) {
                    completions.addAll(phoneManager.getPlanTypes().keySet());
                }
            }
        }

        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}
