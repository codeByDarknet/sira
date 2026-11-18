import { Injectable, computed, inject } from '@angular/core';
import { Session } from '@sira/coquille';

/** Rôles du royaume des administrateurs du socle, tels que le serveur les renvoie dans la session. */
export const ROLES = {
  /** Prépare : membres, registres, questions, démarches, habilitations. */
  instructeur: 'instructeur',
  /** Décide des habilitations ; jamais de celles qu'elle a instruites. */
  autorite: 'autorite',
  /** Lit tout, ne modifie rien. */
  auditeur: 'auditeur',
} as const;

/**
 * Ce que le rôle de l'usager lui permet. L'interface s'en sert pour ne montrer que les gestes
 * permis ; le serveur reste seul juge de chaque appel.
 */
@Injectable({ providedIn: 'root' })
export class Droits {
  private readonly session = inject(Session);
  private readonly roles = computed(() => this.session.utilisateur()?.roles ?? []);

  readonly nom = computed(() => this.session.utilisateur()?.nom ?? '');
  /** Créer, modifier, désactiver, soumettre. */
  readonly ecriture = computed(() => this.roles().includes(ROLES.instructeur));
  /** Approuver, rejeter, suspendre une habilitation. */
  readonly decision = computed(() => this.roles().includes(ROLES.autorite));
  /** Répondre au signalement d'un citoyen. */
  readonly traitement = computed(() => this.ecriture() || this.decision());
}
