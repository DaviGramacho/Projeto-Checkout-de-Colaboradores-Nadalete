package br.edu.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    private static final String CLIENTE = "C1";
    private static final String EMAIL = "ana@ex.com";
    private static final String SKU = "SKU-1";

    private static SolicitacaoCheckout solicitacao(int quantidade, String precoUnitario) {
        return new SolicitacaoCheckout(CLIENTE, EMAIL, SKU, quantidade, new BigDecimal(precoUnitario));
    }

    @Test
    void deveConcluirCompraAprovadaNaOrdemReservarCobrarSalvarNotificar() {
        BigDecimal total = new BigDecimal("59.70");
        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, total)).thenReturn(new Pagamento(true, "TX-9"));
        ArgumentCaptor<PedidoConfirmado> pedidoCaptor = ArgumentCaptor.forClass(PedidoConfirmado.class);
        when(pedidoRepository.salvar(pedidoCaptor.capture())).thenReturn("PED-42");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(3, "19.90"));

        // Estado: o resultado devolvido ao chamador
        assertEquals("PED-42", resultado.numeroPedido());
        assertEquals(total, resultado.total());
        assertTrue(resultado.notificacaoEnviada());

        // Captor: o pedido montado com os seis campos corretos
        PedidoConfirmado pedido = pedidoCaptor.getValue();
        assertEquals(CLIENTE, pedido.clienteId());
        assertEquals(EMAIL, pedido.email());
        assertEquals(SKU, pedido.sku());
        assertEquals(3, pedido.quantidade());
        assertEquals(total, pedido.total());
        assertEquals("TX-9", pedido.transacaoId());

        // Interação: ordem exigida pelo contrato, cada efeito uma vez
        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
        ordem.verify(estoqueGateway, times(1)).reservar(SKU, 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar(CLIENTE, total);
        ordem.verify(pedidoRepository, times(1)).salvar(pedido);
        ordem.verify(notificador, times(1)).enviarConfirmacao(EMAIL, "PED-42", total);
        verify(estoqueGateway, never()).liberar(anyString(), anyInt());
    }

    @Test
    void deveAceitarQuantidadeMinimaMantendoPrecoComoTotal() {
        BigDecimal preco = new BigDecimal("19.90");
        when(estoqueGateway.reservar(SKU, 1)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, preco)).thenReturn(new Pagamento(true, "TX-1"));
        when(pedidoRepository.salvar(new PedidoConfirmado(CLIENTE, EMAIL, SKU, 1, preco, "TX-1")))
                .thenReturn("PED-1");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(1, "19.90"));

        assertEquals(new ResultadoCheckout("PED-1", preco, true), resultado);
    }

    @Test
    void deveCalcularTotalSemArredondarEEnviarEsseTotalAoPagamento() {
        BigDecimal esperado = new BigDecimal("0.015");
        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, esperado)).thenReturn(new Pagamento(true, "TX-2"));
        when(pedidoRepository.salvar(new PedidoConfirmado(CLIENTE, EMAIL, SKU, 3, esperado, "TX-2")))
                .thenReturn("PED-2");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(3, "0.005"));

        // equals exige valor e escala iguais: 0.015 (escala 3), nunca 0.02 ou 0.01
        assertEquals(esperado, resultado.total());
        verify(pagamentoGateway).cobrar(CLIENTE, esperado);
        verify(notificador).enviarConfirmacao(EMAIL, "PED-2", esperado);
    }

    @Test
    void deveFalharSemEstoqueSemCobrarRegistrarNotificarNemLiberar() {
        when(estoqueGateway.reservar(SKU, 2)).thenReturn(false);

        assertThrows(EstoqueInsuficienteException.class,
                () -> servico.finalizar(solicitacao(2, "10.00")));

        verify(estoqueGateway, times(1)).reservar(SKU, 2);
        verify(estoqueGateway, never()).liberar(anyString(), anyInt());
        verifyNoInteractions(pagamentoGateway, pedidoRepository, notificador);
    }
}
