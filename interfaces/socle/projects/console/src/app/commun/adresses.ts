import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Action, Icone } from '@sira/ui';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';

/**
 * Saisie d'une liste d'adresses web, une case par adresse, avec « Ajouter une adresse » et une
 * corbeille par ligne. La valeur du contrôle reste un texte (une adresse par ligne) : les
 * vérifications existantes s'appliquent telles quelles.
 *
 *     <co-adresses formControlName="retours" titre="Adresses de retour autorisées"
 *       exemple="https://concours.gov.bf/retour" />
 */
@Component({
  selector: 'co-adresses',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Action, Button, Icone, InputText],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Adresses), multi: true }],
  template: `<div
    class="sira-liste-saisie"
    role="group"
    [attr.aria-labelledby]="identifiant() + '-titre'"
  >
    <p class="sira-liste-saisie__titre" [id]="identifiant() + '-titre'">{{ titre() }}</p>
    @for (adresse of adresses(); track $index) {
      <div class="sira-liste-saisie__ligne">
        <input
          pInputText
          type="url"
          inputmode="url"
          autocomplete="off"
          [id]="$index === 0 ? identifiant() : null"
          [placeholder]="exemple()"
          [attr.aria-label]="'Adresse ' + ($index + 1)"
          [value]="adresse"
          [disabled]="desactive()"
          (input)="changer($index, $any($event.target).value)"
          (blur)="toucher()"
        />
        @if (!desactive()) {
          <button
            siraAction="trash-2"
            ton="danger"
            [libelle]="'Retirer l’adresse ' + ($index + 1)"
            (click)="retirer($index)"
          ></button>
        }
      </div>
    } @empty {
      <p class="sira-liste-saisie__vide">Aucune adresse pour l’instant.</p>
    }
    @if (!desactive()) {
      <p-button
        styleClass="sira-liste-saisie__ajout"
        severity="secondary"
        [outlined]="true"
        (onClick)="ajouter()"
      >
        <sira-icone nom="plus" />
        Ajouter une adresse
      </p-button>
    }
  </div>`,
})
export class Adresses implements ControlValueAccessor {
  readonly titre = input.required<string>();
  readonly exemple = input('https://');
  /** Identifiant de la première case : un résumé d'erreurs peut y renvoyer. */
  readonly identifiant = input('adresses');

  protected readonly adresses = signal<string[]>([]);
  protected readonly desactive = signal(false);
  private surChangement: (valeur: string) => void = () => undefined;
  private surToucher: () => void = () => undefined;

  writeValue(valeur: string | null): void {
    const lignes = (valeur ?? '')
      .split('\n')
      .map((ligne) => ligne.trim())
      .filter(Boolean);
    this.adresses.set(lignes);
  }

  registerOnChange(fonction: (valeur: string) => void): void {
    this.surChangement = fonction;
  }

  registerOnTouched(fonction: () => void): void {
    this.surToucher = fonction;
  }

  setDisabledState(desactive: boolean): void {
    this.desactive.set(desactive);
  }

  protected ajouter(): void {
    this.adresses.update((liste) => [...liste, '']);
    this.publier();
  }

  protected retirer(position: number): void {
    this.adresses.update((liste) => liste.filter((_, i) => i !== position));
    this.publier();
  }

  protected changer(position: number, valeur: string): void {
    this.adresses.update((liste) => liste.map((adresse, i) => (i === position ? valeur : adresse)));
    this.publier();
  }

  protected toucher(): void {
    this.surToucher();
  }

  private publier(): void {
    this.surChangement(this.adresses().join('\n'));
  }
}
