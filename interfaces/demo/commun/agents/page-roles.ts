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
  Dialogue,
  Etiquette,
  Icone,
  Saisie,
  Squelette,
  Tableau,
} from '@sira/ui';
import { Observable, forkJoin } from 'rxjs';
import { Agents, Permission, ROLES_GERER, Role } from './agents';

interface Groupe {
  nom: string;
  permissions: Permission[];
}

/**
 * Les rôles de l'administration : chacun regroupe des droits. Les trois rôles fournis
 * (Administrateur, Agent, Lecteur) ne se suppriment pas ; l'Administrateur garde tous les droits.
 */
@Component({
  selector: 'demo-page-roles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    Champ,
    Dialogue,
    Etiquette,
    Icone,
    Saisie,
    Squelette,
    Tableau,
  ],
  templateUrl: './page-roles.html',
})
export class PageRoles {
  private readonly agents = inject(Agents);
  private readonly session = inject(Session);

  protected readonly gerer = computed(() => this.session.peut(ROLES_GERER));

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'libelle', libelle: 'Rôle' },
    { cle: 'permissions', libelle: 'Droits', tri: false, filtre: false },
    { cle: 'utilisateurs', libelle: 'Utilisateurs', numerique: true },
  ];

  protected readonly donnees = rxResource({
    stream: () => forkJoin({ roles: this.agents.roles(), permissions: this.agents.permissions() }),
  });
  protected readonly probleme = computed(() =>
    this.donnees.error() ? lireProbleme(this.donnees.error()).message : null,
  );
  protected readonly groupes = computed<Groupe[]>(() => {
    const groupes = new Map<string, Permission[]>();
    for (const permission of this.donnees.value()?.permissions ?? []) {
      groupes.set(permission.groupe, [...(groupes.get(permission.groupe) ?? []), permission]);
    }
    return [...groupes].map(([nom, permissions]) => ({ nom, permissions }));
  });
  protected readonly message = signal<string | null>(null);

  protected readonly fenetre = signal<{ role: Role | null } | null>(null);
  protected libelle = '';
  protected description = '';
  protected readonly cochees = signal<Set<string>>(new Set());
  protected readonly erreurs = signal<Record<string, string>>({});
  protected readonly echec = signal<string | null>(null);
  protected readonly envoi = signal(false);

  protected readonly aSupprimer = signal<Role | null>(null);
  protected readonly echecSuppression = signal<string | null>(null);

  protected libellePermission(code: string): string {
    return this.donnees.value()?.permissions.find((p) => p.code === code)?.libelle ?? code;
  }

  protected resumeDroits(role: Role): string {
    const total = this.donnees.value()?.permissions.length ?? 0;
    if (role.permissions.length === total && total > 0) {
      return 'Tous les droits';
    }
    return role.permissions.length === 1 ? '1 droit' : `${role.permissions.length} droits`;
  }

  /** L'administrateur garde tous les droits : sa fenêtre est en lecture seule. */
  protected verrouille(role: Role | null): boolean {
    return role?.code === 'administrateur';
  }

  protected ajouter(): void {
    this.ouvrir(null);
  }

  protected ouvrir(role: Role | null): void {
    this.libelle = role?.libelle ?? '';
    this.description = role?.description ?? '';
    this.cochees.set(new Set(role?.permissions ?? []));
    this.erreurs.set({});
    this.echec.set(null);
    this.fenetre.set({ role });
  }

  protected basculer(code: string, coche: boolean): void {
    this.cochees.update((cochees) => {
      const suivantes = new Set(cochees);
      if (coche) {
        suivantes.add(code);
      } else {
        suivantes.delete(code);
      }
      return suivantes;
    });
  }

  protected basculerGroupe(groupe: Groupe, coche: boolean): void {
    for (const permission of groupe.permissions) {
      this.basculer(permission.code, coche);
    }
  }

  protected groupeCoche(groupe: Groupe): boolean {
    return groupe.permissions.every((p) => this.cochees().has(p.code));
  }

  protected enregistrer(): void {
    const fenetre = this.fenetre();
    if (!fenetre) {
      return;
    }
    const saisie = {
      libelle: this.libelle.trim(),
      description: this.description.trim(),
      permissions: [...this.cochees()],
    };
    const erreurs: Record<string, string> = {};
    if (!saisie.libelle) {
      erreurs['libelle'] = 'Saisissez le nom du rôle. Exemple : Chef de service';
    }
    if (saisie.permissions.length === 0) {
      erreurs['permissions'] = 'Cochez au moins un droit.';
    }
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) {
      setTimeout(() => document.getElementById(`role-${Object.keys(erreurs)[0]}`)?.focus());
      return;
    }
    const appel: Observable<Role> = fenetre.role
      ? this.agents.modifierRole(fenetre.role.id, saisie)
      : this.agents.creerRole(saisie);
    this.envoi.set(true);
    appel.subscribe({
      next: (role) => {
        this.envoi.set(false);
        this.fenetre.set(null);
        this.message.set(
          fenetre.role
            ? `Le rôle « ${role.libelle} » est à jour.`
            : `Le rôle « ${role.libelle} » est créé.`,
        );
        this.donnees.reload();
      },
      error: (erreur: unknown) => {
        this.envoi.set(false);
        const refus = lireProbleme(erreur);
        this.erreurs.set(refus.champs);
        if (Object.keys(refus.champs).length === 0) {
          this.echec.set(refus.message);
        }
      },
    });
  }

  protected supprimer(role: Role): void {
    this.echecSuppression.set(null);
    this.agents.supprimerRole(role.id).subscribe({
      next: () => {
        this.aSupprimer.set(null);
        this.message.set(`Le rôle « ${role.libelle} » est supprimé.`);
        this.donnees.reload();
      },
      error: (erreur: unknown) => this.echecSuppression.set(lireProbleme(erreur).message),
    });
  }
}
