package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.openapi.ContratOpenApi;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Les routes de la passerelle sont celles de {@code passerelle/openapi.yaml} (plus {@code GET
 * /v1/admin/etat}, décrite par {@code resilience/openapi.yaml}). Les réponses réelles de tous les
 * tests de ce module sont contrôlées au passage (voir {@link PasserelleDeTest#client}) ; ce test en
 * fait passer quelques-unes de chaque couche pour s'assurer que le contrôle est bien branché.
 */
class ContratOpenApiTest extends PasserelleDeTest {

    @Test
    void lesRoutesDeclareesSontCellesDesControleurs() {
        Set<String> declarees = new TreeSet<>(SPEC_PASSERELLE.routes());
        declarees.addAll(SPEC_RESILIENCE.routes());
        assertThat(declarees).isEqualTo(ContratOpenApi.routesDesControleurs("bf.sira.passerelle"));
    }

    @Test
    void lesExemplesRespectentLeursSchemas() {
        assertThat(SPEC_PASSERELLE.validerExemples())
                .isGreaterThanOrEqualTo(SPEC_PASSERELLE.routes().size());
    }

    @Test
    void lesReponsesReellesDeChaqueCoucheSontControlees() {
        int passerelle = SPEC_PASSERELLE.reponsesVerifiees();
        int consentement = SPEC_CONSENTEMENT.reponsesVerifiees();
        int journal = SPEC_JOURNAL.reponsesVerifiees();
        int resilience = SPEC_RESILIENCE.reponsesVerifiees();
        var auditeur = console("issa", "auditeur");
        String personne = nouvellePersonne();

        reussi(lire(auditeur, "/v1/admin/jeux?niveau=STATUT"), 200);
        reussi(lire(auditeur, "/v1/admin/statistiques"), 200);
        lire(membre(CONCOURS), "/v1/demarches/demarche-inconnue");
        reussi(lire(citoyen(personne), "/v1/citoyen/resume"), 200);
        reussi(lire(citoyen(personne), "/v1/citoyen/journal"), 200);
        reussi(lire(auditeur, "/v1/admin/etat"), 200);
        lire(membre(IdentiteMembre.CONSOLE), "/v1/demandes/8d2f4b61-3c7e-4a90-b5d1-0e9f6a2c7b14");

        assertThat(SPEC_PASSERELLE.reponsesVerifiees() - passerelle).isEqualTo(4);
        assertThat(SPEC_CONSENTEMENT.reponsesVerifiees() - consentement).isEqualTo(1);
        assertThat(SPEC_JOURNAL.reponsesVerifiees() - journal).isEqualTo(1);
        assertThat(SPEC_RESILIENCE.reponsesVerifiees() - resilience).isEqualTo(1);
    }
}
