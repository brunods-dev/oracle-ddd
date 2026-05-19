package com.copa.ticketing.service;

public class SelloutTotals {
    public int batches;
    public int reservations;
    public int seatsSold;
    public int seatsReservedOnly;
    public int seatsPaymentPending;
    public int orders;
    public int paymentsPending;
    public int paymentsPaid;
    public int customers;
    public int tickets;
    public double revenue;

    public String toBatchMessage(String sectorCode) {
        return String.format(
                "batch=%d setor=%s reservados=%d pendentes=%d emitidos=%d pedidos=%d ingressos=%d receita=%.2f",
                batches, sectorCode, seatsReservedOnly, seatsPaymentPending,
                seatsSold, orders, tickets, revenue
        );
    }
}
