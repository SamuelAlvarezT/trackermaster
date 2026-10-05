# Money — Fase 4: importar y revisar comprobantes

Estado: implementación y validación automática terminadas. APK debug firmado en `app/build/outputs/apk/debug/app-debug.apk`. Quedan las verificaciones en teléfono descritas abajo.

## Entrega

- Acceso «Importar comprobante» en el resumen de Money y destino propio en el menú Compartir de Android, identificado como Trackermaster · Money/Dinero/资金. Acepta un archivo o texto por operación. Se mantiene el destino anterior de compartir de la app.
- JPEG, PNG, WebP, texto y PDF de hasta cinco páginas; máximo 10 MB por archivo y 20 000 caracteres interpretados. Copia inmediata a almacenamiento interno, identificación por SHA-256 y rutas relativas `money/receipts/`. El adjunto permanece legible al desaparecer la URI temporal de otra app.
- Reconocimiento de texto en el dispositivo con el modelo latino incluido de ML Kit `com.google.mlkit:text-recognition:16.0.1`. No descarga el modelo al primer uso. La app no declara permiso INTERNET en el APK final. La interpretación usa reglas propias y no servicios externos.
- Sugerencias conservadoras de monto, COP/USD, fecha, referencia, origen/destino y tipo. «Enviaste»/«Pagaste» sugieren gasto; «Recibiste» sugiere ingreso. Un encabezado genérico de transferencia no decide la dirección. No se usan teléfonos, saldo disponible ni referencias como monto; datos ambiguos quedan por completar.
- Revisión editable con cuenta, bolsillo, espacio derivado del origen, categoría, fecha, nota y referencia. El usuario elige dónde contabilizar el dinero y confirma la operación. También puede registrar una transferencia entre cuentas propias, con monto recibido explícito para monedas diferentes.
- Los borradores se guardan en Room. Se pueden retomar o descartar desde la bandeja. SavedStateHandle conserva los cambios y el identificador durante la recreación de la pantalla. Una lectura fallida permite completar el registro manualmente; no crea movimientos por sí misma.
- Duplicados por archivo, referencia del mismo proveedor reconocido o coincidencia cuenta/monto/fecha/tipo. Se muestran los movimientos coincidentes y una confirmación específica para registrar una repetición legítima. La advertencia se vuelve a comprobar dentro de la transacción de guardado y se reinicia al cambiar datos financieros.
- Un comprobante detectado como pendiente/fallido no se puede confirmar. El OCR no verifica autenticidad bancaria. Importar, leer y editar un borrador no cambia el saldo; confirmar escribe el movimiento y su vínculo al comprobante juntos, una sola vez.
- Los movimientos importados conservan el adjunto al editar. Las transferencias lo conservan en ambas partes, también al editar/borrar/deshacer. El menú de movimientos permite abrir el original con una URI temporal y FileProvider limitado al directorio de comprobantes.
- Privacidad compartida con Money, inicio y widgets existentes. Oculta original, miniatura y texto extraído, y enmascara los campos de monto; se necesita mostrar los importes para abrir el adjunto. Interfaz con recursos EN/ES/ZH, tres temas existentes, objetivos táctiles de 48 dp, listas con claves y seis previews de Compose.
- El receptor espera la configuración guardada antes de mostrar datos. Si está habilitada la biometría, exige autenticación fuerte; cancelar/no disponer de autenticación no desbloquea los comprobantes. Ofrece reintento y cerrar.

## Arquitectura y datos

`ReceiptParser` y `ReceiptValidation` son reglas de dominio. `ReceiptFiles` copia y limita entradas; `MoneyReceiptRepository` mantiene borradores, vincula evidencias y comprueba duplicados dentro de Room. `ReceiptReader` ejecuta OCR fuera del hilo principal, reduce imágenes, respeta orientación EXIF y renderiza cada página PDF por separado. `ReceiptGateway` desacopla el procesamiento y `ReceiptViewModel` expone StateFlow con estado inmutable y eventos explícitos. `MoneyReceiptActivity` integra compartir/selector de documentos sin modificar MainActivity.

