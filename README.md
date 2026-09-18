# MisionesMod

Mod de misiones tacticas, incursiones en edificios, suministros aereos y gestion de botin para Minecraft Fabric. Disenado especificamente para series y servidores tematicos de supervivencia, apocalipsis zombie y cooperacion.

- **Version de Minecraft:** 26.2 / 26.3
- **Cargador de Mods:** Fabric Loader (>= 0.19.5)
- **API Requerida:** Fabric API
- **Version de Java:** Java 25

---

## Caracteristicas Principales

### 1. Sistema de Incursiones en Edificios
Permite disenar y ejecutar incursiones tacticas dentro de estructuras cerradas o rascacielos con progresion vertical:
- **Oleadas Progresivas:** El combate transcurre por oleadas sucesivas a medida que los jugadores avanzan piso por piso hacia la cima.
- **Asalto a la Azotea:** La oleada final se combate en la azotea o cima del edificio bajo el modo Climax en Azotea.
- **Fase de Escape:** Al despejar la azotea, los supervivientes deben descender rapidamente hasta el punto de entrada para asegurar el botin y completar la mision.
- **Reinicio por Eliminacion:** Si todos los jugadores del grupo caen en combate, la mision se reinicia. Si al menos uno sobrevive y llega a la entrada, el equipo triunfa.
- **Proteccion Antirreinicios:** Incorpora tiempo de enfriamiento y comprobacion de zona para impedir reinicios involuntarios.

### 2. Registro de Cofres de Botin en Estructuras
- Los administradores pueden marcar cofres preexistentes dentro de un edificio con solo apuntarles la mira y pulsar una tecla.
- Cada cofre registrado recibe un identificador visual y se rellena dinamicamente con el botin configurado al iniciar la incursion.

### 3. Tipos de Misiones Disponibles
- **Incursion (Edificio):** Combate por oleadas, exploracion vertical, saqueo de cofres y huida a la zona de extraccion.
- **Obtencion de Item:** Recoleccion de cantidades especificas de materiales o recursos para su entrega automatica.
- **Crafteo de Item:** Fabricacion guiada con soporte para recetas y cantidades requeridas.

### 4. Suministros Aereos (Drops)
- Generacion de cajas de suministros que descienden desde el cielo acompanadas de humo y bengalas de senalizacion.
- Tiers de botin preconfigurados: Comun, Raro, Epico, Legendario y Personalizado.
- Comandos dedicados para invocar suministros manuales o programados (`/drop`).

### 5. Editor de Drop y Botin Integrado
- Menu visual accesible desde el modo creativo para disenar tablas de botin sin tocar archivos JSON ni reiniciar el servidor.
- Permite arrastrar cualquier item del catalogo (incluidos items de otros mods) y definir cantidades individuales.

### 6. HUD Tactico y Notificaciones
- **Notificaciones Apiladas:** Mensajes contextuales sobre el hotbar que se despliegan en tiempo real sin retrasos, apilandose verticalmente de forma ordenada si coinciden varios eventos.
- **Marcadores 3D (Waypoints):** Guia visual en pantalla que senala distancia en metros y ubicacion exacta de entradas, azoteas, puntos de aparicion de enemigos y cajas de suministros.

---

## Controles y Teclas

### Jugadores
- **M:** Abre la interfaz principal de misiones (consulta de estado, seguimiento y misiones activas).

### Administradores (Modo Construccion / Marcado en el Mundo)
Dentro de la pantalla de creacion de mision, al pulsar "Marcar Puntos en el Mundo", se desbloquean los siguientes controles rapidos:
- **G:** Establece el punto de Entrada y Escape (puerta de acceso a nivel del suelo).
- **H:** Establece la Azotea o Cima del edificio.
- **J:** Anade un punto de aparicion de enemigos (spawn) en la posicion actual del administrador.
- **C:** Apuntando a un cofre, lo registra o desregistra como cofre de botin de incursion.
- **K:** Limpia todos los puntos de aparicion registrados.
- **M:** Finaliza la sesion de marcado y regresa a la pantalla de edicion con todos los datos guardados.

---

## Comandos

- `/drop <tier>`: Invoca un suministro aereo del tier indicado (comun, raro, epico, legendario, personalizado).
- `/misiones`: Acceso directo por comando al panel de gestion.

---

## Requisitos e Instalacion
 
1. **Minecraft:** Version 26.2 o 26.3.
2. **Fabric Loader:** Version 0.19.5 o superior.
3. **Java:** Java Runtime Environment 25.
4. **Fabric API:** Descargar e instalar la version de Fabric API para Minecraft 26.2/26.3 en la carpeta `mods`.
5. **Mod:** Colocar el archivo `misionesmod-26.2-1.0.0.jar` (o `misionesmod-26.3-1.0.0.jar` segun la version del juego) dentro de la carpeta `mods` tanto en el cliente como en el servidor.

---

## Compilacion desde Codigo Fuente

Para compilar el proyecto manualmente con Gradle:

```bash
./gradlew build
```

El archivo `.jar` resultante se encontrara en la ruta:
`build/libs/misionesmod-<version>.jar`
