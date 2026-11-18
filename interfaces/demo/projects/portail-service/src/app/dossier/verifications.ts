import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Carte, DatePipe, Etiquette, Synthese, SyntheseLigne } from '@sira/ui';
import { Fichier } from '../fichier/fichier';
import {
  libelleChamp,
  mentionVerification,
  nomRegistre,
  recue,
  tailleFichier,
  valeurChamp,
} from '../parcours/libelles';
import { AdressesPiece, Verification } from '../parcours/parcours';

/**
 * Les conditions d'un dossier et ce que chaque registre a répondu. Le citoyen et l'agent voient
 * la même chose : uniquement ce que le service a reçu, en lecture seule.
 */
@Component({
  selector: 'ps-verifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Carte, DatePipe, Etiquette, Fichier, Synthese, SyntheseLigne],
  templateUrl: './verifications.html',
})
export class Verifications {
  readonly verifications = input.required<readonly Verification[]>();
  /** Donne les adresses de la pièce reçue pour une condition. */
  readonly adressePiece = input.required<(jeu: string) => AdressesPiece>();

  protected readonly lignes = computed(() =>
    this.verifications().map((verification) => ({
      verification,
      mention: mentionVerification(verification),
      registre: nomRegistre(verification.registre),
      verifiee: recue(verification),
      adresses: verification.piece ? this.adressePiece()(verification.jeu) : null,
      taille: verification.piece ? tailleFichier(verification.piece.taille) : '',
      champs: Object.entries(verification.champs ?? {}).map(([cle, valeur]) => ({
        libelle: libelleChamp(cle),
        valeur: valeurChamp(valeur),
      })),
    })),
  );
}
