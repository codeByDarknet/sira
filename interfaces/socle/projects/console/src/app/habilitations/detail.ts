import { Noms } from '../commun/noms';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Alerte,
  Bouton,
  Champ,
  DatePipe,
  Dialogue,
  Etiquette,
  Fil,
  Icone,
  Lien,
  Saisie,
  Squelette,
} from '@sira/ui';
import { Annonces } from '../commun/annonces';
import { messageErreur, valeur } from '../commun/chargement';
import { Envoi, Erreurs } from '../commun/envoi';
import { Fenetre } from '../commun/fenetre';
import { Jeux } from '../jeux/jeux';
import { libelleNiveau } from '../jeux/regle';
import { Droits } from '../roles';
import { FormulaireHabilitation } from './formulaire';
import { Decision, EtatHabilitation, Habilitations, libelleEtat } from './habilitations';
import { gestesPermis } from './regles';

type Geste = 'soumission' | Decision;

interface DescriptionGeste {
  bouton: string;
  question: string;
  consequence: string;
  /** Le rejet et la suspension s'expliquent ; l'instructeur lira ce motif. */
  avecMotif: boolean;
  danger: boolean;
  annonce: string;
}

const GESTES: Record<Geste, DescriptionGeste> = {
  soumission: {
    bouton: 'Soumettre à l’autorité',
    question: 'Soumettre cette habilitation à l’autorité ?',
    consequence: 'Le dossier ne pourra plus être modifié.',
    avecMotif: false,
    danger: false,
    annonce: 'Habilitation soumise.',
  },
  approbation: {
    bouton: 'Approuver',
    question: 'Approuver cette habilitation ?',
    consequence: 'Le service pourra poser ces questions, avec l’accord du citoyen.',
    avecMotif: false,
    danger: false,
    annonce: 'Habilitation approuvée.',
  },
  rejet: {
    bouton: 'Rejeter',
    question: 'Rejeter cette habilitation ?',
    consequence: 'L’instructeur lira votre motif.',
    avecMotif: true,
    danger: true,
    annonce: 'Habilitation rejetée.',
  },
  suspension: {
    bouton: 'Suspendre',
    question: 'Suspendre cette habilitation ?',
    consequence: 'Les nouvelles demandes du service seront refusées aussitôt.',
    avecMotif: true,
    danger: true,
    annonce: 'Habilitation suspendue.',
  },
};

const MOTIF_MINIMAL = 10;

const EXPLICATIONS_ETAT: Record<EtatHabilitation, string> = {
  BROUILLON: 'En préparation : l’instructeur peut encore le modifier, puis le soumettre.',
  SOUMISE: 'Soumise : elle attend la décision d’une personne qui a le rôle d’autorité.',
  APPROUVEE: 'Approuvée : le service peut poser ces questions, avec l’accord du citoyen.',
  REJETEE: 'Rejetée : le service ne peut pas poser ces questions. Le dossier est clos.',
  SUSPENDUE: 'Suspendue : les nouvelles demandes du service sont refusées.',
};

@Component({
  selector: 'co-detail-habilitation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Alerte,
    Bouton,
    Champ,
    DatePipe,
    Dialogue,
    Etiquette,
    Fenetre,
    Fil,
    FormulaireHabilitation,
    Icone,
    Lien,
    Saisie,
    Squelette,
  ],
  templateUrl: './detail.html',
})
export class DetailHabilitation {
  protected readonly noms = inject(Noms);
  private readonly habilitations = inject(Habilitations);
  private readonly droits = inject(Droits);
  protected readonly libelleEtat = libelleEtat;
  protected readonly libelleNiveau = libelleNiveau;
  protected readonly gestes = GESTES;

  readonly id = input.required<string>();

  protected readonly resultat = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.habilitations.lire(params),
  });
  protected readonly fiche = computed(() => valeur(this.resultat));
  protected readonly probleme = computed(() => messageErreur(this.resultat));

  private readonly catalogueJeux = inject(Jeux);
  private readonly publiees = rxResource({ stream: () => this.catalogueJeux.tous() });
  /** Chaque question avec son libellé ; si le catalogue manque, le code seul reste lisible. */
  protected readonly questions = computed(() => {
    const catalogue = valeur(this.publiees) ?? [];
    return (this.fiche()?.jeux ?? []).map((code) => ({
      code,
      jeu: catalogue.find((jeu) => jeu.code === code),
    }));
  });

  protected readonly permis = computed(() => {
    const habilitation = this.fiche();
    return habilitation
      ? gestesPermis(habilitation, {
          nom: this.droits.nom(),
          ecriture: this.droits.ecriture(),
          decision: this.droits.decision(),
        })
      : null;
  });

  protected readonly annonce = signal(inject(Annonces).prendre());
  protected readonly geste = signal<Geste>('soumission');
  protected readonly ouvert = signal(false);
  protected readonly motif = inject(NonNullableFormBuilder).control('');
  protected readonly envoi = new Envoi();
  protected readonly modification = signal(false);

  protected explicationEtat(etat: EtatHabilitation): string {
    return EXPLICATIONS_ETAT[etat];
  }

  protected apresModification(): void {
    this.modification.set(false);
    this.annonce.set('Brouillon modifié. Relisez-le, puis soumettez-le à l’autorité.');
    this.resultat.reload();
  }

  protected proposer(geste: Geste): void {
    this.geste.set(geste);
    this.motif.reset();
    this.envoi.erreurs.set({});
    this.envoi.echec.set(null);
    this.ouvert.set(true);
  }

  protected confirmer(): void {
    const geste = this.geste();
    const motif = this.motif.value.trim();
    const erreurs: Erreurs =
      GESTES[geste].avecMotif && motif.length < MOTIF_MINIMAL
        ? { motif: 'Expliquez votre décision en une phrase.' }
        : {};
    this.envoi.lancer(
      erreurs,
      () =>
        geste === 'soumission'
          ? this.habilitations.soumettre(this.id())
          : this.habilitations.decider(this.id(), geste, motif),
      (habilitation) => {
        this.ouvert.set(false);
        this.annonce.set(GESTES[geste].annonce);
        this.resultat.set(habilitation);
      },
    );
  }
}
