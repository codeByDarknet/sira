package bf.sira.passerelle.demandes;

import bf.sira.commun.crypto.TempsConstant;
import bf.sira.commun.donnees.Sql;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.CertificatRefuse;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.pki.Pki;
import bf.sira.commun.pki.UsageCle;
import bf.sira.consentement.Canal;
import bf.sira.consentement.Citoyens;
import bf.sira.consentement.Codes;
import bf.sira.consentement.Erreurs;
import bf.sira.consentement.ReglagesAccord;
import bf.sira.journal.Journal;
import bf.sira.journal.Resultat;
import bf.sira.journal.Trace;
import bf.sira.journal.TypeLigne;
import bf.sira.passerelle.collecte.Collecte;
import bf.sira.passerelle.config.Proprietes;
import bf.sira.passerelle.referentiel.Demarche;
import bf.sira.passerelle.referentiel.Demarches;
import bf.sira.passerelle.referentiel.Habilitations;
import bf.sira.passerelle.referentiel.Jeux;
import bf.sira.passerelle.referentiel.MembreDeclare;
import bf.sira.passerelle.referentiel.Membres;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Les demandes vues par les services (contrat §7.1). Le service appelant n'est jamais lu ailleurs
 * que dans son certificat ; une demande d'un autre service n'existe pas pour lui.
 */
@Component
public class Demandes {

    private static final Set<String> ANNULABLES =
            Set.of("EN_ATTENTE_ACCORD", "EN_COURS", "PARTIELLE");
    private static final Set<String> ACCEPTEES = Set.of("EN_COURS", "PARTIELLE", "COMPLETE");

    private final JdbcClient sql;
    private final TransactionTemplate transactions;
    private final Habilitations habilitations;
    private final Demarches demarches;
    private final Membres membres;
    private final Jeux jeux;
    private final Citoyens citoyens;
    private final Codes codes;
    private final Journal journal;
    private final Collecte collecte;
    private final Pki pki;
    private final Proprietes proprietes;
    private final ReglagesAccord reglageAccord;
    private final Clock horloge;

    Demandes(
            JdbcClient sql,
            TransactionTemplate transactions,
            Habilitations habilitations,
            Demarches demarches,
            Membres membres,
            Jeux jeux,
            Citoyens citoyens,
            Codes codes,
            Journal journal,
            Collecte collecte,
            Pki pki,
            Proprietes proprietes,
            ReglagesAccord reglageAccord,
            Clock horloge) {
        this.sql = sql;
        this.transactions = transactions;
        this.habilitations = habilitations;
        this.demarches = demarches;
        this.membres = membres;
        this.jeux = jeux;
        this.citoyens = citoyens;
        this.codes = codes;
        this.journal = journal;
        this.collecte = collecte;
        this.pki = pki;
        this.proprietes = proprietes;
        this.reglageAccord = reglageAccord;
        this.horloge = horloge;
    }

    /**
     * Crée une demande. Rejouée avec la même clé d'idempotence et le même contenu, la requête rend
     * la demande déjà créée, sans ses codes : la passerelle ne les garde pas en clair. Avec un
     * autre contenu, elle est refusée.
     */
    public DemandeCreee creer(String service, String cle, NouvelleDemande saisie) {
        Optional<DemandeCreee> dejaCreee = rejouer(service, cle, saisie);
        if (dejaCreee.isPresent()) {
            return dejaCreee.get();
        }
        Creation creation;
        try {
            creation = transactions.execute(etat -> inserer(service, cle, saisie));
        } catch (DuplicateKeyException e) {
            // Deux requêtes identiques arrivées ensemble : la seconde rend ce que la première a
            // créé.
            return rejouer(service, cle, saisie).orElseThrow(() -> e);
        }
        if (creation.accepteeDOffice()) {
            collecte.essayerMaintenant(creation.id());
        }
        DemandeCreee creee = creee(service, creation.id());
        return new DemandeCreee(
                creee.id(),
                creee.etat(),
                creee.urlAccord(),
                creation.codeCourt(),
                creation.codeVerification(),
                creee.expireLe(),
                creee.jeux());
    }

    private Optional<DemandeCreee> rejouer(String service, String cle, NouvelleDemande saisie) {
        return sql.sql(
                        "SELECT id, empreinte_requete FROM demande WHERE service = ? AND cle_idempotence = ?")
                .params(service, cle)
                .query((ligne, i) -> {
                    if (!ligne.getString("empreinte_requete").equals(saisie.empreinte())) {
                        throw ErreurMetier.conflit(
                                "Cette clé d'idempotence a déjà servi pour une autre"
                                        + " demande.");
                    }
                    return ligne.getObject("id", UUID.class);
                })
                .optional()
                .map(id -> creee(service, id));
    }

