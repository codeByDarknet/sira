import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Droits } from '../roles';

/** Ce que le compte connecté peut faire ; la règle des deux rôles reste repliée. */
@Component({
  selector: 'co-separation-des-roles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: ``,
})
export class SeparationDesRoles {
  private readonly droits = inject(Droits);

  protected readonly role = computed(() => {
    if (this.droits.ecriture() && this.droits.decision()) {
      return 'Instructeur et autorité : vous ne décidez pas de vos propres dossiers.';
    }
    if (this.droits.ecriture()) {
      return 'Instructeur : vous préparez et soumettez.';
    }
    if (this.droits.decision()) {
      return 'Autorité : vous décidez.';
    }
    return 'Auditeur : lecture seule.';
  });
}
