package bf.sira.kit;

import bf.sira.commun.config.ProprietesCommun;
import bf.sira.commun.config.SiraAutoConfiguration;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.commun.pki.Pki;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/** Fournit {@link ClientSira} à tout service qui dispose de ses clés (modes complet et client). */
@AutoConfiguration(after = SiraAutoConfiguration.class)
@ConditionalOnBean(ClientsMtls.class)
public class KitAutoConfiguration {

    // Créé à la première injection : c'est alors que la clé de chiffrement est lue.
    @Bean
    @Lazy
    @ConditionalOnMissingBean
    ClientSira clientSira(
            ClientsMtls clients, ProprietesCommun proprietes, LecteurAttestation lecteur, Pki pki) {
        return new ClientSira(
                clients.client(proprietes.passerelle().url(), IdentiteMembre.PASSERELLE),
                lecteur,
                pki.chiffrement().certificat());
    }
}
