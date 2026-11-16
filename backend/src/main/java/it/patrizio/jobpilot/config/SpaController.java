package it.patrizio.jobpilot.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Quando il frontend Angular è impacchettato dentro il backend (static/), le rotte dell'app
 * (es. /candidature/3) devono restituire index.html: ci pensa Angular a mostrare la pagina giusta.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/ricerca", "/importa", "/profilo", "/statistiche", "/impostazioni", "/candidature/{id}"})
    public String index() {
        return "forward:/index.html";
    }
}
