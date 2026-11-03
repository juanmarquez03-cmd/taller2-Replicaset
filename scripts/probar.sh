#!/usr/bin/env bash
# Consulta cada 2 segundos el endpoint /api/info de TODOS los pods del
# ReplicaSet, uno por uno, directamente por su IP (no hay Service).
# Muestra: nodo (de kubectl) | contenedor y hora (de la respuesta).
#
# Ejecutar en el master:  bash scripts/probar.sh
# Salir: Ctrl+C

while true; do
  echo "----- $(date +%H:%M:%S) -----"
  kubectl get pods -n taller -l app=info-api \
      --field-selector=status.phase=Running \
      -o jsonpath='{range .items[*]}{.status.podIP} {.spec.nodeName}{"\n"}{end}' |
  while read -r ip nodo; do
    [ -z "$ip" ] && continue
    if r=$(curl -s --max-time 2 "http://$ip:8080/api/info"); then
      contenedor=$(echo "$r" | sed -n 's/.*"contenedor":"\([^"]*\)".*/\1/p')
      hora=$(echo "$r" | sed -n 's/.*"horaContenedor":"\([^"]*\)".*/\1/p')
      echo "$nodo | $contenedor | $hora"
    else
      echo "$nodo | [$ip sin respuesta]"
    fi
  done
  sleep 2
done
