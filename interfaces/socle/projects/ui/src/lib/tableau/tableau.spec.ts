import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Cellule, ColonneTableau, Tableau } from './tableau';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Tableau, Cellule],
  template: `<sira-tableau legende="Accès" [colonnes]="colonnes" [lignes]="lignes">
    <ng-template siraCellule="etat" let-ligne
      ><strong>{{ ligne.etat }}</strong></ng-template
    >
  </sira-tableau>`,
})
class Hote {
  readonly colonnes: ColonneTableau[] = [
    { cle: 'service', libelle: 'Service' },
    { cle: 'etat', libelle: 'État' },
    { cle: 'fin', libelle: 'Fin' },
  ];
  readonly lignes = [{ service: 'Agence des concours', etat: 'En cours' }];
}

describe('Tableau', () => {
  it('porte le libellé de colonne sur chaque cellule, pour l’affichage en cartes', async () => {
    const hote = TestBed.createComponent(Hote);
    await hote.whenStable();
    const cellules = Array.from((hote.nativeElement as HTMLElement).querySelectorAll('td'));

    expect(cellules.map((cellule) => cellule.dataset['libelle'])).toEqual([
      'Service',
      'État',
      'Fin',
    ]);
    expect(cellules[0].textContent.trim()).toBe('Agence des concours');
    expect(cellules[1].querySelector('strong')?.textContent).toBe('En cours');
    expect(cellules[2].textContent.trim()).toBe('—');
  });
});
