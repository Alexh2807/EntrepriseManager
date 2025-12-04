/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  com.comphenix.protocol.PacketType
 *  com.comphenix.protocol.PacketType$Play$Client
 *  com.comphenix.protocol.PacketType$Play$Server
 *  com.comphenix.protocol.ProtocolLibrary
 *  com.comphenix.protocol.events.PacketAdapter
 *  com.comphenix.protocol.events.PacketContainer
 *  com.comphenix.protocol.events.PacketEvent
 *  com.comphenix.protocol.events.PacketListener
 *  com.comphenix.protocol.wrappers.BlockPosition
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package de.lightplugins.economy.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.BlockPosition;
import de.lightplugins.economy.master.Main;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SignPackets {
    private final Main plugin;
    private final Map<Player, Menu> inputs;

    public SignPackets(Main plugin) {
        this.plugin = plugin;
        this.inputs = new HashMap<Player, Menu>();
        this.listen();
    }

    public Menu newMenu(List<String> text) {
        return new Menu(text);
    }

    private void listen() {
        ProtocolLibrary.getProtocolManager().addPacketListener((PacketListener)new PacketAdapter(this.plugin.asPlugin(), new PacketType[]{PacketType.Play.Client.UPDATE_SIGN}){

            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                Menu menu = SignPackets.this.inputs.remove(player);
                if (menu == null) {
                    return;
                }
                event.setCancelled(true);
                boolean success = menu.response.test(player, (String[])event.getPacket().getStringArrays().read(0));
                if (!success && menu.reopenIfFail && !menu.forceClose) {
                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> menu.open(player), 2L);
                }
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    if (player.isOnline()) {
                        player.sendBlockChange(menu.location, menu.location.getBlock().getBlockData());
                    }
                }, 2L);
            }
        });
    }

    public final class Menu {
        private final List<String> text;
        private BiPredicate<Player, String[]> response;
        private boolean reopenIfFail;
        private Location location;
        private boolean forceClose;

        Menu(List<String> text) {
            this.text = text;
        }

        public Menu reopenIfFail(boolean value) {
            this.reopenIfFail = value;
            return this;
        }

        public Menu response(BiPredicate<Player, String[]> response) {
            this.response = response;
            return this;
        }

        public void open(Player player) {
            Objects.requireNonNull(player, "player");
            if (!player.isOnline()) {
                return;
            }
            this.location = player.getLocation();
            this.location.setY((double)(this.location.getBlockY() - 4));
            player.sendBlockChange(this.location, Material.OAK_SIGN.createBlockData());
            String[] signText = new String[4];
            for (int i = 0; i < 3; ++i) {
                signText[i] = this.color(this.text.get(i));
            }
            player.sendSignChange(this.location, signText);
            PacketContainer openSign = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
            BlockPosition position = new BlockPosition(this.location.getBlockX(), this.location.getBlockY(), this.location.getBlockZ());
            openSign.getBlockPositionModifier().write(0, position);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, openSign);
            SignPackets.this.inputs.put(player, this);
        }

        public void close(Player player, boolean force) {
            this.forceClose = force;
            if (player.isOnline()) {
                player.closeInventory();
            }
        }

        public void close(Player player) {
            this.close(player, false);
        }

        private String color(String input) {
            return Main.colorTranslation.hexTranslation(input);
        }
    }
}

