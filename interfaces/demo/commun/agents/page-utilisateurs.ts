import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Session, lireProbleme } from '@sira/coquille';
import {
  Action,
  ActionsLigne,
  Alerte,
  Bouton,
  Cellule,
  Champ,
  ColonneTableau,
  DatePipe,
  Dialogue,
  Etiquette,
  Icone,
  RequeteTableau,
  Saisie,
  Squelette,
  Tableau,
} from '@sira/ui';
import { Observable } from 'rxjs';
import { Agents, SaisieUtilisateur, UTILISATEURS_GERER, Utilisateur } from './agents';

const VIDE: SaisieUtilisateur = { prenom: '', nom: '', email: '', role: '', actif: true };
const COURRIEL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/**
 * Les agents de l'administration : liste, ajout, modification, nouveau mot de passe, retrait.
 * Un agent ajouté reçoit par courriel son identifiant et un mot de passe provisoire ; à sa première
 * connexion, il choisit son mot de passe puis enregistre Google Authenticator.
 */
@Component({
  selector: 'demo-page-utilisateurs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    Champ,
    DatePipe,
    Dialogue,
    Etiquette,
    Icone,
    Saisie,
    Squelette,
    Tableau,
  ],
  templateUrl: './page-utilisateurs.html',
})
export class PageUtilisateurs {
  private readonly agents = inject(Agents);
  private readonly session = inject(Session);

