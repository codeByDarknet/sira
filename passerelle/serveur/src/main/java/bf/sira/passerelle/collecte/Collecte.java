package bf.sira.passerelle.collecte;

import bf.sira.commun.donnees.Sql;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Certificats;
import bf.sira.consentement.Citoyens;
import bf.sira.journal.Journal;
import bf.sira.journal.Resultat;
import bf.sira.journal.Trace;
import bf.sira.journal.TypeLigne;
import bf.sira.passerelle.config.Proprietes;
import bf.sira.passerelle.referentiel.Habilitations;
import bf.sira.passerelle.referentiel.Jeu;
import bf.sira.passerelle.referentiel.Jeux;
import bf.sira.resilience.ConnecteurInjoignable;
import bf.sira.resilience.Disjoncteurs;
import bf.sira.resilience.ReglageFile;
import bf.sira.resilience.Reprise;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Obtient auprès des registres les éléments d'une demande acceptée (contrat §6, étape 3). Chaque
 * élément suit son propre chemin : obtenu, refusé, ou laissé à la file d'attente si son registre
 * est en panne. L'appel au registre se fait hors transaction ; son issue s'enregistre, avec sa
 * ligne de journal, dans une transaction courte.
 */
@Component
public class Collecte implements Reprise {

    private static final Logger JOURNAL = LoggerFactory.getLogger(Collecte.class);

    private final JdbcClient sql;
    private final TransactionTemplate transactions;
    private final Map<String, Transport> transports;
    private final Requetes requetes;
    private final Disjoncteurs disjoncteurs;
    private final Citoyens citoyens;
    private final Jeux jeux;
    private final Habilitations habilitations;
    private final Journal journal;
    private final ExecutorService executeur;
    private final Proprietes proprietes;
    private final ReglageFile file;
    private final Clock horloge;

    Collecte(
            JdbcClient sql,
            TransactionTemplate transactions,
            List<Transport> transports,
            Requetes requetes,
            Disjoncteurs disjoncteurs,
            Citoyens citoyens,
            Jeux jeux,
            Habilitations habilitations,
            Journal journal,
            ExecutorService executeurCollecte,
            Proprietes proprietes,
            ReglageFile file,
            Clock horloge) {
        this.sql = sql;
        this.transactions = transactions;
        this.transports = Transport.parCode(transports);
        this.requetes = requetes;
        this.disjoncteurs = disjoncteurs;
        this.citoyens = citoyens;
        this.jeux = jeux;
        this.habilitations = habilitations;
        this.journal = journal;
        this.executeur = executeurCollecte;
        this.proprietes = proprietes;
        this.file = file;
        this.horloge = horloge;
    }

    /** Ce qu'a donné un appel à un registre. */
    private sealed interface Issue {}

    private record Obtenue(String jwe, String idEchange) implements Issue {}

    /**
     * @param etat {@code INCONNU} ou {@code REFUSE}
     */
    private record Close(String etat, Resultat resultat, String detail, String idEchange)
            implements Issue {}

    /**
     * @param tentee faux si le registre n'a pas été appelé (disjoncteur ouvert, cloison pleine)
     */
    private record Reportee(boolean tentee) implements Issue {}

    /** L'élément n'était plus attendu au moment d'appeler : le registre n'a pas été consulté. */
    private record Abandonnee() implements Issue {}

    /** Levée juste avant l'envoi quand l'élément n'est plus attendu. */
    private static final class PlusAttendu extends RuntimeException {
        PlusAttendu() {
            super(null, null, false, false);
        }
    }

