# Publicación conjunta de fases 7 y 8 — 24 de octubre de 2026

Se integran ambas fases en **un único commit**, sustituyendo el commit local de fase 7 `c0b7fc9`, que no se había publicado. La publicación parte de `5636684` y no requiere forzar el historial remoto.

- Fase 7: inversiones y valoraciones, reportes y exportación CSV/PDF.
- Fase 8: widget de Money, respaldo/restauración integral y migración 12→13; alineación de Habits, Focus vinculado con progreso proporcional, Top 3 en Today y apertura configurable al desbloquear.

## Comprobación del contenido publicado

Compilación debug y **385 pruebas JVM/Robolectric aprobadas**, sin fallos ni omisiones, sobre una copia aislada del contenido exacto preparado para Git. [Resultado por módulo](phase78-publication-results.json).

Los informes de fase 7 y fase 8 son evidencia histórica del espacio de trabajo integrado. La fase 8 había aprobado 395 pruebas, incluyendo pruebas del código local de sincronización que queda fuera de esta publicación; esa cifra no debe confundirse con la validación del commit preparado aquí. Los 14 renderizados, APK y registros completos permanecen locales.

Los archivos compartidos se prepararon parcialmente en el índice para excluir sincronización, credenciales y cambios independientes de tareas, hábitos, ajustes y bloqueo. No se modificaron los archivos fuente del usuario. Se mantienen los esquemas y migraciones ya publicados que contienen columnas compartidas, para conservar compatibilidad de datos. El arranque de restauración conserva el marcador que podrá consumir la integración local de sincronización.

## Limitaciones pendientes

La implementación está integrada; la aceptación manual sigue pendiente. Android rechazó el paquete aislado de fase 8 con `INSTALL_FAILED_VERIFICATION_FAILURE`; no se actualizó la app real. Faltan comprobar apertura tras 30 minutos reales, reinicio y permisos del fabricante, widget en el launcher, TalkBack y actualización/restauración con datos reales. No se afirma que las pruebas JVM sustituyan estas verificaciones.

No se publican APK, cachés, logs, aplicaciones web, credenciales ni la integración de red independiente.
