package br.edu.checkout;

import java.math.BigDecimal;
import java.util.Objects;

public final class ServicoCheckout {

    private final EstoqueGateway estoqueGateway;
    private final PagamentoGateway pagamentoGateway;
    private final PedidoRepository pedidoRepository;
    private final Notificador notificador;

    public ServicoCheckout(
            EstoqueGateway estoqueGateway,
            PagamentoGateway pagamentoGateway,
            PedidoRepository pedidoRepository,
            Notificador notificador) {
        this.estoqueGateway = Objects.requireNonNull(estoqueGateway);
        this.pagamentoGateway = Objects.requireNonNull(pagamentoGateway);
        this.pedidoRepository = Objects.requireNonNull(pedidoRepository);
        this.notificador = Objects.requireNonNull(notificador);
    }

    public ResultadoCheckout finalizar(SolicitacaoCheckout solicitacao) {
        validar(solicitacao);
        BigDecimal total = calcularTotal(solicitacao);
        if (!estoqueGateway.reservar(solicitacao.sku(), solicitacao.quantidade())) {
            throw new EstoqueInsuficienteException();
        }
        Pagamento pagamento = pagamentoGateway.cobrar(solicitacao.clienteId(), total);
        if (!pagamento.aprovado()) {
            estoqueGateway.liberar(solicitacao.sku(), solicitacao.quantidade());
            throw new PagamentoRecusadoException();
        }
        String numeroPedido = pedidoRepository.salvar(
                criarPedido(solicitacao, total, pagamento.transacaoId()));
        notificador.enviarConfirmacao(solicitacao.email(), numeroPedido, total);
        return new ResultadoCheckout(numeroPedido, total, true);
    }

    private static BigDecimal calcularTotal(SolicitacaoCheckout solicitacao) {
        // Multiplicação exata: sem double e sem arredondamento (R2).
        return solicitacao.precoUnitario().multiply(BigDecimal.valueOf(solicitacao.quantidade()));
    }

    private static PedidoConfirmado criarPedido(
            SolicitacaoCheckout solicitacao, BigDecimal total, String transacaoId) {
        return new PedidoConfirmado(
                solicitacao.clienteId(),
                solicitacao.email(),
                solicitacao.sku(),
                solicitacao.quantidade(),
                total,
                transacaoId);
    }

    private static void validar(SolicitacaoCheckout solicitacao) {
        if (solicitacao == null) {
            throw new IllegalArgumentException("Solicitação obrigatória");
        }
        exigirTexto(solicitacao.clienteId(), "clienteId");
        exigirTexto(solicitacao.email(), "email");
        exigirTexto(solicitacao.sku(), "sku");
        if (solicitacao.quantidade() <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        if (solicitacao.precoUnitario() == null || solicitacao.precoUnitario().signum() <= 0) {
            throw new IllegalArgumentException("Preço unitário deve ser positivo");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " obrigatório");
        }
    }
}
