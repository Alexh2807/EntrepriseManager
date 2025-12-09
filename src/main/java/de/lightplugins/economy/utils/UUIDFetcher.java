/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package de.lightplugins.economy.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UUIDFetcher {
    private static final String NAME_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Pattern NAME_PATTERN = Pattern.compile(",\\s*\"name\"\\s*:\\s*\"(.*?)\"");

    private UUIDFetcher() {
        throw new UnsupportedOperationException();
    }

    public String getName(UUID uuid) {
        return this.getName(uuid.toString());
    }

    private String getName(String uuid) {
        String output = this.callURL(NAME_URL + (uuid = uuid.replace("-", "")));
        Matcher m = NAME_PATTERN.matcher(output);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private String callURL(String urlStr) {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        InputStreamReader in = null;
        try {
            URLConnection conn = new URL(urlStr).openConnection();
            if (conn != null) {
                conn.setReadTimeout(60000);
            }
            if (conn != null && conn.getInputStream() != null) {
                in = new InputStreamReader(conn.getInputStream(), "UTF-8");
                br = new BufferedReader(in);
                String line = br.readLine();
                while (line != null) {
                    sb.append(line).append("\n");
                    line = br.readLine();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable line) {}
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable line) {}
            }
        }
        return sb.toString();
    }
}

