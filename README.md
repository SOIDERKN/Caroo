# Carro BT v2: control + sensor VL53 + mapeo de trayectoria

Interfaz horizontal (cruceta doble, luces, claxon, velocidad S/M/F) con:
- Panel central: **Objeto a: X cm** (VL53), **Recorrido** (m) y **Orientacion** (grados) en vivo.
- Chevron inferior: abre/cierra el panel con **Iniciar mapeo**, **Detener mapeo** y **Borrar mapeo**.
- Al detener el mapeo se abre una ventana con un PNG (vistas XY, XZ, YZ, distancia total y
  orientacion final) y el boton **Guardar foto** (queda en Imagenes/CarroBT).
- Boton </> : ultimas lineas recibidas del Arduino (para depurar). Boton ☰ : ver ultimo mapa.

## Comandos (editar en `Cmd.kt`)
Movimiento F B L R G H I J S (como el sketch original). Mapeo: M inicia, N detiene.
Los siguientes son SUPOSICIONES, ajustalos a tu sketch: luz baja W/w, luz alta U/u,
estacionamiento P/p, intermitentes Z/z, claxon V/v, derrape K, giro T, velocidad 4/7/q.

## Lineas que la app entiende del Arduino
D,t,dist,x,y,yaw,roll,pitch | MAP,ARMADO | MAP,INICIO | MAP,FIN |
"objeto a: 23 cm" | "objeto cerca" | "Cuidado" | "VL,<mm>"

## Cómo obtener el APK

### Opción A: sin instalar nada (GitHub Actions)
1. Crea un repositorio nuevo en github.com y sube todo el contenido de esta carpeta
   (incluida la carpeta oculta `.github`).
2. Ve a la pestaña **Actions** → **Build APK** → **Run workflow**.
3. Cuando termine (3 a 5 min), abre la ejecución y descarga `CarroBT-apk` al final de la página.
   Dentro está `app-debug.apk`.

### Opción B: Android Studio
1. Abre esta carpeta con **File > Open**.
2. Espera la sincronización de Gradle.
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar y usar
1. Pasa el APK al celular e instálalo (permite "instalar apps de orígenes desconocidos").
2. En **Ajustes > Bluetooth** empareja el HC-05 (PIN `1234` o `0000`).
3. Abre la app → **Conectar** → elige tu HC-05.
