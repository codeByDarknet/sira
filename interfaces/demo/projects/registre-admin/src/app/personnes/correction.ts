import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, FormRecord, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Session, lireProbleme } from '@sira/coquille';
import {
  Action,
  Alerte,
  Bouton,
  Champ,
  ErreurDeChamp,
  Fil,
  FiltreFrappe,
  GroupeFormulaire,
  Icone,
  ResumeErreurs,
  Saisie,
  Squelette,
} from '@sira/ui';
import { Observable, concatMap, forkJoin, from, last, map, of } from 'rxjs';
import { ChampDeFiche, Registre } from '../registre/registre';
import { DepotPieceDialogue } from './depot-piece';
import { DepotPiece, Fiche, Personne, Personnes } from './personnes';

@Component({
  selector: 'ra-correction',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Action,
    Alerte,
    Bouton,
    Champ,
    DepotPieceDialogue,
    Fil,
    FiltreFrappe,
    GroupeFormulaire,
    Icone,
    ResumeErreurs,
    Saisie,
    Squelette,
  ],
  templateUrl: './correction.html',
})
export class Correction {
  private readonly personnes = inject(Personnes);
  private readonly registre = inject(Registre);
  private readonly routeur = inject(Router);
  protected readonly session = inject(Session);

  /** Pièces choisies à l'ajout : enregistrées juste après la fiche. */
  protected readonly enAttente = signal<DepotPiece[]>([]);
  protected readonly depotOuvert = signal(false);

  /** Paramètre `:id` de la route (UUID) ; absent pour une nouvelle personne. */
  readonly id = input<string>();
  protected readonly creation = computed(() => !this.id());
  /** Identifiant d'une nouvelle personne : facultatif, le registre en attribue un sinon. */
  protected readonly nouvelIdentifiant = new FormControl('', { nonNullable: true });

  /**
   * La fiche, les champs que l'agent peut remplir et le formulaire prérempli. Pour une nouvelle
   * personne, tous les champs du registre sauf l'identifiant.
   */
  protected readonly donnees = rxResource({
    params: () => ({ id: this.id() ?? null }),
    stream: ({ params }) =>
      forkJoin({
        personne: params.id ? this.personnes.lire(params.id) : of<Personne | null>(null),
        registre: this.registre.description,
      }).pipe(
        map(({ personne, registre }) => {
          const champs = registre.champs.filter((champ) =>
            personne ? champ.modifiable : champ.nom !== IDENTIFIANT,
          );
          const saisies = champs.map((champ) => [
            champ.nom,
            new FormControl(enSaisie(personne?.fiche[champ.nom]), { nonNullable: true }),
          ]);
          return {
            personne,
            registre,
            champs,
            formulaire: new FormRecord<FormControl<string>>(Object.fromEntries(saisies)),
          };
        }),
      ),
  });
  protected readonly probleme = computed(() =>
    this.donnees.error() ? lireProbleme(this.donnees.error()).message : null,
  );

  protected readonly erreurs = signal<Record<string, string>>({});
  protected readonly resume = computed<ErreurDeChamp[]>(() =>
    Object.entries(this.erreurs())
      .filter(([champ]) => champ !== CHAMPS)
      .map(([champ, message]) => ({ champ, message })),
  );
  protected readonly echec = signal<string | null>(null);
  protected readonly envoi = signal(false);

  protected readonly choix = (champ: ChampDeFiche) => Object.entries(champ.choix);

