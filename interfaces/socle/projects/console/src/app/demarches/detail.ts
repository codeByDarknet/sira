import { Noms } from '../commun/noms';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Alerte, Bouton, Etiquette, Fil, Icone, Lien, Squelette } from '@sira/ui';
import { messageErreur, valeur } from '../commun/chargement';
import { Confirmation } from '../commun/confirmation';
import { Fenetre } from '../commun/fenetre';
import { libelleActif } from '../commun/libelles';
import { Droits } from '../roles';
import { Demarche, Demarches, phraseDureeAutorisation } from './demarches';
import { FormulaireDemarche } from './formulaire';

/** Fiche d'une démarche : chaque information avec ce qu'elle veut dire ; modifier, désactiver. */
@Component({
  selector: 'co-detail-demarche',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    Bouton,
    Confirmation,
    Etiquette,
    Fenetre,
    Fil,
    FormulaireDemarche,
    Icone,
    Lien,
    Squelette,
  ],
  templateUrl: './detail.html',
})
export class DetailDemarche {
  protected readonly noms = inject(Noms);
  private readonly demarches = inject(Demarches);
  protected readonly droits = inject(Droits);
  protected readonly libelleActif = libelleActif;
  protected readonly phraseDuree = phraseDureeAutorisation;

  readonly code = input.required<string>();

  protected readonly resultat = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.demarches.lire(params),
  });
  protected readonly fiche = computed(() => valeur(this.resultat));
  protected readonly probleme = computed(() => messageErreur(this.resultat));

  protected readonly modification = signal(false);
  protected readonly desactivation = signal(false);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => this.demarches.desactiver(this.code());

  protected apresModification(demarche: Demarche): void {
    this.modification.set(false);
    this.message.set(`Démarche « ${demarche.libelle} » modifiée.`);
    this.resultat.reload();
  }

  protected apresDesactivation(): void {
    this.message.set('La démarche a été désactivée.');
    this.resultat.reload();
  }
}
