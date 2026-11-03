# Info API: taller de Kubernetes

**Sistemas Distribuidos · UPTC · Ingeniería de Sistemas y Computación**

Integrantes:
- Nicolas David Castro Cely
- Juan David Márquez Pinto
- Andres Mauricio Sanchez Sanchez

REST API en Spring Boot con un único endpoint, `GET /api/info`, que responde con la información del contenedor que atiende la petición. Se despliega como un **ReplicaSet** de 5 pods (sin Deployment ni Service) en un clúster Kubernetes de 6 nodos unidos por Tailscale. Sirve para demostrar que los pods se reparten entre todos los workers y que Kubernetes los recupera cuando se borran o cuando se apaga un worker.

Documentos relacionados:
- [docs/DECISIONES.md](docs/DECISIONES.md): decisiones técnicas, por qué se tomaron y uso de IA.
- [docs/DEMO.md](docs/DEMO.md): guion de la demostración y explicación de cada caso.

---

## 1. Infraestructura

### Nodos

| Nodo | Rol | Dueño | IP de Tailscale |
|---|---|---|---|
| `k8s-master` | control-plane (no ejecuta pods de la app) | Nicolas | 100.105.119.107 |
| `k8s-worker1` | worker | Nicolas | — |
| `k8s-worker-andres1` | worker | Andres | — |
| `k8s-worker-andres2` | worker | Andres | — |
| `k8s-worker-david1` | worker | Juan David | 100.72.197.114 |
| `k8s-worker-david2` | worker | Juan David | 100.82.10.14 |

- VMs Ubuntu 26.04 en VirtualBox (red NAT), 2 CPU y unos 1,6 GB de RAM cada worker.
- Kubernetes **v1.37.1** instalado con kubeadm, containerd como runtime.
- **Tailscale** une las VMs de los tres integrantes, que están en redes distintas, en una red privada `100.x.x.x`. Cada nodo se registró con su IP de Tailscale (`--node-ip`).
- **flannel** es la red de pods: cada nodo tiene un rango `10.244.X.0/24` y el tráfico entre pods de distintos nodos viaja encapsulado por Tailscale.

### Objetos de Kubernetes

```
Namespace "taller"
└── ReplicaSet "info-api"  (replicas: 5, selector app=info-api)
    ├── Pod en k8s-worker1
    ├── Pod en k8s-worker-andres1
    ├── Pod en k8s-worker-andres2
    ├── Pod en k8s-worker-david1
    └── Pod en k8s-worker-david2
```

| Objeto | Archivo | Función |
|---|---|---|
| Namespace `taller` | `k8s/01-namespace.yaml` | Separa la app de los componentes del clúster |
| ReplicaSet `info-api` | `k8s/02-replicaset.yaml` | Mantiene siempre 5 pods y los reparte uno por worker |

**No hay Deployment ni Service.** Cada pod se consulta directamente por su IP de la red de pods (`scripts/probar.sh`). La recuperación automática la hace el ReplicaSet.

---

## 2. Endpoint

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/info` | JSON con los 5 datos que pide el taller |

### Ejemplo de respuesta

```json
{
  "horaContenedor": "2026-11-03T08:15:30-05:00",
  "integrantes": [
    "Nicolas David Castro Cely",
    "Juan David Márquez Pinto",
    "Andres Mauricio Sanchez Sanchez"
  ],
  "contenedor": "info-api-x2k4p",
  "almacenamientoMB": 18258,
  "ramMB": 384
}
```

| Pide el taller | Campo | De dónde sale |
|---|---|---|
| Hora del contenedor | `horaContenedor` | Reloj del contenedor, zona `America/Bogota` (variable `TZ`) |
| Nombres de los integrantes | `integrantes` | `application.yml` |
| Nombre o id del contenedor que responde | `contenedor` | Hostname del contenedor, que en Kubernetes es el nombre del pod |
| Cantidad de almacenamiento | `almacenamientoMB` | Tamaño total del sistema de archivos `/` del contenedor, en MB |
| Cantidad de RAM | `ramMB` | Límite de memoria del contenedor según su cgroup, en MB (384) |

---

## 3. Estructura del proyecto

```
taller2-Replicaset/
├── pom.xml                         Dependencias (Spring Boot 4.1 web, Java 21)
├── Dockerfile                      Compila con Maven y genera la imagen final
├── .github/workflows/imagen.yml    Publica la imagen en ghcr.io con cada push
├── k8s/
│   ├── 01-namespace.yaml           Namespace "taller"
│   └── 02-replicaset.yaml          ReplicaSet de 5 réplicas, una por worker
├── scripts/probar.sh               Consulta /api/info en cada pod por su IP
├── docs/
│   ├── DECISIONES.md
│   └── DEMO.md
└── src/
    ├── main/java/co/edu/uptc/distribuidos/infoapi/
    │   ├── InfoApiApplication.java         Arranque
    │   ├── config/TallerProperties.java    Integrantes (desde application.yml)
    │   ├── config/RecursosConfig.java      Registra el lector de recursos
    │   ├── controller/InfoController.java  Endpoint GET /api/info
    │   ├── service/InfoService.java        Arma la respuesta
    │   ├── service/LectorRecursos.java     Lee hostname, disco y cgroup
    │   └── model/InfoResponse.java         Los 5 campos de la respuesta
    ├── main/resources/application.yml
    └── test/java/.../LectorRecursosTest.java
