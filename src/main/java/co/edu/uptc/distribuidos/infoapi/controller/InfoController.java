package co.edu.uptc.distribuidos.infoapi.controller;

import co.edu.uptc.distribuidos.infoapi.model.InfoResponse;
import co.edu.uptc.distribuidos.infoapi.service.InfoService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único endpoint de la API: {@code GET /api/info}.
 *
 * <p>Devuelve la hora del contenedor, los integrantes del grupo, el nombre del
 * contenedor que responde, su almacenamiento y su RAM (en MB).</p>
 */
@RestController
public class InfoController {

    private final InfoService infoService;

    public InfoController(InfoService infoService) {
        this.infoService = infoService;
    }

    @GetMapping(value = "/api/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public InfoResponse info() {
        return infoService.info();
    }
}
