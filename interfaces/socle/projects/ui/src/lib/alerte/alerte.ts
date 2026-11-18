import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';

/** Durée d'affichage d'une notification (création, modification, suppression). */
export const DUREE_NOTIFICATION_MS = 6000;
import { Message } from 'primeng/message';
import { Icone, NomIcone } from '../icone/icone';

export type TypeAlerte = 'info' | 'succes' | 'attention' | 'erreur';

const SEVERITES: Record<TypeAlerte, 'info' | 'success' | 'warn' | 'error'> = {
  info: 'info',
  succes: 'success',
  attention: 'warn',
  erreur: 'error',
};

const ICONES: Record<TypeAlerte, NomIcone> = {
  info: 'info',
  succes: 'circle-check',
  attention: 'triangle-alert',
  erreur: 'circle-alert',
};

/**
 * Message placé dans le fil de la page. Une erreur est annoncée tout de suite (`role="alert"`),
 * les autres types poliment (`role="status"`).
 */
@Component({
  selector: 'sira-alerte',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone, Message],
  template: `<p-message
    [severity]="severite()"
    styleClass="sira-alerte__message"
    [attr.role]="null"
    [attr.aria-live]="null"
  >
    <sira-icone [nom]="icone()" />
    <div class="sira-alerte__corps">
      @if (titre()) {
        <p class="sira-alerte__titre">{{ titre() }}</p>
      }
      <ng-content />
    </div>
  </p-message>`,
  host: {
    class: 'sira-alerte',
    '[class.sira-alerte--succes]': "type() === 'succes'",
    '[class.sira-alerte--attention]': "type() === 'attention'",
    '[class.sira-alerte--erreur]': "type() === 'erreur'",
    '[attr.role]': "type() === 'erreur' ? 'alert' : 'status'",
    '[class.sira-alerte--ephemere]': 'ephemere()',
    '[hidden]': 'masquee()',
    tabindex: '-1',
  },
})
export class Alerte {
  readonly type = input<TypeAlerte>('info');
  readonly titre = input<string>();
  /** Notification : disparaît seule après 6 secondes, et réapparaît si son texte change. */
  readonly ephemere = input(false, { transform: booleanAttribute });

  protected readonly masquee = signal(false);

  constructor() {
    const hote = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    let minuterie: ReturnType<typeof setTimeout> | undefined;
    const relancer = () => {
      clearTimeout(minuterie);
      this.masquee.set(false);
      minuterie = setTimeout(() => this.masquee.set(true), DUREE_NOTIFICATION_MS);
    };
    afterNextRender(() => {
      if (!this.ephemere()) {
        return;
      }
      relancer();
      const observateur = new MutationObserver(relancer);
      observateur.observe(hote, { characterData: true, childList: true, subtree: true });
      this.fin = () => {
        observateur.disconnect();
        clearTimeout(minuterie);
      };
    });
    inject(DestroyRef).onDestroy(() => this.fin());
  }

  private fin: () => void = () => undefined;

  protected readonly icone = computed(() => ICONES[this.type()]);
  protected readonly severite = computed(() => SEVERITES[this.type()]);
}

/** Bandeau permanent sur toute la largeur, au-dessus de l'en-tête (« Démonstration — données fictives »). */
@Component({
  selector: 'sira-bandeau',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  template: `<div class="sira-conteneur sira-bandeau__contenu">
    <sira-icone [nom]="type() === 'attention' ? 'triangle-alert' : 'info'" />
    <p class="sira-sans-marge"><ng-content /></p>
  </div>`,
  host: { class: 'sira-bandeau', '[class.sira-bandeau--attention]': "type() === 'attention'" },
})
export class Bandeau {
  readonly type = input<'info' | 'attention'>('info');
}
