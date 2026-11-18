import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Session, lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, Carte, EtatVide, Icone, Squelette } from '@sira/ui';
import { compter } from '../commun/libelles';
import { suivre } from '../commun/rafraichissement';
import { ListeDemandes } from '../demandes/liste-demandes';
import { SaisieCode } from '../demandes/saisie-code';
import { Situations } from './resume';

/** Accueil de l'espace personnel : ce qui attend une réponse d'abord, puis le reste. */
@Component({
  selector: 'ec-tableau-de-bord',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    Bouton,
    Carte,
    EtatVide,
    Icone,
    ListeDemandes,
    SaisieCode,
    Squelette,
  ],
  templateUrl: './tableau-de-bord.html',
})
export class TableauDeBord {
  protected readonly session = inject(Session);
  private readonly situations = inject(Situations);
  private readonly suivi = suivre();

  protected readonly situation = rxResource({
    stream: () => this.suivi(() => this.situations.lire()),
  });
  protected readonly probleme = computed(() =>
    this.situation.error() ? lireProbleme(this.situation.error()).message : null,
  );
  /** Une ressource en erreur refuse qu'on lise sa valeur : on ne la lit que si elle en a une. */
  protected readonly enAttente = computed(() =>
    this.situation.hasValue() ? this.situation.value().demandes : [],
  );
  /** « Wendkuni Aïcha KABORÉ » : on salue par les prénoms, sans le nom de famille en capitales. */
  protected readonly prenom = computed(() => {
    const nom = this.session.utilisateur()?.nom ?? '';
    const prenoms = nom.split(/\s+/).filter((mot) => mot && mot !== mot.toLocaleUpperCase('fr'));
    return prenoms.join(' ') || nom;
  });
  protected readonly titreAttente = computed(() => {
    const nombre = this.enAttente().length;
    return `${compter(nombre, 'demande')} ${nombre > 1 ? 'attendent' : 'attend'} votre réponse`;
  });
}
