package com.gravityyfh.roleplaycity.mdt.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
import com.gravityyfh.roleplaycity.mdt.gui.MDTSetupGUI;
import com.gravityyfh.roleplaycity.mdt.listener.MDTSetupListener;
import com.gravityyfh.roleplaycity.mdt.beds.BedDiagnosticManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MDTCommandHandler implements CommandExecutor, TabCompleter {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;
    private final MDTSetupGUI setupGUI;
    private final BedDiagnosticManager diagnosticManager;

    private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.RED + "MDT" + ChatColor.GRAY + "] " + ChatColor.WHITE;

    public MDTCommandHandler(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.setupGUI = new MDTSetupGUI();
        this.diagnosticManager = new BedDiagnosticManager(plugin, manager);

        MDTSetupListener setupListener = new MDTSetupListener(plugin, manager.getConfig());
        Bukkit.getPluginManager().registerEvents(setupListener, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "join" -> handleJoin(sender);
            case "leave" -> handleLeave(sender);
            case "team" -> handleTeam(sender, args);
            case "setup" -> handleSetup(sender);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            case "test" -> handleTest(sender, false);
            case "testlive" -> handleTest(sender, true);
            case "checkbeds" -> handleCheckBeds(sender);
            case "validatebeds" -> handleValidateBeds(sender);
            case "savebeds" -> handleSaveBeds(sender);
            case "testbeds" -> handleTestBeds(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleStart(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.admin")) return;
        if (sender instanceof Player player) {
            if (manager.startEvent(player)) {
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Event MDT Rush démarré!");
            }
        } else {
            sender.sendMessage(PREFIX + "Cette commande est pour les joueurs.");
        }
    }

    private void handleStop(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.admin")) return;
        if (!manager.hasActiveGame()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Aucune partie en cours.");
            return;
        }
        manager.forceEndGame("Arrêté par " + sender.getName());
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Partie arrêtée!");
    }

    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        if (!player.hasPermission("roleplaycity.mdt.play")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Permission refusée!");
            return;
        }
        // Le joueur rejoint le lobby - le GUI de sélection d'équipe s'ouvrira
        // automatiquement quand la phase JOINING se termine (via startTeamSelectionPhase)
        manager.joinGame(player);
    }

    private void handleLeave(CommandSender sender) {
        if (sender instanceof Player player) {
            manager.leaveGame(player);
        }
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /mdt team <red|blue>");
            return;
        }
        try {
            MDTTeam team = MDTTeam.valueOf(args[1].toUpperCase());
            if (manager.selectTeam(player, team)) {
                player.sendMessage(PREFIX + ChatColor.GREEN + "Tu as rejoint l'équipe " + team.getColoredName() + "!");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Équipe invalide (RED/BLUE).");
        }
    }

    private void handleSetup(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.setup")) return;
        if (sender instanceof Player player) {
            player.openInventory(setupGUI.getInventory());
            player.sendMessage(PREFIX + ChatColor.GREEN + "Menu de configuration ouvert !");
        }
    }

    private void handleInfo(CommandSender sender) {
        MDTGame game = manager.getCurrentGame();
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "=== MDT Rush Info ===");
        if (game == null) {
            sender.sendMessage(ChatColor.GRAY + "Aucune partie en cours.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "État: " + ChatColor.WHITE + game.getState().name());
            sender.sendMessage(ChatColor.YELLOW + "Joueurs: " + ChatColor.WHITE + game.getPlayerCount());
            int red = game.getTeamPlayerCount(MDTTeam.RED);
            int blue = game.getTeamPlayerCount(MDTTeam.BLUE);
            sender.sendMessage(ChatColor.RED + "RED: " + red + ChatColor.GRAY + " | " + ChatColor.BLUE + "BLUE: " + blue);
        }
        sender.sendMessage("");
    }

    private void handleTest(CommandSender sender, boolean liveMode) {
        if (!checkPermission(sender, "roleplaycity.mdt.setup")) return;
        if (sender instanceof Player player) {
            if (manager.startTestMode(player, !liveMode)) {
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Mode TEST " + (liveMode ? "LIVE" : "ARENA") + " activé!");
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.admin")) return;
        manager.getConfig().loadConfig();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Config rechargée.");
    }

    private boolean checkPermission(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Permission refusée!");
            return false;
        }
        return true;
    }

    private void handleCheckBeds(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.admin")) return;
        diagnosticManager.handleCheckBeds(sender);
    }

    private void handleValidateBeds(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.admin")) return;
        diagnosticManager.handleValidateBeds(sender);
    }

    private void handleSaveBeds(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.admin")) return;
        diagnosticManager.handleSaveBeds(sender);
    }

    private void handleTestBeds(CommandSender sender) {
        if (!checkPermission(sender, "roleplaycity.mdt.admin")) return;
        diagnosticManager.handleTestBeds(sender);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MDT Rush ===");
        sender.sendMessage(ChatColor.YELLOW + "/mdt join" + ChatColor.GRAY + " - Rejoindre");
        sender.sendMessage(ChatColor.YELLOW + "/mdt team <red|blue>" + ChatColor.GRAY + " - Choisir équipe");
        if (sender.hasPermission("roleplaycity.mdt.admin")) {
            sender.sendMessage(ChatColor.RED + "/mdt start | stop | setup | reload");
            sender.sendMessage(ChatColor.YELLOW + "/mdt checkbeds | validatebeds | savebeds | testbeds" + ChatColor.GRAY + " - Diagnostic lits");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> cmds = new ArrayList<>(Arrays.asList("join", "leave", "team", "info"));
            if (sender.hasPermission("roleplaycity.mdt.admin")) {
                cmds.addAll(Arrays.asList("start", "stop", "reload", "setup", "test", "testlive",
                    "checkbeds", "validatebeds", "savebeds", "testbeds"));
            }
            return cmds.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            return Arrays.asList("red", "blue");
        }
        return new ArrayList<>();
    }
}