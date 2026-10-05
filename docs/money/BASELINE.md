# Money — referencia inicial de validación

Fecha: 4 de octubre de 2026 (America/Bogota). Fase 1, antes de modificar fuentes Android.

## Compilación

Comando: `gradlew.bat :app:assembleDebug --offline --console=plain --warning-mode=all`.

Resultado: **BUILD SUCCESSFUL**, código de salida 0, 31 s; 334 tareas ya actualizadas. APK generado en `app/build/outputs/apk/debug/app-debug.apk` (21 979 684 bytes), applicationId `com.trackermaster.app`, versión 1.0.0, minSdk 26. No se instaló en un teléfono ni se ejecutó la app.

Registro: `baseline-assemble.log`. El primer intento restringido necesitaba descargar el wrapper y falló por permisos de red (`baseline-build.log`). El intento posterior autorizado descargó Gradle y alcanzó la compilación del APK. La compilación independiente posterior confirma el resultado de assembleDebug.

No se registraron mensajes `w:`, `warning`, `WARNING` o `deprecated` en los registros finales de estas ejecuciones. Como muchas tareas usaron caché, esto no demuestra ausencia de warnings en una reconstrucción limpia. No se ejecutó lint. En esta fase no se cambió código Android ni dependencias, por lo que todavía no existe un cambio de implementación contra el cual certificar «sin warnings nuevos».

## Pruebas existentes

Comando: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --warning-mode=all`.

Resultado: **fallo en compileDebugUnitTestKotlin**, antes de ejecutar JUnit. El APK sí se compiló dentro de esta ejecución. Registro: `baseline-validation.log`.

Causa observada: `HabitsViewModelTest.kt` importa `kotlinx.coroutines.test`, `org.mockito` y `org.mockito.kotlin`, pero las dependencias de test correspondientes no están declaradas en app/build.gradle.kts. Los errores iniciales son referencias sin resolver a `test`, `mockito`, `StandardTestDispatcher`, `setMain`, `runTest` y `any`. No se modificaron los tests de hábitos ni el build para disimular este fallo de referencia.

Antes de exigir la suite verde en fase 2: añadir dependencias de test compatibles en un cambio de infraestructura específico y repetir testDebugUnitTest. Si después aparecen errores adicionales, resolver/reportar con evidencia; la causa observada no garantiza que sea el único problema de la suite. No hay pruebas de Money o migración en el árbol actual. No se ejecutaron pruebas instrumentadas ni MigrationTestHelper en esta fase de diseño.

## Prototipo visual e interacción

Archivo: `wireframes.html`, independiente del APK. Datos ficticios, únicamente en memoria; no OCR, consultas financieras reales ni persistencia. Los botones de acciones previstas muestran una indicación de que aún no ejecutan esa función. Los selectores de periodo y los gráficos ilustran el diseño; no son reportes reales.

Verificado en el navegador integrado:

- Vista de resumen, movimientos, presupuestos, reportes, cuentas, comprobante, deudas, metas, inversiones, editor y widget.
- Ocultar/mostrar importes en el resumen.
- Cambiar Personal COP a Work and Travel USD: aparece saldo separado de USD 850, sin agregarlo a COP.
- Guardar gasto de ejemplo de COP 12 000: saldo ficticio de COP 1 240 000 a COP 1 228 000.
- Cambiar un comprobante de COP 45 000 a ingreso y confirmarlo: saldo ficticio de COP 1 228 000 a COP 1 273 000.
- Resumen minimalista/detallado y navegación por pestañas/secciones; las pestañas se ocultan en el modo de secciones.
- Estado vacío, carga, error y reintentar.
- Temas claro, oscuro y OLED, capturas guardadas.
- Viewport de 390 × 844: ancho de documento 375 (sin desbordamiento horizontal); ningún botón visible con altura menor de 48 píxeles CSS. Esto no sustituye verificar 48dp en Compose.
- Sin errores o warnings de consola capturados durante estas interacciones. Sintaxis del script comprobada mediante `node --check`.

Las capturas de tema oscuro/OLED corresponden a Work and Travel con panel detallado; la clara corresponde a Personal minimalista. Las cifras son ejemplos, no datos personales.

## Archivos y trabajo local

Solo se añadieron documentos, prototipo, registros y capturas en `docs/money/`. No se editaron fuentes Android, esquemas, Gradle ni los 11 archivos con cambios locales previos. Las compilaciones generaron archivos derivados en los directorios de build/caché habituales.

## Verificación manual pendiente

Revisar jerarquía de información y nombres de espacios/bolsillos; confirmar que la selección cuenta + espacio es comprensible para un Nequi compartido entre propósitos. Evaluar formulario con importes reales representativos, TalkBack, fuente ampliada y contraste con todos los acentos existentes una vez implementado en Compose. Validar EN/ES/ZH en Android, tiempo de registro menor de 10 s en teléfono, comprobantes reales anonimizados, recuperación del proceso, duplicados, notificaciones y actualización del widget.

Migraciones, cálculos monetarios, respaldos y restauración se validarán en sus fases de implementación; la fase 1 documenta sus contratos, no certifica su funcionamiento actual.
