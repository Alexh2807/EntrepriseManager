# 🔒 Guide d'intégration du système de prison

## ✅ Fichiers créés

### Modèles de données (`police/data/`)
- ✅ `PrisonData.java` - Données d'un prisonnier
- ✅ `ImprisonedPlayerData.java` - Gestion de tous les prisonniers
- ✅ `PrisonSpawnPoint.java` - Points de spawn des prisons

### Manager (`police/manager/`)
- ✅ `PrisonManager.java` - Logique centrale du système

### Listeners (`police/listeners/`)
- ✅ `PrisonRestrictionListener.java` - Restrictions des prisonniers
- ✅ `PrisonBoundaryListener.java` - Confinement dans le COMMISSARIAT

### GUI (`police/gui/`)
- ✅ `TownPrisonManagementGUI.java` - Menu de gestion pour policiers
- ✅ `ImprisonmentWorkflowGUI.java` - Workflow d'emprisonnement (3 phases)

### Modifications
- ✅ `Plot.java` - Ajout prisonSpawn + méthodes
- ✅ `TownPlotManagementGUI.java` - Bouton "Définir spawn prison"
- ✅ `TownDataManager.java` - Sauvegarde/chargement prisonSpawn

---

## 🔧 ÉTAPE 1: Modifications dans RoleplayCity.java

### 1.1 Ajouter les imports

```java
// Imports pour le système de prison
import com.gravityyfh.roleplaycity.police.manager.PrisonManager;
import com.gravityyfh.roleplaycity.police.listeners.PrisonRestrictionListener;
import com.gravityyfh.roleplaycity.police.listeners.PrisonBoundaryListener;
import com.gravityyfh.roleplaycity.police.gui.TownPrisonManagementGUI;
import com.gravityyfh.roleplaycity.police.gui.ImprisonmentWorkflowGUI;
```

### 1.2 Ajouter les champs de classe

Ajouter ces variables d'instance dans la classe `RoleplayCity` :

```java
// Système de prison
private PrisonManager prisonManager;
private TownPrisonManagementGUI townPrisonManagementGUI;
private ImprisonmentWorkflowGUI imprisonmentWorkflowGUI;
```

### 1.3 Initialisation dans onEnable()

Ajouter après l'initialisation de `handcuffedPlayerData` et avant l'enregistrement des listeners :

```java
// Initialiser le système de prison
this.prisonManager = new PrisonManager(this, townManager, handcuffedPlayerData);
this.townPrisonManagementGUI = new TownPrisonManagementGUI(this, townManager, prisonManager);
this.imprisonmentWorkflowGUI = new ImprisonmentWorkflowGUI(this, townManager, prisonManager, handcuffedPlayerData);

// Enregistrer les listeners de prison
getServer().getPluginManager().registerEvents(new PrisonRestrictionListener(this, prisonManager.getImprisonedPlayerData()), this);
getServer().getPluginManager().registerEvents(new PrisonBoundaryListener(this, townManager, prisonManager.getImprisonedPlayerData()), this);

// Enregistrer les GUIs de prison
getServer().getPluginManager().registerEvents(townPrisonManagementGUI, this);
getServer().getPluginManager().registerEvents(imprisonmentWorkflowGUI, this);

// Démarrer le scheduler de vérification des expirations de prison
prisonManager.startExpirationChecker();

getLogger().info("Système de prison initialisé avec succès");
```

### 1.4 Nettoyage dans onDisable()

Ajouter avant le `saveAllData()` :

```java
// Arrêter le système de prison
if (prisonManager != null) {
    prisonManager.stopExpirationChecker();
    prisonManager.clear();
    getLogger().info("Système de prison arrêté");
}
```

### 1.5 Ajouter le getter

Ajouter cette méthode publique dans la classe :

```java
public PrisonManager getPrisonManager() {
    return prisonManager;
}
```

### 1.6 Intégration dans TownPoliceGUI

Dans le fichier `TownPoliceGUI.java`, ajouter un bouton "Emprisonner" dans la méthode `openPoliceMenu()` :

**Ligne à ajouter après le bouton "Amendes Contestées" (vers ligne 123) :**

