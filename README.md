# Paciência

Paciência Spider para Android, de uso pessoal: offline, sem anúncios e no visual Material 3 verde da tela inicial. O plano completo está em [PLANO.md](PLANO.md).

## Estrutura

- `engine/`: regras do jogo em Kotlin puro (distribuição com semente, movimentos, desfazer, pontuação, dicas), com testes.
- `app/`: app Android em Jetpack Compose.

## Comandos

```sh
./gradlew :engine:test        # testes do motor (não precisa do Android SDK)
./gradlew :app:assembleDebug  # APK de depuração em app/build/outputs/apk/debug/
```

O GitHub Actions roda os dois a cada push e publica o APK como artefato do build, em **Actions → Build → paciencia-debug-apk**.
