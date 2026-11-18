import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Champ, Saisie } from './champ';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Champ, Saisie],
  template: `<sira-champ
    libelle="Numéro"
    aide="12 chiffres"
    identifiant="numero"
    [erreur]="erreur()"
  >
    <input siraSaisie />
  </sira-champ>`,
})
class Hote {
  readonly erreur = signal<string | null>(null);
}

describe('Champ', () => {
  it('relie le libellé, l’aide et l’erreur à la saisie', async () => {
    const hote = TestBed.createComponent(Hote);
    await hote.whenStable();
    const page = hote.nativeElement as HTMLElement;
    const saisie = page.querySelector('input');

    expect(saisie?.id).toBe('numero');
    expect(page.querySelector('label')?.getAttribute('for')).toBe('numero');
    expect(saisie?.getAttribute('aria-describedby')).toBe('numero-aide');
    expect(saisie?.hasAttribute('aria-invalid')).toBe(false);

    hote.componentInstance.erreur.set('Saisissez les 12 chiffres.');
    await hote.whenStable();

    expect(saisie?.getAttribute('aria-invalid')).toBe('true');
    expect(saisie?.getAttribute('aria-describedby')).toBe('numero-aide numero-erreur');
    expect(page.querySelector('#numero-erreur')?.textContent).toContain(
      'Saisissez les 12 chiffres.',
    );
  });
});
