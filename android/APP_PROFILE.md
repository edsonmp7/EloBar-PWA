# Elo Bar — Perfil da Casca Android

Candidata: **1.0.7** (`versionCode 8`).

Este arquivo declara os parâmetros variáveis do APK Elo Bar. O comportamento universal segue o documento canônico **Padrão Universal — Casca Android APK Elo v2.0 (2026-09-06)**.

| Parâmetro | Elo Bar |
|---|---|
| `APP_DISPLAY_NAME` | Elo Bar |
| `APPLICATION_ID` | `com.eloclub.elobar` |
| `WEB_URL` | `https://script.google.com/macros/s/AKfycbyd7UHyQFJA4SsFZuKWmAO___NnfGXq0oNB0M0NWnG2hhLmPHcKTL_ck4yDgB4IqSkOnQ/exec?shell=android-native` |
| `VERSION_NAME` | `1.0.7` |
| `VERSION_CODE` | `8` |
| `ICON_SOURCE` | `res/drawable-nodpi/app_icon_foreground.jpg` — SHA-256 `d5a6bd0fdf2a037fba9233170462fcef2f49267562cd8e067f97f5d902073080` |
| `ICON_BACKGROUND` | `#0F0F0F` |
| `LAUNCHER_FOREGROUND_INSET_PCT` | `15%` inicial; autoridade única em `app_foreground_safe.xml` |
| `PACKAGE_ICON_SCALE_PCT` | fonte oficial integral, validada separadamente no aparelho real |
| `SPLASH_SOURCE` | `res/drawable-nodpi/app_splash_full.jpg` — SHA-256 `d09d2d49c9197f4c97a396317beaf794dfb1c105b0ba1380fb0e302495e82f8c` |
| `SPLASH_BACKGROUND` | `#0D0803` |
| `TAGLINE` | nenhuma tagline nativa adicional; a arte oficial já contém “Gestão Inteligente” |
| `LOADING_MARK` | garrafa dourada em linhas fluidas servindo copo baixo de uísque; líquido acompanha progresso real |
| `READINESS_CONTRACT` | `HOME_READY`: `.home-primary-actions` visível e `.boot` não visível; `ACCESS_READY`: `.elo-access-gate` visível; `GOOGLE_AUTH_READY`: `accounts.google.com` interativo quando necessário |
| `EXTERNAL_HOST_POLICY` | Apps Script/Google auth/googleusercontent internos; WhatsApp, `tel:`, `mailto:`, `sms:`, `intent:` e HTTPS desconhecido externos; HTTP bloqueado |
| `DEEP_LINK_SCHEME` | não definido nesta candidata |

## Loading

- 5%: Activity/WebView iniciados.
- 5–72%: progresso real de `WebChromeClient.onProgressChanged`.
- 88%: estado semântico detectado e confirmado por duas leituras estáveis.
- 100%: varredura final anti-flash concluída e Home/gate pronto.
- A WebView começa com `alpha = 0` e só é revelada após readiness.
- `onPageFinished()` não encerra a splash.
- Android 12+ usa system splash neutro e curto.

## Homologação obrigatória

Validar no aparelho real: launcher em Square/Round/Squircle; ícone do APK/Meus Arquivos; splash inteira em `FIT_CENTER`; garrafa/copo, barra e porcentagem até a Home; nenhum frame da faixa do Google Apps Script; sessão, teclado/cutout/safe areas, links, file chooser e duplo Voltar.
