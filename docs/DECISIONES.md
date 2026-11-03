# Decisiones técnicas

Este documento explica **qué se decidió y por qué** en cada parte del servicio. Sirve como documentación del código para la sustentación.

## 0. Uso de IA

El taller exige que el código sea generado en su totalidad con IA. Así se hizo:

- **Herramienta:** Claude (modelo Opus 5.5, de Anthropic), en conversación, el 1 de noviembre de 2026.
- **Entrada:** el enunciado del taller (PDF), la guía del clúster (Kubernetes v1.37.1, 1 master y 5 workers unidos por Tailscale, flannel como red de pods, VMs Ubuntu de unos 1,6 GB de RAM) y los nombres de los integrantes.
- **Qué generó la IA:** todo el código Java, las pruebas, el `pom.xml`, el `Dockerfile`, el flujo de GitHub Actions, los manifiestos de Kubernetes y estos documentos.
- **Qué hizo el grupo:** revisar el código, publicar la imagen, desplegarla en el clúster, ejecutar la demostración y verificar que la respuesta del endpoint fuera correcta.

Las decisiones de abajo son las que tomó la IA al generar el proyecto, con su justificación.

---

## 1. Infraestructura del clúster

**Decisión:** un clúster kubeadm de 6 nodos (1 master y 5 workers) en VMs de VirtualBox de los tres integrantes, unidos por **Tailscale**, con **flannel** como red de pods.

| Nodo | Rol | Dueño |
|---|---|---|
| `k8s-master` | control-plane | Nicolas |
| `k8s-worker1` | worker | Nicolas |
| `k8s-worker-andres1`, `k8s-worker-andres2` | workers | Andres |
| `k8s-worker-david1`, `k8s-worker-david2` | workers | Juan David |

**Por qué:**
- El taller pide mínimo dos máquinas por integrante. Las VMs están en casas y redes distintas, detrás de NAT, así que no se ven entre sí directamente.
- **Tailscale** crea una red privada cifrada (`100.x.x.x`) entre todas las VMs sin abrir puertos en los routers. Cada nodo se registró en Kubernetes con su IP de Tailscale (`--node-ip`), y el kubelet espera a Tailscale al arrancar.
- **flannel** asigna a cada nodo un rango `10.244.X.0/24` y encapsula el tráfico entre pods de distintos nodos. Gracias a eso el master alcanza directamente la IP de cualquier pod, que es lo que usa `scripts/probar.sh`.
- El master conserva el taint `control-plane` y no ejecuta pods de la aplicación: se reserva para el API server, etcd, el scheduler y los controladores, que son los que hacen la recuperación automática.

## 2. Lenguaje y framework

**Decisión:** Java 21 con Spring Boot 4.1 (`spring-boot-starter-webmvc`).

**Por qué:**
- El taller pide Spring Boot. La línea 4.1 es la versión estable vigente; la 3.5 ya terminó su soporte gratuito.
- Java 21 es LTS y la JVM reconoce los límites de memoria del contenedor (lee el cgroup), lo que evita que el proceso se pase del límite y lo mate el kernel.
- Spring MVC (servlets, Tomcat embebido) en lugar de WebFlux: un endpoint de lectura sin concurrencia alta no necesita programación reactiva, y MVC es más fácil de leer y explicar.

## 3. Diseño de la respuesta

**Decisión:** un único endpoint, `GET /api/info`, que devuelve un JSON plano con exactamente los cinco datos del taller: `horaContenedor`, `integrantes`, `contenedor`, `almacenamientoMB` y `ramMB`. Las cantidades van en MB, como números enteros.

**Por qué:**
- Se pidió solo lo que exige el enunciado, sin campos extra ni endpoints adicionales.
- El modelo es un `record` de Java: inmutable y sin código repetitivo.
- MB enteros se leen de un vistazo durante la demostración (`"ramMB": 384`).

## 4. Cómo se identifica el contenedor

**Decisión:** el `hostname` del contenedor (variable `HOSTNAME`).

