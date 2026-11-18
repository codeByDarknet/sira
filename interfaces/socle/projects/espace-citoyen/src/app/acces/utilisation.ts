import { formaterJour } from '@sira/ui';
import { compter } from '../commun/libelles';
import { Autorisation } from '../commun/modeles';

/** « 3 consultations, dernière le 10 novembre 2026 » */
export function decrireUtilisation(autorisation: Autorisation): string {
  if (autorisation.consultations === 0) {
    return 'Jamais consulté';
  }
  const nombre = compter(autorisation.consultations, 'consultation');
  const derniere = autorisation.derniere_consultation_le;
  return derniere ? `${nombre}, dernière le ${formaterJour(derniere)}` : nombre;
}
