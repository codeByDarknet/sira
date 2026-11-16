import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  booleanAttribute,
  ElementRef,
  computed,
  contentChild,
  forwardRef,
  inject,
  input,
} from '@angular/core';
import { FloatLabel } from 'primeng/floatlabel';
import { InputText } from 'primeng/inputtext';
import { identifiantUnique } from '../commun/identifiant';
import { Icone } from '../icone/icone';

/**
 * Champ de formulaire : libellé visible, aide et message d'erreur autour d'une saisie projetée.
 *
 *     <sira-champ libelle="Numéro d'identification" aide="12 chiffres" [erreur]="erreur()">
 *       <input siraSaisie name="numero" inputmode="numeric" />
 *     </sira-champ>
 */
@Component({
  selector: 'sira-champ',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FloatLabel, Icone, NgTemplateOutlet],
  template: `<ng-template #saisie><ng-content /></ng-template>
    @if (flottant()) {
      <p-floatlabel variant="in" class="sira-champ__flottant">
        <ng-container [ngTemplateOutlet]="saisie" />
        <label [attr.for]="idSaisie"
          >{{ libelle() }}
          @if (obligatoire()) {
            <span class="sira-champ__requis" aria-hidden="true">*</span>
            <span class="sira-masque">(obligatoire)</span>
          }
        </label>
      </p-floatlabel>
    } @else {
      <label class="sira-champ__libelle" [attr.for]="idSaisie"
        >{{ libelle() }}
        @if (obligatoire()) {
          <span class="sira-champ__requis" aria-hidden="true">*</span>
          <span class="sira-masque">(obligatoire)</span>
        }
      </label>
    }
    @if (aide()) {
      <span class="sira-champ__aide" [id]="idAide">
        {{ aideTexte() }}
        @if (aideExemple(); as exemple) {
          <span class="sira-champ__exemple">Exemple : {{ exemple }}</span>
        }
      </span>
    }
    @if (erreur()) {
      <p class="sira-champ__erreur" [id]="idErreur">
        <sira-icone nom="circle-alert" />
        <span><span class="sira-masque">Erreur : </span>{{ erreur() }}</span>
      </p>
    }
    @if (!flottant()) {
      <ng-container [ngTemplateOutlet]="saisie" />
    }`,
  host: { class: 'sira-champ', '[class.sira-champ--erreur]': '!!erreur()' },
})
export class Champ {
  readonly libelle = input.required<string>();
  readonly aide = input<string>();
  readonly erreur = input<string | null>();
  /** Champ obligatoire : astérisque rouge après le libellé. */
  readonly obligatoire = input(false, { transform: booleanAttribute });

  /** L'aide, et son exemple (« Exemple : … ») affiché sur sa propre ligne. */
  protected readonly aideTexte = computed(() => (this.aide() ?? '').split(/\s*Exemple\s*:\s*/)[0]);
  protected readonly aideExemple = computed(
    () => (this.aide() ?? '').split(/\s*Exemple\s*:\s*/)[1] ?? null,
  );
  /** Identifiant de la saisie ; à fixer pour qu'un résumé d'erreurs puisse y renvoyer. */
  readonly identifiant = input<string>();
  /**
   * Libellé flottant (PrimeNG FloatLabel) : par défaut pour les zones de texte, jamais pour une
   * liste, un fichier ou un choix, où le libellé reste au-dessus.
   */
  readonly libelleFlottant = input<boolean | undefined>(undefined);

  private readonly saisieProjetee = contentChild(forwardRef(() => Saisie));
  protected readonly flottant = computed(() => {
    const force = this.libelleFlottant();
    if (force !== undefined) {
      return force;
    }
    const element = this.saisieProjetee()?.element.nativeElement;
    if (!element) {
      return false;
    }
    if (element instanceof HTMLTextAreaElement) {
      return true;
    }
    return (
      element instanceof HTMLInputElement &&
      ['text', 'email', 'search', 'tel', 'url', 'password', 'number'].includes(element.type)
    );
  });

  private readonly idGenere = identifiantUnique('sira-champ');

  get idSaisie(): string {
    return this.identifiant() ?? this.idGenere;
  }
  get idAide(): string {
    return `${this.idSaisie}-aide`;
  }
  get idErreur(): string {
    return `${this.idSaisie}-erreur`;
  }

  /** Éléments qui décrivent la saisie, lus par les lecteurs d'écran à la prise de focus. */
  readonly description = computed(() => {
    const ids = [this.aide() ? this.idAide : null, this.erreur() ? this.idErreur : null];
    return ids.filter(Boolean).join(' ') || null;
  });
}

/** Saisie (`input`, `select`, `textarea`) : reliée au `sira-champ` qui la contient. */
@Directive({
  selector: 'input[siraSaisie], select[siraSaisie], textarea[siraSaisie]',
  // Rendu du champ PrimeNG ; l'accessibilité (id, description, invalidité) reste celle de Sira.
  hostDirectives: [InputText],
  host: {
    class: 'sira-saisie',
    '[attr.id]': 'champ?.idSaisie',
    '[attr.aria-describedby]': 'champ?.description()',
    '[attr.aria-invalid]': 'champ?.erreur() ? "true" : null',
  },
})
export class Saisie {
  protected readonly champ = inject(Champ, { optional: true });
  readonly element: ElementRef<HTMLElement> = inject(ElementRef);
}

/** Ce qu'une saisie filtrée laisse passer pendant la frappe. */
export type FiltreSaisie = 'code' | 'code-point' | 'chiffres' | 'decimal';

const NETTOYAGES: Record<FiltreSaisie, (texte: string) => string> = {
  // Minuscules, chiffres et tirets : les majuscules passent en minuscules, le reste est refusé.
  code: (texte) =>
    texte
      .toLowerCase()
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .replace(/[\s_]+/g, '-')
      .replace(/[^a-z0-9-]/g, '')
      .replace(/-{2,}/g, '-')
      .replace(/^-/, ''),
  // Idem, avec le point (codes de question : « etat-civil.majeur »).
  'code-point': (texte) =>
    texte
      .toLowerCase()
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .replace(/[\s_]+/g, '-')
      .replace(/[^a-z0-9.-]/g, '')
      .replace(/-{2,}/g, '-')
      .replace(/^[-.]/, ''),
  chiffres: (texte) => texte.replace(/\D/g, ''),
  // Nombre à virgule : chiffres et un seul séparateur (« 2,5 »).
  decimal: (texte) => {
    const propre = texte.replace('.', ',').replace(/[^\d,]/g, '');
    const [entier, ...reste] = propre.split(',');
    return reste.length > 0 ? `${entier},${reste.join('')}` : entier;
  },
};

/**
 * Filtre la frappe : un caractère refusé n'apparaît même pas dans le champ.
 *
 *     <input siraSaisie siraFiltre="code" formControlName="code" />
 */
@Directive({
  selector: 'input[siraFiltre]',
  host: { '(input)': 'nettoyer()' },
})
export class FiltreFrappe {
  readonly filtre = input.required<FiltreSaisie>({ alias: 'siraFiltre' });
  private readonly element: ElementRef<HTMLInputElement> = inject(ElementRef);

  protected nettoyer(): void {
    const champ = this.element.nativeElement;
    const propre = NETTOYAGES[this.filtre()](champ.value);
    if (propre !== champ.value) {
      champ.value = propre;
      // Le formulaire lit la valeur nettoyée.
      champ.dispatchEvent(new Event('input'));
    }
  }
}
