package co.edu.uptc.distribuidos.infoapi.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Integrantes del grupo, leídos de {@code application.yml} (prefijo {@code taller}).
 * Están en la configuración para poder cambiarlos sin tocar el código.
 *
 * @param integrantes nombres completos de los integrantes
 */
@ConfigurationProperties(prefix = "taller")
public record TallerProperties(List<String> integrantes) {

    public TallerProperties {
        integrantes = integrantes == null ? List.of() : List.copyOf(integrantes);
    }
}
