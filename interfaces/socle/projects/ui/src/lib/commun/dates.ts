import { Pipe, PipeTransform } from '@angular/core';

const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
const HEURE = new Intl.DateTimeFormat('fr-FR', { hour: 'numeric', minute: '2-digit' });

type DateAdmise = Date | string | number;

/** « 16 novembre 2026 » */
export function formaterJour(date: DateAdmise): string {
  return JOUR.format(new Date(date)).replace(/^1 /, '1er ');
}

/** « 14 h 05 », insécable : l'heure ne se coupe jamais en fin de ligne. */
export function formaterHeure(date: DateAdmise): string {
  const parties = HEURE.formatToParts(new Date(date));
  const valeur = (type: string) => parties.find((partie) => partie.type === type)?.value ?? '';
  return `${valeur('hour')}\u00a0h\u00a0${valeur('minute')}`;
}

/** « 16 novembre 2026 à 14 h 05 » */
export function formaterDateHeure(date: DateAdmise): string {
  return `${formaterJour(date)} à ${formaterHeure(date)}`;
}

/** Date en toutes lettres : `{{ ligne.cree_le | siraDate }}` ou `| siraDate: 'jour'`. */
@Pipe({ name: 'siraDate' })
export class DatePipe implements PipeTransform {
  transform(date: DateAdmise | null | undefined, forme: 'jour' | 'heure' | 'complet' = 'complet') {
    if (date === null || date === undefined || date === '') {
      return '';
    }
    if (forme === 'jour') {
      return formaterJour(date);
    }
    return forme === 'heure' ? formaterHeure(date) : formaterDateHeure(date);
  }
}