  /** La vérification se fait à l'envoi, jamais pendant la frappe. */
  protected enregistrer(): void {
    const page = this.donnees.value();
    if (!page) {
      return;
    }
    const saisies = page.formulaire.getRawValue();
    const erreurs: Record<string, string> = {};
    const corrections: Fiche = {};
    const identifiant = this.nouvelIdentifiant.value.trim();
    if (!page.personne && identifiant && !/^\d{12}$/.test(identifiant)) {
      erreurs['identifiant'] = 'Saisissez douze chiffres, ou laissez vide.';
    }
    for (const champ of page.champs) {
      const lue = lire(champ, saisies[champ.nom].trim());
      if (lue.erreur) {
        erreurs[champ.nom] = lue.erreur;
      } else if (!page.personne || lue.valeur !== (page.personne.fiche[champ.nom] ?? null)) {
        corrections[champ.nom] = lue.valeur;
      }
    }
    this.erreurs.set(erreurs);
    this.echec.set(null);
    if (Object.keys(erreurs).length > 0) {
      afficherPremiereErreur(Object.keys(erreurs)[0]);
      return;
    }
    if (!page.personne) {
      const pieces = this.enAttente();
      this.envoyer(this.personnes.creer(corrections, identifiant || undefined), (cree) => {
        const aller = (message: string) =>
          void this.routeur.navigate(['/personnes', cree.id], { queryParams: { message } });
        if (pieces.length === 0) {
          aller('La personne a été ajoutée au registre.');
          return;
        }
        // Les pièces partent une à une : un fichier refusé n'empêche pas les autres.
        from(pieces)
          .pipe(
            concatMap((piece) => this.personnes.deposer(cree.id, piece)),
            last(),
          )
          .subscribe({
            next: () => aller('La personne et ses documents ont été ajoutés au registre.'),
            error: () =>
              aller('La personne a été ajoutée ; un document n’a pas pu être enregistré.'),
          });
      });
      return;
    }
    if (Object.keys(corrections).length === 0) {
      this.revenir('Aucune modification à enregistrer.');
      return;
    }
    this.envoyer(this.personnes.corriger(page.personne.id, corrections), () =>
      this.revenir('La fiche a été corrigée.'),
    );
  }

  private envoyer(envoi: Observable<Personne>, suite: (personne: Personne) => void): void {
    this.envoi.set(true);
    envoi.subscribe({
      next: suite,
      error: (erreur: unknown) => {
        const refus = lireProbleme(erreur);
        this.envoi.set(false);
        this.erreurs.set(refus.champs);
        if (this.resume().length === 0) {
          this.echec.set(refus.champs[CHAMPS] ?? refus.message);
        } else {
          afficherPremiereErreur(this.resume()[0].champ);
        }
      },
    });
  }

  protected ajouterEnAttente(piece: DepotPiece): void {
    this.enAttente.update((pieces) => [...pieces, piece]);
    this.depotOuvert.set(false);
  }

  protected retirerEnAttente(index: number): void {
    this.enAttente.update((pieces) => pieces.filter((_, i) => i !== index));
  }

  private revenir(message: string): void {
    void this.routeur.navigate(['/personnes', this.id()], { queryParams: { message } });
  }
}

const IDENTIFIANT = 'identifiant_unique';

/** Place le curseur dans le premier champ refusé : l'agent corrige sans chercher. */
function afficherPremiereErreur(champ: string): void {
  setTimeout(() => document.getElementById(champ)?.focus());
}

/** Nom sous lequel le serveur signale un refus qui ne porte sur aucun champ affiché. */
const CHAMPS = 'champs';

type Valeur = string | number | boolean | null;

function enSaisie(valeur: Valeur | undefined): string {
  return valeur === null || valeur === undefined ? '' : String(valeur).replace('.', ',');
}

/** Traduit la saisie dans le type attendu par le serveur, ou dit ce qui ne va pas. */
function lire(champ: ChampDeFiche, saisie: string): { valeur: Valeur; erreur?: string } {
  if (saisie === '') {
    return champ.facultatif
      ? { valeur: null }
      : { valeur: null, erreur: `Remplissez le champ « ${champ.libelle} ».` };
  }
  switch (champ.type) {
    case 'BOOLEEN':
      return { valeur: saisie === 'true' };
    case 'NOMBRE': {
      const nombre = Number(saisie.replace(',', '.'));
      const admis = Number.isFinite(nombre) && nombre >= champ.minimum && nombre <= champ.maximum;
      return admis
        ? { valeur: nombre }
        : {
            valeur: null,
            erreur: `Saisissez un nombre entre ${champ.minimum} et ${champ.maximum}.`,
          };
    }
    default:
      return { valeur: saisie };
  }
}
