# Fase 8: widget, respaldo y mejoras generales

Estado: **implementada; validación automatizada aprobada y aceptación manual pendiente**. Las fases 7 y 8 se publican juntas, sustituyendo el commit local anterior `c0b7fc9`. Véase [publicación conjunta](PUBLICATION78.md).

## Alcance implementado

- **Habits:** una cuadrícula compartida de siete columnas para encabezados y círculos, con los mismos márgenes de las tarjetas. Se mantienen el tamaño de los círculos, los colores y las pestañas Active/Archived.
- **Focus ↔ Habits:** selección de un hábito activo o sesión independiente, duración editable y meta diaria como referencia. Tiempo efectivo por reloj monotónico, pausas, recuperación, contribuciones por sesión y día y escrituras atómicas. El fin de una sesión corta no completa una meta diaria mayor. Los contadores y listas requieren registrar sus unidades en Habits; no se inventan cantidades a partir de minutos.
- **Home / Today:** hasta tres hábitos ordenados por incumplimiento proporcional acumulado. Los días completos reinician la acumulación; los días no programados y periodos archivados se excluyen. Hoy se identifica como provisional. Al tocar una fila se abre el hábito correspondiente. Formato regional con hasta dos decimales.
- **Desbloqueo:** control optativo, intervalos de 30/60/120/180 minutos, conteo desde SCREEN_OFF, conservación del conteo durante consultas a la pantalla bloqueada y una sola oportunidad por bloqueo. Rotación persistente Home → Habits → Tasks → Money → Focus → Journal, confirmada por la actividad visible en el destino. Las aperturas manuales y las notificaciones no avanzan la rotación.
- **Widget Money:** vista compacta y ampliada, monedas separadas, saldos por espacio, ingresos/gastos del mes, pagos próximos y accesos para ingresos/gastos. Selección de moneda y espacio o cuenta desde Ajustes. Privacidad, idioma, tema y refresco después de movimientos/restauración.
- **Respaldo integral:** imagen SQLite consistente, tablas completas, adjuntos y preferencias. Archivo ZIP local con manifiesto de tamaños y SHA-256. Se excluyen copias recursivas, archivos temporales y el estado transitorio del monitor de desbloqueo.
- **Restauración:** extracción restringida, controles de tamaño/rutas/integridad, validación y migración en una base aislada y revisión explícita antes de confirmar. La app se cierra y el reemplazo se aplica al abrirla otra vez, antes de Room, DataStore y los proveedores. Diario de recuperación y reversión ante fallos; se conservan las copias internas anteriores. Se reubican los adjuntos, se pausan sesiones restauradas y se regeneran avisos y widgets.
- **Integración:** recursos compartidos EN/ES/ZH, idioma aplicado también en Android 26–32, estados de carga/error y corrección de la carga inicial del bloqueo biométrico. Si no hay autenticación disponible no se concede acceso automáticamente; se ofrece el mecanismo de seguridad del dispositivo compatible.

