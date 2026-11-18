import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Alerte, Bouton, Carte, Champ, Code, DatePipe, Icone, Saisie, Squelette } from '@sira/ui';
import { catchError, exhaustMap, filter, of, switchMap, tap, timer } from 'rxjs';
import { CITOYENS, Citoyen } from './citoyens';
import { Message, Messagerie } from './messagerie';

const PERIODE_MS = 3000;

function heureCourante(): string {
  return new Intl.DateTimeFormat('fr', { hour: '2-digit', minute: '2-digit' }).format(new Date());
}

@Component({
  selector: 'te-messages',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Carte, Champ, Code, DatePipe, Icone, Saisie, Squelette],
  templateUrl: './messages.html',
})
export class Messages {
  private readonly messagerie = inject(Messagerie);

  protected readonly citoyens = CITOYENS;
  protected readonly destinataire = signal<Citoyen>(CITOYENS[0]);
  /** `null` tant que la première lecture n'a pas abouti. */
  protected readonly messages = signal<Message[] | null>(null);
  protected readonly illisible = signal(false);
  protected readonly dernierCode = computed(
    () => this.messages()?.find((message) => message.code !== null) ?? null,
  );
  private readonly fil = viewChild<ElementRef<HTMLElement>>('fil');
  /** Dernier contenu affiché : on ne redescend en bas que s'il a changé. */
  private dernierAffiche = '';

  /** Comme une application de SMS : le bas du fil (le message le plus récent) reste visible. */
  protected readonly defilement = afterRenderEffect(() => {
    const fil = this.fil()?.nativeElement;
    const empreinte = `${this.destinataire().identifiant}:${this.messages()?.[0]?.id ?? ''}`;
    if (fil && empreinte !== this.dernierAffiche) {
      this.dernierAffiche = empreinte;
      fil.scrollTop = fil.scrollHeight;
    }
  });

  /** Les messages du plus ancien au plus récent, comme dans une application de SMS. */
  protected readonly chronologie = computed(() => [...(this.messages() ?? [])].reverse());
  /** Heure de la barre d'état du téléphone, à la minute. */
  protected readonly heure = signal(heureCourante());
  /** Résultat de la dernière copie, rattaché au message copié : un nouveau code l'efface. */
  protected readonly copie = signal<{ id: string; faite: boolean } | null>(null);

  constructor() {
    timer(0, 15_000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.heure.set(heureCourante()));
    toObservable(this.destinataire)
      .pipe(
        tap(() => this.messages.set(null)),
        switchMap((citoyen) =>
          timer(0, PERIODE_MS).pipe(
            // Onglet en arrière-plan : inutile d'interroger la messagerie.
            filter(() => !document.hidden),
            exhaustMap(() =>
              this.messagerie.recus(citoyen.adresse).pipe(catchError(() => of(null))),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((messages) => {
        this.illisible.set(messages === null);
        if (messages !== null) {
          this.messages.set(messages);
        }
      });
  }

  protected choisir(identifiant: string): void {
    const citoyen = CITOYENS.find((connu) => connu.identifiant === identifiant);
    if (citoyen) {
      this.destinataire.set(citoyen);
    }
  }

  protected copier(message: Message): void {
    const noter = (faite: boolean) => this.copie.set({ id: message.id, faite });
    navigator.clipboard.writeText(message.code ?? '').then(
      () => noter(true),
      () => noter(false),
    );
  }
}
