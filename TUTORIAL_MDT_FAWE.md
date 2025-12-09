# Tutoriel Complet : Système de Restauration de Map MDT avec FAWE

## 🎯 Vue d'ensemble

Le nouveau système utilise **FastAsyncWorldEdit (FAWE)** pour sauvegarder et restaurer automatiquement la map MDT. Fini le système complexe de tracking de blocs - maintenant tout est géré par des schématiques !

## 📋 Prérequis

1. **FastAsyncWorldEdit (FAWE)** doit être installé sur le serveur
2. **Permissions nécessaires** : `mdt.admin` pour toutes les commandes
3. **Région MDT configurée** dans `/plugins/RoleplayCity/mdt.yml`

---

## 🚀 Étape 1 : Installation et Vérification

### 1.1 Vérifier que FAWE est installé
```bash
# Dans la console du serveur
/plugins
# Cherchez "FastAsyncWorldEdit" dans la liste
```

### 1.2 Vérifier l'état du système
```bash
# En jeu (avec permissions admin)
/mdtschematic info
```

**Réponse attendue :**
```
ℹ️ Informations système FAWE:
  • FAWE disponible: Oui
  • Schématique sauvegardée: Non
```

---

## 🔧 Étape 2 : Configuration de la Map MDT

### 2.1 Construire votre map MDT
1. Allez dans le monde où se déroule le MDT
2. Construisez votre arène :
   - Bases pour chaque équipe
   - Lits (un par équipe)
   - Générateurs (fer, or, émeraudes, diamants)
   - Ponts, obstacles, etc.
3. Placez des **coffres** pour l'équipement de départ
4. Ajoutez des **villagers marchands** si nécessaire

### 2.2 Définir la région MDT
```bash
# Obtenir les outils de sélection FAWE
/mdtschematic tools

# Sélectionner la région
# Faites clic gauche avec la hache pour définir le point 1
# Faites clic droit avec la pioche pour définir le point 2

# Vérifier votre sélection
/mdtschematic selection
```

**Réponse attendue :**
```
📐 Votre sélection actuelle:
Région world: (-50,64,-50) → (50,100,50) | Volume: 254,800 blocs
```

### 2.3 Configurer dans mdt.yml
Ouvrez `/plugins/RoleplayCity/mdt.yml` et configurez :

```yaml
mdt:
  world: "world"  # Nom du monde
  region:
    min: "-50,64,-50"  # Point min de votre sélection
    max: "50,100,50"   # Point max de votre sélection
  # Autres configurations...
  teams:
    red:
      bed: "-40,64,-40"
      spawn: "-30,70,-30"
    blue:
      bed: "40,64,40"
      spawn: "30,70,30"
```

---

## 💾 Étape 3 : Sauvegarde de la Map

### 3.1 Première sauvegarde (automatique)
Le système sauvegardera automatiquement la map lors de la première partie :
```bash
# Démarrer une partie MDT normale
/mdt start
```

**Logs console attendus :**
```
[MDT] Première partie détectée, sauvegarde automatique de la map...
[MDT] ✅ Map MDT sauvegardée automatiquement !
[MDT] Protection de la région activée pour la partie
```

### 3.2 Sauvegarde manuelle
```bash
# Sauvegarder la région MDT configurée
/mdtschematic save

# Sauvegarder une sélection personnalisée
/mdtschematic backup_2024
```

**Réponse attendue :**
```
⏳ Sauvegarde de la région MDT en cours...
✅ Région MDT sauvegardée avec succès !
Taille: 2.4 MB
```

---

## 🔄 Étape 4 : Test de Restauration

### 4.1 Lancer une partie pour tester
```bash
# Rejoindre la partie
/mdt join

# Jouer normalement (casser des blocs, construire, etc.)
# La map sera modifiée pendant la partie
```

### 4.2 Fin de partie et restauration automatique
```bash
# Finir la partie (normalement ou avec /mdt stop)
/mdt stop
```

**Logs console attendus :**
```
[MDT] Restauration de la map MDT avec FAWE...
[MDT] ✅ Schématique MDT restaurée avec succès !
```

### 4.3 Vérifier la restauration
1. Retournez à l'arène
2. **Vérifiez que tous les blocs sont revenus à leur état original**
3. Les lits doivent être intacts
4. Les coffres doivent contenir leur équipement d'origine
5. **Aucun bloc placé par les joueurs ne doit rester**

