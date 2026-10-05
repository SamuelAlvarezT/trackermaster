# Money — Fase 3: interfaz nativa y registro rápido

Estado: completada y validada automáticamente. La revisión en un Android real queda pendiente.

## Entrega

- Resumen con saldo actual por moneda y espacio, ingresos/gastos/neto del periodo, semana según idioma, mes, año y rango inclusivo. Cambiar el periodo no cambia el saldo actual.
- Editor de ingreso/gasto/transferencia: teclado decimal propio y entrada física, categoría con icono, cuenta y bolsillo visibles, fecha/nota opcionales. Selecciona el último origen válido o el del espacio activo; no reutiliza un monto al cambiar de moneda. Borrador en SavedStateHandle y solicitud estable para guardar una sola vez.
- Movimientos agrupados por día, búsqueda de notas/cuentas/bolsillos, filtros combinables, editar/borrar por gesto o menú accesible, confirmación en el menú y deshacer con Snackbar. Una transferencia aparece una vez y elimina/restaura ambas partes.
- Cuentas reales con tipo, moneda, espacio, saldo y disponible; crear/editar/archivar/reactivar. Bolsillos virtuales creados en cero; asignar dinero usa una transferencia dentro de la cuenta, sin inflar el total.
- Transferencias entre cuentas/bolsillos y entre COP/USD con monto recibido explícito. La reserva no puede quedar negativa y una edición fallida restaura la operación anterior.
- Dona por categoría y barras mensuales con línea de flujo neto usando Canvas. Descripciones y valores accesibles; no se dibujan proporciones financieras al ocultar montos.
- Ajustes propios de Money: resumen simple/panel detallado, pestañas/secciones y privacidad persistente. Se conserva espacio, periodo y filtros. La privacidad se aplica también al inicio y a los dos widgets existentes que muestran dinero; los widgets reciben refresco después de las escrituras.
- Carga con esqueletos, vacíos con iconos y acciones, errores con reintento y borrador conservado. 142 claves en EN/ES/ZH (141 strings y un plural), categorías nuevas con claves estables y nombres personalizados intactos. Tema compartido claro/oscuro/OLED. Money aplica el idioma elegido también en API 26–32 mediante un contexto localizado, sin cambiar MainActivity. El borrador restaurado espera a que terminen la carga y los errores para mostrar el editor con la moneda correcta.

## Arquitectura y almacenamiento

`MoneyViewModel` expone StateFlow y estados inmutables por pantalla con eventos explícitos. `MoneyPresentation` concentra filtros, agrupaciones y agregados; `MoneyPresenter` reutiliza reportes al escribir en el editor o buscar. `MoneyGateway` desacopla persistencia y preferencias para probar el flujo completo. Composables reciben estado/eventos y usan listas con claves estables.

Room pasa de 5 a 6 mediante dos columnas nullable e índices únicos: `transactions.entryRequestId` y `expense_categories.builtinKey`. Se mantiene sin cambios la migración 4→5 y los esquemas exportados anteriores. No se renombran categorías existentes ni se pierden valores originales, fechas o adjuntos. Los presupuestos antiguos sin moneda siguen identificados como pendientes, sin asignarles COP por suposición.

## Archivos

- `feature/expense`: entrada existente `ExpenseScreens.kt`, estados, ViewModel, gateway, presentación, componentes, resumen, historial, editor, cuentas, paneles, gráficos y lectura de presupuestos; recursos y previews de depuración; pruebas de presentación, ViewModel y UI.
- `core/domain/money/MoneyModels.kt`: contratos de categoría, presupuesto, workspace, solicitud de registro y fallos tipados.
- `core/database`: entidades, DAO, esquema 6, migración 5→6 y pruebas de migración.
- `core/data/money`: repositorio, mapeo y preferencias aisladas de Money; operaciones atómicas y pruebas.
- `core/data/di/DataModule.kt`: registra la migración adicional.
- `app/ui/DashboardScreen.kt` y recursos de Money: privacidad y formato COP/USD en ambas interfaces del inicio.
- `app/build.gradle.kts`: conserva los tres idiomas en el App Bundle para poder cambiar de idioma sin descargar recursos. `gradle/libs.versions.toml` y configuración de expense: dependencias de pruebas Compose en el catálogo, sin modificar versiones existentes.
- `core/widgets`: privacidad, actualización de los dos widgets monetarios y receptor local de refresco; otros widgets conservan su comportamiento.
- `docs/money`: informe, registros de validación, capturas nativas y comprobación de preservación.

