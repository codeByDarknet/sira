// @ts-check
const configurer = require('../socle/outils/eslint-commun.js');

const modules = {
  eslint: require('@eslint/js'),
  tseslint: require('typescript-eslint'),
  angular: require('angular-eslint'),
  prettier: require('eslint-config-prettier'),
};

/** Préfixes de sélecteurs : « demo » pour le dossier commun, deux lettres par application. */
module.exports = [
  { ignores: ['bibliotheques/'] },
  ...configurer(modules, ['demo', 'ps', 'ra', 'te']),
];