---

## 🛡️ Étape 5 : Gestion de la Protection

### 5.1 Protection automatique
La protection est **automatiquement activée** pendant les parties MDT :
- Les joueurs ne peuvent pas modifier la map en dehors des parties
- Les explosions sont bloquées en dehors des jeux
- Seuls les admins avec `mdt.bypass` peuvent modifier la zone

### 5.2 Gérer la protection manuellement
```bash
# Activer la protection
/mdtschematic protect on "Maintenance en cours"

# Désactiver la protection
/mdtschematic protect off

# Obtenir la permission de bypass
/mdtschematic bypass
```

---

## 📁 Étape 6 : Gestion des Schématiques

### 6.1 Lister les schématiques
```bash
/mdtschematic list
```

**Réponse attendue :**
```
📂 Schématiques disponibles (3):
  • latest (2.4 MB)
  • backup_2024 (2.4 MB)
  • backup_halloween (3.1 MB)
```

### 6.2 Restaurer une schématique spécifique
```bash
# Restaurer la sauvegarde automatique
/mdtschematic restore latest

# Restaurer une sauvegarde personnalisée
/mdtschematic restore backup_2024
```

---

## 🔍 Étape 7 : Dépannage

### 7.1 FAWE non disponible
**Problème :** `FAWE disponible: Non`

**Solution :**
```bash
# Arrêter le serveur
# Installer FastAsyncWorldEdit
# Redémarrer le serveur
# Vérifier avec /mdtschematic info
```

### 7.2 Aucune schématique sauvegardée
**Problème :** `Aucune sauvegarde de map trouvée !`

**Solution :**
```bash
# Assurez-vous que la région MDT est configurée
# Vérifiez mdt.yml > world et region
# Sauvegardez manuellement avec /mdtschematic save
```

### 7.3 La restauration échoue
**Problème :** `Échec de la restauration de la schématique`

**Solutions possibles :**
1. Vérifiez que FAWE fonctionne (`//version`)
2. Vérifiez l'espace disque disponible
3. Essayez de sauvegarder une nouvelle schématique
4. Redémarrez le serveur

### 7.4 La protection bloque tout
**Problème :** Impossible de modifier la map même pour les admins

**Solution :**
```bash
# Ajoutez-vous à la liste de bypass
/mdtschematic bypass

# Ou temporairement désactivez la protection
/mdtschematic protect off
```

---

## 📊 Étape 8 : Monitoring et Maintenance

### 8.1 Vérifier l'état du système régulièrement
```bash
# Chaque jour ou avant les événements
/mdtschematic info
```

### 8.2 Sauvegardes régulières
```bash
# Avant les grosses mises à jour
/mdtschematic save backup_major_update
```

### 8.3 Nettoyer les anciennes schématiques
Les fichiers sont stockés dans : `/plugins/RoleplayCity/mdt-schematics/`

Supprimez manuellement les vieux fichiers .schem pour économiser de l'espace.

---

## ⚡ Performance et Limites

### Limites recommandées :
- **Volume maximum** : 5 millions de blocs pour les sauvegardes manuelles
- **Taille des fichiers** : ~10MB maximum par schématique
- **Temps de sauvegarde** : Quelques secondes pour une map standard

### Optimisations :
- Le système utilise le **mode rapide** de FAWE
- Les sauvegardes se font **asynchrones** (pas de lag)
- Le **cache de permissions** évite les vérifications répétées

---

## 🎉 Félicitations !

Votre système de restauration de map MDT avec FAWE est maintenant :
- ✅ **Installé et configuré**
- ✅ **Testé et fonctionnel**
- ✅ **Automatique** (sauvegarde lors de la première partie)
- ✅ **Fiable** (restauration complète après chaque partie)
- ✅ **Protégé** (anti-modifications en dehors des parties)

Le système est maintenant prêt pour une utilisation en production !

---

## 📞 Support

En cas de problème :
1. Vérifiez les logs du serveur (`[MDT]` et `[FAWE]`)
2. Utilisez `/mdtschematic info` pour diagnostiquer
3. Vérifiez que FAWE est à jour
4. Redémarrez le serveur si nécessaire

**Bonne gestion de votre MDT avec FAWE ! 🚀**