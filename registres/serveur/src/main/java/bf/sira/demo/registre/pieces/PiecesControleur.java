package bf.sira.demo.registre.pieces;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.demo.registre.configuration.ProprietesRegistre;
import bf.sira.demo.registre.personnes.DepotPersonnes;
import bf.sira.demo.registre.personnes.Personne;
import bf.sira.demo.registre.pieces.DepotPieces.Contenu;
import bf.sira.demo.registre.pieces.DepotPieces.Information;
import bf.sira.demo.registre.pieces.DepotPieces.Piece;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pièces déposées pour une personne : un fichier (PDF, PNG ou JPEG, 5 Mo au plus), son type, son
 * titre et les informations du registre qu'il contient. Le type de fichier est vérifié sur ses
 * premiers octets, pas sur ce que le navigateur annonce.
 */
@RestController
@RequestMapping("/api/personnes/{personne}/pieces")
class PiecesControleur {

    private static final long TAILLE_MAX = 5L * 1024 * 1024;
    private static final String TITRE = "[\\p{L}\\p{M}\\p{N} '’.,/()°-]{2,100}";

    private static final String LIBELLE = "[\\p{L}\\p{M}\\p{N} '’.,/()°-]{2,60}";
    private static final String TEXTE_LIBRE = "[^\\p{Cntrl}]{1,200}";
    private static final int INFORMATIONS_MAX = 20;

    private final DepotPieces pieces;
    private final DepotPersonnes personnes;
    private final ProprietesRegistre registre;
    private final JsonMapper json;

    PiecesControleur(
            DepotPieces pieces,
            DepotPersonnes personnes,
            ProprietesRegistre registre,
            JsonMapper json) {
        this.pieces = pieces;
        this.personnes = personnes;
        this.registre = registre;
        this.json = json;
    }

    @GetMapping
    List<Piece> lister(@PathVariable UUID personne) {
        return pieces.lister(existante(personne).id());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    List<Piece> deposer(
            @PathVariable UUID personne,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam("titre") String titre,
            @RequestParam(value = "champs", required = false) List<String> champs,
            @RequestParam(value = "informations", required = false) String informations,
            @AuthenticationPrincipal OidcUser agent)
            throws IOException {
        Personne cible = existante(personne);
        String titreLu = titre == null ? "" : titre.strip();
        if (!titreLu.matches(TITRE)) {
            throw new SaisieRefusee("titre", "Saisissez le nom du document (2 à 100 caractères).");
        }
        String typeLu =
                type == null || type.isBlank()
                        ? code(titreLu)
                        : type.strip().toLowerCase(Locale.ROOT);
        if (!typeLu.matches("[a-z0-9]+(-[a-z0-9]+)*") || typeLu.length() > 60) {
            throw new SaisieRefusee("type", "Choisissez le type du document.");
        }
        List<Information> libres = informations(informations);
        List<String> champsLus = champs == null ? List.of() : champs;
        if (!champsLus.stream().allMatch(c -> registre.champ(c).isPresent())) {
            throw new SaisieRefusee("champs", "Une des informations choisies n’existe pas.");
        }
        if (fichier.isEmpty() || fichier.getSize() > TAILLE_MAX) {
            throw new SaisieRefusee("fichier", "Choisissez un fichier de 5 Mo au plus.");
        }
        byte[] octets = fichier.getBytes();
        String typeMime = typeReel(octets);
        if (typeMime == null) {
            throw new SaisieRefusee("fichier", "Le fichier doit être un PDF, un PNG ou un JPEG.");
        }
        String nom = nomFichier(fichier.getOriginalFilename(), typeMime);
        pieces.ajouter(
                cible.id(),
                typeLu,
                titreLu,
                champsLus.stream().distinct().toList(),
                libres,
                nom,
                typeMime,
                octets,
                agent == null ? "agent" : agent.getPreferredUsername());
        return pieces.lister(cible.id());
    }

    @GetMapping("/{piece}")
    ResponseEntity<byte[]> lire(@PathVariable UUID personne, @PathVariable UUID piece) {
        Contenu contenu =
                pieces.contenu(existante(personne).id(), piece)
                        .orElseThrow(ErreurMetier::introuvable);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenu.typeMime()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(contenu.nomFichier())
                                .build()
                                .toString())
                .body(contenu.octets());
    }

    @DeleteMapping("/{piece}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimer(@PathVariable UUID personne, @PathVariable UUID piece) {
        if (!pieces.supprimer(existante(personne).id(), piece)) {
            throw ErreurMetier.introuvable();
        }
    }

    /** Au plus 20 informations libres, chacune vérifiée selon son type. */
    private List<Information> informations(String texte) {
        if (texte == null || texte.isBlank()) {
            return List.of();
        }
        List<Information> lues;
        try {
            lues = json.readValue(texte, DepotPieces.INFORMATIONS);
        } catch (JacksonException e) {
            throw new SaisieRefusee(
                    "informations", "Les informations du document sont illisibles.");
        }
        if (lues.size() > INFORMATIONS_MAX) {
            throw new SaisieRefusee("informations", "20 informations au plus par document.");
        }
        return lues.stream().map(PiecesControleur::verifiee).toList();
    }

    /** Libellé de 2 à 60 caractères, valeur conforme à son type. */
    static Information verifiee(Information information) {
        String libelle = information.libelle() == null ? "" : information.libelle().strip();
        String valeur = information.valeur() == null ? "" : information.valeur().strip();
        String type = information.type() == null ? "" : information.type();
        if (!libelle.matches(LIBELLE)) {
            throw new SaisieRefusee(
                    "informations", "Chaque information a un libellé de 2 à 60 caractères.");
        }
        boolean correcte =
                switch (type) {
                    case "TEXTE" -> valeur.matches(TEXTE_LIBRE);
                    case "DATE" -> estDate(valeur);
                    case "NOMBRE" -> valeur.matches("-?[0-9]{1,12}([.,][0-9]{1,4})?");
                    case "BOOLEEN" -> valeur.equals("true") || valeur.equals("false");
                    default -> false;
                };
        if (!correcte) {
            throw new SaisieRefusee(
                    "informations",
                    "« " + libelle + " » : la valeur ne correspond pas à son type.");
        }
        return new Information(libelle, type, valeur);
    }

    private static boolean estDate(String valeur) {
        try {
            LocalDate.parse(valeur);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private Personne existante(UUID id) {
        return personnes.lireParId(id).orElseThrow(ErreurMetier::introuvable);
    }

    /** Le vrai type du fichier, lu sur ses premiers octets ; null s'il n'est pas admis. */
    static String typeReel(byte[] octets) {
        if (commence(octets, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        if (commence(octets, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (commence(octets, 0xFF, 0xD8, 0xFF)) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return null;
    }

    private static boolean commence(byte[] octets, int... signature) {
        if (octets.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((octets[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Un nom de fichier sûr (lettres, chiffres, tirets), avec l'extension de son vrai type. */
    private static String nomFichier(String nom, String typeMime) {
        String base = nom == null ? "" : nom.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        base =
                Normalizer.normalize(base, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replaceAll("[^A-Za-z0-9-]+", "-")
                        .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) {
            base = "piece";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        String extension =
                switch (typeMime) {
                    case MediaType.APPLICATION_PDF_VALUE -> ".pdf";
                    case MediaType.IMAGE_PNG_VALUE -> ".png";
                    default -> ".jpg";
                };
        return base + extension;
    }

    private static String code(String titre) {
        return Normalizer.normalize(titre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
