module.exports = {
  root: true,
  env: { browser: true, es2020: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime', // 支持 React 19 JSX 自动导入
    'prettier', // 禁用与 Prettier 冲突的 ESLint 规则
    'plugin:prettier/recommended' // 集成 Prettier 到 ESLint
  ],
  ignorePatterns: ['dist', '.eslintrc.cjs'],
  parser: '@typescript-eslint/parser',
  plugins: ['react-refresh'],
  rules: {
    // React 规则
    'react/prop-types': 'off', // TypeScript 已做类型检查，关闭 prop-types
    'react/react-in-jsx-scope': 'off', // React 19 无需显式导入 React
    // 代码风格
    'no-console': ['warn', { allow: ['warn', 'error'] }], // 允许 warn/error 控制台输出
    'indent': ['error', 2], // 缩进 2 空格
    'quotes': ['error', 'single'], // 单引号
    'semi': ['error', 'always'], // 强制分号
    // TypeScript 规则
    '@typescript-eslint/no-explicit-any': 'warn', // 不允许显式 any（警告级别）
    '@typescript-eslint/explicit-module-boundary-types': 'off', // 简化导出类型
    // 开发体验
    'react-refresh/only-export-components': [
      'warn',
      { allowConstantExport: true }
    ]
  },
  settings: {
    react: {
      version: 'detect' // 自动检测 React 版本
    }
  }
};