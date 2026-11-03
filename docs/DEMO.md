# Guion de la demostración (ReplicaSet)

## Infraestructura que se usa

- **6 nodos** unidos por Tailscale: `k8s-master` (Nicolas) y 5 workers: `k8s-worker1` (Nicolas), `k8s-worker-andres1` y `k8s-worker-andres2` (Andres), `k8s-worker-david1` y `k8s-worker-david2` (Juan David).
- **Un ReplicaSet** `info-api` de 5 pods en el namespace `taller`, uno por worker. **Sin Deployment ni Service.**
- **Un endpoint**, `GET /api/info`, en el puerto 8080 de cada pod. Se consulta cada pod por su IP de flannel (`10.244.x.x`) con `scripts/probar.sh`.
- Todo se ejecuta en el **master**, salvo el apagado del worker, que lo hace su dueño en su VM.

## Preparación (antes de la sustentación)

1. Encender las 6 VMs. En cada worker: `tailscale ip -4` y `sudo systemctl is-active kubelet`.
2. En el master:
   ```bash
   kubectl get nodes                                   # 6 en Ready
   cd ~/taller2-Replicaset && git pull
   kubectl apply -f k8s/
   kubectl get rs -n taller                            # 5 / 5 / 5
   kubectl get pods -n taller -o custom-columns=NODE:.spec.nodeName --no-headers | sort | uniq -c
   ```
3. Si un worker tiene 2 pods y otro 0, borrar uno de esos dos para que se reubique.
4. Instalar `tmux` en el master si no está (`sudo apt-get install -y tmux`).

## Terminales

Conviene tener **tres terminales** (o `tmux`) en el master:

| Terminal | Comando |
|---|---|
| T1 | `kubectl get pods -n taller -o wide -w` (pods en vivo) |
| T2 | `bash scripts/probar.sh` (consulta a cada pod por su IP cada 2 s) |
| T3 | Para los comandos de la demostración |

Antes de empezar:

```bash
kubectl get nodes          # 6 nodos en Ready
kubectl get rs -n taller   # DESIRED 5, CURRENT 5, READY 5
```

---

## Parte 1. Todos los workers ejecutan un pod

```bash
kubectl get pods -n taller -o wide
kubectl get pods -n taller -o custom-columns=NODE:.spec.nodeName --no-headers | sort | uniq -c
```

Debe haber un pod en cada uno de los 5 workers. En T2 cada bloque muestra las 5 respuestas, una por pod: `nodo | contenedor | hora`.

**Qué decir:** el ReplicaSet pide 5 réplicas. La regla `topologySpreadConstraints` obliga al scheduler a repartirlas con una diferencia máxima de un pod entre workers; como hay 5 workers, queda uno en cada uno. El master no recibe pods por su taint `control-plane`.

**Por qué no hay Service:** cada pod se consulta directamente por su IP de la red de pods (flannel). El script obtiene esas IPs con `kubectl` y llama a cada una. Sin Service no hay una dirección única ni balanceo: quien consulta tiene que saber las IPs, y cambian cada vez que se recrea un pod.

---

## Parte 2. Borrar un pod

```bash
kubectl delete pod -n taller NOMBRE_DEL_POD
```

Qué se ve:
- **T1:** el pod pasa a `Terminating` y aparece uno nuevo, **con otro nombre**, que pasa por `Pending` → `ContainerCreating` → `Running 1/1`, en el mismo worker.
- **T2:** durante unos segundos ese pod no aparece o sale `[IP sin respuesta]`; luego aparece el nuevo, con **otro nombre de contenedor**. Los otros 4 siguen respondiendo.

Mostrar quién lo hizo:

```bash
kubectl get rs -n taller
kubectl describe rs -n taller info-api | tail -10
```

En los eventos aparecen `SuccessfulDelete` y `SuccessfulCreate` del controlador del ReplicaSet.

### Explicación

Kubernetes trabaja por **estado deseado**. El ReplicaSet declara "quiero 5 pods con la etiqueta `app=info-api`", y eso queda guardado en **etcd**. El **controlador del ReplicaSet** (dentro del `kube-controller-manager`) compara todo el tiempo cuántos pods con esa etiqueta existen con cuántos se desean.

1. `kubectl delete pod` le pide al **API server** borrar el pod; pasa a `Terminating`.
2. El **kubelet** del worker le envía SIGTERM al contenedor; Spring Boot termina las peticiones en curso y se cierra.
3. El **controlador del ReplicaSet** ve 4 pods de 5 y crea uno nuevo a partir de la plantilla (`template`).
4. El **scheduler** elige un nodo. Por la regla de reparto, elige el worker que quedó sin pod.
5. El **kubelet** de ese worker le pide a **containerd** crear el contenedor; **flannel** le da una IP nueva.
6. Cuando la **readinessProbe** responde, el pod queda `READY 1/1`.

Ideas clave:
- No es el mismo pod que revive: es **uno nuevo**, con otro nombre, UID e IP.
- El ReplicaSet reconoce sus pods **por la etiqueta** (`selector`), no por el nombre.
- Funciona porque la app **no guarda estado**.

---

## Parte 3. Apagar un worker

Ubicar el pod de `k8s-worker-david2`:

```bash
kubectl get pods -n taller -o wide | grep david2
```

En la VM **k8s-worker-david2**:

```bash
sudo poweroff
```

En T3 del master:

```bash
kubectl get nodes -w
```

