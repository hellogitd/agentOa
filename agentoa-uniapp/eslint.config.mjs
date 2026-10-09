import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import globals from 'globals';

/** uni-app 全局对象（H5/小程序/App），见 @dcloudio/types */
const uniGlobals = {
  uni: 'readonly',
  wx: 'readonly',
  plus: 'readonly',
  UniApp: 'readonly',
  getCurrentPages: 'readonly',
  getApp: 'readonly',
  App: 'readonly',
  Page: 'readonly',
  Component: 'readonly',
  uniCloud: 'readonly',
  describe: 'readonly',
  it: 'readonly',
  expect: 'readonly'
};

export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'src/static/**']
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...uniGlobals
      }
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }],
      'no-undef': 'error',
      'no-empty': ['error', { allowEmptyCatch: true }],
      'no-console': 'off',
      eqeqeq: ['error', 'smart'],
      'prefer-const': 'error',
      'no-var': 'error'
    }
  },
  {
    files: ['scripts/**/*.mjs'],
    languageOptions: {
      globals: { ...globals.node }
    }
  }
];
