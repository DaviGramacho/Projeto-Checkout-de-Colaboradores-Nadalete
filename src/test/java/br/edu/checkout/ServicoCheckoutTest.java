package br.edu.checkout;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ServicoCheckoutTest {

    @Mock
    private EstoqueGateway estoqueGateway;

    @Mock
    private PagamentoGateway pagamentoGateway;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private Notificador notificador;

    private ServicoCheckout servico;

    @BeforeEach
    void criarServicoReal() {
        servico = new ServicoCheckout(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    void deveRejeitarSolicitacaoNulaSemConsultarColaboradores() {
        // RED inicial: finalizar ainda lança UnsupportedOperationException.
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(null));
        verifyNoInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    static Stream<Arguments> solicitacoesInvalidas() {
        BigDecimal preco = new BigDecimal("19.90");
        return Stream.of(
                Arguments.of("clienteId nulo", new SolicitacaoCheckout(null, "ana@ex.com", "SKU-1", 1, preco)),
                Arguments.of("clienteId vazio", new SolicitacaoCheckout("", "ana@ex.com", "SKU-1", 1, preco)),
                Arguments.of("clienteId em branco", new SolicitacaoCheckout("   ", "ana@ex.com", "SKU-1", 1, preco)),
                Arguments.of("email nulo", new SolicitacaoCheckout("C1", null, "SKU-1", 1, preco)),
                Arguments.of("email vazio", new SolicitacaoCheckout("C1", "", "SKU-1", 1, preco)),
                Arguments.of("email em branco", new SolicitacaoCheckout("C1", " 	 ", "SKU-1", 1, preco)),
                Arguments.of("sku nulo", new SolicitacaoCheckout("C1", "ana@ex.com", null, 1, preco)),
                Arguments.of("sku vazio", new SolicitacaoCheckout("C1", "ana@ex.com", "", 1, preco)),
                Arguments.of("sku em branco", new SolicitacaoCheckout("C1", "ana@ex.com", "  ", 1, preco)),
                Arguments.of("quantidade zero", new SolicitacaoCheckout("C1", "ana@ex.com", "SKU-1", 0, preco)),
                Arguments.of("quantidade negativa", new SolicitacaoCheckout("C1", "ana@ex.com", "SKU-1", -1, preco)),
                Arguments.of("preço nulo", new SolicitacaoCheckout("C1", "ana@ex.com", "SKU-1", 1, null)),
                Arguments.of("preço zero", new SolicitacaoCheckout("C1", "ana@ex.com", "SKU-1", 1, BigDecimal.ZERO)),
                Arguments.of("preço negativo", new SolicitacaoCheckout("C1", "ana@ex.com", "SKU-1", 1, new BigDecimal("-0.01"))));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("solicitacoesInvalidas")
    void deveRejeitarSolicitacaoInvalidaSemConsultarColaboradores(
            String descricao, SolicitacaoCheckout invalida) {
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(invalida));
        verifyNoInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }
}
