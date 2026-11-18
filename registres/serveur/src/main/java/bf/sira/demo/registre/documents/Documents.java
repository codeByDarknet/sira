package bf.sira.demo.registre.documents;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.registre.configuration.ProprietesRegistre;
import bf.sira.demo.registre.configuration.ProprietesRegistre.Champ;
import bf.sira.demo.registre.personnes.DepotPersonnes;
import bf.sira.demo.registre.personnes.Fiches;
import bf.sira.demo.registre.personnes.Personne;
import bf.sira.demo.registre.pieces.DepotPieces;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Les documents que délivre le registre : la pièce déposée par un agent pour la personne s'il en
 * existe une, sinon un document produit à la demande à partir de la fiche. Volontairement sobres :
 * ni armoiries ni emblème.
 */
@Component
public class Documents {

    private static final String PAYS = "BURKINA FASO";
    private static final String DEVISE = "La Patrie ou la Mort, nous Vaincrons";
    private static final Color ENCRE = new Color(0x1B, 0x1F, 0x23);
    private static final Color DISCRET = new Color(0x5A, 0x62, 0x6B);
    private static final Color FILET = new Color(0xD5, 0xD9, 0xDD);
    private static final float MARGE = 56;

    private static final Font POLICE_MENTION = police(Font.BOLD, 10, ENCRE);
    private static final Font POLICE_ORGANISME = police(Font.NORMAL, 10, DISCRET);
    private static final Font POLICE_TITRE = police(Font.BOLD, 20, ENCRE);
    private static final Font POLICE_LIBELLE = police(Font.NORMAL, 10, DISCRET);
    private static final Font POLICE_VALEUR = police(Font.BOLD, 11, ENCRE);
    private static final Font POLICE_TEXTE = police(Font.NORMAL, 10, ENCRE);

    private final ProprietesRegistre registre;
    private final DepotPersonnes personnes;
    private final Fiches fiches;
    private final Clock horloge;
    private final DepotPieces pieces;

    Documents(
            ProprietesRegistre registre,
            DepotPersonnes personnes,
            Fiches fiches,
            Clock horloge,
            DepotPieces pieces) {
        this.pieces = pieces;
        this.registre = registre;
        this.personnes = personnes;
        this.fiches = fiches;
        this.horloge = horloge;
    }

    /**
     * Le document d'une personne, prêt à être servi.
     *
     * @throws ErreurMetier 404 si la personne ou le type de document n'existe pas dans ce registre
     */
    public ResponseEntity<byte[]> reponse(String identifiant, String type) {
        ProprietesRegistre.Document modele = registre.documents().get(type);
        if (modele == null) {
            throw ErreurMetier.introuvable();
        }
        Personne personne = personnes.lire(identifiant).orElseThrow(ErreurMetier::introuvable);
        // Une pièce PDF déposée par un agent l'emporte sur le document produit.
        var depose = pieces.dernierPdf(identifiant, type);
        if (depose.isPresent()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(type + ".pdf").build().toString())
                    .body(depose.get().octets());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(type + ".pdf").build().toString())
                .body(produire(modele, personne));
    }

    private byte[] produire(ProprietesRegistre.Document modele, Personne personne) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document page = new Document(PageSize.A4, MARGE, MARGE, MARGE, MARGE);
        PdfWriter.getInstance(page, sortie);
        page.addTitle(modele.titre());
        page.addAuthor(registre.organisme());
        page.open();

        page.add(bandeau());
        page.add(paragraphe(registre.organisme(), POLICE_ORGANISME, 24));
        page.add(paragraphe(modele.titre(), POLICE_TITRE, 18));
        page.add(lignes(modele, personne));
        page.add(
                paragraphe(
                        "Délivré le %s par le registre « %s »."
                                .formatted(Fiches.date(LocalDate.now(horloge)), registre.nom()),
                        POLICE_TEXTE,
                        28));
        page.close();
        return sortie.toByteArray();
    }

    private static PdfPTable bandeau() {
        Paragraph texte = new Paragraph(PAYS, POLICE_MENTION);
        texte.setAlignment(Element.ALIGN_CENTER);
        Paragraph devise = new Paragraph(DEVISE, POLICE_LIBELLE);
        devise.setAlignment(Element.ALIGN_CENTER);
        PdfPCell cellule = new PdfPCell();
        cellule.addElement(texte);
        cellule.addElement(devise);
        cellule.setPaddingBottom(10);
        cellule.setBorder(Rectangle.BOTTOM);
        cellule.setBorderColor(ENCRE);
        cellule.setBorderWidth(1);
        PdfPTable bandeau = new PdfPTable(1);
        bandeau.setWidthPercentage(100);
        bandeau.addCell(cellule);
        return bandeau;
    }

    private PdfPTable lignes(ProprietesRegistre.Document modele, Personne personne) {
        PdfPTable tableau = new PdfPTable(new float[] {2, 3});
        tableau.setWidthPercentage(100);
        tableau.setSpacingBefore(20);
        for (String nom : modele.champs()) {
            Champ champ = registre.champ(nom).orElseThrow();
            tableau.addCell(cellule(champ.libelle(), POLICE_LIBELLE));
            tableau.addCell(
                    cellule(fiches.afficher(champ, personne.fiche().get(nom)), POLICE_VALEUR));
        }
        return tableau;
    }

    private static PdfPCell cellule(String texte, Font police) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police));
        cellule.setBorder(Rectangle.BOTTOM);
        cellule.setBorderColor(FILET);
        cellule.setPaddingTop(8);
        cellule.setPaddingBottom(9);
        cellule.setPaddingLeft(0);
        return cellule;
    }

    private static Paragraph paragraphe(String texte, Font police, float espaceAvant) {
        Paragraph paragraphe = new Paragraph(texte, police);
        paragraphe.setSpacingBefore(espaceAvant);
        return paragraphe;
    }

    /** Police standard du format PDF, sans fichier à embarquer ; CP1252 couvre le français. */
    private static Font police(int style, float taille, Color couleur) {
        String nom = style == Font.BOLD ? BaseFont.HELVETICA_BOLD : BaseFont.HELVETICA;
        try {
            BaseFont base = BaseFont.createFont(nom, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            return new Font(base, taille, Font.NORMAL, couleur);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
