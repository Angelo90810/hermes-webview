# Hermes Web

App Android nativo (Java + WebView) para usar o Hermes Agent webapp do celular como um app — sem navegador.

![icon](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

## Funcionalidades

- WebView nativa apontando para `http://IP:PORTA` configurável (rede local **ou** IP da tailnet — nunca `*.ts.net`)
- Credenciais opcionais salvas com criptografia (EncryptedSharedPreferences) e auto-preenchidas no formulário de login do webapp
- Sessão do Nous persiste entre aberturas (cookies por origem salvos e restaurados no arranque)
- Tela de espera com a identidade do app (splash "Conectando ao Hermes…"), some quando o webapp carrega
- Pull-to-refresh, tela de erro amigável com "Tentar de novo", suporte a anexos (file chooser do Android)
- UI Material 3 (claro/escuro automático), barra mínima de 32dp com engrenagem, ícone próprio
- Botão "Testar conexão" na configuração mostra a latência até o PC

## Build

```bash
cd hermes-webview
export JAVA_HOME="C:\\Users\\Angelo\\AppData\\Local\\hermes\\tools\\android-build\\jdk-17.0.21+9"  # Windows
$LOCALAPPDATA/hermes/tools/android-build/gradle/bin/gradle assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17, Android SDK (platforms;android-35), Gradle 8.10.2. `local.properties` aponta `sdk.dir`.

## Instalação

Baixe o APK da [release mais recente](../../releases/latest) e instale (aceite "fontes desconhecidas"). APK debug-signed.

## Estrutura

```
app/src/main/java/com/angelo/hermesweb/
  MainActivity.java    WebView, cookies, splash, file chooser, pull-to-refresh
  SettingsActivity.java IP/porta/credenciais + teste de conexão
  Prefs.java           armazenamento criptografado (EncryptedSharedPreferences)
```