```

---

## 4. Imagen del contenedor

Cada commit en la rama `main` de este repositorio ejecuta el flujo **"Publicar imagen"** (pestaña **Actions**). Compila el proyecto dentro de Docker, corre las pruebas y publica:

```
ghcr.io/juanmarquez03-cmd/info-api:latest
```

La imagen es pública, así que los workers la descargan sin credenciales. Si se crea de nuevo, hay que hacerla pública otra vez: perfil de GitHub → **Packages** → `info-api` → **Package settings** → **Change visibility** → **Public**.

---

## 5. Montar la infraestructura

### 5.1 Comprobar el clúster

En cada worker, después de encenderlo:

```bash
tailscale ip -4                       # su IP 100.x
sudo systemctl is-active kubelet      # active
```

En el master:

```bash
kubectl get nodes                     # los 6 nodos en Ready
```

### 5.2 Desplegar la aplicación

En el master:

```bash
git clone https://github.com/juanmarquez03-cmd/taller2-Replicaset.git   # o: cd ~/taller2-Replicaset && git pull
cd taller2-Replicaset
kubectl apply -f k8s/
kubectl get rs -n taller
kubectl get pods -n taller -o wide -w
```

En 1 a 3 minutos el ReplicaSet debe mostrar `DESIRED 5  CURRENT 5  READY 5`, con **cada pod en un worker distinto** (columna `NODE`). `Ctrl+C` para salir de la vista en vivo.

Si antes existía otra versión (por ejemplo, con Deployment y Service), primero borrarla completa:

```bash
kubectl delete namespace taller
```

### 5.3 Verificar

```bash
kubectl get rs -n taller
kubectl get pods -n taller -o custom-columns=NODE:.spec.nodeName --no-headers | sort | uniq -c
kubectl get deployment,service -n taller      # debe decir: No resources found
```

Debe haber un `1` en cada uno de los 5 workers. Si un worker queda con 2 pods y otro con 0, borrar uno de esos dos (`kubectl delete pod -n taller NOMBRE`): el reemplazo va al worker vacío.

---

## 6. Probar

En el master:

```bash
bash scripts/probar.sh
```

Cada 2 segundos consulta `/api/info` en los 5 pods y muestra una línea por pod: `nodo | contenedor | hora`. El nodo lo saca de `kubectl`; el contenedor y la hora, de la respuesta. `Ctrl+C` para salir.

Respuesta completa de un pod:

```bash
POD_IP=$(kubectl get pods -n taller -o jsonpath='{.items[0].status.podIP}')
curl -s http://$POD_IP:8080/api/info
```

**En el navegador:** en el master,

```bash
kubectl port-forward -n taller --address 0.0.0.0 pod/NOMBRE_DEL_POD 8080:8080
```

y desde un PC en la red de Tailscale del grupo: `http://100.105.119.107:8080/api/info`. Siempre responde ese pod; si se borra, hay que repetir el comando con el pod nuevo.

---

## 7. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `ImagePullBackOff` / `ErrImagePull` | Imagen privada o nombre mal escrito en `image:` | Hacer pública la imagen (sección 4) y revisar con `kubectl describe pod -n taller NOMBRE` |
| Pods en `Pending` | Falta RAM en los workers o hay menos de 5 workers en Ready | `kubectl describe pod -n taller NOMBRE` (sección Events) y `kubectl get nodes` |
| `CrashLoopBackOff` | La app falla al arrancar | `kubectl logs -n taller NOMBRE --previous` |
| `Running` pero `READY 0/1` mucho tiempo | La JVM tarda en arrancar en VMs lentas | Esperar; la startupProbe da hasta 3 min |
| `probar.sh` muestra `[IP sin respuesta]` para un pod `Running` | El master no alcanza la red de pods de ese nodo (flannel) | `kubectl get pods -n kube-flannel -o wide` y revisar que el nodo esté en Ready |
| Un nodo en `NotReady` después de encender | Tailscale o kubelet no arrancaron | En esa VM: `tailscale status` y `sudo systemctl restart kubelet` |
| Al actualizar el código los pods siguen con la versión vieja | Un ReplicaSet no actualiza pods que ya existen | `kubectl delete pods -n taller -l app=info-api` para que se creen con la imagen nueva |

Comandos útiles:

```bash
kubectl get all -n taller
kubectl logs -n taller -l app=info-api --tail=20
kubectl get events -n taller --sort-by=.lastTimestamp
kubectl delete -f k8s/          # borra todo lo del taller
```

---

## 8. Ejecutar sin Kubernetes (opcional)

Con Java 21 y Maven:

```bash
mvn spring-boot:run
# http://localhost:8080/api/info
```

Fuera de Kubernetes, `contenedor` es el nombre del PC y `ramMB` es la RAM total del equipo, porque no hay límite de cgroup.

---

## 9. Uso de IA

Todo el código, los manifiestos y esta documentación fueron generados con **Claude (Anthropic)**, como exige el taller. El detalle está en [docs/DECISIONES.md](docs/DECISIONES.md).