```java
// Emprisonner un joueur menotté
ItemStack imprisonItem = new ItemStack(Material.IRON_BARS);
ItemMeta imprisonMeta = imprisonItem.getItemMeta();
imprisonMeta.setDisplayName(ChatColor.DARK_RED + "⛓️ Emprisonner");
List<String> imprisonLore = new ArrayList<>();
imprisonLore.add(ChatColor.GRAY + "Emprisonner un joueur");
imprisonLore.add(ChatColor.GRAY + "menotté sur le COMMISSARIAT");
imprisonLore.add("");
imprisonLore.add(ChatColor.YELLOW + "Cliquez pour commencer");
imprisonMeta.setLore(imprisonLore);
imprisonItem.setItemMeta(imprisonMeta);
inv.setItem(12, imprisonItem); // Slot 12

// Gestion des prisonniers
ItemStack manageItem = new ItemStack(Material.CHAIN);
ItemMeta manageMeta = manageItem.getItemMeta();
manageMeta.setDisplayName(ChatColor.GOLD + "Gérer les Prisonniers");
List<String> manageLore = new ArrayList<>();
manageLore.add(ChatColor.GRAY + "Voir et gérer les joueurs");
manageLore.add(ChatColor.GRAY + "actuellement emprisonnés");
manageLore.add("");
manageLore.add(ChatColor.YELLOW + "Cliquez pour accéder");
manageMeta.setLore(manageLore);
manageItem.setItemMeta(manageMeta);
inv.setItem(14, manageItem); // Slot 14
```

**Et dans la méthode `onInventoryClick()`, ajouter la gestion des clics :**

```java
// Après les autres conditions (vers ligne 155), ajouter :
else if (displayName.contains("Emprisonner")) {
    player.closeInventory();
    plugin.getImprisonmentWorkflowGUI().openPrisonerSelectionMenu(player);
}
else if (displayName.contains("Gérer les Prisonniers")) {
    player.closeInventory();
    plugin.getTownPrisonManagementGUI().openPrisonManagementMenu(player);
}
```

**Ajouter les getters dans RoleplayCity.java :**

```java
public TownPrisonManagementGUI getTownPrisonManagementGUI() {
    return townPrisonManagementGUI;
}

public ImprisonmentWorkflowGUI getImprisonmentWorkflowGUI() {
    return imprisonmentWorkflowGUI;
}
```

---

## ⚙️ ÉTAPE 2: Configuration dans config.yml

Ajouter cette section à la fin du fichier `config.yml` :

```yaml
# ============================================================
# SYSTÈME DE PRISON
# ============================================================
prison-system:
  # Activer le système de prison
  enabled: true

  # Durée maximale d'emprisonnement (en minutes)
  max-duration-minutes: 60

  # Commandes autorisées pendant l'emprisonnement
  allowed-commands:
    - "prisoninfo"
    - "appeal"
    - "help"

  # Diffuser une notification à tous les joueurs lors d'un emprisonnement
  notification-broadcast: true

  # Téléporter le joueur au spawn principal lors de la libération
  # Si false, le joueur reste dans le COMMISSARIAT
  teleport-on-release: true
```

---

## 🧪 ÉTAPE 3: Tests à effectuer

### 3.1 Test de base
1. Créer une ville avec un claim municipal de type COMMISSARIAT
2. En tant que maire/adjoint, définir le spawn prison :
   - Ouvrir le menu de gestion du plot COMMISSARIAT
   - Cliquer sur "🔒 Spawn Prison"
   - Vérifier le message de confirmation
3. Menotter un joueur sur le COMMISSARIAT
4. Ouvrir `/town police` → Cliquer "Emprisonner"
5. Sélectionner le joueur menotté
6. Choisir une durée (ex: 5 minutes)
7. Entrer une raison dans le chat
8. Vérifier :
   - ✅ Le joueur est téléporté au spawn prison
   - ✅ Ses menottes sont retirées
   - ✅ Une boss bar apparaît avec le temps restant
   - ✅ Un broadcast est envoyé à tous les joueurs
   - ✅ Le joueur ne peut pas sortir du COMMISSARIAT

### 3.2 Test des restrictions
Vérifier que le prisonnier NE PEUT PAS :
- ✅ Casser/placer des blocs
- ✅ Ouvrir des portes, coffres
- ✅ Utiliser ender pearl
- ✅ Attaquer d'autres joueurs
- ✅ Utiliser des commandes (sauf whitelist)
- ✅ Se téléporter hors du COMMISSARIAT
- ✅ Ouvrir son inventaire

Vérifier que le prisonnier PEUT :
- ✅ Se déplacer dans le COMMISSARIAT
- ✅ Parler dans le chat global
- ✅ Voir les autres joueurs

