import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Champ,
  ErreurDeChamp,
  Fil,
  GroupeFormulaire,
  ResumeErreurs,
  Saisie,
} from '@sira/ui';
import { Demarches } from './demarches';

type NomDeChamp = 'nom' | 'organisme' | 'finalite' | 'duree_jours';

@Component({
  selector: 'vi-formulaire',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Alerte,
    Bouton,
    Champ,
    Fil,
    GroupeFormulaire,
    ResumeErreurs,
    Saisie,
  ],
  templateUrl: './formulaire.html',
})
export class Formulaire {
  private readonly demarches = inject(Demarches);
  private readonly routeur = inject(Router);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    nom: '',
    organisme: '',
    finalite: '',
    duree_jours: '',
  });

  /** Erreurs par champ : celles de la vérification locale, puis celles du serveur. */
  protected readonly erreurs = signal<Partial<Record<NomDeChamp, string>>>({});
  protected readonly resume = computed<ErreurDeChamp[]>(() =>
    Object.entries(this.erreurs()).map(([champ, message]) => ({ champ, message })),
  );
  protected readonly echec = signal<string | null>(null);
  protected readonly envoi = signal(false);

  /** La vérification se fait à l'envoi, jamais pendant la frappe. */
  protected enregistrer(): void {
    const saisie = this.formulaire.getRawValue();
    const erreurs = verifier(saisie);
    this.erreurs.set(erreurs);
    this.echec.set(null);
    if (Object.keys(erreurs).length > 0) {
      return;
    }
    this.envoi.set(true);
    const demarche = { ...saisie, nom: saisie.nom.trim(), duree_jours: Number(saisie.duree_jours) };
    this.demarches.creer(demarche).subscribe({
      next: () =>
        void this.routeur.navigate(['/exemple'], {
          queryParams: { message: 'La démarche a été ajoutée.' },
        }),
      error: (erreur: unknown) => {
        const probleme = lireProbleme(erreur);
        this.envoi.set(false);
        this.erreurs.set(probleme.champs);
        if (Object.keys(probleme.champs).length === 0) {
          this.echec.set(probleme.message);
        }
      },
    });
  }
}

function verifier(saisie: Record<NomDeChamp, string>): Partial<Record<NomDeChamp, string>> {
  const erreurs: Partial<Record<NomDeChamp, string>> = {};
  if (saisie.nom.trim().length < 3) {
    erreurs.nom = 'Saisissez le nom de la démarche, en 3 lettres au moins.';
  }
  if (!saisie.organisme.trim()) {
    erreurs.organisme = 'Saisissez le nom de l’organisme.';
  }
  if (!saisie.finalite.trim()) {
    erreurs.finalite = 'Expliquez pourquoi le service a besoin de ces informations.';
  }
  if (saisie.duree_jours === '') {
    erreurs.duree_jours = 'Choisissez la durée de l’accès.';
  }
  return erreurs;
}
