package com.ecrans.vente.adapter.in.payvia;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Étape 7.4 · branche l'ACL WebhookPayVia sur HTTP. Toute la traduction du langage PayVia reste dans l'ACL :
 * ce contrôleur ne fait que la brancher. 204 : PayVia ne réessaie pas (annexe A).
 */
@RestController
public class WebhookPayViaController {

    private final WebhookPayVia webhook;

    public WebhookPayViaController(WebhookPayVia webhook) {
        this.webhook = webhook;
    }

    @PostMapping("/webhooks/payvia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recevoir(@RequestBody NotificationPayVia notification) {
        webhook.recevoir(notification);
    }
}
