import { TestBed } from '@angular/core/testing';
import { Fichier } from './fichier';

describe('Fichier', () => {
  async function afficher(entrees: Record<string, string | null>) {
    const vue = TestBed.createComponent(Fichier);
    for (const [cle, valeur] of Object.entries(entrees)) {
      vue.componentRef.setInput(cle, valeur);
    }
    await vue.whenStable();
    return vue.nativeElement as HTMLElement;
  }

  it('prévisualise la pièce dans la page et la télécharge', async () => {
    const page = await afficher({
      nom: 'cv.pdf',
      details: 'Déclaré par vous · 12 Ko',
      apercu: '/api/piece?apercu=true',
      telechargement: '/api/piece',
    });
    expect(page.classList).toContain('sira-fichier');
    expect(page.querySelector('.sira-fichier__nom')?.textContent).toContain('cv.pdf');
    expect(page.textContent).toContain('Déclaré par vous · 12 Ko');

    // L'aperçu s'ouvre dans une fenêtre de la page (pdf.js), pas dans un nouvel onglet.
    const apercu = Array.from(page.querySelectorAll('button')).find((bouton) =>
      bouton.textContent?.includes('Prévisualiser'),
    );
    expect(apercu).toBeTruthy();
    const telechargement = page.querySelector('a[download]');
    expect(telechargement?.textContent).toContain('Télécharger');
    expect(telechargement?.getAttribute('href')).toBe('/api/piece');
  });

  it('n’offre aucun lien tant que la pièce n’est pas là', async () => {
    const page = await afficher({ nom: 'Extrait d’acte de naissance', details: 'En attente' });
    expect(page.querySelectorAll('a')).toHaveLength(0);
  });
});
