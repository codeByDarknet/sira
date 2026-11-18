import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  inject,
  input,
} from '@angular/core';

export interface ErreurDeChamp {
  /** Identifiant de la saisie fautive (entrée `identifiant` du `sira-champ`). */
  champ: string;
  message: string;
}

/**
 * Résumé des erreurs, en haut du formulaire. Il prend le focus dès qu'il apparaît ;
 * chaque ligne renvoie au champ fautif.
 */
@Component({
  selector: 'sira-resume-erreurs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (erreurs().length > 0) {
    <div class="sira-resume-erreurs" role="alert" tabindex="-1">
      <h2 class="sira-resume-erreurs__titre">{{ titre() }}</h2>
      <ul>
        @for (erreur of erreurs(); track erreur.champ) {
          <li>
            <a [attr.href]="'#' + erreur.champ" (click)="allerAuChamp($event, erreur.champ)">{{
              erreur.message
            }}</a>
          </li>
        }
      </ul>
    </div>
  }`,
})
export class ResumeErreurs {
  readonly erreurs = input.required<readonly ErreurDeChamp[]>();
  readonly titre = input('Corrigez les points suivants');

  private readonly hote: ElementRef<HTMLElement> = inject(ElementRef);

  constructor() {
    afterRenderEffect(() => {
      if (this.erreurs().length > 0) {
        this.hote.nativeElement.querySelector<HTMLElement>('.sira-resume-erreurs')?.focus();
      }
    });
  }

  /** Le lien porte une ancre lisible, mais c'est le focus qui compte : on le pose nous-mêmes. */
  protected allerAuChamp(evenement: Event, champ: string): void {
    evenement.preventDefault();
    this.hote.nativeElement.ownerDocument.getElementById(champ)?.focus();
  }
}
