# Plano: Paciência (Spider para Android)

## 1. Objetivo

**Paciência** é um app de Paciência Spider para **uso pessoal, só no Android**. Funciona offline, sem anúncios, sem contas e sem coleta de dados. O visual é minimalista no **Material Design 3**, com a paleta verde da tela inicial do aparelho.

Fora do escopo: lojas de aplicativos, iOS, monetização, conquistas e serviços online.

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

| Área | Escolha | Motivo |
|---|---|---|
| Linguagem | Kotlin | Padrão do Android |
| UI | Jetpack Compose + Material 3 | Componentes Material prontos, animações e gestos com controle total |
| Arquitetura | ViewModel + estado imutável com fluxo unidirecional | Fácil de testar e de salvar |
| Injeção de dependência | Manual (um `AppContainer` simples) | O app é pequeno; um framework seria peso à toa |
| Persistência | DataStore guardando JSON via kotlinx.serialization | Partida salva, configurações e histórico num lugar só, sem banco de dados |
| Concorrência | Coroutines + StateFlow | |
| Testes | JUnit, Compose UI Test | |
| Build | Gradle KTS + version catalog | |
| Versão mínima | Android 8.0 (API 26), target na versão mais recente | |

## 4. Estrutura do projeto

```
engine/   -> módulo Kotlin puro (sem Android): cartas, regras, movimentos, desfazer, dicas, solver
app/      -> telas em Compose, tema, ViewModels, persistência, MainActivity
```

O motor fica separado para ser testado rapidamente na JVM, sem emulador.

## 5. Modelo do motor (engine)

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
- **Partidas numeradas:** o embaralhamento usa uma semente, então a "Partida nº 12345" sempre sai igual. Isso permite reiniciar a mesma partida e torna os testes determinísticos.
- **Dicas:** os movimentos legais são ordenados por heurística: completar conjunto, revelar carta, juntar cartas do mesmo naipe, liberar coluna e, por último, distribuir.
- **Solver (fase 4, opcional):** busca em profundidade com limite e memória de estados já visitados, rodando em segundo plano. Serve para avisar quando não há mais jogadas úteis e para um modo que só sorteia partidas com solução.

## 6. Identidade visual

### Paleta

A paleta foi tirada da tela inicial do aparelho, em que o papel de parede verde e os ícones temáticos usam três tons principais:

| Papel | Cor | Origem na tela inicial | Uso no app |
|---|---|---|---|
| Verde-base (semente) | `#109D58` | Papel de parede | Mesa do jogo |
| Verde-escuro | `#00391C` | Fundo dos ícones | Barras, verso das cartas, texto e naipes pretos |
| Verde-menta | `#5CFF9F` | Desenho dos ícones | Ícones das barras, destaque de destino válido, contador do monte |
| Verde-floresta | `#084927` | Fundo das pastas | Colunas vazias, cartão de vitória |
| Verde-acinzentado | `#539575` | Ícones desativados | Ações indisponíveis (Desfazer sem histórico, Distribuir bloqueado) |
| Cinza-esverdeado | `#374542` | Barra de pesquisa | Diálogos e painéis inferiores |
| Branco-esverdeado | `#F3FBF5` | Texto do relógio, levemente tingido | Frente das cartas |
| Vermelho suave | `#C62F3B` | Contraste necessário | Naipes de copas e ouros |

- O restante dos papéis do Material 3 (`surfaceContainer`, `outline` etc.) é gerado no Material Theme Builder com `#109D58` como semente, e depois os tons acima são fixados por cima.
- **Tema escuro:** a mesa passa para `#00391C`, as barras para um verde quase preto (`#00210E`), e as cartas e o menta continuam iguais.
- **Cor dinâmica do Android** fica disponível nas configurações, desligada por padrão. Como o papel de parede atual é verde, o resultado seria parecido.

### Elementos

- **Cartas:** desenhadas em vetor pelo próprio app, sem imagens.
  - Retângulo branco-esverdeado com cantos de 6–8dp e sombra leve.
  - Valor e naipe no canto superior esquerdo, e um naipe grande no centro.
  - Não há figuras ilustradas: J, Q e K aparecem só como letras.
  - O verso é verde-escuro com um padrão geométrico fino em menta.
- **Barras:**
  - A barra de cima é verde-escura, com pontos, movimentos e tempo em menta.
  - A barra de baixo, do mesmo jeito, tem três ações: Desfazer, Dica e Distribuir, esta com um contador de rodadas restantes.
  - O visual lembra os ícones temáticos da tela inicial.
