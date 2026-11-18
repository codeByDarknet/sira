import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  ElementRef,
  TemplateRef,
  computed,
  contentChildren,
  inject,
  input,
  model,
} from '@angular/core';
import { identifiantUnique } from '../commun/identifiant';

export interface Onglet {
  id: string;
  libelle: string;
}

/** Contenu d'un onglet : `<ng-template siraPanneau="actifs">…</ng-template>`. */
@Directive({ selector: 'ng-template[siraPanneau]' })
export class Panneau {
  readonly id = input.required<string>({ alias: 'siraPanneau' });
  readonly gabarit: TemplateRef<unknown> = inject(TemplateRef);
}

/**
 * Onglets pour des vues d'un même objet. Flèches gauche et droite, Début et Fin changent
 * d'onglet ; seul l'onglet actif est dans le parcours de tabulation.
 */
@Component({
  selector: 'sira-onglets',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet],
  template: `<div class="sira-onglets">
    <div class="sira-onglets__liste" role="tablist" [attr.aria-label]="libelle()">
      @for (onglet of onglets(); track onglet.id) {
        <button
          class="sira-onglets__onglet"
          type="button"
          role="tab"
          [id]="prefixe + '-onglet-' + onglet.id"
          [attr.aria-selected]="onglet.id === courant()"
          [attr.aria-controls]="prefixe + '-panneau'"
          [attr.tabindex]="onglet.id === courant() ? 0 : -1"
          (click)="actif.set(onglet.id)"
          (keydown)="auClavier($event, $index)"
        >
          {{ onglet.libelle }}
        </button>
      }
    </div>
    <div
      class="sira-onglets__panneau"
      role="tabpanel"
      tabindex="0"
      [id]="prefixe + '-panneau'"
      [attr.aria-labelledby]="prefixe + '-onglet-' + courant()"
    >
      @if (gabarit(); as contenu) {
        <ng-container [ngTemplateOutlet]="contenu" />
      }
    </div>
  </div>`,
})
export class Onglets {
  readonly onglets = input.required<readonly Onglet[]>();
  /** Nom du groupe d'onglets, lu par les lecteurs d'écran. */
  readonly libelle = input.required<string>();
  /** Identifiant de l'onglet affiché ; le premier par défaut. */
  readonly actif = model<string>();

  protected readonly prefixe = identifiantUnique('sira-onglets');
  private readonly panneaux = contentChildren(Panneau);
  private readonly hote: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly courant = computed(() => this.actif() ?? this.onglets()[0]?.id);
  protected readonly gabarit = computed(
    () => this.panneaux().find((panneau) => panneau.id() === this.courant())?.gabarit,
  );

  protected auClavier(evenement: KeyboardEvent, position: number): void {
    const dernier = this.onglets().length - 1;
    const cibles: Record<string, number> = {
      ArrowRight: position === dernier ? 0 : position + 1,
      ArrowLeft: position === 0 ? dernier : position - 1,
      Home: 0,
      End: dernier,
    };
    const cible = cibles[evenement.key];
    if (cible === undefined) {
      return;
    }
    evenement.preventDefault();
    const onglet = this.onglets()[cible];
    this.actif.set(onglet.id);
    this.hote.nativeElement
      .querySelector<HTMLElement>(`#${this.prefixe}-onglet-${CSS.escape(onglet.id)}`)
      ?.focus();
  }
}
