# ⏰ HORAIRES DES PAIEMENTS ET NOTIFICATIONS

**Date**: 2025-11-06
**Statut**: Configuration Actuelle

---

## 🕐 COLLECTE DES TAXES DE VILLE

### ⏰ Horaire : **Toutes les heures pile**

**Exemples** :
- 14:00:00
- 15:00:00
- 16:00:00
- 17:00:00
- etc.

### 📍 Configuration

**Fichier** : `TownEconomyTask.java`
**Lignes** : 119-146

```java
private void startHourlyTaxCollection() {
    LocalDateTime now = LocalDateTime.now();

    // Calcule la prochaine heure pile (ex: si on est à 14:23, calcule 15:00)
    LocalDateTime nextFullHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);

    // Calcule le délai jusqu'à la prochaine heure
    long initialDelayTicks = java.time.Duration.between(now, nextFullHour).toSeconds() * 20L;

    // S'exécute toutes les heures (72000 ticks = 3600 secondes)
    long ticksParHeure = 20L * 60L * 60L; // 72000 ticks

    hourlyTaxTask.runTaskTimer(plugin, initialDelayTicks, ticksParHeure);
}
```

### 🔄 Synchronisation Automatique

Le système se **synchronise automatiquement** sur l'heure réelle :
- ✅ Si le serveur démarre à **14:23** → Première collecte à **15:00**
- ✅ Puis répétée toutes les heures : **16:00**, **17:00**, **18:00**...

---

## 💼 PAIEMENT DES ENTREPRISES

### ⏰ Horaire : **Identique aux taxes de ville**

**Les taxes des entreprises sont collectées EN MÊME TEMPS que les taxes de ville.**

Il n'y a **pas de collecte séparée** pour les entreprises.

### 📊 Pourquoi ?

Les terrains PRO (entreprises) font partie du système de taxation général :
- **Groupes PRO** → Taxés à chaque heure pile
- **Terrains individuels PRO** → Taxés à chaque heure pile

**C'est unifié** : Entreprises + Particuliers = même moment.

---

## 🔔 AFFICHAGE DES NOTIFICATIONS DE DETTES

### 🚨 1. Notification Immédiate (si joueur connecté)

**Horaire** : **Immédiatement après la collecte des taxes**

**Exemple** :
```
15:00:00 → Collecte des taxes
15:00:01 → Détection des fonds insuffisants
15:00:01 → 🔔 Bannière de dette affichée
```

### 📩 2. Notification Différée (si joueur déconnecté)

**Horaire** : **À la prochaine connexion du joueur + 10 secondes**

**Exemple** :
```
15:00:00 → Collecte des taxes (joueur offline)
15:00:01 → Dette enregistrée dans notifications.yml

[Plus tard]
18:30:00 → Joueur se connecte
18:30:10 → 🔔 Bannière de dette affichée (10s après connexion)
```

**Raison du délai de 10s** :
- Éviter la noyade dans les messages de connexion
- Laisser le temps au client de charger
- Garantir que le joueur voit bien la notification

### 🔄 3. Rafraîchissement Automatique

**Horaire** : **Toutes les heures**

**But** :
- Mettre à jour le temps restant avant saisie
- Détecter si dette < 24h (alerte imminente)
- Recalculer les montants

**Code** : `DebtNotificationService.java` ligne 158-165

```java
public void start() {
    Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
        for (UUID uuid : targets) {
            refresh(uuid, DebtUpdateReason.SCHEDULED_REFRESH);
        }
    }, HOURLY_REFRESH_TICKS, HOURLY_REFRESH_TICKS);
}
```

---

## 📅 CALENDRIER TYPE D'UNE JOURNÉE

```
00:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
01:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
02:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
03:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
04:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
05:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
06:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
07:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
08:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
09:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
10:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
11:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
12:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
13:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
14:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
15:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
16:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
17:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
18:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
19:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
20:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
21:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
22:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
23:00:00 → 💰 Collecte taxes + 🔔 Notifications dettes
```

**Total** : **24 collectes par jour** (une par heure)

---

## 🔍 AUTRES TÂCHES PLANIFIÉES

### 🏠 Vérification des locations expirées
**Fréquence** : Toutes les **5 minutes** (6000 ticks)
**Action** : Vérifie et met à jour les soldes de location

### 📬 Nettoyage des invitations
**Fréquence** : Toutes les **30 minutes** (36000 ticks)
**Action** : Supprime les invitations de ville expirées

---

## ⚙️ TECHNIQUE : Conversion Ticks

Pour référence :
- **20 ticks** = 1 seconde
- **1200 ticks** = 1 minute (60 secondes)
- **72000 ticks** = 1 heure (3600 secondes)
- **1728000 ticks** = 24 heures

---

## 🎯 RÉSUMÉ RAPIDE

| Événement | Fréquence | Horaire |
|-----------|-----------|---------|
| 💰 **Taxes Ville** | Toutes les heures | XX:00:00 |
| 💼 **Taxes Entreprises** | Toutes les heures | XX:00:00 (en même temps) |
| 🔔 **Dettes (online)** | Immédiat | Juste après la collecte |
| 🔔 **Dettes (offline)** | À la connexion | +10 secondes |
| 🔄 **Rafraîchissement** | Toutes les heures | XX:00:00 |
| 🏠 **Locations** | Toutes les 5 min | XX:X5:00 |
| 📬 **Invitations** | Toutes les 30 min | XX:30:00 |

---

## 💡 NOTES IMPORTANTES

### ✅ Avantages du système horaire

1. **Synchronisation parfaite** : Villes + Entreprises en même temps
2. **Prédictibilité** : Les joueurs savent quand les taxes tombent
3. **Performance** : Une seule passe au lieu de deux
4. **Simplicité** : Un seul calendrier à gérer

### ⚠️ Comportement au démarrage du serveur

Si le serveur démarre à **14:37** :
- ⏰ Première collecte planifiée : **15:00** (dans 23 minutes)
- ⏰ Deuxième collecte : **16:00**
- ⏰ Troisième collecte : **17:00**
- etc.

Le système **ne fait PAS de collecte immédiate** au démarrage, il attend la prochaine heure pile.

### 🔧 Modification des horaires

Pour changer la fréquence, modifier dans `TownEconomyTask.java` :

```java
// Actuellement : toutes les heures
long ticksParHeure = 20L * 60L * 60L; // 72000 ticks

// Exemple pour toutes les 2 heures :
long ticksParHeure = 20L * 60L * 60L * 2; // 144000 ticks

// Exemple pour toutes les 30 minutes :
long ticksParHeure = 20L * 60L * 30L; // 36000 ticks
```

---

**Document généré le** : 2025-11-06
**Version du plugin** : RoleplayCity 1.04.00
