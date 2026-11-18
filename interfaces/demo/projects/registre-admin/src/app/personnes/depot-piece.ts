import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Action, Alerte, Bouton, Champ, Dialogue, FiltreFrappe, Icone, Saisie } from '@sira/ui';
import { ChampDeFiche, DocumentDuRegistre } from '../registre/registre';
import { DepotPiece, InformationLibre } from './personnes';

const AUTRE = '';
const TAILLE_MAX = 5 * 1024 * 1024;
const TYPES_ADMIS = ['application/pdf', 'image/png', 'image/jpeg'];

const TYPES_INFO: { valeur: InformationLibre['type']; libelle: string }[] = [
  { valeur: 'TEXTE', libelle: 'Texte' },
  { valeur: 'DATE', libelle: 'Date' },
  { valeur: 'NOMBRE', libelle: 'Nombre' },
  { valeur: 'BOOLEEN', libelle: 'Oui ou non' },
];

interface LigneInformation extends InformationLibre {
  cle: number;
}

/** Mêmes règles que le serveur : libellé de 2 à 60 caractères, valeur conforme au type. */
function informationValide({ libelle, type, valeur }: InformationLibre): boolean {
  if (libelle.length < 2 || libelle.length > 60) {
    return false;
  }
  switch (type) {
    case 'DATE':
      return /^\d{4}-\d{2}-\d{2}$/.test(valeur);
    case 'NOMBRE':
      return /^-?\d{1,12}([.,]\d{1,4})?$/.test(valeur);
    case 'BOOLEEN':
      return valeur === 'true' || valeur === 'false';
    default:
      return valeur.length > 0 && valeur.length <= 200;
  }
}

/**
 * Fenêtre « Ajouter un document » : le type (un document du registre, ou un autre), son nom, les
 * informations du registre qu'il contient, puis le fichier (PDF, PNG ou JPEG, 5 Mo au plus).
 * Rien n'est envoyé ici : l'écran hôte reçoit le choix par `valide`.
 */
