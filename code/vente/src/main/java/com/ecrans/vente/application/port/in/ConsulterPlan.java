package com.ecrans.vente.application.port.in;

import com.ecrans.vente.domaine.commun.SeanceId;

/** Port primaire (bonus 9.2, en lecture) : le plan d'une séance, place par place, libre ou non. */
public interface ConsulterPlan {
    PlanDeSalle executer(SeanceId seance);
}