**Por qué:** en Kubernetes el hostname de un contenedor es el nombre de su pod. Es la forma estándar de saber quién responde y no requiere configuración extra. Se descartó extraer el ID interno del contenedor de `/proc/self/...`, porque depende del runtime (containerd, Docker) y no es estable entre versiones.

## 5. Cómo se mide la RAM

**Decisión:** el límite de memoria del **cgroup** del contenedor (`/sys/fs/cgroup/memory.max` en cgroup v2, `memory.limit_in_bytes` en v1), en MB. Si no hay límite, la RAM total que ve la JVM.

**Por qué:**
- Los `resources.limits.memory` del ReplicaSet (384 Mi) los aplica containerd con cgroups. Leer el cgroup da la RAM **del contenedor**.
- `/proc/meminfo` o `free` mostrarían la RAM del **nodo completo**, que es un error común en este tipo de ejercicio.

## 6. Cómo se mide el almacenamiento

**Decisión:** tamaño total, en MB, del sistema de archivos raíz del contenedor (`/`).

**Por qué:** es el almacenamiento que el contenedor ve y puede usar (lo mismo que mostraría `df /` dentro del contenedor). Ese sistema de archivos (overlay) está sobre el disco del nodo, por eso el valor coincide con el tamaño de ese disco. El ReplicaSet además fija un límite `ephemeral-storage` de 512 Mi: si el contenedor escribe más que eso, el kubelet lo expulsa.

## 7. Lógica separada de Spring y probada

**Decisión:** `LectorRecursos` no usa Spring y recibe por constructor la ruta del cgroup, la del disco y el acceso a las variables de entorno. Las pruebas (`LectorRecursosTest`) le pasan archivos falsos en un directorio temporal.

**Por qué:** se puede probar cgroup v1, v2 y "sin límite" en cualquier PC, sin Kubernetes. El controlador queda delgado: solo traduce HTTP a una llamada del servicio.

## 8. Servicio sin estado

**Decisión:** no hay base de datos ni sesión; cada petición lee los datos en ese momento.

**Por qué:** cualquier réplica puede atender cualquier petición y un pod puede borrarse en cualquier momento sin perder nada. Es requisito para escalar y para que la recuperación automática no tenga efectos secundarios.

## 9. Imagen del contenedor

**Decisión:** `Dockerfile` en dos etapas: compila con `maven:3.9-eclipse-temurin-21` y ejecuta sobre `eclipse-temurin:21-jre-alpine`, con un usuario sin privilegios (UID 10001).

**Por qué:**
- La compilación ocurre dentro de Docker: nadie del grupo necesita instalar Java ni Maven.
- La imagen final solo lleva el JRE y el `.jar` (unos 100 MB comprimida), lo que acelera la descarga en los workers por Tailscale.
- `-XX:MaxRAMPercentage=75`: el heap se ajusta al límite del contenedor y deja un 25 % para el resto de la JVM. `-XX:+UseSerialGC` usa menos memoria que G1, suficiente para una API pequeña.
- Correr sin root reduce el daño si la aplicación fuera comprometida; el ReplicaSet lo exige con `runAsNonRoot`.

## 10. Publicación de la imagen

**Decisión:** GitHub Actions publica la imagen en GitHub Container Registry (`ghcr.io`) con cada push. Docker Hub queda como alternativa.

**Por qué:** no requiere Docker en los PCs del grupo (que tienen poca RAM y ya corren VMs) ni guardar contraseñas: el flujo usa el token automático de GitHub.

## 11. ReplicaSet, sin Deployment ni Service

**Decisión:** un **ReplicaSet** de 5 réplicas, creado directamente, sin Deployment encima y sin Service delante.

**Por qué:**
- El ReplicaSet es el objeto que de verdad mantiene la cantidad de pods: si falta uno, lo crea. Es lo que el taller pide demostrar (borrar pods y apagar un worker), y usarlo directamente deja ver ese mecanismo sin capas extra.
- Un Deployment solo agregaría actualizaciones graduales, historial y vuelta atrás de versiones, que el taller no pide. A cambio, sin Deployment, para cambiar la imagen hay que borrar los pods a mano y para redistribuirlos también.
- Un DaemonSet pone un pod por nodo, pero si el nodo se apaga **no** recrea ese pod en otro lado; no serviría para la demostración.
- 5 réplicas porque hay 5 workers: así cada uno ejecuta un pod.

