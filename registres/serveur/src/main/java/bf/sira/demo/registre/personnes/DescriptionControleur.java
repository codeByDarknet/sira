package bf.sira.demo.registre.personnes;

import bf.sira.demo.registre.configuration.ProprietesRegistre;
import bf.sira.demo.registre.configuration.ProprietesRegistre.Champ;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Back-office : ce que l'écran doit savoir du registre pour afficher et corriger une fiche. */
@RestController
class DescriptionControleur {

    record TypeDocument(String type, String titre, List<String> champs) {}

    record Description(
            String code,
            String nom,
            String organisme,
            List<Champ> champs,
            List<TypeDocument> documents) {}

    private final Description description;

    DescriptionControleur(ProprietesRegistre registre) {
        this.description =
                new Description(
                        registre.code(),
                        registre.nom(),
                        registre.organisme(),
                        registre.champs(),
                        registre.documents().entrySet().stream()
                                .map(d ->
                                        new TypeDocument(
                                                d.getKey(),
                                                d.getValue().titre(),
                                                d.getValue().champs()))
                                .toList());
    }

    @GetMapping("/api/registre")
    Description decrire() {
        return description;
    }
}
