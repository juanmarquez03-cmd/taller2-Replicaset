package co.edu.uptc.distribuidos.infoapi.service;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Lee del sistema operativo el nombre, el almacenamiento y la RAM del contenedor.
 *
 * <p>No depende de Spring: las rutas y el acceso a variables de entorno se reciben
 * por el constructor, así se puede probar con archivos falsos.</p>
 */
public class LectorRecursos {

    private static final long BYTES_POR_MB = 1024L * 1024L;

    /** cgroup v1 usa un número enorme (casi 2^63) para decir "sin límite". */
    private static final long UMBRAL_SIN_LIMITE = 1L << 60;

    private final Path cgroupRaiz;
    private final Path discoRaiz;
    private final Function<String, String> entorno;

    /** Producción: cgroup real, disco "/" y variables de entorno reales. */
    public LectorRecursos() {
        this(Path.of("/sys/fs/cgroup"), Path.of("/"), System::getenv);
    }

    /** Pruebas. */
    public LectorRecursos(Path cgroupRaiz, Path discoRaiz, Function<String, String> entorno) {
        this.cgroupRaiz = cgroupRaiz;
        this.discoRaiz = discoRaiz;
        this.entorno = entorno;
    }

    /**
     * Nombre del contenedor. En Kubernetes el hostname del contenedor es el nombre
     * del pod, y la variable HOSTNAME lo trae.
     */
    public String nombreContenedor() {
        String hostname = entorno.apply("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "desconocido";
        }
    }

    /** Tamaño total del sistema de archivos del contenedor ("/"), en MB. */
    public long almacenamientoMB() {
        return discoRaiz.toFile().getTotalSpace() / BYTES_POR_MB;
    }

    /**
     * RAM del contenedor en MB: el límite que el kernel le impone por cgroup
     * (memory.max en cgroup v2, memory.limit_in_bytes en v1). Si no tiene límite,
     * devuelve la RAM total que ve la máquina.
     */
    public long ramMB() {
        Long limite = leerLimite(cgroupRaiz.resolve("memory.max"));
        if (limite == null) {
            limite = leerLimite(cgroupRaiz.resolve("memory").resolve("memory.limit_in_bytes"));
        }
        if (limite == null) {
            limite = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        }
        return limite / BYTES_POR_MB;
    }

    /** Lee un límite del cgroup; "max", un valor gigante o un archivo inexistente = sin límite (null). */
    private Long leerLimite(Path archivo) {
        try {
            String texto = Files.readString(archivo, StandardCharsets.US_ASCII).trim();
            if (texto.equals("max")) {
                return null;
            }
            long valor = Long.parseLong(texto);
            return valor >= UMBRAL_SIN_LIMITE ? null : valor;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