    private Creation inserer(String service, String cle, NouvelleDemande saisie) {
        List<String> jeuxHabilites = habilitations.jeuxHabilites(service, saisie.demarche());
        if (jeuxHabilites.isEmpty()) {
            throw Erreurs.habilitationAbsente();
        }
        verifierCanal(service, saisie);
        verifierCertificat(service, saisie.certificatChiffrement());
        String citoyen = citoyenVise(service, saisie);
        // Par identifiant unique, la personne n'est que désignée, quel que soit le canal. Aucune
        // ligne citoyen n'est créée pour un identifiant tapé, et la réponse est la même qu'il
        // désigne quelqu'un ou non. La demande lui est remise quand elle se connecte
        // (Accords.recevoir) ; personne d'autre ne peut s'y rattacher, ni par lien ni par code.
        String designation =
                saisie.identifiantUnique() == null
                        ? null
                        : citoyens.designation(saisie.identifiantUnique());

        Instant maintenant = horloge.instant();
        UUID id = UUID.randomUUID();
        String codeCourt = saisie.canal() == Canal.CODE ? codes.codeCourt() : null;
        // Le citoyen tape ce code, lu sur l'écran du service : il n'accepte que la demande de
        // l'écran qu'il a sous les yeux. Inutile quand le navigateur est celui de l'accord
        // (REDIRECTION, désigné ou non) ou quand le service désigne par pseudonyme un citoyen déjà
        // connu de lui.
        String codeVerification =
                saisie.canal() == Canal.CODE
                                || (saisie.canal() == Canal.NOTIFICATION && designation != null)
                        ? codes.codeVerification()
                        : null;
        sql.sql(
                        "INSERT INTO demande (id, service, demarche, jeux, canal, etat, citoyen,"
                                + " designation, retour_url, code_court_empreinte,"
                                + " code_verification_empreinte, certificat_chiffrement,"
                                + " cle_idempotence, empreinte_requete, creee_le, expire_le) VALUES"
                                + " (?, ?, ?, ?, ?, 'EN_ATTENTE_ACCORD', ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                                + " ?)")
                .params(
                        id,
                        service,
                        saisie.demarche(),
                        Sql.tableau(jeuxHabilites),
                        saisie.canal().name(),
                        citoyen,
                        designation,
                        saisie.retourUrl(),
                        codeCourt == null ? null : codes.empreinteCodeCourt(codeCourt),
                        codeVerification == null
                                ? null
                                : codes.empreinteCodeVerification(codeVerification),
                        saisie.certificatChiffrement(),
                        cle,
                        saisie.empreinte(),
                        Sql.date(maintenant),
                        Sql.date(maintenant.plus(reglageAccord.dureeAccord())))
                .update();
        if (citoyen == null) {
            // Le citoyen n'est pas encore connu : la ligne s'écrira quand il se rattachera.
            return new Creation(id, false, codeCourt, codeVerification);
        }
        Demarche demarche = demarches.lire(saisie.demarche());
        journal.ecrire(
                Trace.de(TypeLigne.DEMANDE, Resultat.EN_ATTENTE)
                        .citoyen(citoyen)
                        .service(service)
                        .demarche(demarche.code(), demarche.finalite())
                        .canal(saisie.canal().name())
                        .demande(id));
        return new Creation(
                id,
                accepterDOffice(id, service, demarche, citoyen, jeuxHabilites, maintenant),
                null,
                null);
    }

    /**
     * Le service désigne par pseudonyme un citoyen qui lui a déjà donné son accord, depuis un poste
     * d'agent authentifié (contrat §6). Tant que cette autorisation est active pour ce service et
     * cette démarche, et couvre ce qui est demandé, la demande est acceptée sans nouvel écran :
     * c'est journalisé, et visible de lui. Une demande par identifiant unique, qu'un navigateur
     * anonyme peut faire partir, n'est jamais acceptée d'office.
     */
    private boolean accepterDOffice(
            UUID id,
            String service,
            Demarche demarche,
            String citoyen,
            List<String> jeuxDemandes,
            Instant maintenant) {
        Optional<UUID> autorisation =
                sql.sql(
                                "SELECT id FROM autorisation WHERE citoyen = ? AND service = ? AND"
                                        + " demarche = ? AND retiree_le IS NULL AND expire_le > ? AND"
                                        + " jeux @> CAST(? AS text[]) ORDER BY expire_le DESC LIMIT 1")
                        .params(
                                citoyen,
                                service,
                                demarche.code(),
                                Sql.date(maintenant),
                                Sql.tableau(jeuxDemandes))
                        .query(UUID.class)
                        .optional();
        if (autorisation.isEmpty()) {
            return false;
        }
        sql.sql(
                        "UPDATE demande SET etat = 'EN_COURS', autorisation = ?, decidee_le = ?"
                                + " WHERE id = ?")
                .params(autorisation.get(), Sql.date(maintenant), id)
                .update();
        journal.ecrire(
                Trace.de(TypeLigne.ACCORD, Resultat.REUSSI)
                        .citoyen(citoyen)
                        .service(service)
                        .demarche(demarche.code(), demarche.finalite())
                        .canal(Canal.NOTIFICATION.name())
                        .demande(id)
                        .autorisation(autorisation.get())
                        .detail("AUTORISATION_ACTIVE"));
        collecte.preparer(id);
        return true;
    }

