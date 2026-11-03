package co.edu.uptc.distribuidos.infoapi.model;

import java.util.List;

/**
 * Respuesta de {@code GET /api/info}: exactamente los cinco datos que pide el taller.
 *
 * @param horaContenedor   hora actual del contenedor (ISO-8601 con zona horaria)
 * @param integrantes      nombres de los integrantes del grupo
 * @param contenedor       nombre del contenedor que responde (en Kubernetes, el nombre del pod)
 * @param almacenamientoMB almacenamiento total del contenedor, en MB
 * @param ramMB            RAM del contenedor (límite del cgroup), en MB
 */
public record InfoResponse(
        String horaContenedor,
        List<String> integrantes,
        String contenedor,
        long almacenamientoMB,
        long ramMB) {
}
