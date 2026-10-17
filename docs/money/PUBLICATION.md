# Publicación de Money — 17 de octubre de 2026

Se publican las fases 5 y 6. **El proyecto todavía está en desarrollo: faltan las fases 7 y 8 y las verificaciones en teléfono.** La validación integrada de fase 6 aprobó 256 pruebas, compilación debug y lint de Money/datos sin errores; consulte PHASE6.md para sus límites.

El commit conserva las migraciones y entidades SQLite 8→9→10 como requisito de compatibilidad de la base 11 ya utilizada localmente. Incluye únicamente sus definiciones de almacenamiento y pruebas; no publica la integración de red, credenciales, aplicaciones web ni pantallas o lógica independientes de hábitos, tareas, ajustes y bloqueo.

Tres archivos compartidos se preparan parcialmente en el índice: el manifest solo incorpora el inicializador WorkManager; core/data solo incorpora dependencias WorkManager/Hilt; DataModule conserva la cadena de migraciones y excluye los callbacks de sincronización/restauración independientes. Los archivos de trabajo del usuario permanecen intactos. Las columnas compartidas del esquema 10 se conservan para evitar una actualización incompatible.

Los APK, registros, cachés y directorios de validación no forman parte del commit. Los resultados históricos incluyen pruebas de las modificaciones externas presentes en aquella compilación; no deben interpretarse como una validación nueva del checkout publicado.