### 3.3 Test de gestion
1. Ouvrir `/town police` → "Gérer les Prisonniers"
2. Vérifier la liste des prisonniers
3. Cliquer sur un prisonnier → Tester :
   - ✅ Libération anticipée
   - ✅ Prolongation de peine (+5, +10 minutes)
4. Vérifier que la boss bar se met à jour

### 3.4 Test de déconnexion
1. Emprisonner un joueur pour 10 minutes
2. Le faire déconnecter
3. Attendre quelques minutes
4. Le faire reconnecter
5. Vérifier :
   - ✅ La boss bar réapparaît
   - ✅ Le temps restant a continué de décompter
   - ✅ Le joueur est toujours dans le COMMISSARIAT

### 3.5 Test de libération automatique
1. Emprisonner un joueur pour 1 minute
2. Attendre l'expiration du timer
3. Vérifier :
   - ✅ Le joueur est téléporté au spawn principal
   - ✅ Message de libération affiché
   - ✅ Boss bar disparue
   - ✅ Le joueur peut à nouveau interagir normalement

### 3.6 Test de persistance
1. Emprisonner un joueur
2. Redémarrer le serveur
3. Vérifier :
   - ✅ Le spawn prison est toujours défini
   - ✅ Note: les prisonniers sont libérés au restart (comportement actuel)

### 3.7 Test de suppression de claim
1. Emprisonner un joueur dans un COMMISSARIAT
2. Changer le type du plot ou le supprimer
3. Vérifier :
   - ✅ Le prisonnier est automatiquement libéré
   - ✅ Message de libération

---

## 📋 Checklist finale

- [ ] Tous les fichiers sont créés et compilent sans erreur
- [ ] RoleplayCity.java modifié avec tous les ajouts
- [ ] TownPoliceGUI.java modifié avec les boutons
- [ ] config.yml contient la section prison-system
- [ ] Les 7 tests ci-dessus passent avec succès
- [ ] Aucune erreur dans les logs du serveur
- [ ] La sauvegarde dans towns.yml fonctionne

---

## 🐛 Troubleshooting

### Le bouton "Spawn Prison" n'apparaît pas
→ Vérifier que le plot est bien de type MUNICIPAL avec sous-type COMMISSARIAT
→ Vérifier que le joueur est maire ou adjoint

### L'emprisonnement ne fonctionne pas
→ Vérifier que le joueur est bien menotté
→ Vérifier qu'il est sur un COMMISSARIAT de la ville du policier
→ Vérifier qu'un spawn prison est défini (voyant vert dans le menu)

### Le prisonnier peut sortir du COMMISSARIAT
→ Vérifier que PrisonBoundaryListener est bien enregistré
→ Vérifier dans les logs si des erreurs apparaissent

### La boss bar ne s'affiche pas
→ Vérifier que le scheduler est démarré (startExpirationChecker)
→ Vérifier qu'il n'y a pas d'autres plugins qui interfèrent avec les boss bars

---

## 📝 Notes importantes

1. **Timer en temps réel** : Le temps de prison continue même si le joueur est déconnecté
2. **Menottes automatiquement retirées** : Lors de l'emprisonnement, les menottes sont enlevées
3. **Un seul prisonnier par emprisonnement** : Le workflow est conçu pour un prisonnier à la fois
4. **Broadcast public** : Par défaut, tous les joueurs voient les emprisonnements (configurable)
5. **Durée maximale** : Configurable dans config.yml (défaut: 60 minutes)

---

## 🎯 Fonctionnalités implémentées

✅ Définition spawn prison par maire/adjoint
✅ Emprisonnement workflow 3 phases (joueur → durée → raison)
✅ Durées prédéfinies : 5, 10, 15, 20, 30, 45, 60 minutes
✅ Restrictions complètes des actions
✅ Confinement au COMMISSARIAT
✅ Boss bar temps restant
✅ Timer en temps réel (continue offline)
✅ Gestion des prisonniers (libération, prolongation, historique)
✅ Transfert entre COMMISSARIAT
✅ Libération automatique à expiration
✅ Téléportation au spawn à la fin
✅ Broadcast notifications
✅ Persistance dans towns.yml
✅ Libération auto si claim supprimé

---

## 🚀 Prochaines améliorations possibles

- Système de points de comportement
- Réduction de peine pour bon comportement
- Travaux d'intérêt général dans la prison
- Système de visite (amis/famille)
- Logs détaillés des emprisonnements
- Statistiques par joueur/ville
- Intégration avec système d'amendes (prison si amende impayée)
