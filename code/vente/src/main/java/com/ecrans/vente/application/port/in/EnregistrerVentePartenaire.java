package com.ecrans.vente.application.port.in;

import com.ecrans.vente.domaine.commun.CommandeId;

/** Port primaire : enregistrer une vente déjà payée chez un partenaire (SortiesFlash, CinéMalin…). */
public interface EnregistrerVentePartenaire {
    CommandeId executer(VentePartenaire vente);
}