Room pasa de 6 a 7 añadiendo solamente `money_receipts`, con índices de huella y estado. Se conservan las migraciones 4→5 y 5→6, y los esquemas anteriores. La cadena 4→5→6→7 y la migración 6→7 se prueban con MigrationTestHelper. No se borran, convierten ni reclasifican movimientos existentes. Los adjuntos antiguos se mantienen intactos; el visor nuevo maneja las rutas internas nuevas.

El descarte elimina el borrador, no archivos que podrían pertenecer a un movimiento confirmado. No se hace limpieza automática de archivos sin referencia en esta fase. El vínculo usa los identificadores estables de solicitud/transferencia; los comprobantes de movimientos eliminados dejan de contar como duplicado activo y vuelven a contar si se deshace la eliminación.

## Validación

Resultado final: **137 pruebas aprobadas, cero fallos/errores/omisiones** (52 pruebas nuevas respecto a la fase 3). Dominio: 30; base de datos: 10; datos: 41; Money: 50; app: 6. Las pruebas de migración cubren 6→7 y la cadena 4→5→6→7 con datos existentes.

`phase4-final-exif-validation.log`: BUILD SUCCESSFUL; 467 tareas, 42 ejecutadas y 425 al día. Se reinició exclusivamente el daemon de Gradle después de diagnosticar presión de memoria, conservando los resultados previos; el intento anterior está en `phase4-final-exif-before-restart.log`. La configuración de memoria se aplicó a esa invocación, sin cambiar `gradle.properties`.

Lint de datos y Money: cero errores; se conserva el aviso previo `GradleDependency`. La compilación previa al reinicio conserva cuatro avisos anteriores por iconos obsoletos en la pantalla de bloqueo, cuyo código no se modificó. El aviso de empaquetado de tres bibliotecas nativas sin quitar símbolos incluye la biblioteca OCR; no impide compilar ni firmar el APK.

APK verificado con apksigner (firma v2), CRC del ZIP correcto, receptor/FileProvider configurados, código de autenticación y AndroidX ExifInterface presentes, modelo OCR incluido y permiso INTERNET ausente también en el manifest empaquetado inspeccionado con aapt2. Se comprobaron 55 recursos en cada idioma, 15 capturas nativas de comprobantes y 206 archivos de la referencia inicial, sin cambios fuera del alcance. Hash del APK y resultados detallados en `phase4-test-results.json`.

- Pruebas del intérprete: monedas y separadores, dirección incierta, referencias, fechas inválidas/conflictivas, importes ambiguos, estados pendientes/fallidos y desbordamiento.
- Pruebas de Room: migraciones vacías/pobladas, saldo invariable en borradores, guardado idempotente, confirmación y detección de duplicados, cambio concurrente de coincidencias, rechazo con rollback, ingresos y transferencias COP/USD, adjuntos después de edición/eliminación/deshacer.
- Pruebas de archivos: firma real, URI de contenido, copia persistente tras borrar el origen, tamaño limitado, huella estable, rutas confinadas y entradas inválidas.
- Pruebas de ViewModel e intents: campos pendientes, guardado repetido, restauración, reintento, privacidad, estado bancario, transferencia y extras externos ignorados.
- Pruebas Compose con Robolectric y Canvas nativo: revisar/confirmar, duplicados, pendiente, privacidad y bloqueo del contenido hasta recibir configuración/autenticación. Capturas de revisión en claro/oscuro/OLED × EN/ES/ZH y fuentes al 150 % en los tres idiomas, además de bandeja, privacidad y fallido, en `native/receipts/`.
- Suite de regresión de fases anteriores, lint del alcance y assembleDebug. Resultado y conteos definitivos en el reporte JSON.
- Hashes comparados con el inicio de la fase: cambios anteriores en hábitos, ajustes y bloqueo preservados. No se realizan commits, staging, resets ni stash.

## Pendiente de verificar manualmente

No hay teléfono Android conectado ni emulador instalado en este entorno. El OCR de ML Kit y PdfRenderer con comprobantes reales, el menú Compartir de Nequi, permisos temporales del proveedor, selector de documentos, apertura en otra app, diálogo biométrico real, TalkBack/teclado del sistema y widgets requieren verificación en un dispositivo. Las capturas nativas de Robolectric comprueban Compose, pero no sustituyen esa prueba. No se ha medido aún el objetivo de registrar en menos de diez segundos.

