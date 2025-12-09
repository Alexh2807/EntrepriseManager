package com.gravityyfh.roleplaycity.contract.model;

/**
 * Type de contrat selon la cible
 */
public enum ContractType {
    /**
     * Business to Consumer - Entreprise vers Particulier
     */
    B2C,

    /**
     * Business to Business - Entreprise vers Entreprise
     */
    B2B;

    /**
     * @return true si le contrat est de type B2B
     */
    public boolean isB2B() {
        return this == B2B;
    }

    /**
     * @return true si le contrat est de type B2C
     */
    public boolean isB2C() {
        return this == B2C;
    }
}
