import { TestBed } from '@angular/core/testing';
import { DemandeAcces, DemandeAccesVue, phraseDuree } from './demande-acces';

const DEMANDE: DemandeAcces = {
  demandeur: { nom: 'Agence des concours' },
  finalite: 'Vérifier les conditions du concours.',
  informations: [
    { libelle: 'Nationalité', niveau: 'STATUT', source: 'Direction de la nationalité' },
    { libelle: 'Diplôme', niveau: 'DOCUMENT', source: 'Office du baccalauréat' },
  ],
  dureeJours: 30,
  codeVerification: '492113',
};

async function afficher(entrees: Record<string, unknown> = {}) {
  const vue = TestBed.createComponent(DemandeAccesVue);
  vue.componentRef.setInput('demande', DEMANDE);
  for (const [nom, valeur] of Object.entries(entrees)) {
    vue.componentRef.setInput(nom, valeur);
  }
  await vue.whenStable();
  return { vue, page: vue.nativeElement as HTMLElement };
}

describe('DemandeAccesVue', () => {
  it('dit qui demande, pourquoi, quoi et sous quelle forme', async () => {
    const { page } = await afficher();
    const texte = page.textContent;

    expect(texte).toContain('Agence des concours');
    expect(texte).toContain('Vérifier les conditions du concours.');
    expect(texte).toContain('Réponse : oui ou non');
    expect(texte).toContain('Document PDF');
    expect(texte).toContain('Source : Direction de la nationalité');
    expect(texte).toContain('Pendant 30 jours');
    expect(page.querySelector('.sira-code__chiffres')?.textContent).toContain('492');
  });

  it('donne le même poids à « Autoriser » et à « Refuser »', async () => {
    const { vue, page } = await afficher();
    const [autoriser, refuser] = Array.from(page.querySelectorAll('button'));
    const decisions: string[] = [];
    vue.componentInstance.autoriser.subscribe(() => decisions.push('autoriser'));
    vue.componentInstance.refuser.subscribe(() => decisions.push('refuser'));

    expect(autoriser.textContent).toContain('Autoriser');
    expect(refuser.textContent).toContain('Refuser');
    expect(autoriser.className).toBe(refuser.className);
    expect(autoriser.parentElement?.classList).toContain('sira-groupe-boutons--egaux');

    autoriser.click();
    refuser.click();
    expect(decisions).toEqual(['autoriser', 'refuser']);
  });

  it('n’émet rien pendant l’envoi de la réponse', async () => {
    const { vue, page } = await afficher({ enCours: true });
    let emis = false;
    vue.componentInstance.autoriser.subscribe(() => (emis = true));

    page.querySelector('button')?.click();
    expect(emis).toBe(false);
  });

  it('n’affiche aucun bouton en consultation', async () => {
    const { page } = await afficher({ avecDecision: false });
    expect(page.querySelector('button')).toBeNull();
  });
});

describe('phraseDuree', () => {
  it('distingue la consultation unique de l’accès dans la durée', () => {
    expect(phraseDuree(0)).toContain('Une seule consultation');
    expect(phraseDuree(30, new Date(2026, 10, 16))).toContain('jusqu’au 16 décembre 2026');
  });
});
