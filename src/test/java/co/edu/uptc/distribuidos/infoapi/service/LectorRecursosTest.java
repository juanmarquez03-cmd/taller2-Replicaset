package co.edu.uptc.distribuidos.infoapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas del lector con un cgroup falso en un directorio temporal. */
class LectorRecursosTest {

    @TempDir
    Path tmp;

    @Test
    void ramSaleDelLimiteCgroupV2EnMB() throws IOException {
        Files.writeString(tmp.resolve("memory.max"), "402653184\n");
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.<String, String>of()::get);

        assertThat(lector.ramMB()).isEqualTo(384);
    }

    @Test
    void ramSaleDelLimiteCgroupV1EnMB() throws IOException {
        Path v1 = Files.createDirectory(tmp.resolve("memory"));
        Files.writeString(v1.resolve("memory.limit_in_bytes"), "268435456\n");
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.<String, String>of()::get);

        assertThat(lector.ramMB()).isEqualTo(256);
    }

    @Test
    void sinLimiteUsaLaRamDeLaMaquina() throws IOException {
        Files.writeString(tmp.resolve("memory.max"), "max\n");
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.<String, String>of()::get);

        assertThat(lector.ramMB()).isPositive();
    }

    @Test
    void nombreDelContenedorEsElHostname() {
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.of("HOSTNAME", "info-api-abc12")::get);

        assertThat(lector.nombreContenedor()).isEqualTo("info-api-abc12");
    }

    @Test
    void almacenamientoEnMB() {
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.<String, String>of()::get);

        assertThat(lector.almacenamientoMB()).isPositive();
    }
}
