import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { REDIRECTION, lireProbleme } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Champ,
  Dialogue,
  Etapes,
  GroupeFormulaire,
  Icone,
  Lien,
  Saisie,
  Squelette,
} from '@sira/ui';
import { firstValueFrom } from 'rxjs';
import { Fichier } from '../fichier/fichier';
import { ETAPES } from '../parcours/etapes';
import {
  IdentifiantSaisi,
  identifiantValide,
  normaliserIdentifiant,
} from '../parcours/identifiant';
import { libelleChamp, nomRegistre } from '../parcours/libelles';
import { Parcours } from '../parcours/parcours';

const ERREUR_FORMAT = 'Saisissez les 12 chiffres de votre identifiant unique.';

/**
 * Le formulaire de la démarche, vide : la personne ne saisit que son identifiant unique. Chaque
 * information dit quel registre la remplira, après l'accord donné dans Sira.
 */
@Component({
  selector: 'ps-demande',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    Bouton,
    Champ,
    Dialogue,
    Etapes,
    Fichier,
    GroupeFormulaire,
    Icone,
    Lien,
    Saisie,
    Squelette,
  ],
  templateUrl: './demande.html',
})
export class VotreDemande {
  private readonly parcours = inject(Parcours);
  private readonly memoire = inject(IdentifiantSaisi);
  private readonly router = inject(Router);
  private readonly rediriger = inject(REDIRECTION);

  protected readonly etapes = ETAPES;
  protected readonly nomRegistre = nomRegistre;
  protected readonly libelleChamp = libelleChamp;

  protected readonly demarche = rxResource({ stream: () => this.parcours.demarche() });
  protected readonly presentation = computed(() =>
    this.demarche.hasValue() ? this.demarche.value() : undefined,
  );

  /** Saisie en cours ; reprise de la mémoire quand la personne revient sur cet écran. */
  protected readonly saisie = signal(this.memoire.identifiant() ?? '');
  protected readonly erreurIdentifiant = signal<string | null>(null);
  protected readonly confirmation = signal(false);
  protected readonly envoi = signal(false);
  protected readonly probleme = signal<string | null>(null);

  protected suivant(evenement: SubmitEvent): void {
    evenement.preventDefault();
    const identifiant = normaliserIdentifiant(this.saisie());
    if (!identifiantValide(identifiant)) {
      this.erreurIdentifiant.set(ERREUR_FORMAT);
      document.getElementById('identifiant-unique')?.focus();
      return;
    }
    this.erreurIdentifiant.set(null);
    this.probleme.set(null);
    this.memoire.retenir(identifiant);
    this.confirmation.set(true);
  }

  /** « Continuer avec Sira » : l'accord se donne sur cet appareil. */
  protected async continuer(): Promise<void> {
    const identifiant = this.memoire.identifiant();
    if (!identifiant || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.probleme.set(null);
    try {
      const demande = await firstValueFrom(this.parcours.ouvrir('REDIRECTION', identifiant));
      if (demande.url_accord) {
        // La page change : le bouton reste en attente jusqu'au départ.
        this.rediriger(demande.url_accord);
        return;
      }
      await this.router.navigateByUrl('/accord');
    } catch (erreur) {
      const probleme = lireProbleme(erreur);
      const champ = probleme.champs['identifiant_unique'];
      if (champ) {
        this.confirmation.set(false);
        this.erreurIdentifiant.set(champ);
      } else {
        this.probleme.set(probleme.message);
      }
    }
    this.envoi.set(false);
  }
}
