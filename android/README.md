# Android Studio — Elo Bar

Aplicativo Android nativo fino para o Elo Bar.

## Arquitetura

`APK Android → WebView nativa → https://edsonmp7.github.io/EloBar-PWA/ → WebApp Elo Bar /exec`

O APK não replica banco de dados nem regras de negócio. A autoridade operacional continua sendo o Elo Bar no Google Apps Script. A PWA pública continua sendo a camada web atualizável automaticamente.

## Por que WebView nativa neste wrapper

Para este app, a WebView evita dependência de Digital Asset Links/certificado da TWA para esconder completamente a interface do navegador. Assim o Elo Bar aparece como aplicativo Android próprio, com ícone, splash e pacote próprios, sem badge/interface do Chrome.

## Identidade

- App: `Elo Bar`
- Package/Application ID: `com.eloclub.elobar`
- Fundo: `#121212`
- URL da casca: `https://edsonmp7.github.io/EloBar-PWA/`

## Abrir no Android Studio

Abra diretamente a pasta `android/` como projeto.

O projeto usa Java 17, Android Gradle Plugin 8.8.2, `compileSdk 35`, `targetSdk 35` e `minSdk 26`.

## Comportamentos do wrapper

- tela cheia/immersive;
- portrait;
- fundo e splash nativos `#121212`;
- cookies e cookies de terceiros habilitados para compatibilidade com Google Apps Script;
- JavaScript/DOM Storage habilitados;
- HTTP cleartext bloqueado;
- links externos saem para o app correspondente;
- seleção/upload de arquivo suportado;
- downloads abrem no manipulador externo;
- botão Voltar usa o histórico do WebView antes de sair;
- estado do WebView é restaurado após recriação da Activity;
- WebView debug somente em builds `debug`.

## Atualizações

Mudanças no Elo Bar ou na PWA **não exigem reinstalar o APK**: o wrapper continua carregando a URL publicada.

Só é necessário gerar um novo APK/AAB quando houver mudança nativa, como ícone Android, package ID, permissões, integração com hardware ou comportamento da Activity.

## Assinatura de release

Gere e guarde o keystore fora deste repositório público. Nunca faça commit de `.jks`, `.keystore`, senhas ou credenciais. No Android Studio use **Build → Generate Signed Bundle / APK** para a versão definitiva.
