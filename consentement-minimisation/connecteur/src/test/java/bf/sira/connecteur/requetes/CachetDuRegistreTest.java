package bf.sira.connecteur.requetes;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.test.PkiDeTest;
import bf.sira.connecteur.ProprietesConnecteur;
import java.nio.file.Path;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CachetDuRegistreTest {

    @Test
    void unConnecteurMuniDuCachetDUnAutreRegistreNeDemarrePas() {
        Signataire cachetDuCasier =
                new Signataire(PkiDeTest.creer().cachet(IdentiteMembre.registre("casier")));
        ProprietesConnecteur etatCivil =
                new ProprietesConnecteur(
                        new ProprietesConnecteur.Registre(
                                "etat-civil",
                                "http://registre:8080",
                                null,
                                true,
                                java.util.List.of(),
                                null,
                                null,
                                null,
                                null),
                        Path.of("politique.yaml"),
                        ZoneId.of("Africa/Ouagadougou"),
                        ProprietesConnecteur.Appelant.PASSERELLE);

        assertThatThrownBy(() ->
                        new TraitementRequetes(
                                etatCivil,
                                cachetDuCasier,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                .isInstanceOf(IllegalStateException.class);
    }
}