## 12. Reparto en todos los workers

**Decisión:** `topologySpreadConstraints` con `maxSkew: 1` por `kubernetes.io/hostname`, `whenUnsatisfiable: DoNotSchedule` y `nodeTaintsPolicy: Honor`.

**Por qué:**
- Sin esta regla el scheduler podría poner dos pods en el mismo worker y dejar otro vacío; el taller exige que todos los workers ejecuten algún pod.
- `maxSkew: 1` obliga a que la diferencia de pods entre dos workers sea como máximo uno.
- Como no hay Deployment, todos los pods son del mismo ReplicaSet y la regla los cuenta juntos; no hay versiones mezcladas.
- `nodeTaintsPolicy: Honor` es clave: sin ella, el master (que tiene el taint `control-plane` y no acepta pods) y un worker apagado (taint `unreachable`) contarían como nodos con 0 pods, y el scheduler dejaría pods en `Pending`.

## 13. Recuperación rápida al apagar un worker

**Decisión:** `tolerations` para `node.kubernetes.io/not-ready` y `node.kubernetes.io/unreachable` con `tolerationSeconds: 30`.

**Por qué:** por defecto Kubernetes espera 300 s (5 minutos) antes de sacar los pods de un nodo caído, para no reaccionar ante cortes cortos de red. En la demostración se baja a 30 s para que todo se vea en un minuto y medio. En producción se dejaría más alto.

## 14. Sondas de salud

**Decisión:** tres sondas HTTP sobre el mismo `GET /api/info` (no se agregó Actuator, para que la API tenga un solo endpoint):
- **startupProbe** (hasta 3 minutos): las VMs son lentas y la JVM tarda en arrancar; mientras tanto no se evalúan las otras.
- **livenessProbe**: si la app se cuelga, el kubelet reinicia el contenedor.
- **readinessProbe**: marca el pod como listo (`READY 1/1`) solo cuando responde. Sin Service no filtra tráfico, pero muestra en `kubectl get pods` y `kubectl get rs` cuántos pods están listos de verdad.

## 15. Recursos

**Decisión:** `requests` de 100m de CPU y 192 Mi de RAM; `limits` de 1 CPU y 384 Mi.

**Por qué:** los workers tienen unos 1,6 GB de RAM. Con estos valores un worker puede recibir 2 o 3 pods cuando otro está apagado sin quedarse sin memoria. Las `requests` las usa el scheduler para decidir dónde cabe un pod; los `limits` los aplica el kernel.

## 16. Acceso a los pods sin Service

**Decisión:** no hay Service. Cada pod se consulta directamente por su IP de la red de pods con `scripts/probar.sh`, que obtiene las IPs con `kubectl` y llama a cada una. Para el navegador se usa `kubectl port-forward` hacia un pod.

**Por qué:**
- Las IPs de pods (`10.244.x.x`, de flannel) son alcanzables desde cualquier nodo del clúster, incluido el master, así que no hace falta exponer puertos.
- Consultar cada pod por separado muestra el reparto con claridad: en cada vuelta responden los 5, cada uno con su nodo.
- Se descartó `hostPort` (abrir el puerto del pod en el worker): dos pods no pueden usar el mismo puerto en un nodo, así que al apagar un worker el reemplazo no cabría en ningún otro y quedaría en `Pending`.
- Contrapartida, que se explica en la sustentación: sin Service no hay dirección fija ni balanceo, y cuando un pod se recrea cambia su IP. Ese es el problema que resuelve un Service.

## 17. Apagado ordenado

**Decisión:** `server.shutdown: graceful` con 10 s de espera y `terminationGracePeriodSeconds: 20`.

**Por qué:** al borrar un pod, Kubernetes le envía SIGTERM. La app termina las peticiones en curso antes de cerrarse, así que no se cortan respuestas a medias.