@Component({
  selector: 'ra-depot-piece',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, Action, Alerte, Bouton, Champ, Dialogue, FiltreFrappe, Icone, Saisie],
  template: `<sira-dialogue
    titre="Ajouter un document"
    [ouvert]="true"
    (ouvertChange)="$event || annule.emit()"
  >
    @if (echec(); as message) {
      <sira-alerte type="erreur" titre="Le document n’a pas été ajouté">{{ message }}</sira-alerte>
    }
    <form class="ra-formulaire" id="form-piece" novalidate (ngSubmit)="valider()">
      <sira-champ
        obligatoire
        libelle="Type de document"
        identifiant="piece-type"
        [libelleFlottant]="false"
      >
        <select siraSaisie name="type" [ngModel]="type()" (ngModelChange)="choisirType($event)">
          @for (document of documents(); track document.type) {
            <option [value]="document.type">{{ document.titre }}</option>
          }
          <option [value]="autre">Autre document</option>
        </select>
      </sira-champ>
      <sira-champ
        obligatoire
        libelle="Nom du document"
        identifiant="piece-titre"
        [libelleFlottant]="false"
        [erreur]="erreurs()['titre']"
      >
        <input siraSaisie name="titre" maxlength="100" autocomplete="off" [(ngModel)]="titre" />
      </sira-champ>
      <fieldset class="ra-droits">
        <legend>Informations contenues dans le document</legend>
        <div class="ra-droits__groupe">
          @for (champ of champs(); track champ.nom) {
            <label class="sira-choix">
              <input
                type="checkbox"
                [checked]="coches().has(champ.nom)"
                (change)="cocher(champ.nom, $any($event.target).checked)"
              />
              {{ champ.libelle }}
            </label>
          }
        </div>
      </fieldset>
      @if (type() === autre) {
        <fieldset class="ra-infos ra-formulaire__plein">
          <legend>Autres informations du document</legend>
          <p class="sira-secondaire ra-infos__aide">
            Ce que ce document contient en plus (numéro de jugement, date de délivrance…). Ces
            informations restent dans le registre : seules celles cochées ci-dessus peuvent être
            transmises par Sira.
          </p>
          @for (ligne of informations(); track ligne.cle; let rang = $index) {
            <div class="ra-infos__ligne">
              <sira-champ
                libelle="Libellé"
                [identifiant]="'info-libelle-' + rang"
                [libelleFlottant]="false"
              >
                <input
                  siraSaisie
                  maxlength="60"
                  autocomplete="off"
                  [name]="'info-libelle-' + rang"
                  [(ngModel)]="ligne.libelle"
                />
              </sira-champ>
              <sira-champ
                libelle="Type"
                [identifiant]="'info-type-' + rang"
                [libelleFlottant]="false"
              >
                <select
                  siraSaisie
                  [name]="'info-type-' + rang"
                  [ngModel]="ligne.type"
                  (ngModelChange)="changerType(ligne, $event)"
                >
                  @for (choix of typesInfo; track choix.valeur) {
                    <option [value]="choix.valeur">{{ choix.libelle }}</option>
                  }
                </select>
              </sira-champ>
              <sira-champ
                libelle="Valeur"
                [identifiant]="'info-valeur-' + rang"
                [libelleFlottant]="false"
              >
                @switch (ligne.type) {
                  @case ('DATE') {
                    <input
                      siraSaisie
                      type="date"
                      [name]="'info-valeur-' + rang"
                      [(ngModel)]="ligne.valeur"
                    />
                  }
                  @case ('NOMBRE') {
                    <input
                      siraSaisie
                      siraFiltre="decimal"
                      inputmode="decimal"
                      maxlength="17"
                      autocomplete="off"
                      [name]="'info-valeur-' + rang"
                      [(ngModel)]="ligne.valeur"
                    />
                  }
                  @case ('BOOLEEN') {
                    <select siraSaisie [name]="'info-valeur-' + rang" [(ngModel)]="ligne.valeur">
                      <option value="true">Oui</option>
                      <option value="false">Non</option>
                    </select>
                  }
                  @default {
                    <input
                      siraSaisie
                      maxlength="200"
                      autocomplete="off"
                      [name]="'info-valeur-' + rang"
                      [(ngModel)]="ligne.valeur"
                    />
                  }
                }
              </sira-champ>
              <button
                type="button"
                siraAction="trash-2"
                [libelle]="'Retirer l’information ' + (rang + 1)"
                (click)="retirerInformation(ligne.cle)"
              ></button>
            </div>
          }
          @if (erreurs()['informations']; as erreur) {
            <p class="sira-champ__erreur">{{ erreur }}</p>
          }
          @if (informations().length < 20) {
            <button siraBouton variante="secondaire" type="button" (click)="ajouterInformation()">
              <sira-icone nom="plus" />
              Ajouter une information
            </button>
          }
        </fieldset>
      }
      <sira-champ
        obligatoire
        class="ra-formulaire__plein"
        libelle="Fichier"
        aide="PDF, PNG ou JPEG, 5 Mo au plus."
        identifiant="piece-fichier"
        [libelleFlottant]="false"
        [erreur]="erreurs()['fichier']"
      >
        <input
          siraSaisie
          type="file"
          name="fichier"
          accept="application/pdf,image/png,image/jpeg"
          (change)="choisirFichier($any($event.target).files)"
        />
      </sira-champ>
    </form>
    <button dialogue-actions siraBouton variante="secondaire" type="button" (click)="annule.emit()">
      Annuler
    </button>
    <button dialogue-actions siraBouton type="submit" form="form-piece" [enCours]="enCours()">
      Ajouter le document
    </button>
  </sira-dialogue>`,
})
export class DepotPieceDialogue {
  readonly documents = input.required<DocumentDuRegistre[]>();
  readonly champs = input.required<ChampDeFiche[]>();
  readonly enCours = input(false);
  readonly echec = input<string | null>(null);
  readonly valide = output<DepotPiece>();
  readonly annule = output();

