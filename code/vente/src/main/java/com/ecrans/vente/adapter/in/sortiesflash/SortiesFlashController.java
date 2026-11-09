package com.ecrans.vente.adapter.in.sortiesflash;

import com.ecrans.vente.application.port.in.EnregistrerVentePartenaire;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.domaine.commun.CommandeId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Étape 8 · le point d'entrée imposé par SortiesFlash (annexe B). Il confie leur JSON à l'ACL, appelle le PORT,
 * et répond dans LEUR format. Les refus passent par GestionErreurs : 409, 404, 400.
 */
@RestController
public class SortiesFlashController {

    private final AclSortiesFlash acl;
    private final EnregistrerVentePartenaire enregistrerVentePartenaire;

    public SortiesFlashController(AclSortiesFlash acl, EnregistrerVentePartenaire enregistrerVentePartenaire) {
        this.acl = acl;
        this.enregistrerVentePartenaire = enregistrerVentePartenaire;
    }

    @PostMapping("/partner/v2/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ReponseSortiesFlash vendre(@RequestBody CommandeSortiesFlash commande) {
        VentePartenaire vente = acl.traduire(commande);
        CommandeId id = enregistrerVentePartenaire.executer(vente);
        return ReponseSortiesFlash.confirmee(vente.referencePartenaire(), id.valeur());
    }
}
