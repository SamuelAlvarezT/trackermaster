# Money: fase 8 integrada, aceptación manual pendiente

Estado de publicación al 24 de octubre de 2026: las ocho fases tienen implementación integrada. **El cierre de calidad y la aceptación en Android real todavía requieren las comprobaciones manuales documentadas.**

- Fase 1: auditoría, diseño y prototipo.
- Fases 2–3: base monetaria, espacios, cuentas, movimientos, transferencias, privacidad y navegación configurable.
- Fase 4: comprobantes, OCR local, revisión, duplicados y adjuntos.
- Fase 5: presupuestos, recurrencias, compras, previsión y recordatorios. [Detalle](PHASE5.md).
- Fase 6: deudas, abonos y metas respaldadas por bolsillos. [Detalle](PHASE6.md).
- Fase 7: inversiones, valoraciones, reportes y CSV/PDF. [Detalle](PHASE7.md).
- Fase 8: widget y respaldo/restauración integral; calendario de Habits, apertura al desbloquear, Focus vinculado a hábitos y Top 3 proporcional en Today. [Detalle y límites](PHASE8.md).

La validación histórica del espacio de trabajo integrado produjo el [APK debug local de fase 8](artifacts/trackermaster-money-phase8-debug.apk): 395 pruebas JVM/Robolectric aprobadas, 14 capturas de fase 8, compilación debug y lint completados, esquema 13 y certificado de firma idéntico al de fase 7. Se conservan todas las migraciones, incluida la ruta 4→13; las pruebas usan datos sintéticos.

**Android real pendiente:** la instrumentación de fase 8 no se pudo completar. Estado registrado: `installation_rejected`. Los resultados JVM/Robolectric no sustituyen estas pruebas. Véase el detalle de conexión/instalación en [resultado nativo](phase8-native-results.json).

El contenido exacto de la publicación conjunta aprobó nuevamente la compilación debug y 385 pruebas JVM/Robolectric.

Antes de distribuir siguen pendientes la apertura después de 30 minutos reales y reinicio, permisos/restricciones del fabricante, el widget en el launcher, TalkBack, actualización sobre datos reales, comprobantes reales de Nequi y registro en menos de diez segundos. Las metas y fechas históricas no guardadas por versiones antiguas no se pueden reconstruir; la UI identifica el inicio de seguimiento conocido. El fallo previo de acceso automático sin biometría fue corregido en esta fase y tiene una prueba de regresión.

Las fases 7 y 8 se publican juntas en un único commit sobre `5636684`. El commit local anterior `c0b7fc9` queda sustituido. Se conservan los cambios locales independientes. Véase [publicación y validación del contenido exacto](PUBLICATION78.md). APK, cachés, capturas y registros completos permanecen locales.

Evidencia: [fase 8](PHASE8.md), [resultados](phase8-test-results.json), [archivos](phase8-files.json), [preservación](phase8-preservation.json). La exportación nativa de fase 7 en Android 14 es una comprobación previa distinta de las pruebas de fase 8.
