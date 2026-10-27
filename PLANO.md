# Plano: app Android de Paciência Spider

## 1. Objetivo

Um app de Paciência Spider para Android, offline, sem anúncios intrusivos, com as três dificuldades clássicas (1, 2 e 4 naipes), desfazer ilimitado, dicas, estatísticas e animações fluidas.

## 2. Regras do jogo (o que o motor precisa implementar)

- **Baralho:** 104 cartas (2 baralhos). Com 1 naipe são 8 conjuntos de espadas; com 2 naipes, 4 de espadas e 4 de copas; com 4 naipes, 2 de cada.
- **Distribuição inicial:** 10 colunas, 54 cartas. As 4 primeiras colunas recebem 6 cartas e as outras 6 recebem 5. Só a carta de cima fica virada para cima.
- **Monte (estoque):** as 50 cartas restantes, distribuídas em 5 rodadas de 10 (uma por coluna). Não dá para distribuir se houver coluna vazia.
- **Movimentos:**
  - Uma carta pode ir sobre outra de valor imediatamente superior, de qualquer naipe.
  - Uma sequência só se move junta se for decrescente **e do mesmo naipe**.
  - Qualquer carta ou sequência válida pode ir para uma coluna vazia.
  - Carta virada para baixo que fica no topo é revelada automaticamente.
- **Conjunto completo:** uma sequência K→A do mesmo naipe sai da mesa e vai para a área de fundação.
- **Vitória:** 8 conjuntos completos.
- **Pontuação clássica:** começa em 500, perde 1 por movimento e ganha 100 por conjunto completo.

## 3. Stack técnica

| Área | Escolha | Motivo |
|---|---|---|
| Linguagem | Kotlin | Padrão do Android moderno |
| UI | Jetpack Compose + Canvas | Animações e arrastar e soltar com controle total |
| Arquitetura | MVVM + fluxo de estado unidirecional | Estado do jogo imutável e fácil de testar |
| Injeção de dependência | Hilt | Padrão da plataforma |
| Persistência | Room (estatísticas, histórico) + DataStore (configurações, partida salva) | |
| Concorrência | Coroutines + StateFlow | |
| Testes | JUnit 5, Turbine, Compose UI Test | |
| Build | Gradle KTS + version catalog | |
| Versão mínima | minSdk 24, targetSdk mais recente | Cobre a grande maioria dos aparelhos |

## 4. Estrutura de módulos

```
app/                  -> Activity, navegação, tema, DI
core/engine/          -> Kotlin puro, sem Android: regras, estado, movimentos, desfazer, dicas, solver
core/data/            -> Room, DataStore, repositórios
feature/game/         -> tela de jogo (Compose), GameViewModel
feature/menu/         -> menu inicial, escolha de dificuldade
feature/stats/        -> estatísticas
feature/settings/     -> configurações (verso das cartas, fundo, sons, mão esquerda)
```

O motor em Kotlin puro pode ser testado sem emulador e reaproveitado (por exemplo em Kotlin Multiplatform, se um dia houver versão iOS).

## 5. Modelo do motor (core/engine)

```kotlin
enum class Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
data class Card(val rank: Int /* 1..13 */, val suit: Suit, val faceUp: Boolean, val id: Int)

data class GameState(
    val tableau: List<List<Card>>,   // 10 colunas
    val stock: List<Card>,           // monte
    val foundations: Int,            // conjuntos completos (0..8)
    val score: Int,
    val moves: Int,
    val seed: Long,
    val difficulty: Difficulty,
)

sealed interface Move {
    data class Transfer(val from: Int, val cardIndex: Int, val to: Int) : Move
    data object Deal : Move
}

object Rules {
    fun legalMoves(state: GameState): List<Move>
    fun apply(state: GameState, move: Move): GameState   // já trata revelação e remoção de conjunto
    fun isWon(state: GameState): Boolean
}
```

- **Desfazer:** pilha de `GameState` anteriores (o estado é imutável e pequeno, então o custo é baixo). Desfazer custa 1 ponto, como no clássico.
- **Embaralhamento com semente:** `Random(seed)` garante que cada partida possa ser repetida ("Partida nº 12345") e torna os testes determinísticos.
- **Dicas:** ordenar os movimentos legais por heurística: completar um conjunto, revelar uma carta, juntar cartas do mesmo naipe, liberar coluna, e por último distribuir.
- **Solver (fase 2):** busca em profundidade com limite e tabela de estados já vistos, rodando em segundo plano. Serve para o modo "só partidas com solução" e para avisar quando não há mais jogadas úteis.

