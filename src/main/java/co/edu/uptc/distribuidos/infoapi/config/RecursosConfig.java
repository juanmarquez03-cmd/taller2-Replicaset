package co.edu.uptc.distribuidos.infoapi.config;

import co.edu.uptc.distribuidos.infoapi.service.LectorRecursos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el {@link LectorRecursos} como bean de Spring con las rutas reales del
 * contenedor ({@code /sys/fs/cgroup} y {@code /}). En las pruebas se crea a mano
 * con rutas falsas.
 */
@Configuration
public class RecursosConfig {

    @Bean
    public LectorRecursos lectorRecursos() {
        return new LectorRecursos();
    }
}