  protected readonly autre = AUTRE;
  protected readonly type = signal<string>('');
  protected titre = '';
  protected readonly coches = signal<Set<string>>(new Set());
  protected readonly erreurs = signal<Record<string, string>>({});
  protected readonly typesInfo = TYPES_INFO;
  /** Lignes « libellé, type, valeur » d'un autre document ; `cle` sert au suivi de la liste. */
  protected readonly informations = signal<LigneInformation[]>([]);
  private prochaineCle = 0;
  private fichier: File | null = null;
  private readonly premier = computed(() => this.documents()[0]);

  constructor() {
    queueMicrotask(() => {
      const premier = this.premier();
      if (premier) {
        this.choisirType(premier.type);
      }
    });
  }

  /** Un document du registre préremplit son nom et les informations qu'il imprime. */
  protected choisirType(type: string): void {
    this.type.set(type);
    const document = this.documents().find((d) => d.type === type);
    this.titre = document?.titre ?? '';
    this.coches.set(new Set(document?.champs ?? []));
    if (type !== AUTRE) {
      this.informations.set([]);
    }
  }

  protected ajouterInformation(): void {
    this.informations.update((lignes) => [
      ...lignes,
      { cle: this.prochaineCle++, libelle: '', type: 'TEXTE', valeur: '' },
    ]);
  }

  protected retirerInformation(cle: number): void {
    this.informations.update((lignes) => lignes.filter((ligne) => ligne.cle !== cle));
  }

  /** Un autre type rend la valeur d'avant sans objet. */
  protected changerType(ligne: LigneInformation, type: InformationLibre['type']): void {
    ligne.type = type;
    ligne.valeur = type === 'BOOLEEN' ? 'true' : '';
    this.informations.update((lignes) => [...lignes]);
  }

  protected cocher(champ: string, coche: boolean): void {
    this.coches.update((coches) => {
      const suivants = new Set(coches);
      if (coche) {
        suivants.add(champ);
      } else {
        suivants.delete(champ);
      }
      return suivants;
    });
  }

  protected choisirFichier(fichiers: FileList | null): void {
    this.fichier = fichiers?.item(0) ?? null;
  }

  protected valider(): void {
    const erreurs: Record<string, string> = {};
    const titre = this.titre.trim();
    if (titre.length < 2) {
      erreurs['titre'] = 'Saisissez le nom du document. Exemple : Acte de mariage';
    }
    if (!this.fichier) {
      erreurs['fichier'] = 'Choisissez un fichier.';
    } else if (!TYPES_ADMIS.includes(this.fichier.type)) {
      erreurs['fichier'] = 'Le fichier doit être un PDF, un PNG ou un JPEG.';
    } else if (this.fichier.size > TAILLE_MAX) {
      erreurs['fichier'] = 'Le fichier dépasse 5 Mo.';
    }
    const informations = this.informations().map(({ libelle, type, valeur }) => ({
      libelle: libelle.trim(),
      type,
      valeur: valeur.trim(),
    }));
    const fautive = informations.find((info) => !informationValide(info));
    if (fautive) {
      erreurs['informations'] =
        fautive.libelle.length < 2
          ? 'Chaque information a un libellé de 2 caractères au moins.'
          : `« ${fautive.libelle} » : la valeur ne correspond pas à son type.`;
    }
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0 || !this.fichier) {
      setTimeout(() => document.getElementById(`piece-${Object.keys(erreurs)[0]}`)?.focus());
      return;
    }
    this.valide.emit({
      type: this.type(),
      titre,
      champs: [...this.coches()],
      informations,
      fichier: this.fichier,
    });
  }
}
