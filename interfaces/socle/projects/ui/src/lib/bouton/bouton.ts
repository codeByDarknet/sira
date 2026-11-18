import {
  Directive,
  ElementRef,
  OnInit,
  booleanAttribute,
  effect,
  inject,
  input,
} from '@angular/core';
import { ButtonDirective } from 'primeng/button';

export type VarianteBouton = 'primaire' | 'secondaire' | 'danger' | 'discret';

/**
 * Bouton, posé sur un `<button>` ou sur un `<a>` : `<button siraBouton variante="secondaire">`.
 * Le rendu est celui du bouton PrimeNG (`pButton`), aux coins légèrement arrondis. Un seul bouton primaire par
 * écran. Pendant l'envoi, `enCours` bloque les clics répétés sans retirer le bouton du parcours
 * clavier.
 */
@Directive({
  selector: 'button[siraBouton], a[siraBouton]',
  hostDirectives: [ButtonDirective],
  host: {
    class: 'sira-bouton',
    '[class.sira-bouton--secondaire]': "variante() === 'secondaire'",
    '[class.sira-bouton--danger]': "variante() === 'danger'",
    '[class.sira-bouton--discret]': "variante() === 'discret'",
    '[class.sira-bouton--en-cours]': 'enCours()',
    '[attr.aria-disabled]': 'enCours() ? "true" : null',
    '[attr.aria-busy]': 'enCours() ? "true" : null',
  },
})
export class Bouton implements OnInit {
  readonly variante = input<VarianteBouton>('primaire');
  readonly enCours = input(false, { transform: booleanAttribute });

  private readonly primeng = inject(ButtonDirective, { self: true });

  constructor() {
    // En phase de capture : ce filtre passe avant le `(click)` écrit dans le gabarit de l'écran.
    inject<ElementRef<HTMLElement>>(ElementRef).nativeElement.addEventListener(
      'click',
      (evenement) => {
        if (this.enCours()) {
          evenement.preventDefault();
          evenement.stopImmediatePropagation();
        }
      },
      { capture: true },
    );
    // Une variante qui change après l'affichage (rare) repasse par le setter de PrimeNG.
    effect(() => this.appliquer(this.variante()));
  }

  /** Avant `ngAfterViewInit` de PrimeNG, qui pose les classes à partir de ces propriétés. */
  ngOnInit(): void {
    this.appliquer(this.variante());
  }

  private appliquer(variante: VarianteBouton): void {
    const bouton = this.primeng;
    bouton.rounded = false;
    bouton.outlined = variante === 'secondaire';
    bouton.text = variante === 'discret';
    bouton.severity = variante === 'danger' ? 'danger' : variante === 'primaire' ? null : 'primary';
  }
}
