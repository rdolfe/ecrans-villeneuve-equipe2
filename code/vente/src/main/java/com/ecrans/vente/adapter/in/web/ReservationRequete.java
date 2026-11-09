package com.ecrans.vente.adapter.in.web;

import java.util.List;

/**
 * Le JSON reçu du site, de l'appli et des bornes : {"billets": [{"place": "A1", "categorie": "PLEIN"}, …]}.
 * Format propre à l'API : il n'expose aucun objet du domaine.
 */
public record ReservationRequete(List<BilletRequete> billets) {

    public record BilletRequete(String place, String categorie) {
    }
}
