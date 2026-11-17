package bf.sira.passerelle.amorcage;

import bf.sira.commun.jetons.Acteur;
import bf.sira.passerelle.referentiel.Demarche;
import bf.sira.passerelle.referentiel.Demarches;
import bf.sira.passerelle.referentiel.Habilitations;
import bf.sira.passerelle.referentiel.Jeu;
import bf.sira.passerelle.referentiel.Jeux;
import bf.sira.passerelle.referentiel.MembreDeclare;
import bf.sira.passerelle.referentiel.Membres;
import bf.sira.passerelle.referentiel.Registre;
import bf.sira.passerelle.referentiel.Registres;
import bf.sira.passerelle.referentiel.SaisieHabilitation;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Premier démarrage : si un répertoire d'amorçage est désigné et que le référentiel est vide, les
 * fichiers qu'il contient y sont importés, en une transaction. Le socle ne porte aucune donnée :
 * c'est ainsi qu'une plateforme (une démonstration, un ministère) y déclare les siennes. Ensuite,
 * le référentiel ne se modifie plus que par la console.
 */
@Component
class Amorcage implements ApplicationRunner {

    private static final Logger journal = LoggerFactory.getLogger(Amorcage.class);

    /**
     * Le contenu d'un fichier d'amorçage : les mêmes formes que celles de la console.
     *
     * @param habilitations enregistrées directement en vigueur
     */
    record Fichier(
            List<@Valid MembreDeclare> membres,
            List<@Valid Registre> registres,
            List<@Valid Jeu> jeux,
            List<@Valid Demarche> demarches,
            List<@Valid HabilitationEnVigueur> habilitations) {

        Fichier {
            membres = membres == null ? List.of() : membres;
            registres = registres == null ? List.of() : registres;
            jeux = jeux == null ? List.of() : jeux;
            demarches = demarches == null ? List.of() : demarches;
            habilitations = habilitations == null ? List.of() : habilitations;
        }
    }

    /**
     * @param etat facultatif ; seule une habilitation {@code APPROUVEE} s'amorce
     */
    record HabilitationEnVigueur(
            @NotNull @Valid @JsonUnwrapped SaisieHabilitation saisie,
            @Pattern(regexp = "APPROUVEE") String etat) {}

    private final Path repertoire;
    private final Environment environnement;
    private final JdbcClient sql;
    private final TransactionTemplate transactions;
    private final JsonMapper json;
    private final Validator validateur;
    private final Membres membres;
    private final Registres registres;
    private final Jeux jeux;
    private final Demarches demarches;
    private final Habilitations habilitations;

    Amorcage(
            @Value("${sira.amorcage.repertoire:}") Path repertoire,
            Environment environnement,
            JdbcClient sql,
            TransactionTemplate transactions,
            JsonMapper json,
            Validator validateur,
            Membres membres,
            Registres registres,
            Jeux jeux,
            Demarches demarches,
            Habilitations habilitations) {
        this.repertoire = repertoire;
        this.environnement = environnement;
        this.sql = sql;
        this.transactions = transactions;
        // Une clé inconnue est une faute de frappe : mieux vaut refuser de démarrer que l'ignorer.
        this.json =
                json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.validateur = validateur;
        this.membres = membres;
        this.registres = registres;
        this.jeux = jeux;
        this.demarches = demarches;
        this.habilitations = habilitations;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (repertoire == null || repertoire.toString().isEmpty()) {
            return;
        }
        List<Fichier> fichiers = lire();
        transactions.executeWithoutResult(transaction -> importer(fichiers));
    }

    private void importer(List<Fichier> fichiers) {
        // Deux instances qui démarrent ensemble : une seule importe, l'autre trouve la base
        // remplie.
        sql.sql("LOCK TABLE membre IN EXCLUSIVE MODE").update();
        if (sql.sql("SELECT EXISTS (SELECT FROM membre)").query(Boolean.class).single()) {
            journal.info("Amorçage ignoré : le référentiel n'est pas vide");
            return;
        }
        // Chaque famille après celles dont elle dépend, quel que soit le fichier qui la porte.
        fichiers.forEach(f -> f.membres().forEach(m -> membres.creer(m, Acteur.AMORCAGE)));
        fichiers.forEach(f -> f.registres().forEach(r -> registres.creer(r, Acteur.AMORCAGE)));
        fichiers.forEach(f -> f.jeux().forEach(j -> jeux.creer(j, Acteur.AMORCAGE)));
        fichiers.forEach(f -> f.demarches().forEach(d -> demarches.creer(d, Acteur.AMORCAGE)));
        fichiers.forEach(f -> f.habilitations().forEach(h -> habilitations.amorcer(h.saisie())));
        journal.info("Amorçage terminé : fichiers={}", fichiers.size());
    }

    private List<Fichier> lire() {
        // Un fichier seul est admis : une plateforme peut tenir plusieurs amorçages côte à côte.
        if (Files.isRegularFile(repertoire)) {
            return List.of(lire(repertoire));
        }
        try (Stream<Path> contenu = Files.list(repertoire)) {
            return contenu.filter(Amorcage::estFichierDAmorcage).sorted().map(this::lire).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Répertoire d'amorçage illisible : " + repertoire, e);
        }
    }

    private static boolean estFichierDAmorcage(Path fichier) {
        String nom = fichier.getFileName().toString();
        return Files.isRegularFile(fichier)
                && (nom.endsWith(".yaml") || nom.endsWith(".yml") || nom.endsWith(".json"));
    }

    Fichier lire(Path chemin) {
        Fichier fichier;
        try {
            // Les adresses publiques dépendent du déploiement (port publié) : « ${SIRA_PORT:8600} »
            // est remplacé par la valeur de l'environnement. Une variable inconnue sans valeur par
            // défaut arrête le démarrage.
            String texte =
                    environnement.resolveRequiredPlaceholders(
                            Files.readString(chemin, StandardCharsets.UTF_8));
            // YAML sûr (aucun type Java instancié) ; un fichier JSON est aussi du YAML.
            Object arbre = new Yaml(new SafeConstructor(new LoaderOptions())).load(texte);
            fichier = json.convertValue(arbre, Fichier.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Fichier d'amorçage illisible : " + chemin, e);
        }
        Set<ConstraintViolation<Fichier>> refus = validateur.validate(fichier);
        if (!refus.isEmpty()) {
            throw new IllegalStateException(
                    "Fichier d'amorçage refusé : "
                            + chemin
                            + " : "
                            + refus.stream()
                                    .map(r -> r.getPropertyPath() + " " + r.getMessage())
                                    .sorted()
                                    .toList());
        }
        return fichier;
    }
}
