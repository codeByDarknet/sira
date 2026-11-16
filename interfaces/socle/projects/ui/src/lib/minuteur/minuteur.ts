import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { formaterDateHeure, formaterHeure } from '../commun/dates';
import { Icone } from '../icone/icone';

const MINUTE = 60_000;
const HEURE = 60 * MINUTE;

/** Phrase d'échéance, sans compte à rebours : « expire dans 4 minutes, à 14 h 10 ». */
export function phraseEcheance(objet: string, echeance: Date, maintenant: Date): string {
  const reste = echeance.getTime() - maintenant.getTime();
  if (reste <= 0) {
    return `${objet} a expiré à ${formaterHeure(echeance)}.`;
  }
  if (reste > HEURE) {
    return `${objet} expire le ${formaterDateHeure(echeance)}.`;
  }
  const minutes = Math.ceil(reste / MINUTE);
  const delai = minutes <= 1 ? 'moins d’une minute' : `${minutes} minutes`;
  return `${objet} expire dans ${delai}, à ${formaterHeure(echeance)}.`;
}

/**
 * Minuteur d'expiration. L'heure limite est écrite en clair ; le texte ne change qu'une
 * fois par minute et n'est pas annoncé en continu. `expire` est émis une seule fois.
 */
@Component({
  selector: 'sira-minuteur',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  template: `<sira-icone nom="clock" /> <span>{{ phrase() }}</span>`,
  host: {
    class: 'sira-minuteur',
    '[class.sira-minuteur--expire]': 'expiree()',
    '[attr.role]': 'expiree() ? "status" : null',
  },
})
export class Minuteur {
  readonly echeance = input.required<Date | string>();
  /** Sujet de la phrase : « Cette demande », « Ce code »… */
  readonly objet = input('Cette demande');
  readonly expire = output();

  private readonly maintenant = signal(new Date());
  private readonly limite = computed(() => new Date(this.echeance()));
  protected readonly expiree = computed(() => this.limite() <= this.maintenant());
  protected readonly phrase = computed(() =>
    phraseEcheance(this.objet(), this.limite(), this.maintenant()),
  );

  constructor() {
    const horloge = setInterval(() => this.maintenant.set(new Date()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(horloge));

    let signale = false;
    effect(() => {
      if (this.expiree() && !signale) {
        signale = true;
        clearInterval(horloge);
        this.expire.emit();
      }
    });
  }
}
