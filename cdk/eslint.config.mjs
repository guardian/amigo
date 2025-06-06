import guardian from '@guardian/eslint-config';
import prettier from 'eslint-plugin-prettier';

export default [
  ...guardian.configs.recommended,
  ...guardian.configs.jest,
  {
    plugins: {
      prettier,
    },
    rules: {
      'prettier/prettier': 'error',
    },
  },
];
