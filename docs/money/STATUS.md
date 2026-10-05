# Money: mejora en curso

Este commit guarda un avance parcial. **El rediseño completo de Money todavía no está terminado.**

Estado al 5 de octubre de 2026:

- Fase 1: auditoría, diseño y prototipo realizados.
- Fase 2: base monetaria, espacios, cuentas, bolsillos y migraciones implementados.
- Fase 3: nueva interfaz, movimientos, transferencias, privacidad y navegación configurable implementados.
- Fase 4: importación de comprobantes, OCR local, revisión, borradores, duplicados y adjuntos implementados.
- Fase 5 pendiente: presupuestos completos, recurrentes y recordatorios.
- Fase 6 pendiente: deudas y metas de ahorro.
- Fase 7 pendiente: inversiones, ampliación de reportes y exportación CSV/PDF.
- Fase 8 pendiente: ampliación del widget y respaldo/restauración integral, incluidos comprobantes y preferencias.

La validación del último avance produjo un APK debug firmado y 137 pruebas aprobadas, sin errores de lint ni nuevas advertencias de código Money. Room conserva la cadena de migraciones 4→5→6→7.

Antes de distribuir queda verificar en un teléfono los comprobantes reales de Nequi, OCR/PDF, compartir y abrir adjuntos, biometría, TalkBack, teclado, actualización con datos existentes y el objetivo de registrar en menos de diez segundos. Hay un fallo previo de biometría en MainActivity documentado en PHASE4.md.

Este commit incluye la implementación de Money y sus adaptaciones de integración. Los cambios locales independientes de hábitos, ajustes y pantalla de bloqueo se conservan fuera del commit. El APK, las cachés y los registros de ejecución permanecen como archivos locales.

Detalles: [diseño y fases](DESIGN.md), [fase 2](PHASE2.md), [fase 3](PHASE3.md) y [fase 4](PHASE4.md).
