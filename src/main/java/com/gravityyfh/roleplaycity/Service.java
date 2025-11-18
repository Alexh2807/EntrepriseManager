package com.gravityyfh.roleplaycity;

import java.util.List;

/**
 * Représente un service prédéfini dans la configuration.
 *
 * @param id ex: "Construction_Maison_Simple"
 */
public record Service(String id, String nomAffiche, List<String> description, double prix) {
}

