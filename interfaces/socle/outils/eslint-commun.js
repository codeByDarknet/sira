// @ts-check

/**
 * Règles ESLint communes aux espaces de travail Angular de Sira. Chaque espace passe ses
 * propres modules : ils sont résolus depuis son `node_modules`, pas depuis celui du socle.
 *
 * @param {{ eslint: any, tseslint: any, angular: any, prettier: any }} modules
 * @param {string[]} prefixes préfixes de sélecteurs admis (« sira » et un par application)
 */
module.exports = function configurer({ eslint, tseslint, angular, prettier }, prefixes) {
  return tseslint.config(
    { ignores: ['dist/', '.angular/', 'out-tsc/', 'node_modules/'] },
    {
      files: ['**/*.ts'],
      extends: [
        eslint.configs.recommended,
        ...tseslint.configs.strict,
        ...tseslint.configs.stylistic,
        ...angular.configs.tsRecommended,
        prettier,
      ],
      processor: angular.processInlineTemplates,
      rules: {
        '@angular-eslint/directive-selector': [
          'error',
          { type: 'attribute', prefix: prefixes, style: 'camelCase' },
        ],
        '@angular-eslint/component-selector': [
          'error',
          { type: ['element', 'attribute'], prefix: prefixes, style: 'kebab-case' },
        ],
        '@angular-eslint/prefer-on-push-component-change-detection': 'error',
        '@angular-eslint/prefer-signals': 'error',
        // Un composant sans style propre : les styles vivent dans la couche CSS (CSP sans style en ligne).
        '@angular-eslint/component-max-inline-declarations': ['error', { template: 80, styles: 0 }],
        'no-restricted-syntax': [
          'error',
          {
            selector: "Property[key.name='styleUrl'], Property[key.name='styleUrls']",
            message: 'Pas de style de composant : écrivez les classes dans la couche CSS.',
          },
        ],
        // Un composant ou une directive sans membre reste une classe légitime.
        '@typescript-eslint/no-extraneous-class': ['error', { allowWithDecorator: true }],
        eqeqeq: ['error', 'always'],
        'no-console': 'error',
      },
    },
    {
      files: ['**/*.html'],
      extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
      rules: {
        // Un attribut style écrit dans un gabarit serait bloqué par la politique de sécurité du contenu.
        '@angular-eslint/template/no-inline-styles': 'error',
        // Les boutons à icône seule (`siraAction`) portent leur nom par l'entrée `libelle`,
        // posée par le composant en aria-label et title.
        '@angular-eslint/template/elements-content': ['error', { allowList: ['libelle'] }],
      },
    },
    {
      files: ['outils/**/*.mjs'],
      extends: [eslint.configs.recommended],
      languageOptions: { globals: { process: 'readonly', console: 'readonly' } },
    },
  );
};
