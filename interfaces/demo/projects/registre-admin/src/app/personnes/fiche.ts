import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { lireProbleme, Session } from '@sira/coquille';
import {
  Action,
  Alerte,
  Bouton,
  DatePipe,
  Dialogue,
  Fil,
  Icone,
  Squelette,
  Synthese,
  SyntheseLigne,
} from '@sira/ui';
import { ApercuPdf } from '@demo/apercu';
import { forkJoin } from 'rxjs';
import { ChampDeFiche, Registre, afficher } from '../registre/registre';
import { DepotPieceDialogue } from './depot-piece';
import { DepotPiece, Personnes, Piece, adresseDocument, adressePiece } from './personnes';

@Component({
  selector: 'ra-fiche',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ApercuPdf,
    DepotPieceDialogue,
    Action,
    Alerte,
    Bouton,
    DatePipe,
    Dialogue,
    Fil,
    Icone,
    Squelette,
    Synthese,
    SyntheseLigne,
  ],
  templateUrl: './fiche.html',
})
export class FichePersonne {
  /** Ce que le rôle de l'agent lui permet : boutons montrés ou cachés. */
  protected readonly session = inject(Session);
  private readonly personnes = inject(Personnes);
  private readonly registre = inject(Registre);
  private readonly routeur = inject(Router);

  /** Paramètre `:id` de la route (UUID), relié par le routeur. */
  readonly id = input.required<string>();
  /** Message laissé par l'écran de correction. */
  protected readonly confirmation = inject(ActivatedRoute).snapshot.queryParamMap.get('message');

  protected readonly donnees = rxResource({
    params: () => this.id(),
    stream: ({ params }) =>
      forkJoin({ personne: this.personnes.lire(params), registre: this.registre.description }),
  });
  protected readonly probleme = computed(() =>
    this.donnees.error() ? lireProbleme(this.donnees.error()).message : null,
  );
  protected readonly corrigeable = computed(
    () => this.donnees.value()?.registre.champs.some((champ) => champ.modifiable) ?? false,
  );

  protected readonly afficher = afficher;
  protected readonly adresseDocument = adresseDocument;

  protected readonly adressePiece = adressePiece;

  /** Document montré dans la fenêtre d'aperçu (PDF dessiné, ou image). */
  protected readonly apercu = signal<{ titre: string; adresse: string; image: boolean } | null>(
    null,
  );

  /** Pièces déposées pour la personne. */
  protected readonly pieces = rxResource({
    params: () => (this.session.peut('DOCUMENTS_LIRE') ? this.id() : undefined),
    stream: ({ params }) => this.personnes.pieces(params),
  });
  protected readonly deposees = computed(() => this.pieces.value() ?? []);
  /** Les pièces qui ne remplacent pas un document du registre. */
  protected readonly autresPieces = computed(() => {
    const types = new Set(this.donnees.value()?.registre.documents.map((d) => d.type) ?? []);
    return this.deposees().filter((p) => !types.has(p.type) || p.type_mime !== 'application/pdf');
  });
  protected readonly depotOuvert = signal(false);
  protected readonly depot = signal(false);
  protected readonly echecDepot = signal<string | null>(null);
  protected readonly messagePiece = signal<string | null>(null);

  protected ouvrirApercu(titre: string, adresse: string, image: boolean): void {
    this.apercu.set({ titre, adresse, image });
  }

  /** La dernière pièce PDF déposée pour ce type : elle remplace le document produit. */
  protected deposePour(type: string): Piece | undefined {
    return this.deposees().find((p) => p.type === type && p.type_mime === 'application/pdf');
  }

  protected libellesChamps(champs: ChampDeFiche[], noms: string[]): string {
    if (noms.length === 0) {
      return 'Aucune information indiquée';
    }
    return noms.map((nom) => this.champ(champs, nom)?.libelle ?? nom).join(', ');
  }

  protected deposer(id: string, depot: DepotPiece): void {
    this.depot.set(true);
    this.echecDepot.set(null);
    this.personnes.deposer(id, depot).subscribe({
      next: (pieces) => {
        this.depot.set(false);
        this.depotOuvert.set(false);
        this.pieces.set(pieces);
        this.messagePiece.set(`« ${depot.titre} » a été ajouté.`);
      },
      error: (erreur: unknown) => {
        this.depot.set(false);
        const refus = lireProbleme(erreur);
        this.echecDepot.set(Object.values(refus.champs)[0] ?? refus.message);
      },
    });
  }

  protected retirerPiece(id: string, piece: Piece): void {
    this.personnes.retirerPiece(id, piece.id).subscribe({
      next: () => {
        this.pieces.reload();
        this.messagePiece.set(`« ${piece.titre} » a été retiré.`);
      },
      error: (erreur: unknown) => this.messagePiece.set(lireProbleme(erreur).message),
    });
  }
  protected readonly suppressionOuverte = signal(false);
  protected readonly suppression = signal(false);
  protected readonly echecSuppression = signal<string | null>(null);

  protected champ(champs: ChampDeFiche[], nom: string): ChampDeFiche | undefined {
    return champs.find((champ) => champ.nom === nom);
  }

  protected supprimer(identifiant: string): void {
    this.suppression.set(true);
    this.echecSuppression.set(null);
    this.personnes.supprimer(identifiant).subscribe({
      next: () =>
        void this.routeur.navigate(['/personnes'], {
          queryParams: { message: 'La personne a été retirée du registre.' },
        }),
      error: (erreur: unknown) => {
        this.suppression.set(false);
        this.echecSuppression.set(lireProbleme(erreur).message);
      },
    });
  }
}
