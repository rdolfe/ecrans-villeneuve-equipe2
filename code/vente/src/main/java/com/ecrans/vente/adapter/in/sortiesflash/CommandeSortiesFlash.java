package com.ecrans.vente.adapter.in.sortiesflash;

import java.util.List;

/**
 * Le JSON envoyé par SortiesFlash (annexe B), tel quel, avec SES noms de champs.
 * C'est leur langage : ce record ne sort jamais de ce package (l'ACL le traduit en VentePartenaire).
 */
public record CommandeSortiesFlash(String show_ref, List<String> seats, List<String> ticket_types,
                                   String partner_order_id, Long paid_amount_cents) {
}
