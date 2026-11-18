import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, Dialogue, Icone } from '@sira/ui';
import { Autorisation } from '../commun/modeles';
import { Autorisations } from './autorisations';

/**
 * « Retirer l'accès » : le bouton, et la fenêtre qui dit ce que le retrait fait et ce
 * qu'il ne fait pas, avant de l'enregistrer.
 */
@Component({
  selector: 'ec-retrait',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Dialogue, Icone],
  template: `<button siraBouton variante="secondaire" type="button" (click)="ouvert.set(true)">
      <sira-icone nom="ban" />
      Retirer l’accès<span class="sira-masque"> de {{ service() }}</span>
    </button>
    <sira-dialogue [titre]="'Retirer l’accès de ' + service() + ' ?'" [(ouvert)]="ouvert">
      @if (echec(); as message) {
        <sira-alerte type="erreur" titre="L’accès n’a pas été retiré">{{ message }}</sira-alerte>
      }
      <p>Ce qui a déjà été transmis reste chez ce service.</p>
      <button
        dialogue-actions
        siraBouton
        variante="danger"
        type="button"
        [enCours]="envoi()"
        (click)="retirer()"
      >
        Retirer l’accès
      </button>
      <button
        dialogue-actions
        siraBouton
        variante="secondaire"
        type="button"
        (click)="ouvert.set(false)"
      >
        Annuler
      </button>
    </sira-dialogue>`,
})
export class Retrait {
  private readonly autorisations = inject(Autorisations);

  readonly autorisation = input.required<Autorisation>();
  /** Émis une fois le retrait enregistré par le serveur. */
  readonly retire = output<Autorisation>();

  protected readonly ouvert = signal(false);
  protected readonly envoi = signal(false);
  protected readonly echec = signal<string | null>(null);

  protected service(): string {
    return this.autorisation().service.nom;
  }

  protected retirer(): void {
    this.envoi.set(true);
    this.echec.set(null);
    this.autorisations.retirer(this.autorisation().id).subscribe({
      next: () => {
        this.envoi.set(false);
        this.ouvert.set(false);
        this.retire.emit(this.autorisation());
      },
      error: (erreur: unknown) => {
        this.envoi.set(false);
        this.echec.set(lireProbleme(erreur).message);
      },
    });
  }
}