| Tiempo | Qué pasa | Dónde se ve |
|---|---|---|
| 0–50 s | El nodo aún figura `Ready`; su pod deja de responder | T2: `[IP sin respuesta]` |
| ~40–50 s | El nodo pasa a `NotReady` | `kubectl get nodes` |
| +30 s | Su pod pasa a `Terminating` y se crea uno nuevo en otro worker | T1 |
| ~1,5–2,5 min | El pod nuevo queda `Running 1/1`; hay 5 pods en 4 workers | T1, T2 y `kubectl get rs` |

El pod viejo se queda en `Terminating` mientras el nodo esté apagado, porque el kubelet no puede confirmar que se detuvo. No cuenta como réplica. Mientras siga en `Terminating`, T2 puede seguir mostrando `[IP sin respuesta]` para su IP; es el pod viejo, no el nuevo.

### Volver a encender el worker

Encender la VM. En 1 o 2 minutos vuelve a `Ready` y el pod viejo desaparece.

Los pods **no se reparten solos**: un worker queda con 2 pods y david2 con 0. Un ReplicaSet no tiene `rollout restart` (eso es del Deployment), así que se redistribuye borrando uno de los dos pods del worker que tiene 2:

```bash
kubectl get pods -n taller -o custom-columns=POD:.metadata.name,NODO:.spec.nodeName
kubectl delete pod -n taller NOMBRE_DE_UNO_DE_LOS_DOS
```

El ReplicaSet crea el reemplazo, y la regla de reparto lo obliga a ir al worker con 0: david2.

### Explicación

1. **Latidos.** Cada kubelet renueva un objeto `Lease` en `kube-node-lease` cada ~10 s.
2. **Detección.** El **node lifecycle controller** ve que pasan ~40–50 s sin latido y marca el nodo `NotReady`.
3. **Taints.** Le pone `node.kubernetes.io/unreachable:NoSchedule` (no programar ahí) y `:NoExecute` (sacar los pods).
4. **Desalojo.** Los pods toleran el `NoExecute` solo 30 s (`tolerationSeconds`; por defecto serían 300 s); después se marcan para borrado.
5. **Reemplazo.** El ReplicaSet ve 4 activos de 5 y crea uno nuevo; el scheduler descarta el nodo caído por el taint y lo pone en un worker sano.

Límites para mencionar:
- El **master** es punto único de falla: si se apaga, nadie recrea pods hasta que vuelva.
- Sin Service, un cliente que tenía guardada la IP del pod caído pierde la conexión; tiene que averiguar las IPs nuevas. Ese problema es justamente lo que resuelve un Service.
- Sin Deployment no hay actualización gradual: para cambiar la versión de la imagen hay que borrar los pods a mano.

---

## Ver la API en el navegador (sin Service)

Desde el master, redirigir un pod a un puerto del master:

```bash
kubectl port-forward -n taller --address 0.0.0.0 pod/NOMBRE_DEL_POD 8080:8080
```

Con Tailscale en el PC: `http://100.105.119.107:8080/api/info`. Siempre responde **ese** pod; si se borra, el port-forward se corta y hay que repetirlo con el pod nuevo.

---

## Preguntas probables

**¿Qué diferencia hay entre Pod, ReplicaSet y Deployment?**
El Pod corre uno o más contenedores. El ReplicaSet mantiene una cantidad fija de pods iguales. El Deployment administra ReplicaSets y agrega actualizaciones graduales, historial y vuelta atrás. Aquí se usa el ReplicaSet directamente.

**¿Cómo sabe el ReplicaSet cuáles pods son suyos?**
Por el `selector`: cuenta todos los pods con la etiqueta `app=info-api`. Si se crea a mano un pod con esa etiqueta, el ReplicaSet lo adopta y borra otro para quedar en 5.

**¿Qué pasa si se borra el ReplicaSet?**
Se borran sus pods y nadie los recrea.

**¿Qué pasa si se cambia la imagen en el ReplicaSet?**
Los pods que ya existen no cambian; solo los que se creen después. Hay que borrarlos para que salgan con la imagen nueva. Eso es lo que automatiza un Deployment.

**¿Por qué no un DaemonSet?**
Pone un pod por nodo, pero si el nodo se cae no lo recrea en otro.

**¿Para qué serviría un Service?**
Daría una IP y un nombre fijos delante de los pods, repartiría el tráfico y solo enviaría peticiones a pods listos. Sin él hay que consultar cada pod por su IP.

**¿Cómo se comunica el master con un pod de otro worker?**
Por **flannel**: cada nodo tiene un rango `10.244.X.0/24` y los paquetes entre nodos viajan encapsulados (VXLAN) por la red de Tailscale.

**¿De dónde sale el nombre del contenedor?**
Del hostname: Kubernetes le pone al contenedor como hostname el nombre del pod. El nodo donde corre se ve con `kubectl get pods -o wide`.

**¿Por qué la RAM se lee del cgroup y no de `/proc/meminfo`?**
`/proc/meminfo` muestra la memoria del nodo completo; el límite del contenedor (384 Mi) lo aplica el kernel con cgroups. Por eso `ramMB` da 384.

**¿Por qué el almacenamiento da unos 18 000 MB?**
Es el tamaño del sistema de archivos que ve el contenedor, que está sobre el disco del worker. Lo que el contenedor puede escribir lo limita Kubernetes con `ephemeral-storage` (512 Mi).

---

## Comandos de emergencia

```bash
kubectl describe pod -n taller NOMBRE        # por qué no arranca
kubectl logs -n taller NOMBRE --previous     # error de la app
kubectl delete -f k8s/ && kubectl apply -f k8s/   # recrear todo
kubectl scale rs/info-api -n taller --replicas=5  # volver a 5 réplicas
# Nodo que no vuelve a Ready (en la VM del worker):
tailscale status
sudo systemctl restart kubelet
```
