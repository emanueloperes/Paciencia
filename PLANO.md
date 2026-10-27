# Plano: app de Paciência Spider (Android + iOS)

## 1. Objetivo

Um app de Paciência Spider para **uso pessoal**: offline, sem anúncios, sem contas e sem coleta de dados. Roda no Android e também pode ser instalado num iPhone/iPad. O visual é **minimalista, seguindo o Material Design 3** do Google.

Fora do escopo: Play Store/App Store, monetização, conquistas e serviços online.

## 2. Regras do jogo (o que o motor precisa implementar)

- **Baralho:** 104 cartas (2 baralhos). Com 1 naipe são 8 conjuntos de espadas; com 2 naipes, 4 de espadas e 4 de copas; com 4 naipes, 2 de cada.
- **Distribuição inicial:** 10 colunas, 54 cartas. As 4 primeiras colunas recebem 6 cartas e as outras 6 recebem 5. Só a carta de cima fica virada para cima.
- **Monte (estoque):** as 50 cartas restantes, distribuídas em 5 rodadas de 10 (uma por coluna). Não dá para distribuir se houver coluna vazia.
- **Movimentos:**
  - Uma carta pode ir sobre outra de valor imediatamente superior, de qualquer naipe.
  - Uma sequência só se move junta se for decrescente **e do mesmo naipe**.
  - Qualquer carta ou sequência válida pode ir para uma coluna vazia.
  - Carta virada para baixo que fica no topo é revelada automaticamente.
- **Conjunto completo:** uma sequência K→A do mesmo naipe sai da mesa e vai para a área de conjuntos completos.
- **Vitória:** 8 conjuntos completos.
- **Pontuação clássica:** começa em 500, perde 1 por movimento (inclusive desfazer) e ganha 100 por conjunto completo.

## 3. Stack técnica

Como pode haver uma versão iOS, o app é feito com **Kotlin Multiplatform + Compose Multiplatform**: regras, telas e persistência ficam num único código compartilhado, e cada plataforma tem só uma "casca" mínima.

| Área | Escolha | Motivo |
|---|---|---|
| Linguagem | Kotlin | Um só código para Android e iOS |
| UI | Compose Multiplatform + Material 3 | Mesmo visual Material nas duas plataformas |
| Arquitetura | ViewModel + estado imutável com fluxo unidirecional | Fácil de testar e de salvar |
| Injeção de dependência | Manual (um `AppContainer` simples) | O app é pequeno; Hilt não roda no iOS |
| Persistência | DataStore (KMP) guardando JSON via kotlinx.serialization | Partida salva, configurações e histórico num lugar só, sem banco de dados |
| Concorrência | Coroutines + StateFlow | |
| Testes | kotlin.test (código comum), Compose UI Test | |
| Build | Gradle KTS + version catalog | |
| Versões mínimas | Android 7.0 (API 24), iOS 15 | |

## 4. Estrutura do projeto

```
shared/
  engine/        -> Kotlin puro: cartas, regras, movimentos, desfazer, dicas, solver
  data/          -> DataStore, serialização, repositório de partidas e estatísticas
  ui/            -> todas as telas em Compose Multiplatform, tema Material 3, ViewModels
androidApp/      -> MainActivity (só chama a UI compartilhada) + cor dinâmica
iosApp/          -> projeto Xcode mínimo (ContentView que hospeda a UI compartilhada)
```

## 5. Modelo do motor (shared/engine)

```kotlin
enum class Suit { SPADES, HEARTS, DIAMONDS, CLUBS }

@Serializable
data class Card(val rank: Int /* 1..13 */, val suit: Suit, val faceUp: Boolean, val id: Int)

@Serializable
data class GameState(
    val tableau: List<List<Card>>,   // 10 colunas
    val stock: List<Card>,           // monte
    val completed: List<Suit>,       // conjuntos completos, na ordem (0..8)
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
    fun newGame(seed: Long, difficulty: Difficulty): GameState
    fun legalMoves(state: GameState): List<Move>
    fun apply(state: GameState, move: Move): GameState   // já trata revelação e remoção de conjunto
    fun isWon(state: GameState): Boolean
}
```

