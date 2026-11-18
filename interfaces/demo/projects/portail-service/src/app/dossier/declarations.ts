import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Synthese, SyntheseLigne } from '@sira/ui';
import { Fichier } from '../fichier/fichier';
import { tailleFichier } from '../parcours/libelles';
import { AdressesPiece, Declaration } from '../parcours/parcours';

/**
 * Ce que la personne a déclaré elle-même : aucun registre ne l'a vérifié, et l'écran le dit.
 * Une pièce qui remplace une information non transmise par un registre le précise.
 */
@Component({
  selector: 'ps-declarations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Fichier, Synthese, SyntheseLigne],
  template: `@if (textes().length > 0) {
      <dl siraSynthese>
        @for (ligne of textes(); track ligne.code) {
          <div siraSyntheseLigne [cle]="ligne.libelle">
            {{ ligne.valeur }}
            <span class="sira-secondaire sira-petit"> · {{ mention() }}</span>
          </div>
        }
      </dl>
    }
    <div class="sira-pile">
      @for (ligne of pieces(); track ligne.code) {
        <ps-fichier
          [nom]="ligne.libelle + ' · ' + ligne.nom"
          [details]="ligne.details"
          [apercu]="ligne.adresses.apercu"
          [telechargement]="ligne.adresses.telechargement"
        />
      }
    </div>`,
})
export class Declarations {
  readonly declarations = input.required<readonly Declaration[]>();
  readonly adressePiece = input.required<(code: string) => AdressesPiece>();
  /** « Déclaré par vous » pour la personne, « Déclaré par l'usager » pour l'agent. */
  readonly mention = input.required<string>();

  protected readonly textes = computed(() =>
    this.declarations().filter((declaration) => declaration.nature === 'TEXTE'),
  );
  protected readonly pieces = computed(() =>
    this.declarations().flatMap((declaration) =>
      declaration.piece
        ? [
            {
              code: declaration.code,
              libelle: declaration.libelle,
              nom: declaration.piece.nom,
              adresses: this.adressePiece()(declaration.code),
              details: [
                this.mention(),
                declaration.remplace ? 'remplace une information non transmise' : null,
                tailleFichier(declaration.piece.taille),
              ]
                .filter(Boolean)
                .join(' · '),
            },
          ]
        : [],
    ),
  );
}
