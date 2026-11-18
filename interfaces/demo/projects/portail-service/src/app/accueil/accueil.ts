import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CONFIGURATION } from '@demo/commun';
import { Alerte, Bouton, Icone, Squelette } from '@sira/ui';
import { ETAPES } from '../parcours/etapes';
import { nomRegistre } from '../parcours/libelles';
import { Parcours } from '../parcours/parcours';

/** Accueil de la démarche : ce qui sera vérifié, et les pièces que la personne n'aura plus à fournir. */
@Component({
  selector: 'ps-accueil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Alerte, Bouton, Icone, Squelette],
  templateUrl: './accueil.html',
  host: { class: 'ps-accueil sv-pleine' },
})
export class Accueil {
  private readonly parcours = inject(Parcours);
  protected readonly configuration = inject(CONFIGURATION);
  protected readonly etapes = ETAPES;
  protected readonly nomRegistre = nomRegistre;

  protected readonly demarche = rxResource({ stream: () => this.parcours.demarche() });
  protected readonly indisponible = computed(() => this.demarche.error() !== undefined);
  /** Lire la valeur d'une ressource en erreur lèverait l'erreur : on ne la lit que présente. */
  protected readonly presentation = computed(() =>
    this.demarche.hasValue() ? this.demarche.value() : undefined,
  );
}
