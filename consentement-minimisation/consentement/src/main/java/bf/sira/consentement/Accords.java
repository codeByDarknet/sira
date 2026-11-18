package bf.sira.consentement;

import bf.sira.commun.crypto.TempsConstant;
import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Sql;
import bf.sira.consentement.DemandeCitoyen.Decision;
import bf.sira.consentement.DemandeCitoyen.DemarcheVue;
import bf.sira.journal.Journal;
import bf.sira.journal.Resultat;
import bf.sira.journal.Trace;
import bf.sira.journal.TypeLigne;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * L'accord du citoyen (contrat §6, étape 2). Les trois canaux mènent ici : une demande se rattache
 * à un citoyen, il la lit, il décide. Une demande rattachée à quelqu'un d'autre n'existe pas pour
 * lui.
 */
@Component
public class Accords {

    private static final String COLONNES =
            "d.id, d.etat, d.canal, d.creee_le, d.expire_le, d.jeux,"
                    + " d.code_verification_empreinte IS NOT NULL AS code_exige,"
                    + " d.service, s.nom AS service_nom, s.organisme, d.demarche, m.libelle,"
                    + " m.finalite, m.duree_autorisation_jours";

    /** Verrou des essais de code court ; celui du journal est un autre nombre. */
    private static final long VERROU_ESSAIS = 0x5349524145535341L;

    private static final String SOURCE =
            "demande d JOIN membre s ON s.code = d.service JOIN demarche m ON m.code = d.demarche";

    private final JdbcClient sql;
    private final TransactionTemplate transactions;
    private final Pages pages;
    private final Vues vues;
    private final Codes codes;
    private final Journal journal;
    private final Echanges echanges;
    private final ReglagesAccord reglage;
    private final Clock horloge;

    Accords(
            JdbcClient sql,
            TransactionTemplate transactions,
            Pages pages,
            Vues vues,
            Codes codes,
            Journal journal,
            Echanges echanges,
            ReglagesAccord reglage,
            Clock horloge) {
        this.sql = sql;
        this.transactions = transactions;
        this.pages = pages;
        this.vues = vues;
        this.codes = codes;
        this.journal = journal;
        this.echanges = echanges;
        this.reglage = reglage;
        this.horloge = horloge;
    }

    /**
     * @param etat filtre facultatif ; {@code EN_ATTENTE_ACCORD} ne rend que les demandes auxquelles
     *     il est encore temps de répondre
     */
    public Page<DemandeCitoyen> lister(String citoyen, String etat, Pagination pagination) {
        return pages.lire(
                COLONNES,
                SOURCE
                        + " WHERE d.citoyen = :citoyen AND (CAST(:etat AS text) IS NULL OR (d.etat ="
                        + " :etat AND (d.etat <> 'EN_ATTENTE_ACCORD' OR d.expire_le > :maintenant)))",
                "d.creee_le DESC, d.id",
                Sql.parametres(
                        "citoyen",
                        citoyen,
                        "etat",
                        Sql.filtre(etat),
                        "maintenant",
                        Sql.date(horloge.instant())),
                pagination,
                (ligne, i) -> lire(ligne));
    }

    public DemandeCitoyen lire(String citoyen, UUID id) {
        return sql.sql(
                        "SELECT "
                                + COLONNES
                                + " FROM "
                                + SOURCE
                                + " WHERE d.id = ? AND d.citoyen = ?")
                .params(id, citoyen)
                .query((ligne, i) -> lire(ligne))
                .optional()
                .orElseThrow(Erreurs::demandeIntrouvable);
    }

    /**
     * Remet au citoyen qui vient de se connecter les demandes qui le désignent par son identifiant
     * unique, quel que soit le canal (sa désignation est son pseudonyme interne). Elles ne lui
     * appartenaient pas encore : aucune ligne citoyen n'est créée pour un identifiant tapé par un
     * tiers.
     */
    public void recevoir(String citoyen) {
        transactions.executeWithoutResult(transaction ->
                sql.sql(
                                "UPDATE demande d SET citoyen = d.designation, designation = NULL"
                                        + " FROM demarche m WHERE m.code = d.demarche AND"
                                        + " d.designation = ? AND d.citoyen IS NULL AND d.etat ="
                                        + " 'EN_ATTENTE_ACCORD' RETURNING d.id, d.service,"
                                        + " d.demarche, m.finalite, d.canal")
                        .param(citoyen)
                        .query((ligne, i) -> trace(citoyen, ligne))
                        .list()
                        .forEach(journal::ecrire));
    }

