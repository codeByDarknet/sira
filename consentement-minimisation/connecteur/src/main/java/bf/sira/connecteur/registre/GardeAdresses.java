package bf.sira.connecteur.registre;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Le connecteur n'appelle pas n'importe quelle adresse écrite dans un fichier de raccordement
 * (protection contre la falsification de requêtes côté serveur). Un hôte est admis s'il figure dans
 * la liste {@code hotes-permis} que tient l'exploitant, ou s'il ne désigne que des adresses
 * publiques : jamais la machine elle-même, le réseau privé ou les métadonnées d'un hébergeur.
 *
 * <p>Le contrôle a lieu au démarrage. Un nom qui changerait d'adresse ensuite (« rebinding ») n'est
 * pas couvert : pour un registre hors de la liste, l'exploitant écrit une adresse publique stable.
 */
final class GardeAdresses {

    private GardeAdresses() {}

    /**
     * @throws IllegalStateException si l'hôte n'est pas admis
     */
    static void verifier(URI adresse, List<String> hotesPermis) {
        String hote = adresse.getHost().toLowerCase(Locale.ROOT);
        if (hotesPermis.stream().anyMatch(permis -> permis.toLowerCase(Locale.ROOT).equals(hote))) {
            return;
        }
        InetAddress[] adresses;
        try {
            adresses = InetAddress.getAllByName(hote);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("hôte du registre inconnu : " + hote);
        }
        for (InetAddress ip : adresses) {
            if (!publique(ip)) {
                throw new IllegalStateException(
                        "hôte du registre refusé (adresse interne) : "
                                + hote
                                + " ; l'ajouter à sira.connecteur.registre.hotes-permis s'il est"
                                + " voulu");
            }
        }
    }

    static boolean publique(InetAddress ip) {
        if (ip.isAnyLocalAddress()
                || ip.isLoopbackAddress()
                || ip.isLinkLocalAddress()
                || ip.isSiteLocalAddress()
                || ip.isMulticastAddress()) {
            return false;
        }
        byte[] octets = ip.getAddress();
        if (ip instanceof Inet4Address) {
            int premier = octets[0] & 0xff;
            int second = octets[1] & 0xff;
            // 0/8, 100.64/10 (réseau d'opérateur), 192.0.0/24, 198.18/15 (essais), 240/4.
            return premier != 0
                    && !(premier == 100 && second >= 64 && second < 128)
                    && !(premier == 192 && second == 0 && (octets[2] & 0xff) == 0)
                    && !(premier == 198 && (second == 18 || second == 19))
                    && premier < 240;
        }
        if (ip instanceof Inet6Address) {
            // fc00::/7 : adresses locales uniques. Une adresse IPv4 transposée (::ffff:a.b.c.d)
            // arrive ici déjà convertie en Inet4Address par le JDK.
            return (octets[0] & 0xfe) != 0xfc;
        }
        return false;
    }
}
