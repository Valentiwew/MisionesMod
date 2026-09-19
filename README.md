# MisionesMod

Mod de misiones tacticas, incursiones por oleadas, suministros aereos y gestion de botin para Minecraft Fabric. Disenado especificamente para servidores tematicos de supervivencia, cooperacion y combate tactico.

- **Version de Minecraft:** 26.2 / 26.3
- **Cargador de Mods:** Fabric Loader (>= 0.19.5)
- **API Requerida:** Fabric API
- **Version de Java:** Java 25

---

## Caracteristicas Principales

### 1. Sistema de Incursiones en Estructuras y Edificios
Permite disenar y ejecutar incursiones tacticas completas con seguimiento en vivo:
- **Punto de Inicio y Reunion:** Los jugadores se congregan en la entrada. La incursion no inicia hasta reunir al equipo, iniciando un conteo regresivo sincronizado.
- **Rutas y Checkpoints Dinamicos:** El objetivo guia a los jugadores a traves de una sucesion de puntos de control. Al alcanzar un checkpoint (rango de 4 bloques), se emite un sonido de campana y la guia avanza automaticamente al siguiente objetivo sin repetirse.
- **Oleadas de Combate:** Cada oleada genera grupos de enemigos en los puntos de spawn definidos. Durante las oleadas activas, los cofres permanecen bloqueados contra apertura y rotura.
- **Fase de Botin:** Tras superar las oleadas, se activa un temporizador de saqueo donde los cofres se desbloquean.
- **Desaparicion de Cofres Vacios:** Al saquear y vaciar por completo un cofre, este desaparece instantaneamente emitiendo humo y sonido de explosion.
- **Deteccion y Anuncio de Saqueadores:** El mod registra a los jugadores que interactuan con cada cofre y anuncia publicamente quienes tomaron los suministros (ejemplo: "<Jugador1> y <Jugador2> tomaron cosas del cofre #1").
- **Fase de Escape y Evacuacion:** Se activan refuerzos hostiles y los supervivientes deben descender o alcanzar la zona de extraccion/escape para recibir la recompensa. Si todos los participantes caen, la incursion fracasa.

### 2. Tipos de Misiones
- **Incursion (Edificio / Estructura):** Progresion por oleadas, recorrido de checkpoints, saqueo de cofres con botin personalizado y huida a la salida.
- **Obtencion de Item:** Recoleccion de cantidades especificas de materiales o recursos para su entrega y validacion automatica.
- **Crafteo de Item:** Fabricacion obligatoria en mesa de trabajo o cuadricula de inventario. Utiliza la estadistica oficial de fabricacion del jugador (`Stats.ITEM_CRAFTED`), evitando que se complete la mision simplemente recogiendo items arrojados al suelo.

### 3. Suministros Aereos (Drops)
- Cajas de suministros que descienden desde el cielo con bengalas de humo y senalizadores.
- Notificacion en pantalla y marcador con flecha direccional hacia la caja.
- Al vaciar el drop, este se consume y anuncia al jugador que obtuvo el contenido.
- Tiers preconfigurados: Comun, Raro, Epico, Legendario y Personalizado.

### 4. Selector de Mobs y Editor de Botin Integrado
- **Selector de Mobs:** Interfaz con buscador en tiempo real compatible con monstruos vanilla y de cualquier mod instalado en el servidor.
- **Editor de Botin:** Permite configurar visualmente el contenido exacto de los drops y cofres de incursion desde el inventario del juego, sin requerir edicion manual de archivos JSON.

### 5. Interfaz Visual (HUD) y Brujula Direccional
- **Brujula Dinamica:** Flechas direccionales contextuales (arriba, abajo, izquierda, derecha) y distancia en metros hacia el objetivo actual (punto de inicio, checkpoints, salida o suministros aereos).
- **Iconos de Items:** En misiones de crafteo y obtencion, el HUD proyecta el icono del item requerido junto al progreso numerico.
- **Menu de Ajustes de HUD:** Accesible para activar/desactivar el widget en pantalla y seleccionar la esquina deseada (Superior Izquierda, Superior Derecha, Inferior Izquierda, Inferior Derecha).

---

## Controles y Atajos de Teclado

### Jugadores
- **M:** Abre la interfaz principal de misiones activas y disponibles.

### Administradores (Modo "En el Mundo")
Al presionar el boton "En el Mundo" en la creacion o edicion de una incursion, el administrador ingresa al modo de configuracion rapida:
- **C (Checkpoint):** Anade un punto de control secuencial en la posicion actual.
- **R (Inicio):** Fija el punto de inicio y reunion de la incursion.
- **X (Escape):** Establece la zona de extraccion y escape final.
- **G (Mob Spawn):** Registra un punto de aparicion de oleadas.
- **B (Loot):** Registra un cofre apuntado o anade una posicion de cofre.
- **V (Mobs):** Abre el selector de mobs vanilla y mods para elegir que enemigos generara la incursion.
- **K (Limpiar):** Vacia todos los puntos y configuraciones registradas en la sesion actual.
- **M (Listo):** Guarda los cambios del mapa y regresa a la pantalla de mision.

---

## Comandos

- `/drop <tier>`: Invoca un suministro aereo del tier indicado (comun, raro, epico, legendario, personalizado).
- `/misiones`: Abre el panel general de misiones del mod.

---

## Requisitos e Instalacion

1. **Minecraft:** Version 26.2 o 26.3.
2. **Fabric Loader:** Version 0.19.5 o superior.
3. **Java:** Java Runtime Environment 25.
4. **Fabric API:** Instalar la version correspondiente a 26.2 / 26.3 en la carpeta `mods`.
5. **Mod:** Colocar el archivo `misionesmod-26.2-1.0.0.jar` (o la version para 26.3) dentro de la carpeta `mods` en cliente y servidor.

---

## Compilacion

Para compilar el proyecto manualmente con Gradle:

```bash
# Compilar para la version base (26.2)
./gradlew jar

# Compilar para la version 26.3
./gradlew jar263
```

Los archivos `.jar` generados se ubican en la carpeta `build/libs/`.