    /**
     * Rattache au citoyen la demande dont il a suivi le lien ou scanné le QR code. La demande
     * n'appartient à personne tant que personne ne s'y est rattaché ; ensuite, à lui seul. Une
     * demande qui désigne une autre personne ne se rattache pas.
     */
    public DemandeCitoyen rattacher(String citoyen, UUID id) {
        transactions.executeWithoutResult(transaction -> {
            Optional<Trace> rattachee =
                    sql.sql(
                                    "UPDATE demande d SET citoyen = ? FROM demarche m WHERE"
                                            + " m.code = d.demarche AND d.id = ? AND d.citoyen IS"
                                            + " NULL AND d.designation IS NULL AND d.etat ="
                                            + " 'EN_ATTENTE_ACCORD' AND d.expire_le > ? RETURNING"
                                            + " d.id, d.service, d.demarche, m.finalite, d.canal")
                            .params(citoyen, id, Sql.date(horloge.instant()))
                            .query((ligne, i) -> trace(citoyen, ligne))
                            .optional();
            rattachee.ifPresent(journal::ecrire);
        });
        return lire(citoyen, id);
    }

    private static Trace trace(String citoyen, ResultSet ligne) throws SQLException {
        return Trace.de(TypeLigne.DEMANDE, Resultat.EN_ATTENTE)
                .citoyen(citoyen)
                .service(ligne.getString("service"))
                .demarche(ligne.getString("demarche"), ligne.getString("finalite"))
                .canal(ligne.getString("canal"))
                .demande(ligne.getObject("id", UUID.class));
    }

    /**
     * Rattache la demande dont le citoyen a tapé le code court. Les codes erronés sont comptés par
     * citoyen et, contre les essais répartis sur plusieurs comptes, pour tous : au-delà d'une
     * limite, tout code est refusé jusqu'à ce que les essais vieillissent. Compter, chercher et
     * noter l'échec se font sous un même verrou : des essais simultanés ne passent pas ensemble.
     */
    public DemandeCitoyen rattacher(String citoyen, String codeCourt) {
        String empreinte = codes.empreinteCodeCourt(codeCourt);
        // L'échec est noté et validé avant que le refus ne parte.
        Recherche recherche = transactions.execute(transaction -> chercher(citoyen, empreinte));
        if (recherche.bloquee()) {
            throw Erreurs.tropDEssais();
        }
        return rattacher(
                citoyen,
                recherche.demande().orElseThrow(() -> Erreurs.codeInvalide(HttpStatus.NOT_FOUND)));
    }

    /**
     * La demande de ce code ; un code erroné est noté comme un essai manqué. Le code d'une demande
     * qui désigne une autre personne est, pour lui, un code erroné.
     */
    private Recherche chercher(String citoyen, String empreinte) {
        sql.sql("SELECT pg_advisory_xact_lock(?)")
                .param(VERROU_ESSAIS)
                .query((RowCallbackHandler) ligne -> {});
        Instant maintenant = horloge.instant();
        var debut = Sql.date(maintenant.minus(reglage.dureeAccord()));
        Manques manques =
                sql.sql(
                                "SELECT count(*) FILTER (WHERE citoyen = ?) AS siens, count(*) AS"
                                        + " tous FROM essai_rattachement WHERE quand > ?")
                        .params(citoyen, debut)
                        .query((ligne, i) ->
                                new Manques(ligne.getInt("siens"), ligne.getInt("tous")))
                        .single();
        if (manques.siens() >= reglage.essaisRattachementMax()
                || manques.tous() >= reglage.essaisRattachementGlobalMax()) {
            return new Recherche(true, Optional.empty());
        }
        Optional<UUID> demande =
                sql.sql(
                                "SELECT id FROM demande WHERE code_court_empreinte = ? AND etat ="
                                        + " 'EN_ATTENTE_ACCORD' AND expire_le > ? AND ((citoyen IS"
                                        + " NULL AND designation IS NULL) OR citoyen = ?)")
                        .params(empreinte, Sql.date(maintenant), citoyen)
                        .query(UUID.class)
                        .optional();
        if (demande.isEmpty()) {
            sql.sql("INSERT INTO essai_rattachement (citoyen, quand) VALUES (?, ?)")
                    .params(citoyen, Sql.date(maintenant))
                    .update();
        }
        return new Recherche(false, demande);
    }

