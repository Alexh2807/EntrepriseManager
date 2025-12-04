package com.gravityyfh.roleplaycity.phone.model;

/**
 * Represente une piste musicale configurable
 */
public class MusicTrack {

    private final String id;
    private final String name;
    private final String source;

    public MusicTrack(String id, String name, String source) {
        this.id = id;
        this.name = name;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }
}
