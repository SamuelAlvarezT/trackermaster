# Money: mejora en curso

**El rediseño completo de Money todavía no está terminado.** Estado al 15 de octubre de 2026:

- Fase 1: auditoría, diseño y prototipo realizados.
- Fase 2: base monetaria, espacios, cuentas, bolsillos y migraciones implementados.
- Fase 3: nueva interfaz, movimientos, transferencias, privacidad y navegación configurable implementados.
- Fase 4: importación de comprobantes, OCR local, revisión, borradores, duplicados y adjuntos implementados.
- Fase 5 implementada y validada: presupuestos, pagos/ingresos recurrentes, recordatorios de compra, previsión, avisos locales y contraste en los tres temas. Véase [fase 5](PHASE5.md).
- **Fase 6 implementada y validada:** deudas por pagar/cobrar, abonos, vinculación de comprobantes a abonos, correcciones del capital y metas respaldadas por bolsillos reales. COP/USD separados; capital de préstamos excluido del ingreso/gasto operativo. Véase [fase 6](PHASE6.md).
- Fase 7 pendiente: inversiones, ampliación de reportes y exportación CSV/PDF.
- Fase 8 pendiente: ampliación del widget y respaldo/restauración integral, incluidos comprobantes y preferencias.

La entrega actual incluye un [APK debug de fase 6](artifacts/trackermaster-money-phase6-debug.apk), 256 pruebas aprobadas, 22 capturas nativas nuevas y cero errores de lint en datos y Money. La firma v2 coincide con la entrega anterior; se verificaron la integridad del archivo y las huellas de las fuentes compiladas. No hay advertencias nuevas de código Money; permanecen los avisos anteriores de configuración/dependencias y los iconos del bloqueo.

Money conserva las migraciones 4→5→6→7→8 y añade 10→11. Los cambios independientes de sincronización y métricas incorporaron 8→9→10. La ruta completa 4→11 está probada con datos antiguos; no hay restauración destructiva. Se conservaron los cambios locales independientes de hábitos, ajustes, bloqueo, modelos compartidos y otras funciones.

Antes de distribuir queda verificar en un teléfono comprobantes reales de Nequi, OCR/PDF, compartir y abrir adjuntos, biometría, TalkBack, teclado, notificaciones con Doze/reinicio, actualización con datos existentes y el objetivo de registrar en menos de diez segundos. No hay dispositivo ni emulador conectado; las pruebas de interfaz con Robolectric no sustituyen esas comprobaciones. El fallo previo de biometría en MainActivity continúa documentado en PHASE4.md.

Las fases 5 y 6 permanecen como cambios locales después del commit y push anteriores de las fases 1–4 (c89f4fc). Esta fase no crea commit ni push. El APK, las cachés y los registros de ejecución siguen siendo archivos locales.

Detalles: [diseño y fases](DESIGN.md), [fase 2](PHASE2.md), [fase 3](PHASE3.md), [fase 4](PHASE4.md), [fase 5](PHASE5.md), [fase 6](PHASE6.md) y [resultados finales](phase6-test-results.json).