- **Desfazer ilimitado:** pilha de `GameState` anteriores. O estado é imutável e pequeno, então o custo de memória é baixo.
- **Partidas numeradas:** o embaralhamento usa um gerador com semente implementado no próprio código (não o `Random` da plataforma), para que a "Partida nº 12345" seja **idêntica no Android e no iOS**.
- **Dicas:** os movimentos legais são ordenados por heurística: completar conjunto, revelar carta, juntar cartas do mesmo naipe, liberar coluna e, por último, distribuir.
- **Solver (fase 4):** busca em profundidade com limite e memória de estados já visitados, rodando em segundo plano. Serve para avisar quando não há mais jogadas úteis e para um modo opcional que só sorteia partidas com solução.

## 6. Identidade visual: Material 3 minimalista

**Princípios:** pouca coisa na tela, a mesa como protagonista, nada de texturas de feltro ou madeira, ícones e cantos arredondados do Material.

- **Cores:**
  - No Android 12+ usa a **cor dinâmica** (Material You), que acompanha o papel de parede.
  - No iOS e em Android mais antigo usa uma paleta fixa gerada a partir de uma cor-semente, por exemplo verde-azulado `#3F6E66`, com o Material Theme Builder.
  - Tema claro e escuro automáticos, seguindo o sistema.
  - A mesa usa `surfaceContainer`, as cartas usam `surface` com elevação 1, e os destinos válidos ficam destacados com contorno `primary`.
- **Cartas:** desenhadas em vetor pelo próprio app, sem imagens.
  - Retângulo com cantos de 6–8dp, valor e naipe no canto superior esquerdo e um naipe grande no centro.
  - Não há figuras ilustradas: J, Q e K aparecem só como letras.
  - Copas e ouros em `error`/vermelho suave, espadas e paus em `onSurface`.
  - O verso tem um padrão geométrico simples na cor `primaryContainer`.
- **Tipografia:** Roboto Flex no Android; no iOS a mesma fonte, embutida no app para manter a consistência. A escala de tipos é a do Material 3.
- **Componentes:**
  - `TopAppBar` pequena com pontos, movimentos e tempo.
  - `BottomAppBar` com três ações: Desfazer, Dica e Distribuir, esta com um contador de rodadas restantes.
  - Diálogos e `ModalBottomSheet` do Material para novo jogo e configurações.
- **Movimento:** as animações seguem as curvas e durações do Material (emphasized easing, 200–400 ms). A vitória é celebrada de forma discreta: as cartas se recolhem e aparece um cartão com o resultado, sem fogos de artifício.
- **Ícone:** um naipe de espadas estilizado sobre fundo da cor-semente, com suporte a ícone temático (monocromático) no Android 13+.

## 7. Telas

1. **Início:** botão grande "Continuar" (se houver partida salva), "Novo jogo" com escolha de 1/2/4 naipes e atalhos para estatísticas e configurações.
2. **Jogo:**
   - 10 colunas que comprimem o espaçamento vertical quando ficam altas.
   - Monte no canto e área de conjuntos completos.
   - **Interações:**
     - **toque único** para mover para o melhor destino;
     - arrastar e soltar com destaque dos destinos válidos;
     - animação ao distribuir, revelar e completar conjuntos.
3. **Estatísticas por dificuldade:** jogos, vitórias, % de vitória, melhor pontuação, menor tempo, sequência atual e melhor sequência.
4. **Configurações:** tema (sistema/claro/escuro), cor dinâmica liga/desliga, sons e vibração, modo canhoto, confirmar antes de distribuir, modo "só partidas com solução", e exportar/importar dados.

**Layout:** retrato e paisagem, celular e tablet (WindowSizeClass). O tamanho da carta é calculado a partir da largura disponível dividida por 10 colunas.

