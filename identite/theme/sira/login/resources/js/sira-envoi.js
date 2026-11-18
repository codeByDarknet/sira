// Pendant l'envoi d'un formulaire, le bouton principal montre une roue de chargement à la place
// de son icône (css/sira-connexion.css, classe sira-envoi). Écoute posée sur le document : le
// script est chargé dans <head>, avant les formulaires.
(function () {
  'use strict';
  document.addEventListener('submit', function (evenement) {
    var formulaire = evenement.target;
    var bouton = evenement.submitter || formulaire.querySelector('button[type="submit"], input[type="submit"]');
    if (!bouton || !bouton.classList.contains('pf-m-primary')) {
      return;
    }
    bouton.classList.add('sira-envoi');
    bouton.setAttribute('aria-busy', 'true');
  });
  // Retour arrière : la page revient du cache du navigateur, le bouton redevient normal.
  window.addEventListener('pageshow', function () {
    document.querySelectorAll('.sira-envoi').forEach(function (bouton) {
      bouton.classList.remove('sira-envoi');
      bouton.removeAttribute('aria-busy');
      bouton.disabled = false;
    });
  });
})();
