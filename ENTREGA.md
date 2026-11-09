# Entrega — Checkout com colaboradores simulados

- Código: `src/main/java/br/edu/checkout/ServicoCheckout.java`
- Testes: `src/test/java/br/edu/checkout/ServicoCheckoutTest.java` (22 execuções, todas verdes com `mvn test`)
- Evidências de TDD: histórico de commits (`git log --oneline`) e saídas do Maven em `docs/tdd-logs/`

## 1. Registro dos ciclos Red–Green–Refactor

Cada etapa virou um commit separado, e a saída real do `mvn test` foi salva em `docs/tdd-logs/cicloN-{red,green,refactor}.txt`. Os três ciclos pedidos estão detalhados abaixo; os ciclos 4 a 7 seguiram o mesmo processo e aparecem resumidos na tabela.

### Ciclo 1 — `deveRejeitarSolicitacaoNulaSemConsultarColaboradores`

- **Falha observada (RED):** `expected: <IllegalArgumentException> but was: <UnsupportedOperationException>`, porque `finalizar` ainda era o método pendente.
- **Mudança mínima (GREEN):** adicionei `if (solicitacao == null) throw new IllegalArgumentException(...)` no início de `finalizar`, mantendo o `UnsupportedOperationException` para o restante.
- **Refatoração:** nenhuma. Com só uma condição, não havia duplicação nem problema de legibilidade.

### Ciclo 2 — `deveRejeitarSolicitacaoInvalidaSemConsultarColaboradores` (parametrizado, 14 casos + o nulo do ciclo 1 = 15 entradas)

- **Falha observada (RED):** `Tests run: 15, Failures: 14`, cada caso com `expected: <IllegalArgumentException> but was: <UnsupportedOperationException>`.
- **Mudança mínima (GREEN):** um único `if` com todas as condições combinadas por `||` (texto nulo ou `isBlank()`, `quantidade <= 0`, preço nulo ou `signum() <= 0`).
- **Refatoração:** o `if` gigante juntava regras diferentes e repetia `x == null || x.isBlank()` três vezes. Extraí `validar(...)` e `exigirTexto(valor, campo)`. Os 15 testes continuaram verdes.

### Ciclo 3 — `deveConcluirCompraAprovadaNaOrdemReservarCobrarSalvarNotificar`

- **Falha observada (RED):** `java.lang.UnsupportedOperationException: Implemente por TDD` (o fluxo aprovado não existia).
- **Mudança mínima (GREEN):** implementei só o caminho feliz: total com `precoUnitario.multiply(BigDecimal.valueOf(quantidade))`, depois `reservar`, `cobrar`, `salvar` e `enviarConfirmacao`, retornando `notificacaoEnviada = true`. Conforme o roteiro, **não antecipei** nenhum tratamento de erro; o retorno de `reservar` era até ignorado.
- **Refatoração:** extraí `calcularTotal` (deixando explícita a regra R2 de não arredondar) e `criarPedido` (o construtor do record com seis argumentos atrapalhava a leitura de `finalizar`).

### Ciclos seguintes (mesmo processo)

| Ciclo | Teste | Falha observada no RED | Mudança no GREEN | Refactor |
|---|---|---|---|---|
| — | `deveAceitarQuantidadeMinima…` e `deveCalcularTotalSemArredondar…` | Nenhuma: já passaram, porque o cálculo exato veio do ciclo 3. Ficaram como testes de caracterização dos limites. | — | — |
| 4 | `deveFalharSemEstoque…` | `expected EstoqueInsuficienteException but was NullPointerException` (o mock de pagamento devolveu `null`, porque o serviço ignorava o `false` da reserva) | `if (!reservar(...)) throw new EstoqueInsuficienteException()` | Nenhum necessário |
| 5 | `deveLiberarReservaAposCobrancaRecusada…` | `Expected PagamentoRecusadoException to be thrown, but nothing was thrown` | `if (!pagamento.aprovado()) { liberar(...); throw new PagamentoRecusadoException(); }` | Nenhum necessário |
| 6 | `deveLiberarReservaEPropagarMesmaFalhaTecnica…` | `VerificationInOrderFailure: Wanted but not invoked: estoqueGateway.liberar("SKU-1", 2)` (a exceção já propagava, mas sem compensação) | `try/catch (FalhaPagamentoException)` liberando a reserva e relançando a mesma instância | Extraí `cobrarOuLiberarReserva` e `liberarReserva`, unindo as duas compensações (R4 e R5) sem misturar as duas situações |
| 7 | `deveManterCompraQuandoNotificacaoFalha` | `FalhaNotificacaoException: SMTP indisponível` vazou até o chamador | Método `notificar(...)` que captura **só** `FalhaNotificacaoException` e devolve `false` | Nenhum: o método já nasceu extraído |
| Ajuste de teste | Teste do ciclo 3 | — | — | O `ArgumentCaptor` saiu do stub (`when(salvar(captor.capture()))`) e foi para a verificação (`ordem.verify(...).salvar(captor.capture())`), como recomenda a documentação do Mockito |

