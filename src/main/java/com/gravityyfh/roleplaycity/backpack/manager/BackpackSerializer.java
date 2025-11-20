package com.gravityyfh.roleplaycity.backpack.manager;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utilitaire pour sérialiser/désérialiser le contenu des backpacks en base64
 * ⚡ OPTIMISATION: Support de la compression GZIP pour réduire la taille des données
 */
public class BackpackSerializer {
    private static final Logger logger = Logger.getLogger("BackpackSerializer");

    // ⚡ OPTIMISATION: Activer/désactiver la compression
    private static boolean compressionEnabled = false;

    /**
     * Active ou désactive la compression GZIP
     *
     * @param enabled true pour activer, false pour désactiver
     */
    public static void setCompressionEnabled(boolean enabled) {
        compressionEnabled = enabled;
        logger.info("[Backpack] Compression " + (enabled ? "activée" : "désactivée"));
    }

    /**
     * Sérialise un tableau d'ItemStack en String base64
     * ⚡ OPTIMISATION: Avec compression GZIP optionnelle (-50% à -70% de taille)
     *
     * @param items Le tableau d'items à sérialiser
     * @return La chaîne base64 représentant les items, ou null en cas d'erreur
     */
    public static String serialize(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return "";
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Écrire la taille du tableau
            dataOutput.writeInt(items.length);

            // Écrire chaque item
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();

            byte[] serializedData = outputStream.toByteArray();

            // ⚡ OPTIMISATION: Compresser si activé
            if (compressionEnabled) {
                serializedData = compress(serializedData);
            }

            return Base64.getEncoder().encodeToString(serializedData);
        } catch (IOException e) {
            logger.severe("Erreur lors de la sérialisation du backpack: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Désérialise une String base64 en tableau d'ItemStack
     * ⚡ OPTIMISATION: Avec décompression GZIP automatique
     *
     * @param data La chaîne base64 à désérialiser
     * @param defaultSize La taille par défaut si la désérialisation échoue
     * @return Le tableau d'items, ou un tableau vide en cas d'erreur
     */
    public static ItemStack[] deserializeData(String data, int defaultSize) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[defaultSize];
        }

        try {
            byte[] decodedData = Base64.getDecoder().decode(data);

            // ⚡ OPTIMISATION: Détecter et décompresser si nécessaire
            if (isGzipCompressed(decodedData)) {
                decodedData = decompress(decodedData);
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedData);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            // Lire la taille du tableau
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            // Lire chaque item
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (IOException | ClassNotFoundException e) {
            logger.warning("Erreur lors de la désérialisation du backpack: " + e.getMessage());
            return new ItemStack[defaultSize];
        }
    }

    /**
     * ⚡ OPTIMISATION: Compresse les données avec GZIP
     */
    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
        gzipOutputStream.write(data);
        gzipOutputStream.close();
        return outputStream.toByteArray();
    }

    /**
     * ⚡ OPTIMISATION: Décompresse les données GZIP
     */
    private static byte[] decompress(byte[] data) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;
        while ((len = gzipInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, len);
        }

        gzipInputStream.close();
        outputStream.close();
        return outputStream.toByteArray();
    }

    /**
     * ⚡ OPTIMISATION: Vérifie si les données sont compressées en GZIP
     */
    private static boolean isGzipCompressed(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        // Magic number GZIP: 0x1f8b
        return (data[0] == (byte) 0x1f && data[1] == (byte) 0x8b);
    }

    /**
     * Vérifie si une chaîne base64 est valide
     *
     * @param data La chaîne à vérifier
     * @return true si la chaîne est valide, false sinon
     */
    public static boolean isValidBase64(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        try {
            Base64.getDecoder().decode(data);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