Recorrido de prueba en el teléfono:

1. Instalar la actualización sobre la app existente conservando datos. Comprobar cuentas, movimientos, hábitos y ajustes anteriores.
2. Compartir un comprobante de Nequi a Trackermaster · Money y revisar monto, moneda, fecha y dirección. Elegir cuenta/bolsillo y categoría. El saldo debe permanecer igual hasta confirmar.
3. Confirmar y comprobar el movimiento, el saldo, el widget y la apertura del original desde el historial. Compartir el mismo comprobante otra vez y comprobar la advertencia de duplicado.
4. Probar un comprobante pendiente/fallido y una imagen ilegible: el primero debe impedir el guardado; la segunda debe permitir completar manualmente los datos. Salir y retomar el borrador.
5. Probar PDF, giro de pantalla, privacidad, TalkBack, teclado, tres temas y los tres idiomas. En una transferencia COP/USD, comprobar que solicita el monto recibido y conserva el adjunto.
6. Con biometría activada, entrar por Compartir: cancelar debe mantener el contenido bloqueado y autenticar debe permitir revisar. Esta prueba corresponde al receptor nuevo; el problema previo de MainActivity se describe abajo.

El modelo incluido reconoce escritura latina (por ejemplo español/inglés). La interfaz tiene chino, pero no se incluye un modelo OCR para comprobantes con escritura china. Esos comprobantes se pueden completar manualmente. No se ha proporcionado un comprobante real para comprobar las variaciones del diseño de Nequi; las reglas se prueban con ejemplos sintéticos. Las fechas numéricas se interpretan como día/mes/año; es necesario revisar formatos estadounidenses de mes/día/año.

Deuda previa fuera de este receptor: `MainActivity` inicializa `unlocked` desde `AppSettings()` (biometría desactivada por defecto) y lo conserva con `remember` sin actualizarlo al cargar la preferencia guardada. Eso puede omitir su puerta biométrica. El nuevo receptor espera la configuración y no usa ese patrón. Se documenta para corregirlo en una revisión separada de seguridad; MainActivity y los cambios locales anteriores permanecen intactos.

El respaldo integral de base de datos, comprobantes y preferencias sigue programado para la fase 8. Esta fase añade rutas portables y persistencia local, pero no modifica ni certifica el restaurador de respaldo existente. La fase 5 continúa con presupuestos y recurrentes/recordatorios; deudas/metas, inversiones y exportación conservan las fases acordadas.

## Archivos del alcance

- `core/domain/.../money/Receipt.kt`, `ReceiptParser.kt` y pruebas: análisis, formulario y validación precisa.
- `core/database/.../MoneyReceiptEntity.kt`, `MoneyReceiptDao.kt`, `ReceiptMigration.kt`, `TrackermasterDatabase.kt`, esquema 7 y pruebas de migración.
- `core/data/.../money/ReceiptFiles.kt`, `ReceiptDraftCodec.kt`, `MoneyReceiptRepository.kt`, cambios pequeños en `MoneyRepository.kt`, registro de migración en `DataModule.kt` y pruebas.
- `feature/expense/.../ReceiptReader.kt`, `ReceiptGateway.kt`, `ReceiptViewModel.kt`, `ReceiptScreen.kt`, `ReceiptAuthentication.kt`, `MoneyReceiptActivity.kt`, previews y pruebas; integración en `ExpenseScreens`, `MoneyUiState`, `MoneyViewModel`, `MoneyOverview` y `MoneyHistory`.
- Manifest, rutas FileProvider, recursos `receipt_ui.xml` EN/ES/ZH y dependencias ML Kit, AndroidX ExifInterface y Biometric en catálogo/build del módulo; exclusión de INTERNET en el manifest de app.

Fuentes de SDK: [ML Kit Text Recognition para Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), [BiometricPrompt para Android](https://developer.android.com/identity/sign-in/biometric-auth) y [AndroidX ExifInterface 1.4.2](https://developer.android.com/jetpack/androidx/releases/exifinterface).