    /**
     * Vérifie, avant d'accepter, le code que le citoyen a lu sur l'écran du service et tapé dans
     * son espace : il n'accepte ainsi que la demande faite devant lui, pas celle d'un tiers qui lui
     * aurait transmis un lien, un code court ou fait partir une notification. Les codes erronés
     * sont comptés sur la demande ; au-delà de la limite, elle ne peut plus être acceptée.
     */
    private void verifierCode(String citoyen, UUID id, String saisi) {
        Verification verification =
                transactions.execute(transaction -> {
                    Optional<Attendu> attendu =
                            sql.sql(
                                            "SELECT code_verification_empreinte,"
                                                    + " essais_verification, etat ="
                                                    + " 'EN_ATTENTE_ACCORD' AND expire_le > ? AS"
                                                    + " ouverte FROM demande WHERE id = ? AND"
                                                    + " citoyen = ? FOR UPDATE")
                                    .params(Sql.date(horloge.instant()), id, citoyen)
                                    .query((ligne, i) ->
                                            new Attendu(
                                                    ligne.getString("code_verification_empreinte"),
                                                    ligne.getInt("essais_verification"),
                                                    ligne.getBoolean("ouverte")))
                                    .optional();
                    if (attendu.isEmpty()) {
                        return Verification.INTROUVABLE;
                    }
                    // Sans code à vérifier, ou déjà décidée ou expirée : la décision dira pourquoi.
                    if (attendu.get().empreinte() == null || !attendu.get().ouverte()) {
                        return Verification.BONNE;
                    }
                    if (attendu.get().essais() >= reglage.essaisVerificationMax()) {
                        return Verification.EPUISEE;
                    }
                    if (saisi != null
                            && TempsConstant.egaux(
                                    codes.empreinteCodeVerification(saisi),
                                    attendu.get().empreinte())) {
                        return Verification.BONNE;
                    }
                    sql.sql(
                                    "UPDATE demande SET essais_verification ="
                                            + " essais_verification + 1 WHERE id = ?")
                            .param(id)
                            .update();
                    return Verification.MAUVAISE;
                });
        switch (verification) {
            case INTROUVABLE -> throw Erreurs.demandeIntrouvable();
            case EPUISEE -> throw Erreurs.tropDEssais();
            case MAUVAISE -> throw Erreurs.codeVerificationInvalide();
            case BONNE -> {}
        }
    }

    /**
     * Enregistre la décision du citoyen. Acceptée, la demande ouvre une autorisation et les
     * registres sont interrogés aussitôt, dans la limite du temps accordé. Refuser ne demande pas
     * de code.
     *
     * @param codeVerification le code tapé par le citoyen, exigé pour accepter quand la demande en
     *     porte un
     */
    public Decision decider(String citoyen, UUID id, boolean acceptee, String codeVerification) {
        if (acceptee) {
            verifierCode(citoyen, id, codeVerification);
        }
        String retour = transactions.execute(transaction -> trancher(citoyen, id, acceptee));
        if (acceptee) {
            echanges.essayerMaintenant(id);
        }
        String etat =
                sql.sql("SELECT etat FROM demande WHERE id = ?")
                        .param(id)
                        .query(String.class)
                        .single();
        return new Decision(etat, retour);
    }

