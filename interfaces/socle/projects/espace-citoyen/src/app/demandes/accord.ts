import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { REDIRECTION, lireProbleme } from '@sira/coquille';
import { Alerte, Champ, DemandeAccesVue, Lien, Saisie, Squelette } from '@sira/ui';
import { focusSur } from '../commun/focus';
import { Decision, EtatDemande } from '../commun/modeles';
import { Demandes } from './demandes';
import { adresseDeRetour, presenter } from './presentation';

const DEMANDES_CLOSES: Partial<Record<EtatDemande, string>> = {
  REFUSEE: 'Vous avez refusé cette demande. Le service n’a rien reçu.',
  EXPIREE: 'Le délai pour répondre est dépassé. Le service n’a rien reçu.',
  ANNULEE: 'Le service a annulé cette demande. Il n’a rien reçu.',
};
const DEMANDE_ACCEPTEE = 'Vous avez déjà autorisé cette demande.';
const CODE_SERVICE = /^[0-9]{6}$/;
/** Réponse perdue en route (passerelle lente, réseau) : la décision a pu être enregistrée. */
const REPONSE_PERDUE = new Set([0, 502, 503, 504]);
const ACCEPTEES = new Set<EtatDemande>(['EN_COURS', 'PARTIELLE', 'COMPLETE']);
const REFUSEES = new Set<EtatDemande>(['REFUSEE']);

/**
 * Écran d'accord : le citoyen lit qui demande quoi, pourquoi et pour combien de temps,
 * puis autorise ou refuse. Pour une demande par code ou par notification, autoriser exige
 * de recopier le code affiché par le service : un accord ne se donne pas à distance, sur la
 * foi d'un message. Atteint par QR code, par code court, par redirection depuis
 * un service ou depuis la liste des demandes en attente.
 */
@Component({
  selector: 'ec-accord',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Alerte,
    Champ,
    DemandeAccesVue,
    Lien,
    Saisie,
    Squelette,
  ],
  templateUrl: './accord.html',
})
export class Accord {
  private readonly demandes = inject(Demandes);
  private readonly rediriger = inject(REDIRECTION);
  private readonly focus = focusSur();

  /** Paramètre `:id` de la route. */
  readonly id = input.required<string>();

  protected readonly demande = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.demandes.ouvrir(params),
  });
  protected readonly vue = computed(() => {
    const demande = this.demande.value();
    return demande ? presenter(demande) : null;
  });
  /** Pourquoi il n'y a plus rien à décider, pour une demande qui n'attend plus. */
  protected readonly close = computed(() => {
    const etat = this.demande.value()?.etat;
    return !etat || etat === 'EN_ATTENTE_ACCORD'
      ? null
      : (DEMANDES_CLOSES[etat] ?? DEMANDE_ACCEPTEE);
  });
  protected readonly introuvable = computed(() =>
    this.demande.error() ? lireProbleme(this.demande.error()).message : null,
  );

  protected readonly codeExige = computed(
    () => this.demande.value()?.code_verification_exige === true,
  );
  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly erreurCode = signal<string | null>(null);

  protected readonly envoi = signal(false);
  protected readonly echec = signal<string | null>(null);
  /** Décision enregistrée, quand le citoyen reste dans son espace. */
  protected readonly decision = signal<Decision | null>(null);
  protected readonly retourEnCours = signal(false);
  /** La décision est enregistrée, mais sa réponse s'est perdue : pas de retour automatique. */
  protected readonly reponseTardive = signal(false);

  protected decider(decision: Decision): void {
    const code = this.code.value.replace(/\s/g, '');
    const avecCode = decision === 'ACCEPTER' && this.codeExige();
    this.erreurCode.set(
      avecCode && !CODE_SERVICE.test(code)
        ? 'Saisissez les 6 chiffres affichés par le service.'
        : null,
    );
    if (this.erreurCode()) {
      this.focus('#code-service');
      return;
    }
    this.envoi.set(true);
    this.echec.set(null);
    this.demandes.decider(this.id(), decision, avecCode ? code : undefined).subscribe({
      next: (resultat) => {
        const retour = adresseDeRetour(resultat.retour_url);
        if (retour) {
          // `envoi` reste vrai : la page part vers le service, plus rien n'est à cliquer.
          this.retourEnCours.set(true);
          this.rediriger(retour);
          return;
        }
        this.envoi.set(false);
        this.decision.set(decision);
        this.focus('main h1');
      },
      error: (erreur: unknown) => {
        const probleme = lireProbleme(erreur);
        if (REPONSE_PERDUE.has(probleme.statut)) {
          this.verifierApresCoupure(decision, probleme.message);
          return;
        }
        this.envoi.set(false);
        this.echec.set(probleme.message);
        this.code.reset();
        // La demande a pu expirer ou être déjà traitée : l'écran se remet à jour.
        this.demande.reload();
        this.focus('main sira-alerte');
      },
    });
  }

  /**
   * La réponse ne nous est pas parvenue : relit la demande avant de parler d'échec. Si elle porte
   * déjà la décision du citoyen, c'est un succès ; sinon l'erreur s'affiche et il peut réessayer.
   */
  private verifierApresCoupure(decision: Decision, message: string): void {
    const attendu = decision === 'ACCEPTER' ? ACCEPTEES : REFUSEES;
    this.demandes.ouvrir(this.id()).subscribe({
      next: (demande) => {
        this.envoi.set(false);
        if (attendu.has(demande.etat)) {
          this.reponseTardive.set(true);
          this.decision.set(decision);
          this.focus('main h1');
          return;
        }
        this.echec.set(message);
        this.code.reset();
        this.demande.reload();
        this.focus('main sira-alerte');
      },
      error: () => {
        this.envoi.set(false);
        this.echec.set(message);
        this.code.reset();
        this.focus('main sira-alerte');
      },
    });
  }
}
