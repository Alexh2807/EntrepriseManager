package com.gravityyfh.roleplaycity.town.gui.scoreboard;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.manager.MailboxManager;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;

import static com.gravityyfh.roleplaycity.town.gui.scoreboard.ScoreboardTheme.*;

/**
 * Fournisseur de contenu pour les scoreboards
 * Génère un affichage optimisé et professionnel selon le type de terrain
 */
public class ScoreboardContentProvider {

    private final RoleplayCity plugin;

    public ScoreboardContentProvider(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    /**
     * Crée un scoreboard pour un terrain donné
     * @param town La ville du terrain
     * @param plot Le terrain
     * @return Le scoreboard configuré
     */
    public Scoreboard createPlotScoreboard(Town town, Plot plot) {
        String title = formatTitle(town.getName());
        ScoreboardBuilder builder = new ScoreboardBuilder(title);

        // Ligne vide en haut
        builder.addEmptyLine();

        // Informations de base du terrain
        addBasicPlotInfo(builder, plot);

        // Contenu spécifique selon le type de terrain
        PlotType type = plot.getType();

        if (type == PlotType.PUBLIC) {
            addPublicPlotContent(builder);
        } else if (type == PlotType.MUNICIPAL) {
            addMunicipalPlotContent(builder, plot);
        } else if (type == PlotType.PROFESSIONNEL) {
            addProfessionalPlotContent(builder, plot);
        } else if (type == PlotType.PARTICULIER) {
            addResidentialPlotContent(builder, plot);
        }

        // Ligne vide en bas
        builder.addEmptyLine();

        return builder.build();
    }

    /**
     * Ajoute les informations de base du terrain
     */
    private void addBasicPlotInfo(ScoreboardBuilder builder, Plot plot) {
        PlotType type = plot.getType();

        // Ligne 1: Numéro + Surface (si applicable)
        if (type != PlotType.PUBLIC) {
            StringBuilder line1 = new StringBuilder();
            line1.append(ICON_LOCATION).append(" ").append(INFO_COLOR);

            // Numéro de terrain
            if (plot.getPlotNumber() != null) {
                line1.append(plot.getPlotNumber());
            } else {
                line1.append("(").append(plot.getChunkX()).append(", ").append(plot.getChunkZ()).append(")");
            }

            // Surface (sauf MUNICIPAL et PUBLIC)
            if (type != PlotType.MUNICIPAL) {
                int surface = plot.isGrouped() ? (plot.getChunks().size() * 256) : 256;
                line1.append(SECONDARY_COLOR).append(SEPARATOR);
                line1.append(INFO_COLOR).append(formatSurface(surface));
            }

            builder.addLine(line1.toString());
        }

        // Ligne 2: Type de terrain avec icône
        String typeIcon = getPlotTypeIcon(type);
        String typeName = plot.getType().getDisplayName();
        builder.addLine(withIcon(typeIcon, TYPE_COLOR, typeName));

        // Sous-type municipal si applicable
        if (plot.isMunicipal() && plot.getMunicipalSubType() != null
            && plot.getMunicipalSubType() != MunicipalSubType.NONE) {
            builder.addLine(indent(LABEL_COLOR + plot.getMunicipalSubType().getDisplayName()));
        }
    }

    /**
     * Contenu pour un terrain PUBLIC
     */
    private void addPublicPlotContent(ScoreboardBuilder builder) {
        builder.addEmptyLine();
        builder.addLine(indent(SECONDARY_COLOR + "Accessible à tous"));
    }

    /**
     * Contenu pour un terrain MUNICIPAL
     */
    private void addMunicipalPlotContent(ScoreboardBuilder builder, Plot plot) {
        // Le sous-type est déjà affiché dans addBasicPlotInfo
        // BOÎTE AUX LETTRES
        addMailboxStatus(builder, plot);
    }

    /**
     * Contenu pour un terrain PROFESSIONNEL
     */
    private void addProfessionalPlotContent(ScoreboardBuilder builder, Plot plot) {
        boolean hasOwner = plot.getOwnerUuid() != null;
        boolean hasRenter = plot.getRenterUuid() != null;
        boolean isForSale = plot.isForSale();
        boolean isForRent = plot.isForRent();

        // PROPRIÉTAIRE
        if (hasOwner) {
            builder.addEmptyLine();
            builder.addLine(ICON_OWNER + " " + LABEL_COLOR + "Proprio");

            if (plot.getCompanySiret() != null) {
                // Afficher le nom de l'entreprise uniquement
                Entreprise company = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(plot.getCompanySiret());

                if (company != null) {
                    String companyName = truncate(company.getNom(), 16);
                    builder.addLine(indent(COMPANY_COLOR + companyName));
                } else {
                    // Fallback
                    String companyName = truncate(plot.getCompanyName(), 16);
                    builder.addLine(indent(COMPANY_COLOR + companyName));
                }
            } else {
                // Pas d'entreprise (cas rare pour PROFESSIONNEL)
                String ownerName = truncate(plot.getOwnerName(), 16);
                builder.addLine(indent(VALUE_COLOR + ownerName));
            }
        }

        // LOCATAIRE
        if (hasRenter) {
            builder.addEmptyLine();
            builder.addLine(ICON_RENTER + " " + LABEL_COLOR + "Locataire");

            if (plot.getRenterCompanySiret() != null) {
                // Afficher le nom de l'entreprise locataire
                Entreprise renterCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(plot.getRenterCompanySiret());

                if (renterCompany != null) {
                    String renterName = truncate(renterCompany.getNom(), 16);
                    builder.addLine(indent(RENTER_COLOR + renterName));
                } else {
                    // Fallback: nom du joueur
                    String renterName = truncate(Bukkit.getOfflinePlayer(plot.getRenterUuid()).getName(), 16);
                    builder.addLine(indent(RENTER_COLOR + renterName));
                }
            } else {
                // Pas d'entreprise: afficher le nom du joueur
                String renterName = truncate(Bukkit.getOfflinePlayer(plot.getRenterUuid()).getName(), 16);
                builder.addLine(indent(RENTER_COLOR + renterName));
            }

            // Durée et prix sur la même ligne (avec heures et minutes)
            Plot.RentTimeRemaining timeRemaining = plot.getRentTimeRemaining();
            double pricePerDay = plot.getRentPricePerDay();

            String timeDisplay = timeRemaining != null
                ? formatRentTime(timeRemaining.days(), timeRemaining.hours(), timeRemaining.minutes())
                : formatDays(plot.getRentDaysRemaining());

            builder.addLine(indent(ICON_TIME + " " + VALUE_COLOR + timeDisplay +
                SECONDARY_COLOR + SEPARATOR + RENT_COLOR + formatPricePerDay(pricePerDay)));
        }

        // VENTE
        if (isForSale && !hasRenter) {
            builder.addEmptyLine();
            builder.addLine(ICON_SALE + " " + PRICE_COLOR + ChatColor.BOLD + "À VENDRE");
            builder.addLine(indent(LABEL_COLOR + "Prix: " + PRICE_COLOR + formatPrice(plot.getSalePrice())));
        }

        // LOCATION
        if (isForRent && !hasRenter) {
            builder.addEmptyLine();
            builder.addLine(ICON_RENT + " " + RENT_COLOR + ChatColor.BOLD + "EN LOCATION");
            builder.addLine(indent(LABEL_COLOR + "Prix: " + RENT_COLOR + formatPricePerDay(plot.getRentPricePerDay())));
        }

        // BOÎTE AUX LETTRES
        addMailboxStatus(builder, plot);
    }

    /**
     * Contenu pour un terrain PARTICULIER
     */
    private void addResidentialPlotContent(ScoreboardBuilder builder, Plot plot) {
        boolean hasOwner = plot.getOwnerUuid() != null;
        boolean hasRenter = plot.getRenterUuid() != null;
        boolean isForSale = plot.isForSale();
        boolean isForRent = plot.isForRent();

        // PROPRIÉTAIRE
        if (hasOwner) {
            builder.addEmptyLine();
            builder.addLine(ICON_OWNER + " " + LABEL_COLOR + "Proprio");

            String ownerName = truncate(plot.getOwnerName(), 16);
            builder.addLine(indent(OWNER_COLOR + ownerName));
        }

        // LOCATAIRE
        if (hasRenter) {
            builder.addEmptyLine();
            builder.addLine(ICON_RENTER + " " + LABEL_COLOR + "Locataire");

            String renterName = truncate(Bukkit.getOfflinePlayer(plot.getRenterUuid()).getName(), 16);
            builder.addLine(indent(RENTER_COLOR + renterName));

            // Durée et prix (avec heures et minutes)
            Plot.RentTimeRemaining timeRemaining = plot.getRentTimeRemaining();
            double pricePerDay = plot.getRentPricePerDay();

            String timeDisplay = timeRemaining != null
                ? formatRentTime(timeRemaining.days(), timeRemaining.hours(), timeRemaining.minutes())
                : formatDays(plot.getRentDaysRemaining());

            builder.addLine(indent(ICON_TIME + " " + VALUE_COLOR + timeDisplay +
                SECONDARY_COLOR + SEPARATOR + RENT_COLOR + formatPricePerDay(pricePerDay)));
        }

        // VENTE
        if (isForSale && !hasRenter) {
            builder.addEmptyLine();
            builder.addLine(ICON_SALE + " " + PRICE_COLOR + ChatColor.BOLD + "À VENDRE");
            builder.addLine(indent(LABEL_COLOR + "Prix: " + PRICE_COLOR + formatPrice(plot.getSalePrice())));
        }

        // LOCATION
        if (isForRent && !hasRenter) {
            builder.addEmptyLine();
            builder.addLine(ICON_RENT + " " + RENT_COLOR + ChatColor.BOLD + "EN LOCATION");
            builder.addLine(indent(LABEL_COLOR + "Prix: " + RENT_COLOR + formatPricePerDay(plot.getRentPricePerDay())));
        }

        // BOÎTE AUX LETTRES
        addMailboxStatus(builder, plot);
    }

    /**
     * Retourne l'icône appropriée pour un type de terrain
     */
    private String getPlotTypeIcon(PlotType type) {
        return switch (type) {
            case PROFESSIONNEL -> ICON_PROFESSIONAL;
            case PARTICULIER -> ICON_RESIDENTIAL;
            case MUNICIPAL -> ICON_MUNICIPAL;
            case PUBLIC -> ICON_PUBLIC;
        };
    }

    /**
     * Ajoute le statut de la boîte aux lettres au scoreboard
     * Affiche "Boîte aux lettres : ✓" ou "Boîte aux lettres : ✗"
     */
    private void addMailboxStatus(ScoreboardBuilder builder, Plot plot) {
        MailboxManager mailboxManager = plugin.getMailboxManager();
        if (mailboxManager == null) return;

        boolean hasMailbox = mailboxManager.hasMailbox(plot);

        builder.addEmptyLine();
        if (hasMailbox) {
            builder.addLine(ICON_MAILBOX + " " + LABEL_COLOR + "Boîte aux lettres: " + PRICE_COLOR + ICON_CHECK);
        } else {
            builder.addLine(ICON_MAILBOX + " " + LABEL_COLOR + "Boîte aux lettres: " + ALERT_COLOR + ICON_CROSS);
        }
    }
}
