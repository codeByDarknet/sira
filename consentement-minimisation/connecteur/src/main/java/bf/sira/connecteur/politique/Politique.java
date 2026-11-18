package bf.sira.connecteur.politique;

import bf.sira.connecteur.Niveau;
import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import bf.sira.connecteur.regles.Chemin;
import bf.sira.connecteur.regles.Operateur;
import bf.sira.connecteur.regles.Regle;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * La politique locale du registre : ce qu'il accepte d'exposer, champ par champ. Elle est écrite
 * par l'administration et prime sur toute habilitation centrale. Ce qui n'y figure pas est refusé.
 *
 * @param validite durées de validité des attestations
 * @param champs champs exposés, avec leur niveau maximal
 * @param documents types de documents délivrés
 * @param libellesDocuments nom lisible de chaque type de document (facultatif)
 */
public record Politique(
        Validite validite,
        List<ChampExpose> champs,
        List<String> documents,
        Map<String, String> libellesDocuments) {

    private static final Pattern TYPE_DOCUMENT = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,63})");

    // Une clé inconnue fait échouer le chargement : une faute de frappe dans une restriction ne
    // doit pas la faire disparaître en silence.
    private static final JsonMapper LECTEUR =
            JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

    /**
     * @param parDefaut durée retenue quand la requête n'en propose pas
     * @param maximale plafond appliqué à la durée proposée par la requête
     */
    public record Validite(Duration parDefaut, Duration maximale) {

        public Validite {
            if (parDefaut == null
                    || maximale == null
                    || !parDefaut.isPositive()
                    || parDefaut.compareTo(maximale) > 0) {
                throw new IllegalArgumentException("validite : 0 < par_defaut <= maximale");
            }
        }
    }

    /**
     * @param chemin champ de la fiche, sans filtre (ex. {@code diplomes.mention})
     * @param libelle nom lisible du champ, montré dans la console (le chemin s'il est absent)
     * @param type nature de la valeur ({@code TEXTE} s'il est absent)
     * @param niveau niveau maximal : {@code STATUT} (oui/non seulement) ou {@code CHAMPS} (la
     *     valeur peut sortir)
     * @param operateurs questions admises au niveau STATUT ; toutes si absent
     * @param valeurs valeurs de comparaison admises au niveau STATUT ; toutes si absent
     */
    public record ChampExpose(
            String chemin,
            String libelle,
            TypeChamp type,
            Niveau niveau,
            Set<Operateur> operateurs,
            List<JsonNode> valeurs) {

        public ChampExpose {
            if (chemin == null || !Chemin.lire(chemin).sansFiltres().equals(chemin)) {
                throw new IllegalArgumentException("chemin de champ invalide");
            }
            if (niveau != Niveau.STATUT && niveau != Niveau.CHAMPS) {
                throw new IllegalArgumentException("niveau d'un champ : STATUT ou CHAMPS");
            }
            libelle = libelle == null || libelle.isBlank() ? chemin : libelle.strip();
            type = type == null ? TypeChamp.TEXTE : type;
        }
    }

    public Politique {
        if (validite == null || champs == null || documents == null) {
            throw new IllegalArgumentException("validite, champs et documents sont obligatoires");
        }
        Set<String> chemins = new HashSet<>();
        if (!champs.stream().allMatch(champ -> champ != null && chemins.add(champ.chemin()))) {
            throw new IllegalArgumentException("champ vide ou déclaré deux fois");
        }
        if (!documents.stream().allMatch(d -> d != null && TYPE_DOCUMENT.matcher(d).matches())) {
            throw new IllegalArgumentException("type de document invalide");
        }
        champs = List.copyOf(champs);
        documents = List.copyOf(documents);
        libellesDocuments = libellesDocuments == null ? Map.of() : Map.copyOf(libellesDocuments);
    }

    /** Champ du schéma publié : ce que la console peut proposer, sans rien de la fiche. */
    public record ChampSchema(
            String chemin,
            String libelle,
            TypeChamp type,
            Niveau niveau,
            Set<Operateur> operateurs,
            List<JsonNode> valeurs) {}

    /** Document du schéma publié. */
    public record DocumentSchema(String code, String libelle) {}

    /** Ce que le registre accepte de laisser sortir, publié pour la console. */
    public record Schema(List<ChampSchema> champs, List<DocumentSchema> documents) {}

    /**
     * Le schéma publié : les seuls champs et documents exposés, avec leur nature. Rien d'autre du
     * registre n'en sort (ni chemin de raccordement, ni donnée).
     */
    public Schema schema() {
        return new Schema(
                champs.stream()
                        .map(c ->
                                new ChampSchema(
                                        c.chemin(),
                                        c.libelle(),
                                        c.type(),
                                        c.niveau(),
                                        c.operateurs(),
                                        c.valeurs()))
                        .toList(),
                documents.stream()
                        .map(d -> new DocumentSchema(d, libellesDocuments.getOrDefault(d, d)))
                        .toList());
    }

    /**
     * Lit le fichier monté par l'administration. Un fichier absent ou invalide empêche le démarrage
     * : sans politique, le connecteur n'expose rien.
     */
    public static Politique charger(Path fichier) {
        try (Reader lecteur = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            Object contenu = new Yaml(new SafeConstructor(new LoaderOptions())).load(lecteur);
            if (contenu == null) {
                throw new IllegalArgumentException("fichier vide");
            }
            return LECTEUR.convertValue(contenu, Politique.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("politique locale illisible : " + fichier, e);
        }
    }

    /**
     * Niveau STATUT : le champ doit être exposé, et la question admise.
     *
     * @throws Refus sinon
     */
    public void autoriser(Regle regle) {
        ChampExpose champ = expose(regle.champ(), Niveau.STATUT);
        boolean questionAdmise =
                (champ.operateurs() == null || champ.operateurs().contains(regle.operateur()))
                        && (champ.valeurs() == null
                                || regle.valeur() == null
                                || champ.valeurs().stream()
                                        .anyMatch(v -> Regle.memeValeur(v, regle.valeur())));
        if (!questionAdmise) {
            throw new Refus(Motif.QUESTION_NON_AUTORISEE);
        }
    }

    /**
     * Niveau CHAMPS : chaque champ doit être exposé à ce niveau.
     *
     * @throws Refus sinon
     */
    public void autoriser(List<Chemin> champsDemandes) {
        champsDemandes.forEach(champ -> expose(champ, Niveau.CHAMPS));
    }

    /**
     * @throws Refus si le registre ne délivre pas ce type de document
     */
    public void autoriserDocument(String type) {
        if (!documents.contains(type)) {
            throw new Refus(Motif.DOCUMENT_NON_AUTORISE);
        }
    }

    /** La durée de validité d'une attestation : celle de la requête, plafonnée, ou le défaut. */
    public Duration validite(Duration demandee) {
        if (demandee == null) {
            return validite.parDefaut();
        }
        if (!demandee.isPositive()) {
            throw new Refus(Motif.JEU_INVALIDE);
        }
        return demandee.compareTo(validite.maximale()) > 0 ? validite.maximale() : demandee;
    }

    private ChampExpose expose(Chemin chemin, Niveau niveau) {
        // Un filtre « [type=BAC] » pose une question sur « type » : ce champ doit être exposé.
        chemin.champsDesFiltres().forEach(this::champ);
        ChampExpose champ = champ(chemin.sansFiltres());
        if (niveau == Niveau.CHAMPS && champ.niveau() != Niveau.CHAMPS) {
            throw new Refus(Motif.NIVEAU_DEPASSE);
        }
        return champ;
    }

    private ChampExpose champ(String chemin) {
        return champs.stream()
                .filter(champ -> champ.chemin().equals(chemin))
                .findFirst()
                .orElseThrow(() -> new Refus(Motif.CHAMP_NON_EXPOSE));
    }
}
