package bf.sira.demo.service.dossiers;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.service.configuration.ProprietesFormulaire;
import bf.sira.demo.service.configuration.ProprietesFormulaire.Nature;
import bf.sira.demo.service.configuration.SaisieRefusee;
import bf.sira.kit.EtatElement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Ce que la personne déclare elle-même, à côté de ce que les registres attestent. Une information
 * reçue d'un registre, ou encore attendue, ne se déclare jamais : seule une information que le
 * registre n'a définitivement pas transmise peut être remplacée par une pièce. Rien ne change après
 * le dépôt. Les valeurs et les pièces ne sont jamais journalisées.
 */
@Service
public class Declarations {

    /** 2 Mio : la borne des pièces jointes, et celle du corps de la requête qui les porte. */
    public static final int TAILLE_MAX = 2 * 1024 * 1024;

    static final int LONGUEUR_MAX = 120;

    private static final byte[] SIGNATURE_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern CARACTERES_DE_CONTROLE = Pattern.compile("\\p{Cntrl}");
    private static final Pattern HORS_NOM_DE_FICHIER = Pattern.compile("[^\\p{L}\\p{N} ._()-]+");

    /** Réponses définitives sans attestation : la personne peut fournir la pièce elle-même. */
    private static final Set<EtatElement> NON_TRANSMIS =
            Set.of(EtatElement.REFUSE, EtatElement.INCONNU, EtatElement.EXPIRE);

    private record Cible(String libelle, Nature nature, boolean remplace) {}

    private final DepotDeclarations depot;
    private final DepotDossiers dossiers;
    private final ProprietesFormulaire formulaire;

    Declarations(DepotDeclarations depot, DepotDossiers dossiers, ProprietesFormulaire formulaire) {
        this.depot = depot;
        this.dossiers = dossiers;
        this.formulaire = formulaire;
    }

    /** Enregistre une saisie libre. */
    public void saisir(UUID dossier, String code, String saisie) {
        Cible cible = cible(dossier, code);
        if (cible.nature() != Nature.TEXTE) {
            throw new SaisieRefusee("valeur", "Cette information se joint en PDF.");
        }
        String valeur = saisie == null ? "" : saisie.strip();
        if (valeur.isEmpty()) {
            throw new SaisieRefusee("valeur", "Saisissez une valeur, ou retirez l'information.");
        }
        if (valeur.length() > LONGUEUR_MAX) {
            throw new SaisieRefusee("valeur", "120 caractères au plus.");
        }
        if (CARACTERES_DE_CONTROLE.matcher(valeur).find()) {
            throw new SaisieRefusee("valeur", "Cette saisie contient des caractères non admis.");
        }
        ecrire(dossier, code, cible, valeur, null, null);
    }

    /**
     * Joint une pièce PDF. Le flux n'est lu que jusqu'à la borne : un fichier plus gros est refusé
     * sans être chargé en entier.
     *
     * @param longueur taille annoncée par la requête, ou -1 si elle ne l'annonce pas
     */
    public void joindre(UUID dossier, String code, String nom, long longueur, InputStream flux)
            throws IOException {
        Cible cible = cible(dossier, code);
        if (cible.nature() != Nature.PIECE) {
            throw new SaisieRefusee("piece", "Cette information se saisit : elle ne se joint pas.");
        }
        if (longueur > TAILLE_MAX) {
            throw tropVolumineuse();
        }
        byte[] octets = flux.readNBytes(TAILLE_MAX + 1);
        if (octets.length > TAILLE_MAX) {
            throw tropVolumineuse();
        }
        if (octets.length < SIGNATURE_PDF.length
                || !Arrays.equals(
                        octets, 0, SIGNATURE_PDF.length, SIGNATURE_PDF, 0, SIGNATURE_PDF.length)) {
            throw new SaisieRefusee("piece", "Le fichier n'est pas un PDF.");
        }
        ecrire(dossier, code, cible, null, nomDeFichier(nom), octets);
    }

    /** Retire une déclaration ; sans effet si elle n'existait pas. */
    public void retirer(UUID dossier, String code) {
        cible(dossier, code);
        if (!depot.retirer(dossier, code)) {
            throw depose();
        }
    }

    /** Dépose le dossier : ce que la personne a déclaré ne change plus. */
    public void deposer(UUID dossier) {
        if (!dossiers.deposer(dossier)) {
            throw depose();
        }
    }

    /**
     * Ce que désigne le code : une information du formulaire du service, ou un jeu du dossier que
     * le registre n'a pas transmis. Un code inconnu répond comme un objet absent.
     */
    private Cible cible(UUID dossier, String code) {
        ProprietesFormulaire.Declaration declaree = formulaire.declaration(code);
        if (declaree != null) {
            return new Cible(declaree.libelle(), declaree.nature(), false);
        }
        Verification verification =
                dossiers.verifications(dossier).stream()
                        .filter(v -> v.jeu().equals(code))
                        .findFirst()
                        .orElseThrow(ErreurMetier::introuvable);
        if (!NON_TRANSMIS.contains(verification.etat())) {
            throw new ErreurMetier(
                    HttpStatus.CONFLICT,
                    "information-du-registre",
                    "Information transmise par un registre",
                    "Cette information vient d'un registre : elle ne se déclare pas.");
        }
        return new Cible(verification.libelle(), Nature.PIECE, true);
    }

    private void ecrire(
            UUID dossier, String code, Cible cible, String valeur, String nom, byte[] piece) {
        if (!depot.ecrire(
                dossier,
                code,
                cible.libelle(),
                cible.nature(),
                cible.remplace(),
                valeur,
                nom,
                piece)) {
            throw depose();
        }
    }

    /**
     * Le nom gardé pour la pièce : sans chemin, sans caractère de contrôle ni séparateur, terminé
     * par « .pdf ». Il ne sert qu'à l'affichage et au téléchargement.
     */
    static String nomDeFichier(String nom) {
        String brut = nom == null ? "" : Normalizer.normalize(nom, Normalizer.Form.NFC);
        brut = brut.substring(Math.max(brut.lastIndexOf('/'), brut.lastIndexOf('\\')) + 1);
        String propre = HORS_NOM_DE_FICHIER.matcher(brut).replaceAll("_").strip();
        propre = propre.replaceAll("^[._]+", "");
        if (propre.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            propre = propre.substring(0, propre.length() - 4);
        }
        if (propre.length() > 80) {
            propre = propre.substring(0, 80);
        }
        return (propre.isBlank() ? "piece" : propre.strip()) + ".pdf";
    }

    private static ErreurMetier tropVolumineuse() {
        return new ErreurMetier(
                HttpStatus.CONTENT_TOO_LARGE,
                "piece-trop-volumineuse",
                "Pièce trop volumineuse",
                "La pièce dépasse 2 Mo.");
    }

    private static ErreurMetier depose() {
        return new ErreurMetier(
                HttpStatus.CONFLICT,
                "dossier-depose",
                "Dossier déjà déposé",
                "Le dossier est déposé : ce que vous avez déclaré ne change plus.");
    }
}
