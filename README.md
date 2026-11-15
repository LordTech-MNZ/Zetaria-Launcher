# Zetaria Launcher

Launcher Android con estética de tecnología alienígena / Linux ultra personalizado.
Pensado para el **Tecno Spark 10c (KL5, Unisoc T606, Android 13)**; funciona en Android 8.0+ (API 26).

## Compilar en GitHub (sin instalar nada)

1. Crea un repositorio en GitHub y sube **el contenido** de esta carpeta (no el ZIP).
2. Ve a la pestaña **Actions** y abre el workflow **Build Zetaria Launcher** (corre solo con cada push; también con *Run workflow*).
3. Al terminar, descarga el artefacto **ZetariaLauncher-APK** (`app-release.apk` recomendado, `app-debug.apk` alternativa).
4. Opcional: crea un tag `v1.1.0` y el APK se publica en **Releases**.

> Cada compilación en GitHub se firma con una clave distinta. Si ya tienes Zetaria instalado,
> **desinstálalo antes** de instalar la versión nueva (si no, Android dirá "App no instalada").

## Instalar y activar

1. Instala el APK (permite "instalar apps desconocidas").
2. Pulsa **Inicio** y elige **Zetaria Launcher** → *Siempre* (o Ajustes → Apps → Apps predeterminadas → App de inicio).
3. En HiOS, si el launcher se reinicia solo: permite ejecución en segundo plano en Batería / Gestor del teléfono.

## Uso

| Gesto | Acción |
|---|---|
| Deslizar arriba / botón `NUCLEO ^` | Abrir el **Núcleo** de apps |
| Deslizar a la izquierda / botón `< RECIENTES` | Abrir el panel de **apps recientes** |
| Deslizar abajo | Panel de notificaciones |
| Pulsación larga (inicio) | Menú del núcleo: fondo, color, reloj, animaciones |
| Pulsación larga (app) | Fijar en dock / info / desinstalar |
| Atrás, Inicio o deslizar el asa | Cerrar paneles |

### Reloj y calendario
- Hora en **12 h con AM/PM** (cámbialo a 24 h en el menú o con `>clock 24`).
- Fecha en **calendario Holoceno (Era Humana, EC)**: año gregoriano + 10000. En 2026 es **12026 EC**; el 1 de enero de 2027 pasa a **12027 EC**.

### Fondos de pantalla personalizados
Pulsación larga en el inicio → **Fondo de pantalla…**
- Elegir imagen de la galería (se recorta a tu pantalla y se guarda dentro de la app).
- Efectos sobre la imagen (estrellas, anillos, escaneo): ON/OFF.
- Oscurecer imagen: Ninguno / Suave / Medio / Fuerte (para que el texto se lea).
- Quitar imagen: vuelve al fondo alienígena animado.

### Apps recientes
Desde Android 10, el botón cuadrado de navegación lo controla el sistema y un launcher normal no puede reemplazarlo.
Por eso Zetaria incluye su **propio panel de recientes**:
1. Ábrelo con deslizar a la izquierda, `< RECIENTES` o `>recent`.
2. La primera vez pulsa **CONCEDER ACCESO DE USO** y activa Zetaria Launcher (es el permiso que Android exige para saber qué apps usaste).
3. **LIMPIAR TODO** vacía la lista y cierra los procesos en segundo plano de esas apps.

### Terminal integrada
En el buscador del Núcleo escribe `>` seguido de un comando:

`help` · `sysinfo` · `date` · `ls` · `launch <app>` · `recent` · `theme zeta|violeta|magenta|ambar|hielo` · `clock 12|24` · `wallpaper [clear]` · `anim on|off` · `wifi` · `bt` · `settings` · `dock [reset]` · `reload` · `clear` · `exit`

Sin `>` el buscador filtra apps; **Enter** abre la primera coincidencia.

## Estructura

```
app/src/main/java/com/zetaria/launcher/
  MainActivity.kt          inicio, dock, cajón, recientes, fondos, terminal, gestos
  AlienBackgroundView.kt   fondo animado + imagen personalizada
  AppAdapter / RecentAdapter / AppInfo / AppRepository / Ui
.github/workflows/build.yml  compilación en GitHub Actions (JDK 17 + Gradle 8.7)
```