  protected readonly gerer = computed(() => this.session.peut(UTILISATEURS_GERER));
  protected readonly moi = computed(() => this.session.utilisateur()?.identifiant ?? '');

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'nom', libelle: 'Nom et prénom' },
    { cle: 'email', libelle: 'Adresse électronique' },
    { cle: 'libelle_role', libelle: 'Rôle', tri: false },
    { cle: 'actif', libelle: 'État', tri: false },
    { cle: 'cree_le', libelle: 'Créé le', tri: false },
  ];

  private readonly requete = signal({ page: 1, recherche: '' });
  protected readonly liste = rxResource({
    params: () => this.requete(),
    stream: ({ params }) => this.agents.utilisateurs(params.page, 10, params.recherche),
  });
  protected readonly roles = rxResource({ stream: () => this.agents.roles() });
  protected readonly probleme = computed(() =>
    this.liste.error() ? lireProbleme(this.liste.error()).message : null,
  );

  protected readonly message = signal<{ type: 'succes' | 'attention'; texte: string } | null>(null);

  // --- Fenêtre d'ajout ou de modification
  protected readonly fenetre = signal<{ utilisateur: Utilisateur | null } | null>(null);
  protected saisie: SaisieUtilisateur = { ...VIDE };
  protected readonly erreurs = signal<Record<string, string>>({});
  protected readonly echec = signal<string | null>(null);
  protected readonly envoi = signal(false);

  // --- Confirmations
  protected readonly aSupprimer = signal<Utilisateur | null>(null);
  protected readonly aReinitialiser = signal<Utilisateur | null>(null);
  protected readonly echecAction = signal<string | null>(null);
  protected readonly action = signal(false);

  /** Page ou recherche demandée par le tableau ; une demande identique ne relance rien. */
  protected demander(requete: RequeteTableau): void {
    const suivante = { page: requete.page, recherche: requete.recherche.trim() };
    if (suivante.recherche !== this.requete().recherche) {
      suivante.page = 1;
    }
    const actuelle = this.requete();
    if (suivante.page !== actuelle.page || suivante.recherche !== actuelle.recherche) {
      this.requete.set(suivante);
    }
  }

  protected ajouter(): void {
    this.saisie = { ...VIDE, role: 'agent' };
    this.ouvrir(null);
  }

  protected modifier(utilisateur: Utilisateur): void {
    this.saisie = {
      prenom: utilisateur.prenom,
      nom: utilisateur.nom,
      email: utilisateur.email,
      role: utilisateur.role ?? '',
      actif: utilisateur.actif,
    };
    this.ouvrir(utilisateur);
  }

  private ouvrir(utilisateur: Utilisateur | null): void {
    this.erreurs.set({});
    this.echec.set(null);
    this.fenetre.set({ utilisateur });
  }

  protected enregistrer(): void {
    const fenetre = this.fenetre();
    if (!fenetre) {
      return;
    }
    const saisie: SaisieUtilisateur = {
      prenom: this.saisie.prenom.trim(),
      nom: this.saisie.nom.trim(),
      email: this.saisie.email.trim().toLowerCase(),
      role: this.saisie.role,
      actif: this.saisie.actif,
    };
    const erreurs = verifier(saisie);
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) {
      premiereErreur(Object.keys(erreurs)[0]);
      return;
    }
    const existant = fenetre.utilisateur;
    if (existant) {
      this.envoyer(this.agents.modifierUtilisateur(existant.id, saisie), () =>
        this.terminer('succes', `La fiche de ${saisie.prenom} ${saisie.nom} est à jour.`),
      );
    } else {
      this.envoyer(this.agents.creerUtilisateur(saisie), (reponse) =>
        reponse.courriel_envoye
          ? this.terminer(
              'succes',
              `${saisie.prenom} ${saisie.nom} est ajouté. Ses identifiants lui ont été envoyés à ${saisie.email}.`,
            )
          : this.terminer(
              'attention',
              `${saisie.prenom} ${saisie.nom} est ajouté, mais le courriel n’est pas parti. Utilisez « Nouveau mot de passe » pour le renvoyer.`,
            ),
      );
    }
  }

  private envoyer<T>(appel: Observable<T>, suite: (reponse: T) => void): void {
    this.envoi.set(true);
    this.echec.set(null);
    appel.subscribe({
      next: (reponse) => {
        this.envoi.set(false);
        suite(reponse);
      },
      error: (erreur: unknown) => {
        this.envoi.set(false);
        const refus = lireProbleme(erreur);
        this.erreurs.set(refus.champs);
        if (Object.keys(refus.champs).length > 0) {
          premiereErreur(Object.keys(refus.champs)[0]);
        } else {
          this.echec.set(refus.message);
        }
      },
    });
  }

  private terminer(type: 'succes' | 'attention', texte: string): void {
    this.fenetre.set(null);
    this.message.set({ type, texte });
    this.liste.reload();
    this.roles.reload();
  }

  protected supprimer(utilisateur: Utilisateur): void {
    this.agir(this.agents.supprimerUtilisateur(utilisateur.id), () => {
      this.aSupprimer.set(null);
      this.terminer('succes', `${utilisateur.prenom} ${utilisateur.nom} a été retiré.`);
    });
  }

  protected reinitialiser(utilisateur: Utilisateur): void {
    this.agir(this.agents.reinitialiser(utilisateur.id), (reponse) => {
      this.aReinitialiser.set(null);
      this.terminer(
        reponse.courriel_envoye ? 'succes' : 'attention',
        reponse.courriel_envoye
          ? `Un nouveau mot de passe provisoire a été envoyé à ${utilisateur.email}.`
          : 'Le mot de passe a été changé, mais le courriel n’est pas parti. Réessayez.',
      );
    });
  }

  private agir<T>(appel: Observable<T>, suite: (reponse: T) => void): void {
    this.action.set(true);
    this.echecAction.set(null);
    appel.subscribe({
      next: (reponse) => {
        this.action.set(false);
        suite(reponse);
      },
      error: (erreur: unknown) => {
        this.action.set(false);
        this.echecAction.set(lireProbleme(erreur).message);
      },
    });
  }

  protected fermerConfirmations(): void {
    this.aSupprimer.set(null);
    this.aReinitialiser.set(null);
    this.echecAction.set(null);
  }
}

function verifier(saisie: SaisieUtilisateur): Record<string, string> {
  const erreurs: Record<string, string> = {};
  if (!saisie.prenom) {
    erreurs['prenom'] = 'Saisissez le prénom.';
  }
  if (!saisie.nom) {
    erreurs['nom'] = 'Saisissez le nom.';
  }
  if (!COURRIEL.test(saisie.email)) {
    erreurs['email'] = 'Saisissez une adresse valide. Exemple : awa.ouedraogo@exemple.bf';
  }
  if (!saisie.role) {
    erreurs['role'] = 'Choisissez un rôle.';
  }
  return erreurs;
}

/** Le curseur va dans le premier champ refusé. */
function premiereErreur(champ: string): void {
  setTimeout(() => document.getElementById(`utilisateur-${champ}`)?.focus());
}
