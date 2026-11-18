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
import { AssistantJeu } from './assistant';
import { Jeu, Jeux } from './jeux';
import { libelleNiveau, phraseContenu, phraseValidite } from './regle';

/** Fiche d'une question : chaque information avec ce qu'elle veut dire ; modifier, désactiver. */
@Component({
  selector: 'co-detail-jeu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    AssistantJeu,
    Bouton,
    Confirmation,
    Etiquette,
    Fenetre,
    Fil,
    Icone,
    Lien,
    Squelette,
  ],
  templateUrl: './detail.html',
})
export class DetailJeu {
  protected readonly noms = inject(Noms);
  private readonly jeux = inject(Jeux);
  protected readonly droits = inject(Droits);
  protected readonly libelleActif = libelleActif;
  protected readonly libelleNiveau = libelleNiveau;
  protected readonly phraseContenu = phraseContenu;
  protected readonly phraseValidite = phraseValidite;

  readonly code = input.required<string>();

  protected readonly resultat = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.jeux.lire(params),
  });
  protected readonly fiche = computed(() => valeur(this.resultat));
  protected readonly probleme = computed(() => messageErreur(this.resultat));

  protected readonly modification = signal(false);
  protected readonly desactivation = signal(false);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => this.jeux.desactiver(this.code());

  protected apresModification(jeu: Jeu): void {
    this.modification.set(false);
    this.message.set(`Question « ${jeu.libelle} » modifiée.`);
    this.resultat.reload();
  }

  protected apresDesactivation(): void {
    this.message.set('La question a été désactivée.');
    this.resultat.reload();
  }
}