Los once archivos protegidos de hábitos, ajustes, bloqueo y configuración mantienen exactamente sus hashes al inicio de esta fase: ver `phase3-preservation.json`. Sin commits ni cambios al índice de Git.

## Alcance pendiente

Los presupuestos guardados se muestran en lectura; creación, progreso, umbrales y alertas corresponden a la fase 5. Importar/interpretar comprobantes y detectar duplicados corresponden a la fase 4. Recurrentes, deudas, metas, inversiones, exportación CSV/PDF y widget configurable/respaldo integral mantienen las fases aprobadas. Registrar una transferencia aquí documenta una operación realizada en tu billetera/banco.

## Validación automática

85 pruebas distintas aprobadas en debug, sin fallos, errores ni omisiones:

| Módulo | Pruebas |
| --- | ---: |
| Dominio monetario | 17 |
| Base de datos y migraciones | 8 |
| Repositorio y preferencias | 26 |
| Presentación, ViewModel e interfaz Money | 28 |
| Regresión de hábitos en app | 6 |

Las 17 pruebas de dominio también se aprobaron en release: 102 ejecuciones contando esta repetición. Las nueve pruebas de interfaz de Money incluyen registro con teclado propio, teclado físico, privacidad, edición, eliminación/deshacer, navegación, transferencia COP→USD con importe recibido explícito, reintento sin tapar el error con un borrador restaurado y renderizado en temas/idiomas/fuente ampliada.

Lint de `core:data`, `feature:expense` y `core:widgets`: cero errores y únicamente el aviso previo `GradleDependency` de compileSdk 35 en cada módulo. La compilación Kotlin conserva las cuatro advertencias previas de iconos de `DefumblrLockScreenActivity.kt`; ese archivo está protegido y sin cambios. No se modificaron versiones para silenciar avisos.

Registro final: `phase3-release-validation.log`. Resultados y metadatos del APK: `phase3-test-results.json`.

Compilación final: `BUILD SUCCESSFUL` (546 tareas; 449 ejecutadas y 97 desde caché). Se desactivó la caché de configuración solamente para la ejecución de validación porque una pasada anterior agotó el tiempo al escribir su informe; no se cambió `gradle.properties`.

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`, paquete `com.trackermaster.app`, versión 1.0.0, 22.462.639 bytes. Archivo generado el 4 de octubre de 2026 a las 19:08:45 (Bogotá), estructura ZIP/CRC comprobada. SHA-256: `4221af62ceec8000105b310cb43f81f43535186bd4295e2158d4041fa6ca07fc`.

`git diff --check` pasó y los once archivos protegidos conservaron sus hashes al cerrar la fase.

## Verificación manual necesaria

No hay teléfono conectado ni AVD instalado. Robolectric permite verificar flujos y renderizado nativo, pero quedan por comprobar en un Android real: TalkBack, teclado físico/IME, temporización y gestos, selector de fechas, fuentes ampliadas, modo privacidad y refresco en un launcher real, actualización sobre una copia de tus datos y gasto habitual en menos de 10 segundos.

Las capturas en `native/` se obtienen de la jerarquía Android real mediante Canvas/Skia nativo en Robolectric, con datos de demostración disponibles únicamente en depuración. Incluyen los tres temas e idiomas, historial, cuentas, reportes, presupuestos, privacidad, editor y fuente al 150 %. No sustituyen la revisión en un teléfono.

## Referencia técnica

Los widgets monetarios observan saldos y preferencias dentro de la composición además de recibir `updateAll`, siguiendo el contrato de [GlanceAppWidget](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/GlanceAppWidget). Así pueden actualizarse también mientras la sesión del widget está activa.
