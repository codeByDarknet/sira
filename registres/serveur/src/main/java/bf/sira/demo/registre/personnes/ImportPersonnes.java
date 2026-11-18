package bf.sira.demo.registre.personnes;

import bf.sira.demo.registre.configuration.ProprietesRegistre;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Charge au démarrage les personnes fictives du fichier {@code personnes.json} du registre. Une
 * personne déjà présente n'est pas touchée : une correction faite en démonstration survit au
 * redémarrage.
 */
@Component
class ImportPersonnes implements ApplicationRunner {

    private static final Logger journal = LoggerFactory.getLogger(ImportPersonnes.class);

    record Fichier(List<Map<String, Object>> personnes) {}

    private final Path fichier;
    private final Fiches fiches;
    private final DepotPersonnes depot;
    private final JsonMapper json;

    ImportPersonnes(
            ProprietesRegistre registre, Fiches fiches, DepotPersonnes depot, JsonMapper json) {
        this.fichier = registre.dossier().resolve("personnes.json");
        this.fiches = fiches;
        this.depot = depot;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        List<Map<String, Object>> personnes = json.readValue(fichier, Fichier.class).personnes();
        long ajoutees =
                personnes.stream()
                        .filter(fiche -> depot.ajouter(fiches.identifiantDe(fiche), fiche))
                        .count();
        journal.info("Import des personnes : lues={} ajoutees={}", personnes.size(), ajoutees);
    }
}
