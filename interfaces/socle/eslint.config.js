// @ts-check
const configurer = require('./outils/eslint-commun.js');

const modules = {
  eslint: require('@eslint/js'),
  tseslint: require('typescript-eslint'),
  angular: require('angular-eslint'),
  prettier: require('eslint-config-prettier'),
};

/** Préfixes de sélecteurs : « sira » pour les bibliothèques, deux lettres par application. */
module.exports = configurer(modules, ['sira', 'ec', 'co', 'vi']);
