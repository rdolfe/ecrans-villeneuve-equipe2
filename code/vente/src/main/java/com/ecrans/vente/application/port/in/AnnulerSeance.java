package com.ecrans.vente.application.port.in;

import com.ecrans.vente.domaine.commun.SeanceId;

/**
 * Port primaire (bonus 9.4) : la Programmation a annulé ou déplacé une séance (B10).
 * En production, il serait déclenché par l'événement SeanceAnnulee de la Programmation.
 */
public interface AnnulerSeance {
    void executer(SeanceId seance);
}
