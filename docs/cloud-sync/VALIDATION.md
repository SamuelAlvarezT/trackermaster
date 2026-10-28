# Verificación de sincronización y dashboard

## Comprobaciones aprobadas

- Room: 4 pruebas de migración, conservación de registros, claves y rollback atómico.
- Cola Android: 5 pruebas, incluidas edición durante envío, eliminaciones, identificación del propietario, precisión de enteros y reapertura de la base de datos.
- Web: 12 pruebas actuales, incluidas navegación, detalle del diario, saneamiento HTML y precisión monetaria.
- Reglas Firestore: 12 pruebas actuales en el emulador `demo-trackmaster`, incluidas restricciones del escritor Android, bloqueo de otros usuarios, eliminación versionada y aprobación del asistente.
- Build web: TypeScript y Vite aprobados. JavaScript principal 234,62 KB (76,49 KB gzip); CSS 9,76 KB (2,91 KB gzip).
- CDN Google: comprobados HTTP 200 para Firebase App, Auth y Firestore 12.19.0.
- Android: `:app:assembleDebug` aprobado, con código de salida 0. El manifiesto final contiene `INTERNET` y `ACCESS_NETWORK_STATE`. Se desactivaron las cachés incrementales de Kotlin/KSP para completar la verificación tras un error de caché, sin borrar datos locales.

## APK de prueba generado

- Archivo: `app/build/outputs/apk/debug/app-debug.apk`.
- Generado: 9 de octubre de 2026, 11:07 (America/Bogota).
- Tamaño: 71.298.156 bytes.
- SHA-256: `0651037284AF3066D09F7F2E3A93019848C80448D4D414C1B788B15B06FBFAE8`.
- Firebase sigue sin configurar: para activar la sincronización, completa SETUP.md y recompila manteniendo la firma de tu instalación anterior.

## Pendientes que requieren configuración del usuario

- Crear Firebase Spark y configurar Google, UID propietario y clave privada Android siguiendo SETUP.md.
- Publicar reglas, índices y Hosting en el proyecto real.
- Probar autenticación Google real y envío desde el teléfono; consultar después con el teléfono apagado.
- Revisión visual manual: el navegador integrado no respondió. Las pruebas de interfaz se ejecutaron con JSDOM.

La sincronización no se activa ni sube registros con la configuración Firebase vacía. No se creó un proyecto externo ni se habilitó facturación.