## 6. Telas e experiência de uso

1. **Menu:** continuar, novo jogo (1/2/4 naipes), partida do dia, estatísticas, configurações.
2. **Jogo:**
   - Barra superior com pontos, movimentos e tempo; menu com reiniciar e nova partida.
   - 10 colunas que comprimem o espaçamento vertical quando ficam altas.
   - Monte no canto, mostrando quantas distribuições restam, e área de conjuntos completos.
   - Barra inferior com Desfazer, Dica e Distribuir.
   - **Interações:** arrastar e soltar com destaque dos destinos válidos, **toque único** para mover automaticamente ao melhor destino, animação ao distribuir, ao revelar e ao completar conjuntos, e animação de vitória.
3. **Estatísticas por dificuldade:** jogos, vitórias, % de vitória, melhor pontuação, menor tempo, sequência atual e melhor sequência.
4. **Configurações:** tema claro/escuro, fundo e verso das cartas, sons e vibração, modo canhoto, orientação, confirmar antes de distribuir.

**Layout:** suporte a retrato e paisagem, tablets e dobráveis (WindowSizeClass). O tamanho da carta é calculado a partir da largura disponível dividida por 10 colunas.

**Acessibilidade:** descrição de conteúdo para cada carta ("Dama de copas"), naipes distinguíveis sem depender da cor, opção de cartas grandes e alvos de toque de pelo menos 48dp.

## 7. Persistência

- A partida em andamento é salva automaticamente (estado serializado com kotlinx.serialization no DataStore) a cada movimento e em `onStop`, e retomada ao abrir o app.
- Cada partida terminada ou abandonada vira um registro no Room (`GameRecord`: semente, dificuldade, resultado, pontos, tempo, data), de onde saem as estatísticas.

## 8. Roteiro de desenvolvimento

| Fase | Entregas | Estimativa |
|---|---|---|
| **0. Base** | Projeto Gradle multimódulo, CI (GitHub Actions: build, lint, testes), tema | 2 dias |
| **1. Motor** | Modelo, distribuição com semente, regras, desfazer, pontuação, vitória, testes unitários (meta: mais de 90% de cobertura) | 1 semana |
| **2. MVP jogável** | Tela de jogo em Compose, toque para mover, distribuir, desfazer, salvar e retomar, 1/2/4 naipes | 1,5 semana |
| **3. Polimento** | Arrastar e soltar, animações, sons, vibração, dicas, tela de vitória | 1 semana |
| **4. Conteúdo** | Estatísticas, configurações, temas e versos de carta, partida do dia | 1 semana |
| **5. Extras** | Solver e modo "só partidas com solução", conquistas, Play Games (opcional) | 1–2 semanas |
| **6. Lançamento** | Testes em aparelhos reais, Baseline Profiles, ícone, capturas de tela, ficha da Play Store, política de privacidade, faixa de teste interno → produção | 1 semana |

Total estimado: **6 a 8 semanas** para uma pessoa.

## 9. Testes

- **Motor:** cada regra com casos positivos e negativos, partida completa determinística a partir de uma semente, e teste de propriedade garantindo que sempre existam 104 cartas e que desfazer após aplicar devolva o mesmo estado.
- **ViewModel:** fluxo de estados com Turbine.
- **UI:** testes de Compose para tocar, distribuir e desfazer, e testes de captura de tela (Paparazzi ou Roborazzi) para o layout em vários tamanhos.
- **Desempenho:** Macrobenchmark de inicialização e de rolagem/animação, mirando 60 fps em aparelhos intermediários.

## 10. Monetização (escolher uma)

- **Gratuito com uma compra única** para remover anúncios e liberar temas (recomendado, porque é bem aceito em jogos de cartas).
- Pago, sem anúncios.
- Totalmente gratuito e de código aberto.

Se houver anúncios, usar só banner ou intersticial entre partidas, nunca durante o jogo.

## 11. Riscos e decisões em aberto

- **Arrastar e soltar em colunas altas** no celular em modo retrato: resolver com espaçamento dinâmico e toque para mover como interação principal.
- **Custo do solver** em aparelhos fracos: rodar em segundo plano com limite de tempo, e oferecer partidas com solução pré-geradas (lista de sementes validadas) como alternativa.
- **Arte das cartas:** desenhar em vetor próprio (o que evita problemas de licença) ou usar um conjunto de domínio público.
- A definir: nome do app, identidade visual, modelo de monetização e se vale ter versão iOS (o que favoreceria Kotlin Multiplatform no motor).
