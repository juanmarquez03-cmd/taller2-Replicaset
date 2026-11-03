package co.edu.uptc.distribuidos.infoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API del taller de Kubernetes.
 *
 * <p>Expone un único endpoint ({@code GET /api/info}) que describe el contenedor que
 * atiende la petición: hora, integrantes del grupo, nombre del contenedor,
 * almacenamiento y RAM.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class InfoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfoApiApplication.class, args);
    }
}
