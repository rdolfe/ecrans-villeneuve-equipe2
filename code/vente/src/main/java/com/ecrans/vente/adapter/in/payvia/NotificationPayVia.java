package com.ecrans.vente.adapter.in.payvia;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Le JSON envoyé par PayVia sur notre webhook (annexe A du sujet), tel quel.
 * Ce modèle appartient à PayVia : il ne doit JAMAIS entrer dans le domaine.
 * PayVia écrit « payment_id » et « occurred_at » (annexe A) : les alias acceptent les deux écritures.
 */
public record NotificationPayVia(String event,
                                 @JsonAlias("payment_id") String paymentId,
                                 String reference,
                                 long amount,
                                 String currency,
                                 @JsonAlias("occurred_at") String occurredAt) {
}
