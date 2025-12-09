package com.gravityyfh.roleplaycity.customitems.model;

import java.util.Map;

public class ItemAction {
    private final ActionType type;
    private final Map<String, Object> parameters;

    public ItemAction(ActionType type, Map<String, Object> parameters) {
        this.type = type;
        this.parameters = parameters;
    }

    public ActionType getType() {
        return type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    public String getString(String key, String def) {
        Object val = parameters.get(key);
        return val != null ? val.toString() : def;
    }
    
    public int getInt(String key, int def) {
        Object val = parameters.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }
    
    public double getDouble(String key, double def) {
        Object val = parameters.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object val = parameters.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return def;
    }
}
