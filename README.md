# Zetaria Launcher

Launcher Android con estética de tecnología alienígena / Linux ultra personalizado.
Pensado para el **Tecno Spark 10c (KL5, Unisoc T606, Android 13)**; funciona en Android 8.0+ (API 26).

## Compilar en GitHub (sin instalar nada)

1. Crea un repositorio en GitHub y sube **el contenido** de esta carpeta (no el ZIP).
2. Ve a la pestaña **Actions** y abre el workflow **Build Zetaria Launcher** (se ejecuta solo con cada push; también puedes lanzarlo con *Run workflow*).
3. Al terminar, entra en la ejecución y descarga el artefacto **ZetariaLauncher-APK**.
   - `app-release.apk` (recomendado, optimizado)
   - `app-debug.apk` (alternativa)
4. Opcional: crea un tag `v1.0.0` y el APK se publica en **Releases**.

## Instalar y activar

1. Pasa el APK al teléfono e instálalo (permite "instalar apps desconocidas").
2. Pulsa el botón **Inicio**. Android preguntará qué launcher usar: elige **Zetaria Launcher** → *Siempre*.
   - Si no pregunta: Ajustes → Apps → Apps predeterminadas → App de inicio.
3. En HiOS, si el launcher se reinicia solo: Ajustes → Batería / Gestor del teléfono → permitir ejecución en segundo plano para Zetaria Launcher.

## Uso

| Gesto | Acción |
|---|---|
| Deslizar arriba | Abrir el **Núcleo** de apps |
| Deslizar abajo | Panel de notificaciones |
| Pulsación larga (inicio) | Menú del núcleo: color de energía, animaciones, reindexar |
| Pulsación larga (app) | Fijar en dock / info / desinstalar |
| Atrás, Inicio o deslizar el asa | Cerrar el Núcleo |

### Terminal integrada
En el buscador del Núcleo escribe `>` seguido de un comando:

`help` · `sysinfo` · `ls` · `launch <app>` · `theme zeta|violeta|magenta|ambar|hielo` · `anim on|off` · `wifi` · `bt` · `settings` · `dock [reset]` · `reload` · `clear` · `exit`

Sin `>` el buscador filtra apps; **Enter** abre la primera coincidencia.

## Estructura

```
app/src/main/java/com/zetaria/launcher/
  MainActivity.kt          inicio, dock, cajón, terminal, gestos
  AlienBackgroundView.kt   fondo animado (nebulosas, estrellas, hexágonos, anillos)
  AppAdapter / AppInfo / AppRepository / Ui
.github/workflows/build.yml  compilación en GitHub Actions (JDK 17 + Gradle 8.7)
```

## Compilar localmente (opcional)

Con Android Studio (Koala o superior) abre la carpeta y pulsa *Run*. Por línea de comandos: `gradle assembleDebug` (Gradle 8.7+, JDK 17).