**Acessibilidade:** descrição de cada carta para leitores de tela ("Dama de copas"), naipes distinguíveis pelo símbolo e não só pela cor, e alvos de toque de pelo menos 48dp.

## 8. Persistência e compartilhamento entre aparelhos

- A partida em andamento é salva automaticamente a cada movimento e ao sair do app, e retomada ao abrir.
- Cada partida terminada ou abandonada entra num histórico (semente, dificuldade, resultado, pontos, tempo, data), de onde saem as estatísticas.
- Não há nuvem nem sincronização automática. Para levar os dados de um aparelho para outro, as configurações terão **Exportar/Importar**: um arquivo JSON enviado pelo menu de compartilhar do sistema (AirDrop, WhatsApp, e-mail, Drive etc.).
- Como as partidas numeradas são iguais nas duas plataformas, dá para jogar a mesma partida no Android e no iPhone e comparar os resultados.

## 9. Instalação (sem lojas)

- **Android:** gerar um APK assinado com uma chave própria e instalar diretamente ("fontes desconhecidas"). O GitHub Actions pode gerar o APK a cada versão e anexá-lo a uma Release.
- **iOS:** precisa de um **Mac com Xcode**. Há duas opções:
  - **Apple ID gratuito:** instala pelo Xcode direto no aparelho, mas a assinatura expira a cada 7 dias e precisa ser reinstalada.
  - **Apple Developer Program (US$ 99/ano):** a assinatura dura 1 ano e permite distribuir pelo TestFlight para outras pessoas.

  Sem Mac, a versão iOS não é viável. Nesse caso o código continua pronto para quando houver um.

## 10. Roteiro de desenvolvimento

| Fase | Entregas | Estimativa |
|---|---|---|
| **0. Base** | Projeto KMP (Android + iOS), tema Material 3, CI no GitHub Actions (build Android, testes do código comum, geração do APK) | 2 dias |
| **1. Motor** | Modelo, gerador com semente, regras, desfazer, pontuação, vitória, testes unitários (meta: mais de 90% de cobertura) | 1 semana |
| **2. Versão jogável** | Tela de jogo, toque para mover, distribuir, desfazer, salvar e retomar, 1/2/4 naipes, rodando no Android | 1,5 semana |
| **3. Acabamento** | Arrastar e soltar, animações Material, vibração, dicas, tela de vitória, estatísticas, configurações, exportar/importar | 1,5 semana |
| **4. iOS e extras** | Ajustes no iOS (área segura, gestos, fonte), solver e modo "só partidas com solução" | 1 semana |

Total estimado: **5 a 6 semanas** para uma pessoa, no ritmo de projeto pessoal. A fase 4 é opcional.

## 11. Testes

- **Motor (código comum, roda nas duas plataformas):**
  - cada regra com casos positivos e negativos;
  - partida completa determinística a partir de uma semente;
  - testes de propriedade: sempre existem 104 cartas, e desfazer após aplicar devolve o mesmo estado;
  - teste garantindo que a mesma semente gera a mesma distribuição no Android e no iOS.
- **ViewModel:** sequência de estados após tocar, distribuir e desfazer.
- **UI:** alguns testes de Compose para as interações principais.
- **Manual:** jogar em pelo menos um celular Android real e, quando possível, num iPhone.

## 12. Riscos e decisões em aberto

- **Arrastar e soltar em colunas altas** no celular em modo retrato: resolver com espaçamento dinâmico e com o toque para mover como interação principal.
- **iOS depende de um Mac.** Se não houver, o app fica só no Android, sem retrabalho, porque o código já é compartilhado.
- **Compose no iOS** não tem a aparência nativa da Apple. Isso é intencional, já que a identidade escolhida é Material.
- A definir:
  - o nome do app;
  - a cor-semente da paleta;
  - se a versão iOS terá Apple ID gratuito ou conta de desenvolvedor paga.