    /**
     * @return l'adresse où renvoyer le navigateur, ou {@code null} hors canal REDIRECTION
     */
    private String trancher(String citoyen, UUID id, boolean acceptee) {
        Instant maintenant = horloge.instant();
        EnAttente demande =
                sql.sql(
                                "SELECT d.etat, d.expire_le, d.service, d.demarche, m.finalite,"
                                        + " m.duree_autorisation_jours, d.canal, d.jeux, d.retour_url FROM"
                                        + " demande d JOIN demarche m ON m.code = d.demarche WHERE d.id ="
                                        + " ? AND d.citoyen = ? FOR UPDATE OF d")
                        .params(id, citoyen)
                        .query((ligne, i) -> EnAttente.lire(ligne))
                        .optional()
                        .orElseThrow(Erreurs::demandeIntrouvable);
        if (!"EN_ATTENTE_ACCORD".equals(demande.etat())) {
            throw "EXPIREE".equals(demande.etat())
                    ? Erreurs.demandeExpiree()
                    : Erreurs.decisionDejaPrise();
        }
        if (!demande.expireLe().isAfter(maintenant)) {
            throw Erreurs.demandeExpiree();
        }
        // Une habilitation suspendue depuis la demande : l'accord du citoyen n'ouvrirait plus rien.
        if (acceptee
                && !echanges.jeuxHabilites(demande.service(), demande.demarche())
                        .containsAll(demande.jeux())) {
            throw Erreurs.habilitationAbsente();
        }
        // Le code de retour n'est remis qu'au navigateur qui décide ; seule son empreinte reste.
        String codeRetour = demande.canal() == Canal.REDIRECTION ? codes.codeRetour() : null;
        UUID autorisation = acceptee ? autoriser(citoyen, demande, maintenant) : null;
        sql.sql(
                        "UPDATE demande SET etat = ?, autorisation = ?, decidee_le = ?,"
                                + " code_court_empreinte = NULL, code_verification_empreinte ="
                                + " NULL, code_retour_empreinte = ? WHERE id = ?")
                .params(
                        acceptee ? "EN_COURS" : "REFUSEE",
                        autorisation,
                        Sql.date(maintenant),
                        codeRetour == null ? null : Codes.empreinte(codeRetour),
                        id)
                .update();
        journal.ecrire(
                Trace.de(
                                acceptee ? TypeLigne.ACCORD : TypeLigne.REFUS,
                                acceptee ? Resultat.REUSSI : Resultat.REFUSE)
                        .citoyen(citoyen)
                        .service(demande.service())
                        .demarche(demande.demarche(), demande.finalite())
                        .canal(demande.canal().name())
                        .demande(id)
                        .autorisation(autorisation));
        if (acceptee) {
            echanges.preparer(id);
        }
        return codeRetour == null
                ? null
                : UriComponentsBuilder.fromUriString(demande.retourUrl())
                        .queryParam("demande", id)
                        .queryParam("code_retour", codeRetour)
                        .build()
                        .toUriString();
    }

    /** Un seul accord en cours par service et par démarche : le nouveau remplace l'ancien. */
    private UUID autoriser(String citoyen, EnAttente demande, Instant maintenant) {
        sql.sql(
                        "UPDATE autorisation SET expire_le = ? WHERE citoyen = ? AND service = ? AND"
                                + " demarche = ? AND retiree_le IS NULL AND expire_le > ?")
                .params(
                        Sql.date(maintenant),
                        citoyen,
                        demande.service(),
                        demande.demarche(),
                        Sql.date(maintenant))
                .update();
        UUID autorisation = UUID.randomUUID();
        sql.sql(
                        "INSERT INTO autorisation (id, citoyen, service, demarche, jeux, canal,"
                                + " accordee_le, expire_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        autorisation,
                        citoyen,
                        demande.service(),
                        demande.demarche(),
                        Sql.tableau(demande.jeux()),
                        demande.canal().name(),
                        Sql.date(maintenant),
                        // Durée 0 : l'accord ne vaut que pour cette demande.
                        Sql.date(maintenant.plus(Duration.ofDays(demande.dureeJours()))))
                .update();
        return autorisation;
    }

    private DemandeCitoyen lire(ResultSet ligne) throws SQLException {
        return new DemandeCitoyen(
                ligne.getObject("id", UUID.class),
                ligne.getString("etat"),
                ligne.getString("canal"),
                Sql.instant(ligne, "creee_le"),
                Sql.instant(ligne, "expire_le"),
                ligne.getBoolean("code_exige"),
                Vues.service(ligne),
                new DemarcheVue(
                        ligne.getString("demarche"),
                        ligne.getString("libelle"),
                        ligne.getString("finalite"),
                        ligne.getInt("duree_autorisation_jours")),
                vues.jeux(Sql.liste(ligne, "jeux")));
    }

    private record Manques(int siens, int tous) {}

    private record Recherche(boolean bloquee, Optional<UUID> demande) {}

    private record Attendu(String empreinte, int essais, boolean ouverte) {}

    private enum Verification {
        BONNE,
        MAUVAISE,
        EPUISEE,
        INTROUVABLE
    }

    private record EnAttente(
            String etat,
            Instant expireLe,
            String service,
            String demarche,
            String finalite,
            int dureeJours,
            Canal canal,
            List<String> jeux,
            String retourUrl) {

        static EnAttente lire(ResultSet ligne) throws SQLException {
            return new EnAttente(
                    ligne.getString("etat"),
                    Sql.instant(ligne, "expire_le"),
                    ligne.getString("service"),
                    ligne.getString("demarche"),
                    ligne.getString("finalite"),
                    ligne.getInt("duree_autorisation_jours"),
                    Canal.valueOf(ligne.getString("canal")),
                    Sql.liste(ligne, "jeux"),
                    ligne.getString("retour_url"));
        }
    }
}
