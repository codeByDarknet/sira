import { ChangeDetectionStrategy, Component, DOCUMENT, inject, signal } from '@angular/core';
import {
  Alerte,
  Bandeau,
  Bouton,
  Carte,
  Cellule,
  Champ,
  Code,
  ColonneTableau,
  DemandeAcces,
  DemandeAccesVue,
  Dialogue,
  ErreurDeChamp,
  Etapes,
  EtatVide,
  Etiquette,
  Fil,
  GroupeFormulaire,
  Icone,
  Lien,
  Minuteur,
  Onglet,
  Onglets,
  Pagination,
  Panneau,
  ResumeErreurs,
  Saisie,
  Squelette,
  Synthese,
  SyntheseLigne,
  Tableau,
} from '@sira/ui';

interface Acces {
  service: string;
  information: string;
  etat: 'actif' | 'retire';
  consultations: number;
}

/** Tous les composants de @sira/ui sur une page, pour les voir, les tester et les recopier. */
@Component({
  selector: 'vi-catalogue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Alerte,
    Bandeau,
    Bouton,
    Carte,
    Cellule,
    Champ,
    Code,
    DemandeAccesVue,
    Dialogue,
    Etapes,
    EtatVide,
    Etiquette,
    Fil,
    GroupeFormulaire,
    Icone,
    Lien,
    Minuteur,
    Onglets,
    Pagination,
    Panneau,
    ResumeErreurs,
    Saisie,
    Squelette,
    Synthese,
    SyntheseLigne,
    Tableau,
  ],
  templateUrl: './catalogue.html',
})
export class Catalogue {
  private readonly racine = inject(DOCUMENT).documentElement;

  protected readonly themes = [
    { id: 'socle', libelle: 'Socle (vert)' },
    { id: 'console', libelle: 'Console (bleu nuit, dense)' },
    { id: 'concours', libelle: 'Concours (bleu acier)' },
    { id: 'bourses', libelle: 'Bourses (prune)' },
    { id: 'entreprises', libelle: 'Entreprises (sarcelle)' },
    { id: 'etat-civil', libelle: 'État civil (terre)' },
    { id: 'nationalite', libelle: 'Nationalité (olive)' },
    { id: 'casier', libelle: 'Casier judiciaire (ardoise)' },
    { id: 'diplomes', libelle: 'Diplômes (violet)' },
  ];

  protected readonly erreurs: ErreurDeChamp[] = [
    { champ: 'catalogue-numero', message: 'Saisissez les 12 chiffres de votre numéro.' },
  ];

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'service', libelle: 'Service' },
    { cle: 'information', libelle: 'Information' },
    { cle: 'etat', libelle: 'État' },
    { cle: 'consultations', libelle: 'Consultations', numerique: true },
  ];
  protected readonly acces: Acces[] = [
    { service: 'Agence des concours', information: 'Nationalité', etat: 'actif', consultations: 2 },
    { service: 'Office des bourses', information: 'Diplôme', etat: 'actif', consultations: 1 },
    {
      service: 'Agence des concours',
      information: 'Casier judiciaire',
      etat: 'retire',
      consultations: 0,
    },
  ];

  protected readonly onglets: Onglet[] = [
    { id: 'actifs', libelle: 'Accès en cours' },
    { id: 'passes', libelle: 'Accès terminés' },
  ];

  protected readonly page = signal(2);
  protected readonly dialogue = signal(false);
  protected readonly decision = signal<string | null>(null);

  protected readonly echeance = new Date(Date.now() + 4 * 60_000);
  protected readonly demande: DemandeAcces = {
    demandeur: { nom: 'Agence des concours', organisme: 'Ministère de la fonction publique' },
    finalite: 'Vérifier que vous remplissez les conditions du concours des agents des impôts.',
    informations: [
      { libelle: 'Nationalité burkinabè', niveau: 'STATUT', source: 'Direction de la nationalité' },
      {
        libelle: 'État civil',
        niveau: 'CHAMPS',
        source: 'État civil',
        precision: 'Nom, prénoms, date et lieu de naissance.',
      },
      { libelle: 'Diplôme du baccalauréat', niveau: 'DOCUMENT', source: 'Office du baccalauréat' },
    ],
    dureeJours: 30,
    codeVerification: '492113',
    expireLe: this.echeance.toISOString(),
  };

  protected changerDeTheme(theme: string): void {
    this.racine.dataset['theme'] = theme;
  }
}