    /**
     * Sur REDIRECTION et CODE, l'identifiant unique est facultatif : celui que l'usager a saisi
     * lui-même chez le service, pour que seul lui puisse donner l'accord. Le pseudonyme, lui, n'y a
     * pas cours.
     */
    private void verifierCanal(String service, NouvelleDemande saisie) {
        switch (saisie.canal()) {
            case REDIRECTION -> {
                MembreDeclare membre = membres.lire(service);
                // Comparaison exacte avec la liste déclarée : aucune redirection ouverte possible.
                if (saisie.retourUrl() == null
                        || !membre.retoursAutorises().contains(saisie.retourUrl())) {
                    throw new SaisieRefusee(
                            "retour_url",
                            "Cette adresse de retour n'est pas déclarée pour votre service.");
                }
                refuserSi(
                        saisie.pseudonyme() != null,
                        "pseudonyme",
                        "Le canal REDIRECTION ne désigne pas par pseudonyme.");
            }
            case CODE -> {
                refuserSi(
                        saisie.pseudonyme() != null,
                        "pseudonyme",
                        "Le canal CODE ne désigne pas par pseudonyme.");
                refuserSi(
                        saisie.retourUrl() != null,
                        "retour_url",
                        "Le canal CODE n'a pas d'adresse de retour.");
            }
            case NOTIFICATION -> {
                refuserSi(
                        (saisie.identifiantUnique() == null) == (saisie.pseudonyme() == null),
                        "identifiant_unique",
                        "Donnez l'identifiant unique ou le pseudonyme du citoyen, pas les deux.");
                refuserSi(
                        saisie.retourUrl() != null,
                        "retour_url",
                        "Le canal NOTIFICATION n'a pas d'adresse de retour.");
            }
        }
    }

    /**
     * Les attestations ne seront chiffrées que pour un certificat de chiffrement émis par
     * l'autorité Sira au service qui appelle (contrat §5.6) : un service ne peut pas faire chiffrer
     * pour un tiers.
     */
    private void verifierCertificat(String service, String pem) {
        try {
            List<X509Certificate> chaine = Certificats.lirePem(pem);
            Certificats.validerChaine(chaine, pki.autorite(), horloge.instant());
            Certificats.validerUsage(chaine.getFirst(), UsageCle.CHIFFREMENT);
            String titulaire = Certificats.uriSan(chaine.getFirst()).orElse("");
            if (!TempsConstant.egaux(titulaire, IdentiteMembre.service(service).uri())) {
                throw new CertificatRefuse("certificat d'un autre membre");
            }
        } catch (CertificatRefuse e) {
            throw new SaisieRefusee(
                    "certificat_chiffrement",
                    "Ce n'est pas le certificat de chiffrement de votre service.");
        }
    }

    /** Le citoyen que le service désigne par son pseudonyme ; sinon aucun pour l'instant. */
    private String citoyenVise(String service, NouvelleDemande saisie) {
        if (saisie.pseudonyme() == null) {
            return null;
        }
        return citoyens.parPseudonyme(service, saisie.pseudonyme())
                .orElseThrow(Erreurs::citoyenInconnu);
    }

    private DemandeCreee creee(String service, UUID id) {
        return sql.sql("SELECT id, etat, expire_le, jeux FROM demande WHERE id = ? AND service = ?")
                .params(id, service)
                .query((ligne, i) ->
                        new DemandeCreee(
                                id,
                                ligne.getString("etat"),
                                proprietes.espaceCitoyenUrl() + "/accord/" + id,
                                null,
                                null,
                                Sql.instant(ligne, "expire_le"),
                                jeux.parCodes(Sql.liste(ligne, "jeux")).stream()
                                        .map(JeuDemande::de)
                                        .toList()))
                .single();
    }