## 2. Recursos do Mockito usados (cada um com propósito real)

| Recurso | Onde | Para quê |
|---|---|---|
| `when(...).thenReturn(...)` | Todos os cenários com reserva/pagamento/salvar | Programar as respostas que o cenário consome (stub) |
| `thenThrow(...)` | Falha técnica de pagamento | Simular `FalhaPagamentoException` vinda do gateway |
| `doThrow(...)` | Falha de notificação | Simular falha num método `void` |
| `verify(...)` | Precisão, notificação, estoque | Confirmar efeitos e argumentos |
| `never()` | Sem estoque, recusa, compra aprovada, falha de notificação | Proibir `liberar` e `salvar` onde não são permitidos |
| `times(1)` | Ordem, compensações, falha de notificação | Garantir que cada efeito acontece exatamente uma vez (nada de nova cobrança ou novo registro) |
| `verifyNoInteractions(...)` | Entradas inválidas, sem estoque, falha técnica | Nenhum efeito antes da validação ou após falta de estoque |
| `ArgumentCaptor` | Compra aprovada | Conferir os 6 campos do `PedidoConfirmado` |
| `InOrder` | Compra aprovada, recusa, falha técnica | reservar → cobrar → salvar → notificar; reservar → cobrar → liberar |

`@ExtendWith(MockitoExtension.class)` com `STRICT_STUBS` cria mocks novos para cada teste. O `@BeforeEach` só instancia o serviço real e não configura nenhum fluxo.

## 3. Respostas às perguntas

**O que foi mockado e por quê?**
Só as quatro fronteiras externas: `EstoqueGateway`, `PagamentoGateway`, `PedidoRepository` e `Notificador`. Elas representam sistemas fora do processo (estoque, adquirente, banco de dados, e-mail), que seriam lentos, não determinísticos ou teriam efeitos reais. Com mocks, dá para programar respostas como estoque em falta, recusa ou falha técnica, que seriam difíceis de reproduzir de verdade, e observar o que o serviço pediu a cada sistema. `ServicoCheckout`, os records e `BigDecimal` são reais, porque o comportamento deles é justamente o que está sendo testado.

**Qual teste verifica estado e qual verifica interação?**
- *Estado:* `deveAceitarQuantidadeMinimaMantendoPrecoComoTotal` compara o `ResultadoCheckout` devolvido com o esperado. Os testes de entrada inválida verificam a exceção lançada (resultado), e `deveManterCompraQuandoNotificacaoFalha` verifica `notificacaoEnviada == false`.
- *Interação:* `deveFalharSemEstoqueSemCobrarRegistrarNotificarNemLiberar` não tem resultado de retorno a conferir além da exceção. O que importa ali é o que **não** aconteceu (`verifyNoInteractions`, `never().liberar`). Os testes de recusa e de falha técnica verificam a compensação (`liberar` uma vez, após `cobrar`).
- `deveConcluirCompraAprovada…` faz as duas coisas: confere o resultado (estado) e a sequência de chamadas (interação).

**Que defeito seria revelado pelo captor?**
Um `PedidoConfirmado` montado errado, mesmo com o fluxo aparentemente correto: campos trocados (por exemplo, `sku` no lugar de `clienteId`, ou `email` e `clienteId` invertidos), o preço unitário salvo como `total`, um total arredondado, a quantidade errada ou um `transacaoId` nulo ou inventado em vez do devolvido pelo pagamento. Um `verify(repo).salvar(any())` passaria com qualquer um desses defeitos.

**Por que verificar a ordem aqui não é apenas verificar um detalhe de implementação?**
Porque a ordem é uma regra de negócio explícita (R3, R4 e R6), com consequências observáveis fora do serviço. Cobrar antes de reservar poderia cobrar um cliente por um produto sem estoque. Salvar antes de cobrar registraria pedidos não pagos. Notificar antes de salvar enviaria ao cliente um número de pedido que ainda não existe. Liberar antes de cobrar devolveria ao estoque um item que talvez seja vendido. A ordem verificada é só a dos **efeitos externos** que o contrato exige; métodos privados e a ordem de cálculo interno não são verificados, o que mantém a suíte livre para refatorações.

## 4. Desafio opcional: mutações temporárias

Cada alteração foi aplicada, testada com `mvn test` e desfeita com `git checkout`. Nenhuma ficou no código entregue.

| Mutação | Testes que ficaram vermelhos |
|---|---|
| Notificar antes de salvar | `deveConcluirCompraAprovada…` (InOrder), `deveCalcularTotalSemArredondar…`, `deveManterCompraQuandoNotificacaoFalha` |
| Usar outro número na notificação (o `transacaoId`) | Os mesmos três: o número esperado `PED-…` não chega ao notificador |
| Remover a liberação após recusa | `deveLiberarReservaAposCobrancaRecusada…` |
| Arredondar o total para 2 casas | `deveCalcularTotalSemArredondar…` (`cobrar` recebeu `0.02` em vez de `0.015`; o strict stubs acusa a diferença) |

Todas as mutações foram detectadas, então não foi preciso fortalecer a suíte.