    /**
     * Inscrit les éléments d'une demande qui vient d'être acceptée, dans la transaction de
     * l'accord. Une attestation encore valide, déjà obtenue pour le même service, le même citoyen
     * et la même démarche, est réutilisée au lieu d'être redemandée.
     */
    public void preparer(UUID demande) {
        Instant maintenant = horloge.instant();
        Dossier dossier =
                sql.sql(
                                "SELECT d.service, d.citoyen, d.demarche, m.finalite, d.canal,"
                                        + " d.autorisation, d.jeux, d.certificat_chiffrement"
                                        + " FROM demande d JOIN demarche m ON m.code = d.demarche"
                                        + " WHERE d.id = ?")
                        .param(demande)
                        .query((ligne, i) ->
                                new Dossier(
                                        ligne.getString("service"),
                                        ligne.getString("citoyen"),
                                        ligne.getString("demarche"),
                                        ligne.getString("finalite"),
                                        ligne.getString("canal"),
                                        ligne.getObject("autorisation", UUID.class),
                                        Sql.liste(ligne, "jeux"),
                                        ligne.getString("certificat_chiffrement")))
                        .single();
        String empreinte =
                Certificats.empreinteSha256(Certificats.lirePem(dossier.certificat()).getFirst());
        boolean resteAObtenir = false;
        for (Jeu jeu : jeux.parCodes(dossier.jeux())) {
            var reutilisable =
                    sql.sql(
                                    "SELECT id, id_echange FROM attestation WHERE service = ? AND"
                                            + " citoyen = ? AND demarche = ? AND jeu = ? AND"
                                            + " empreinte_certificat = ? AND expire_le > ? ORDER BY"
                                            + " expire_le DESC LIMIT 1")
                            .params(
                                    dossier.service(),
                                    dossier.citoyen(),
                                    dossier.demarche(),
                                    jeu.code(),
                                    empreinte,
                                    Sql.date(maintenant))
                            .query((ligne, i) ->
                                    Map.entry(ligne.getObject(1, UUID.class), ligne.getString(2)))
                            .optional();
            sql.sql(
                            "INSERT INTO element (id, demande, jeu, registre, etat, attestation,"
                                    + " prochaine_tentative_le, bail_jusqu_a, cree_le)"
                                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(
                            UUID.randomUUID(),
                            demande,
                            jeu.code(),
                            jeu.registre(),
                            reutilisable.isPresent() ? "REUTILISE" : "A_TRAITER",
                            reutilisable.map(Map.Entry::getKey).orElse(null),
                            Sql.date(maintenant),
                            // L'essai immédiat réserve l'élément : la file ne le prend pas en même
                            // temps.
                            Sql.date(maintenant.plus(file.bail())),
                            Sql.date(maintenant))
                    .update();
            resteAObtenir |= reutilisable.isEmpty();
            reutilisable.ifPresent(attestation ->
                    journal.ecrire(
                            Trace.de(TypeLigne.REUTILISATION, Resultat.REUSSI)
                                    .citoyen(dossier.citoyen())
                                    .service(dossier.service())
                                    .jeu(jeu.registre(), jeu.code(), jeu.niveau().name())
                                    .demarche(dossier.demarche(), dossier.finalite())
                                    .canal(dossier.canal())
                                    .idEchange(attestation.getValue())
                                    .demande(demande)
                                    .autorisation(dossier.autorisation())));
        }
        if (!resteAObtenir) {
            actualiser(demande);
        }
    }

    /**
     * Demande tout de suite les éléments d'une demande, en parallèle, et rend la main au plus tard
     * après le temps accordé. Un appel encore en cours à ce moment se termine quand même.
     */
    public void essayerMaintenant(UUID demande) {
        List<Tache> taches =
                sql.sql(Tache.SELECTION + " AND e.demande = ?")
                        .param(demande)
                        .query(Tache::lire)
                        .list();
        attendre(taches, true, proprietes.collecte().budgetImmediat());
    }

    /** Reprend les éléments que la file d'attente vient de réserver. */
    @Override
    public void reprendre(List<UUID> elements, Duration bail) {
        List<Tache> taches =
                sql.sql(Tache.SELECTION + " AND e.id = ANY (?)")
                        .param(elements.toArray(UUID[]::new))
                        .query(Tache::lire)
                        .list();
        attendre(taches, false, bail);
    }

    /** Lance les appels sur des threads virtuels et attend leur fin, sans dépasser la durée. */
    void attendre(List<Tache> taches, boolean immediat, Duration duree) {
        CompletableFuture<?>[] appels =
                taches.stream()
                        .map(tache ->
                                CompletableFuture.runAsync(
                                        () -> traiter(tache, immediat), executeur))
                        .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(appels).get(duree.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Rien à faire : les éléments en retard seront lus plus tard par le service.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // Le bail de l'élément expirera : la file le reprendra.
            JOURNAL.error("Collecte interrompue", e.getCause());
        }
    }

    private void traiter(Tache tache, boolean immediat) {
        Issue issue = appeler(tache, immediat);
        transactions.executeWithoutResult(etat -> enregistrer(tache, issue));
    }

    private Issue appeler(Tache tache, boolean immediat) {
        Transport transport = transports.get(tache.transport());
        if (transport == null) {
            JOURNAL.warn("Registre {} : transport {} absent", tache.registre(), tache.transport());
            return new Reportee(false);
        }
        // Une habilitation suspendue coupe aussi ce qui attendait dans la file.
        if (!habilitations.jeuxHabilites(tache.service(), tache.demarche()).contains(tache.jeu())) {
            return new Close("REFUSE", Resultat.REFUSE, "HABILITATION_ABSENTE", null);
        }
        Jeu jeu = jeux.lire(tache.jeu());
        String identifiant = citoyens.identifiantUnique(tache.citoyen());
        String pseudonyme = citoyens.pseudonymeChez(tache.service(), tache.citoyen());
        String[] idEchange = new String[1];
        try {
            Transport.Reponse reponse =
                    disjoncteurs.appeler(
                            tache.registre(),
                            immediat,
                            () -> {
                                // Le citoyen a pu retirer son accord, ou le service annuler, depuis
                                // que la tâche a été prise : on le relit juste avant d'envoyer.
                                if (!encoreAttendu(tache)) {
                                    throw new PlusAttendu();
                                }
                                // Chaque essai porte son propre identifiant : une requête ne sert
                                // qu'une fois.
                                idEchange[0] = UUID.randomUUID().toString();
                                return verifier(
                                        transport.envoyer(
                                                tache.registre(),
                                                tache.adresse(),
                                                requetes.signer(
                                                        new Requetes.Demande(
                                                                idEchange[0],
                                                                tache.registre(),
                                                                identifiant,
                                                                pseudonyme,
                                                                IdentiteMembre.service(
                                                                        tache.service()),
                                                                tache.certificat(),
                                                                tache.demarche(),
                                                                tache.finalite(),
                                                                tache.autorisation().toString(),
                                                                tache.accordeeLe(),
                                                                tache.canal(),
                                                                jeu))));
                            });
            return switch (HttpStatus.resolve(reponse.statut())) {
                case OK -> new Obtenue(reponse.attestation(), idEchange[0]);
                case NOT_FOUND ->
                        new Close("INCONNU", Resultat.ECHEC, "PERSONNE_INCONNUE", idEchange[0]);
                case UNPROCESSABLE_CONTENT ->
                        new Close("REFUSE", Resultat.REFUSE, "JEU_REFUSE", idEchange[0]);
                case null, default ->
                        new Close("REFUSE", Resultat.REFUSE, "REQUETE_REFUSEE", idEchange[0]);
            };
        } catch (PlusAttendu e) {
            return new Abandonnee();
        } catch (ConnecteurInjoignable e) {
            JOURNAL.warn("Registre {} injoignable : {}", tache.registre(), e.getMessage());
            return new Reportee(true);
        } catch (CallNotPermittedException | BulkheadFullException e) {
            // Le disjoncteur a pu s'ouvrir entre deux essais : le premier a bien eu lieu.
            return new Reportee(idEchange[0] != null);
        }
    }

    private boolean encoreAttendu(Tache tache) {
        return sql.sql(
                        "SELECT EXISTS (SELECT FROM element e JOIN autorisation a ON a.id = ? WHERE"
                                + " e.id = ? AND e.etat = 'A_TRAITER' AND a.retiree_le IS NULL)")
                .params(tache.autorisation(), tache.element())
                .query(Boolean.class)
                .single();
    }

    /**
     * Une réponse 200 doit porter un JWE de la forme convenue (contrat §5.5) ; sinon c'est une
     * panne.
     */
    static Transport.Reponse verifier(Transport.Reponse reponse) {
        if (reponse.statut() != HttpStatus.OK.value()) {
            return reponse;
        }
        try {
            JWEHeader enTete = JWEObject.parse(reponse.attestation()).getHeader();
            if (!JWEAlgorithm.ECDH_ES_A256KW.equals(enTete.getAlgorithm())
                    || !EncryptionMethod.A256GCM.equals(enTete.getEncryptionMethod())) {
                throw new ConnecteurInjoignable("attestation chiffrée hors du format convenu");
            }
            return reponse;
        } catch (ParseException e) {
            throw new ConnecteurInjoignable("attestation illisible", e);
        }
    }

    private void enregistrer(Tache tache, Issue issue) {
        Instant maintenant = horloge.instant();
        // Toute écriture sur une demande commence par verrouiller sa ligne : pas d'interblocage.
        sql.sql("SELECT id FROM demande WHERE id = ? FOR UPDATE")
                .param(tache.demande())
                .query(UUID.class)
                .single();
        boolean attendu =
                sql.sql("SELECT etat = 'A_TRAITER' FROM element WHERE id = ? FOR UPDATE")
                        .param(tache.element())
                        .query(Boolean.class)
                        .single();
        switch (issue) {
            case Obtenue obtenue -> {
                // La consultation a eu lieu : elle est tracée même si l'accord vient d'être retiré.
                journal.ecrire(
                        trace(tache, TypeLigne.CONSULTATION, Resultat.REUSSI)
                                .idEchange(obtenue.idEchange()));
                repondu(tache.registre(), "dernier_succes_le", maintenant);
                if (attendu) {
                    deposer(tache, obtenue, maintenant);
                }
            }
            case Close close -> {
                journal.ecrire(
                        trace(tache, TypeLigne.CONSULTATION, close.resultat())
                                .idEchange(close.idEchange())
                                .detail(close.detail()));
                if (close.idEchange() != null) {
                    repondu(tache.registre(), "dernier_succes_le", maintenant);
                }
                clore(tache.element(), close.etat());
            }
            case Reportee reportee -> {
                if (reportee.tentee()) {
                    repondu(tache.registre(), "dernier_echec_le", maintenant);
                }
                if (attendu) {
                    reporter(tache, reportee.tentee(), maintenant);
                }
            }
            case Abandonnee abandonnee -> {
                // Rien à tracer ni à clore : le retrait ou l'annulation l'a déjà fait.
            }
        }
        actualiser(tache.demande());
    }

    private void deposer(Tache tache, Obtenue obtenue, Instant maintenant) {
        UUID attestation = UUID.randomUUID();
        sql.sql(
                        "INSERT INTO attestation (id, service, citoyen, demarche, jeu,"
                                + " empreinte_certificat, jwe, id_echange, obtenue_le, expire_le)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        attestation,
                        tache.service(),
                        tache.citoyen(),
                        tache.demarche(),
                        tache.jeu(),
                        Certificats.empreinteSha256(
                                Certificats.lirePem(tache.certificat()).getFirst()),
                        obtenue.jwe(),
                        obtenue.idEchange(),
                        Sql.date(maintenant),
                        Sql.date(maintenant.plus(Jeux.validite(jeux.lire(tache.jeu())))))
                .update();
        sql.sql(
                        "UPDATE element SET etat = 'OBTENU', attestation = ?, bail_jusqu_a = NULL WHERE id = ?")
                .params(attestation, tache.element())
                .update();
    }

    private void clore(UUID element, String etat) {
        sql.sql(
                        "UPDATE element SET etat = ?, bail_jusqu_a = NULL WHERE id = ? AND etat = 'A_TRAITER'")
                .params(etat, element)
                .update();
    }

    private void reporter(Tache tache, boolean tentee, Instant maintenant) {
        if (Duration.between(tache.creeLe(), maintenant).compareTo(file.dureeMax()) > 0) {
            clore(tache.element(), "EXPIRE");
            journal.ecrire(trace(tache, TypeLigne.ECHEC, Resultat.ECHEC).detail("ABANDON"));
            return;
        }
        int tentatives = tache.tentatives() + (tentee ? 1 : 0);
        boolean dejaJournalise =
                sql.sql(
                                "UPDATE element e SET tentatives = ?, prochaine_tentative_le = ?,"
                                        + " bail_jusqu_a = NULL, echec_journalise = true FROM element"
                                        + " avant WHERE e.id = ? AND avant.id = e.id RETURNING"
                                        + " avant.echec_journalise")
                        .params(
                                tentatives,
                                Sql.date(maintenant.plus(delai(tentatives))),
                                tache.element())
                        .query(Boolean.class)
                        .single();
        if (!dejaJournalise) {
            // Une seule ligne par élément en panne : le citoyen voit l'attente, pas chaque essai.
            journal.ecrire(
                    trace(tache, TypeLigne.ECHEC, Resultat.EN_ATTENTE)
                            .detail("REGISTRE_INJOIGNABLE"));
        }
    }

    /**
     * Attente avant le prochain essai : elle double à chaque échec, jusqu'à un plafond, et une part
     * de hasard évite que tous les éléments d'un registre repartent au même instant.
     */
    Duration delai(int tentatives) {
        long plafond = file.delaiMax().toMillis();
        long initial = file.delaiInitial().toMillis();
        int doublements = Math.clamp(tentatives - 1, 0, 30);
        long delai = Math.min(plafond, initial << doublements);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(delai / 2, delai + 1));
    }