- **Tipografia:** Roboto Flex, com a escala de tipos do Material 3. Os números do placar usam algarismos de largura fixa, para não "pularem" enquanto mudam.
- **Movimento:** as animações seguem as curvas e durações do Material (emphasized easing, 200–400 ms). A vitória é discreta: as cartas se recolhem e aparece um cartão verde-floresta com o resultado.
- **Ícone do app:** um naipe de espadas menta sobre fundo verde-escuro, igual ao estilo dos outros ícones. Também tem a versão monocromática, para o ícone temático do Android 13+ combinar automaticamente.

## 7. Telas

1. **Início:** botão grande "Continuar" (se houver partida salva), "Novo jogo" com escolha de 1/2/4 naipes e atalhos para estatísticas e configurações.
2. **Jogo:**
   - 10 colunas que comprimem o espaçamento vertical quando ficam altas.
   - Monte no canto e área de conjuntos completos.
   - **Interações:**
     - **toque único** para mover para o melhor destino;
     - arrastar e soltar com destaque em menta dos destinos válidos;
     - animação ao distribuir, revelar e completar conjuntos;
     - vibração curta ao completar um conjunto.
3. **Estatísticas por dificuldade:** jogos, vitórias, % de vitória, melhor pontuação, menor tempo, sequência atual e melhor sequência.
4. **Configurações:** tema (sistema/claro/escuro), cor dinâmica liga/desliga, sons e vibração, modo canhoto, confirmar antes de distribuir e modo "só partidas com solução".

**Layout:** retrato e paisagem, celular e tablet (WindowSizeClass). O tamanho da carta é calculado a partir da largura disponível dividida por 10 colunas. A tela usa edge-to-edge, com a mesa indo por trás das barras do sistema.

**Acessibilidade:** descrição de cada carta para leitores de tela ("Dama de copas"), naipes distinguíveis pelo símbolo e não só pela cor, e alvos de toque de pelo menos 48dp.

## 8. Persistência

- A partida em andamento é salva automaticamente a cada movimento e ao sair do app, e retomada ao abrir.
- Cada partida terminada ou abandonada entra num histórico (semente, dificuldade, resultado, pontos, tempo, data), de onde saem as estatísticas.
- O backup automático do Android fica ligado, então ao trocar de celular as estatísticas e a partida salva vão junto com o backup do Google.

## 9. Instalação

- O GitHub Actions compila e assina o APK com uma chave própria, guardada nos segredos do repositório, e anexa o arquivo a uma Release a cada versão.
- Para instalar, basta baixar o APK no celular e permitir a instalação de fontes desconhecidas para o navegador ou o gerenciador de arquivos. As atualizações são feitas instalando o APK novo por cima, sem perder os dados.

## 10. Roteiro de desenvolvimento

| Fase | Entregas | Estimativa |
|---|---|---|
| **0. Base** | Projeto Gradle (`engine` + `app`), tema com a paleta, CI no GitHub Actions (testes + APK) | 1–2 dias |
| **1. Motor** | Modelo, distribuição com semente, regras, desfazer, pontuação, vitória, testes unitários (meta: mais de 90% de cobertura) | 1 semana |
| **2. Versão jogável** | Tela de jogo, toque para mover, distribuir, desfazer, salvar e retomar, 1/2/4 naipes | 1,5 semana |
| **3. Acabamento** | Arrastar e soltar, animações, vibração, dicas, tela de vitória, estatísticas, configurações, ícone | 1 semana |
| **4. Extras (opcional)** | Solver e modo "só partidas com solução" | 1 semana |

Total estimado: **4 a 5 semanas** para uma pessoa, no ritmo de projeto pessoal, já contando a fase opcional.

## 11. Testes

- **Motor:**
  - cada regra com casos positivos e negativos;
  - partida completa determinística a partir de uma semente;
  - testes de propriedade: sempre existem 104 cartas, e desfazer após aplicar devolve o mesmo estado.
- **ViewModel:** sequência de estados após tocar, distribuir e desfazer.
- **UI:** alguns testes de Compose para as interações principais.
- **Manual:** jogar no próprio celular a cada versão.

## 12. Riscos

- **Arrastar e soltar em colunas altas** no celular em modo retrato: resolver com espaçamento dinâmico e com o toque para mover como interação principal.
- **Contraste do menta sobre o verde-base** é baixo para texto. Por isso o menta só aparece sobre o verde-escuro, nunca direto sobre a mesa.