    /**
     * L'état d'une demande et ses attestations.
     *
     * @param codeRetour canal REDIRECTION : le code que seul le navigateur ayant donné l'accord a
     *     reçu ; sans lui, le service ne lit que l'état
     */
    public EtatDemande lire(String service, UUID id, String codeRetour) {
        Suivi suivi =
                sql.sql(
                                "SELECT etat, canal, citoyen, code_retour_empreinte FROM demande WHERE"
                                        + " id = ? AND service = ?")
                        .params(id, service)
                        .query((ligne, i) ->
                                new Suivi(
                                        ligne.getString("etat"),
                                        Canal.valueOf(ligne.getString("canal")),
                                        ligne.getString("citoyen"),
                                        ligne.getString("code_retour_empreinte")))
                        .optional()
                        .orElseThrow(Erreurs::demandeIntrouvable);
        boolean remise = suivi.canal() != Canal.REDIRECTION;
        if (!remise && codeRetour != null) {
            if (suivi.empreinteCodeRetour() == null
                    || !TempsConstant.egaux(
                            Codes.empreinte(codeRetour), suivi.empreinteCodeRetour())) {
                throw Erreurs.codeInvalide(HttpStatus.FORBIDDEN);
            }
            remise = true;
        }
        boolean livrable = remise;
        List<EtatDemande.Element> elements =
                sql.sql(
                                "SELECT e.jeu, e.etat, a.jwe, e.prochaine_tentative_le FROM element e"
                                        + " LEFT JOIN attestation a ON a.id = e.attestation WHERE"
                                        + " e.demande = ? ORDER BY e.jeu")
                        .param(id)
                        .query((ligne, i) ->
                                new EtatDemande.Element(
                                        ligne.getString("jeu"),
                                        ligne.getString("etat"),
                                        livrable ? ligne.getString("jwe") : null,
                                        "A_TRAITER".equals(ligne.getString("etat"))
                                                ? Sql.instant(ligne, "prochaine_tentative_le")
                                                : null))
                        .list();
        String pseudonyme =
                livrable && ACCEPTEES.contains(suivi.etat())
                        ? citoyens.pseudonymeChez(service, suivi.citoyen())
                        : null;
        return new EtatDemande(id, suivi.etat(), pseudonyme, elements);
    }

    /** Annule une demande qui n'est pas terminée ; ce qui restait à obtenir ne le sera pas. */
    public void annuler(String service, UUID id) {
        transactions.executeWithoutResult(transaction -> {
            Annulation demande =
                    sql.sql(
                                    "SELECT d.etat, d.citoyen, d.demarche, m.finalite, d.canal FROM"
                                            + " demande d JOIN demarche m ON m.code = d.demarche"
                                            + " WHERE d.id = ? AND d.service = ? FOR UPDATE OF d")
                            .params(id, service)
                            .query((ligne, i) ->
                                    new Annulation(
                                            ligne.getString("etat"),
                                            ligne.getString("citoyen"),
                                            ligne.getString("demarche"),
                                            ligne.getString("finalite"),
                                            ligne.getString("canal")))
                            .optional()
                            .orElseThrow(Erreurs::demandeIntrouvable);
            if ("ANNULEE".equals(demande.etat())) {
                return;
            }
            if (!ANNULABLES.contains(demande.etat())) {
                throw ErreurMetier.conflit("Cette demande est terminée : elle ne s'annule plus.");
            }
            sql.sql(
                            "UPDATE demande SET etat = 'ANNULEE', code_court_empreinte = NULL,"
                                    + " code_verification_empreinte = NULL WHERE id = ?")
                    .param(id)
                    .update();
            sql.sql(
                            "UPDATE element SET etat = 'EXPIRE', bail_jusqu_a = NULL WHERE"
                                    + " demande = ? AND etat = 'A_TRAITER'")
                    .param(id)
                    .update();
            journal.ecrire(
                    Trace.de(TypeLigne.DEMANDE, Resultat.ECHEC)
                            .citoyen(demande.citoyen())
                            .service(service)
                            .demarche(demande.demarche(), demande.finalite())
                            .canal(demande.canal())
                            .demande(id)
                            .detail("ANNULATION"));
        });
    }

    /**
     * @throws bf.sira.commun.erreurs.ErreurMetier 403 si le service n'est pas habilité pour cette
     *     démarche, qu'elle existe ou non
     */
    public DemarcheDecrite decrire(String service, String code) {
        List<String> jeuxHabilites = habilitations.jeuxHabilites(service, code);
        if (jeuxHabilites.isEmpty()) {
            throw Erreurs.habilitationAbsente();
        }
        return new DemarcheDecrite(
                code,
                demarches.lire(code).libelle(),
                jeux.parCodes(jeuxHabilites).stream().map(JeuDemande::de).toList());
    }

    private static void refuserSi(boolean condition, String champ, String message) {
        if (condition) {
            throw new SaisieRefusee(champ, message);
        }
    }

    /** Les codes en clair ne vivent que le temps de la réponse au service. */
    private record Creation(
            UUID id, boolean accepteeDOffice, String codeCourt, String codeVerification) {}

    private record Suivi(String etat, Canal canal, String citoyen, String empreinteCodeRetour) {}

    private record Annulation(
            String etat, String citoyen, String demarche, String finalite, String canal) {}
}