    private void repondu(String registre, String colonne, Instant maintenant) {
        sql.sql("UPDATE registre SET " + colonne + " = ? WHERE code = ?")
                .params(Sql.date(maintenant), registre)
                .update();
    }

    /**
     * L'état d'une demande acceptée découle de ses éléments : complète quand plus rien n'est
     * attendu.
     */
    public void actualiser(UUID demande) {
        sql.sql(
                        "UPDATE demande SET etat = CASE WHEN EXISTS (SELECT FROM element WHERE"
                                + " demande = demande.id AND etat = 'A_TRAITER') THEN 'PARTIELLE'"
                                + " ELSE 'COMPLETE' END WHERE id = ? AND etat IN ('EN_COURS',"
                                + " 'PARTIELLE', 'COMPLETE')")
                .param(demande)
                .update();
    }

    private Trace trace(Tache tache, TypeLigne type, Resultat resultat) {
        Jeu jeu = jeux.lire(tache.jeu());
        return Trace.de(type, resultat)
                .citoyen(tache.citoyen())
                .service(tache.service())
                .jeu(tache.registre(), tache.jeu(), jeu.niveau().name())
                .demarche(tache.demarche(), tache.finalite())
                .canal(tache.canal())
                .demande(tache.demande())
                .autorisation(tache.autorisation());
    }

    private record Dossier(
            String service,
            String citoyen,
            String demarche,
            String finalite,
            String canal,
            UUID autorisation,
            List<String> jeux,
            String certificat) {}
}
