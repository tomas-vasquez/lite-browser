# Ultralite Browser

Navegador ultraligero para Android hecho con React (Vite) + Capacitor.

## Propósito

Ultralite Browser no es solo un navegador: está pensado como **herramienta de desarrollo** para cuando programas desde Android. Permite **desplegar y abrir servidores de desarrollo directamente en el teléfono**:

- Conecta a tu `localhost` o a los servidores de desarrollo (Vite, webpack dev server, Node, etc.) levantados en tu equipo o en la misma red LAN.
- Carga páginas reales en `WebView` a pantalla completa por pestaña, sin restricciones de iframes.
- Recarga, navega y prueba tu app en el dispositivo mientras programas, con pestañas, historial y favoritos ligeros.

Ideal para streams de desarrollo, pruebas rápidas en Android o despliegues locales sin publicar nada.

## Características

- **Interfaz (web shell):** mini home con búsqueda e historial reciente, favoritos persistentes y selector de pestañas en bottom sheet.
- **Páginas:** una `android.webkit.WebView` nativa a pantalla completa por pestaña (ideal para sitios que bloquean iframes). En modo web (dev) se usa un stack de iframes persistentes como fallback.
- **Navegación:** FAB nativo flotante y arrastrable para cambiar de pestaña, y botón atrás del sistema gestionado de forma nativa (historia real por pestaña).
- **Footer con créditos:** en la pantalla de inicio se muestra la versión, el GitHub y el repositorio del proyecto.

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