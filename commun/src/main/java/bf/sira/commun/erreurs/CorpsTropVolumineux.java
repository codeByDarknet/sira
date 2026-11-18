package bf.sira.commun.erreurs;

import java.io.IOException;

/** Le corps de la requête dépasse la taille autorisée. */
final class CorpsTropVolumineux extends IOException {

    CorpsTropVolumineux() {
        super("corps de requête trop volumineux");
    }
}
