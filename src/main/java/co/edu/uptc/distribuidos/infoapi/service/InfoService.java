package co.edu.uptc.distribuidos.infoapi.service;

import co.edu.uptc.distribuidos.infoapi.config.TallerProperties;
import co.edu.uptc.distribuidos.infoapi.model.InfoResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/**
 * Arma la respuesta de /api/info. No guarda estado: cada petición lee los datos en
 * ese momento, así cualquier pod puede responder y puede borrarse sin perder nada.
 */
@Service
public class InfoService {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final TallerProperties taller;
    private final LectorRecursos lector;

    public InfoService(TallerProperties taller, LectorRecursos lector) {
        this.taller = taller;
        this.lector = lector;
    }

    public InfoResponse info() {
        return new InfoResponse(
                ZonedDateTime.now().format(FORMATO_HORA),
                taller.integrantes(),
                lector.nombreContenedor(),
                lector.almacenamientoMB(),
                lector.ramMB());
    }
}
