package br.edu.checkout;

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
        throw new UnsupportedOperationException("Implemente por TDD");
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
