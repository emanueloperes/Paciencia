# Paciência

Paciência Spider para Android, de uso pessoal: offline, sem anúncios e no visual Material 3 verde da tela inicial. O plano completo está em [PLANO.md](PLANO.md).

## Estrutura

- `engine/`: regras do jogo em Kotlin puro (distribuição com semente, movimentos, desfazer, pontuação, dicas), com testes.
- `app/`: app Android em Jetpack Compose.

## Comandos

```sh
./gradlew :engine:test        # testes do motor (não precisa do Android SDK)
./gradlew :app:assembleDebug  # APK de depuração em app/build/outputs/apk/debug/
./gradlew :engine:generateSolvableSeeds  # refaz a lista de partidas com solução (alguns minutos)
```

## Partidas com solução

Com a opção "Só partidas com solução" ligada, o app sorteia entre números de partida que o
`Solver` do motor já resolveu (lista em `engine/.../SolvableSeeds.kt`). O solver conhece todas
as cartas, inclusive as viradas para baixo, e a solução de cada partida da lista é conferida até
a vitória na geração. As partidas numeradas são idênticas no app e na versão web.

O GitHub Actions roda os dois a cada push e publica o APK como artefato do build, em **Actions → Build → paciencia-debug-apk**.
