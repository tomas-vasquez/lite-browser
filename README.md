# Ultralite Browser

Navegador ultraligero para Android hecho con React (Vite) + Capacitor.

- **Interfaz (web shell):** mini home con búsqueda e historial reciente, selector de pestañas en bottom sheet.
- **Páginas:** una `android.webkit.WebView` nativa a pantalla completa por pestaña (ideal para sitios que bloquean iframes). En modo web (dev) se usa un stack de iframes persistentes como fallback.
- **Navegación:** FAB nativo flotante para cambiar de pestaña y botón atrás del sistema gestionado de forma nativa (historia real por pestaña).

## Desarrollo web

```bash
npm i
npm run dev
```

En modo web cada página se pinta en un iframe; algunos sitios (X-Frame-Options/CSP) se verán limitados y mostrarán el banner de respaldo.

## Build APK

El APK se compila en **GitHub Actions** (no requiere Android SDK local):

1. Sube el repo a GitHub.
2. `Actions` → `Build Android APK` → *Run workflow* (o `git tag v1.0.0 && git push --tags`).
3. Descarga el APK desde el artefacto `ultralite-browser-apk`.

Compilación manual (con Android SDK + JDK 21):

```bash
npm run build:android
cd android
./gradlew assembleDebug
# APK en android/app/build/outputs/apk/debug/
```

## Estructura

- `src/App.jsx` — shell React: pestañas, mini home, switcher.
- `src/browser-tabs.js` — puente JS al plugin nativo (`Capacitor.registerPlugin('TabWebView')`).
- `android/app/src/main/java/com/ultralite/browser/TabWebViewPlugin.java` — plugin Java: WebViews nativas por pestaña, FAB y manejo del back del sistema.
- `.github/workflows/build-android.yml` — CI para compilar el APK.