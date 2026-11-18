import {
  ChangeDetectionStrategy,
  Component,
  InjectionToken,
  booleanAttribute,
  computed,
  inject,
  input,
} from '@angular/core';

/** Noms des icônes présentes dans le sprite `assets/icones.svg` (Lucide, licence ISC). */
export type NomIcone =
  | 'arrow-left'
  | 'arrow-up-right'
  | 'ban'
  | 'battery'
  | 'building-2'
  | 'check'
  | 'check-check'
  | 'chevron-down'
  | 'chevron-left'
  | 'chevron-right'
  | 'circle-alert'
  | 'circle-check'
  | 'clock'
  | 'copy'
  | 'download'
  | 'external-link'
  | 'eye'
  | 'file-text'
  | 'flag'
  | 'history'
  | 'house'
  | 'inbox'
  | 'info'
  | 'key-round'
  | 'landmark'
  | 'layout-dashboard'
  | 'list'
  | 'lock'
  | 'log-out'
  | 'menu'
  | 'message-square'
  | 'pencil'
  | 'plus'
  | 'qr-code'
  | 'refresh-cw'
  | 'search'
  | 'send'
  | 'shield-check'
  | 'signal'
  | 'smartphone'
  | 'trash-2'
  | 'triangle-alert'
  | 'user'
  | 'users'
  | 'wifi'
  | 'x'
  | 'zoom-in'
  | 'zoom-out';

/** Adresse du sprite d'icônes, relative à la base de l'application. */
export const ADRESSE_ICONES = new InjectionToken<string>('ADRESSE_ICONES', {
  factory: () => 'assets/sira/icones.svg',
});

/** Icône décorative : elle accompagne toujours un texte et reste muette pour les lecteurs d'écran. */
@Component({
  selector: 'sira-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg
    class="sira-icone"
    [class.sira-icone--grande]="grande()"
    aria-hidden="true"
    focusable="false"
  >
    <use [attr.href]="adresse()" />
  </svg>`,
})
export class Icone {
  readonly nom = input.required<NomIcone>();
  readonly grande = input(false, { transform: booleanAttribute });

  private readonly sprite = inject(ADRESSE_ICONES);
  protected readonly adresse = computed(() => `${this.sprite}#${this.nom()}`);
}
