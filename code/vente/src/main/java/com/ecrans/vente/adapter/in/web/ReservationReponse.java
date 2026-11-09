package com.ecrans.vente.adapter.in.web;

import com.ecrans.vente.application.port.in.Reservation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Le JSON renvoyé : la commande, le total (en centimes et lisible), où payer et jusqu'à quand. */
public record ReservationReponse(String commande, long totalCentimes, String total, String urlPaiement,
                                 LocalDateTime payerAvant) {

    static ReservationReponse de(Reservation reservation) {
        return new ReservationReponse(reservation.commande().valeur(), reservation.total().centimes(),
                reservation.total().toString(), reservation.urlPaiement(),
                reservation.payerAvant().truncatedTo(ChronoUnit.SECONDS));
    }
}