El widget reutiliza los colores de `TrackermasterTheme`. La vista mediana prioriza los saldos y accesos rápidos cuando hay dos monedas; la ampliada añade totales mensuales y hasta cuatro espacios, indicando si existen más. Los tres idiomas se incluyen en el paquete y se deshabilita la separación de idiomas del App Bundle para permitir cambios sin conexión, según la [configuración oficial de Android](https://developer.android.com/guide/app-bundle/configure-base).

## Datos y migración

La base actual era versión 12: se añade **12 → 13**, manteniendo también la ruta completa **4 → 13** y todas las migraciones previas de Money. No se utiliza restauración destructiva.

El esquema añade historial de programación/metas de hábitos, fecha de creación o inicio de seguimiento conocido, metas selladas en registros y tiempo/contribuciones de Focus. Las modificaciones posteriores conservan las versiones previas. Se usa la zona horaria del dispositivo y el día local existente en Android.

Los hábitos antiguos no guardaban su fecha de creación ni todas sus metas históricas. Esa información no se puede reconstruir con precisión: la migración utiliza el primer registro conocido o el día de migración y la UI identifica ese inicio de seguimiento. Se conservan las metas antiguas que sí estaban guardadas. Para sesiones antiguas sin desglose diario se conserva el criterio histórico de su fecha registrada; las sesiones nuevas se distribuyen al cruzar medianoche.

La recuperación de Focus dentro del mismo arranque conserva el temporizador y excluye pausas. Tras reiniciar el teléfono se conserva el tiempo ya persistido y la sesión queda pausada; no se atribuye como concentración el tiempo con el equipo apagado. El servicio guarda puntos de control cada cinco segundos.

## Compatibilidad de apertura automática

Android restringe las actividades iniciadas desde segundo plano. En API 26–28 no se solicita permiso de superposición para este mecanismo. En API 29+ se utiliza la excepción asociada a SYSTEM_ALERT_WINDOW, concedida explícitamente desde Ajustes. El monitor no dibuja una pantalla de bloqueo, no despierta el dispositivo y no usa accesibilidad ni intents de pantalla completa.

El servicio en primer plano declara el tipo specialUse y su finalidad. Para una distribución en Google Play corresponde declarar y someter ese uso a revisión. El permiso de notificaciones habilita la alternativa manual y sus controles visibles; no sustituye el permiso de apertura. La excepción de batería es opcional y su pantalla de configuración está disponible desde Ajustes.

Los fabricantes pueden detener el monitor o bloquear el inicio aunque se conceda el permiso. Los estados muestran la configuración real y ofrecen reactivar el monitoreo. Cuando un intento es rechazado se ofrece una notificación silenciosa, si está permitida. Esa alternativa exige un toque y no se presenta como apertura automática. Un cierre forzado del sistema o restricciones del fabricante requieren volver a abrir la app. La configuración y la rotación sobreviven al reinicio; el siguiente periodo empieza cuando se apaga la pantalla de nuevo.

Referencias oficiales: [restricciones de actividades](https://developer.android.com/guide/components/activities/secure-bal), [servicios desde segundo plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [tipos de servicio](https://developer.android.com/develop/background-work/services/fgs/service-types), [autenticación compatible](https://developer.android.com/identity/sign-in/biometric-auth).

## Validación y entrega

**395 pruebas JVM/Robolectric aprobadas, sin fallos ni omisiones.** Los controles de UI usan Robolectric API 28 y renderizado nativo de gráficos; no se utilizó un emulador Android. La compilación debug normal y lint finalizaron correctamente.

| Módulo | Pruebas |
|---|---:|
| `core/domain` | 107 |
| `core/database` | 24 |
| `core/data` | 131 |
| `core/ui` | 3 |
| `feature/expense` | 114 |
| `feature/habits` | 3 |
| `app` | 13 |

Se generaron 14 capturas: ambas pestañas del calendario a 320/393/600 dp y componentes Home/Focus, incluyendo EN/ES/ZH y fuente al 150 %. Se revisaron visualmente las capturas de calendario y Focus en oscuro/OLED. Los eventos de la UI y la persistencia se prueban por separado.

| Lint | Errores | Advertencias registradas |
|---|---:|---:|
| `app` | 0 | 56 |
| `core/data` | 0 | 13 |
| `core/ui` | 0 | 1 |
| `feature/expense` | 0 | 1 |
| `core/widgets` | 0 | 1 |
| `feature/settings` | 0 | 1 |
| `feature/backup` | 0 | 1 |
| `feature/focus` | 0 | 1 |

Las advertencias de compilación conservadas corresponden a los cuatro iconos obsoletos preexistentes de `DefumblrLockScreenActivity`. El informe contiene los identificadores de lint; en datos y Money se compararon con la fase 7, y en los otros módulos revisados solo permanece el aviso anterior de compileSdk.

**Android real pendiente:** la instrumentación de fase 8 no se pudo completar. Estado registrado: `installation_rejected`. Los resultados JVM/Robolectric no sustituyen estas pruebas. Véase el detalle de conexión/instalación en [resultado nativo](phase8-native-results.json).

**Entrega:** [APK debug de fase 8](artifacts/trackermaster-money-phase8-debug.apk), identificador `com.trackermaster.app`, esquema Room 13 y 33 tablas. Firma v2 comprobada con el mismo certificado que fase 7. El APK normal no se instaló sobre Trackermaster durante las pruebas.

SHA-256: `bbd8aaa000b2b67c1619fa903ba8c04a8442362c4ecc8b2ee0c71c4f2155d494`.

**Archivos y preservación:** 42 fuentes existentes modificadas y 45 nuevas. Se verificaron 309 fuentes contra la compilación y 222 archivos previos ajenos al alcance permanecen idénticos. Se conservan las integraciones locales de sincronización y los cambios independientes de tareas, hábitos, ajustes y bloqueo.

Detalle completo: [archivos](phase8-files.json), [pruebas y capturas](phase8-test-results.json), [preservación](phase8-preservation.json), [firma](phase8-signing-comparison.json) y [compilación](phase8-close-validation.log). La publicación conjunta de fases 7 y 8 se documenta en [PUBLICATION78.md](PUBLICATION78.md); los resultados anteriores describen la validación histórica del espacio de trabajo integrado.


Las pruebas JVM/Robolectric y de reloj controlado están separadas de las comprobaciones pendientes en Android real. La apertura tras 30 minutos reales, el reinicio físico, los permisos y restricciones del fabricante, TalkBack y la colocación/redimensión del widget necesitan comprobaciones específicas; una simulación de tiempo no demuestra esos comportamientos del sistema.

### Comprobación manual pendiente

| Flujo | Comprobación en Android |
|---|---|
| Apertura | Activar el intervalo de 30 minutos, apagar la pantalla, consultar la hora sin desbloquear y desbloquear después del intervalo. Confirmar una sola apertura y la sección siguiente únicamente después de verla. Un desbloqueo anterior al intervalo no debe abrir la app. |
| Uso normal | Mantener el teléfono desbloqueado durante más de un intervalo: no debe interrumpir. Repetir con los otros tres intervalos. |
| Permisos y reinicio | Probar notificaciones y superposición concedidas, denegadas y revocadas. Reiniciar y confirmar que se mantienen intervalo/rotación y empieza un nuevo periodo al apagar la pantalla. La notificación alternativa requiere un toque. |
| Focus | Hábito de 30 minutos: realizar 10 minutos, pausar, cambiar de sección y recuperar. Después realizar 20 minutos. Revisar parcial/completo, Home y que reabrir no duplique tiempo. Incluir una sesión sin hábito. |
| Top 3 | Revisar hábitos diarios, días específicos, contadores y nunca completados. Corregir un registro, completar hoy, archivar y cruzar medianoche; comprobar el orden y abrir el detalle desde Today. |
| Widget | Colocar y redimensionar las tres vistas; probar COP/USD, espacio/cuenta, privacidad, temas e idioma. Abrir ingreso/gasto sin reemplazar un borrador existente. |
| Restauración | Usar primero datos de prueba: exportar, modificar datos, revisar la copia y confirmar; cerrar/reabrir. Verificar comprobantes, preferencias, Money y Focus pausado. La actualización sobre la instalación real sigue pendiente. |
| Accesibilidad | TalkBack, tamaños de fuente del dispositivo y controles de notificaciones/biometría en Android 26–36 y en el fabricante del teléfono. |

Las capturas de Home corresponden al componente nuevo de Today; las pruebas de Focus verifican controles y eventos de UI, junto con pruebas independientes del repositorio. No constituyen una prueba física completa de navegación y servicios.
