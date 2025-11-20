import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
      '@img': '/src/assets/img',
      '@ts': '/src/assets/ts',
      '@comp': '/src/components',
      '@axios': '/src/axios/request'
    }
  },
  css: {
    preprocessorOptions: {
      // css: {
      //   additionalData: `@import "@arcgis/core/assets/esri/themes/light/main.css";`,
      // },
    },
  },
})
