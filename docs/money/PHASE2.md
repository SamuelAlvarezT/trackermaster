# Money — fase 2: base financiera

4 de octubre de 2026. Implementación de la segunda entrega del plan aprobado: unidades monetarias precisas, espacios, cuentas, bolsillos y transferencias, con migración de datos. El módulo real sigue llamándose `feature/expense`.

## Resultado

- Importes nuevos en unidades menores `Long`, conversión decimal con `BigDecimal`, validación de precisión y desbordamiento. Los totales COP y USD se calculan y presentan por separado, con código de moneda y formato regional.
- Espacios iniciales: Sin organizar, Personal, Negocio, Ahorros y Work and Travel; espacios personalizados disponibles en el repositorio. Una cuenta real se cuenta una sola vez. Los bolsillos distribuyen su saldo entre espacios sin crear dinero.
- Repositorio transaccional para crear/clasificar/archivar cuentas, crear bolsillos y registrar movimientos. La moneda y el saldo inicial quedan protegidos cuando ya hay movimientos. Archivar conserva saldo e historial.
- Transferencias con dos movimientos atómicos e identificador idempotente. Misma moneda conserva el importe; entre monedas requiere ambos montos explícitos. Las transferencias no inflan ingresos/gastos operativos. Se controla el saldo disponible y el dinero reservado; el borrado afecta al par completo.
- Lectura consistente de cuentas, movimientos y bolsillos con relaciones Room y consultas transaccionales. Cálculo puro de saldo por cuenta, bolsillo, espacio y moneda, y totales por periodo inclusivo.
- Integración de los nuevos totales en Money, Inicio y widgets existentes. Recursos EN/ES/ZH para los textos modificados, claves estables en listas y validación mínima del formulario existente.

La interfaz de cuentas/bolsillos y transferencias todavía no expone estas operaciones nuevas: se implementa en la fase 3 junto con el rediseño. Esta entrega prepara y verifica las reglas que utilizará esa interfaz.

## Migración y conservación

Room pasa de 4 a 5 mediante migración explícita, sin `fallbackToDestructiveMigration`. Se exportó el esquema 4 antes de modificar las entidades; se conservan los archivos `4.json` y `5.json`.

Las cuentas antiguas mantienen nombre, moneda y saldo original, clasificadas en Sin organizar. La conversión a unidades menores usa `HALF_EVEN`; los campos antiguos REAL y las fechas originales se conservan como trazabilidad. Una edición que solo cambia nombre o nota no reescribe esos valores. Las fechas se congelan al día que mostraba la app en la zona del dispositivo al actualizar; después se almacenan como `epochDay`.

Los presupuestos antiguos no tenían moneda ni espacio: sus campos nuevos quedan nulos, pendientes de clasificación en la fase de presupuestos. No se les asigna COP de forma arbitraria.

La variante de adjuntos creada por el migrador anterior 3→4 tenía una FK ausente en la entidad 4. Se normaliza únicamente esa variante, conservando filas, IDs y el contador AUTOINCREMENT, incluso si antes se habían borrado adjuntos. El migrador 3→4 se mantiene sin modificaciones. No se certificó la cadena completa desde una instalación auténtica de versión 3.

Una moneda inválida o un importe que no se puede convertir interrumpe la actualización y revierte la migración: no se sustituye el dinero por cero ni se descartan datos.

## Archivos del alcance

| Área | Archivos |
| --- | --- |
| Dominio | `core/domain/.../money/Money.kt`, `MoneyModels.kt`, `MoneyLedger.kt`; campo adicional por moneda en `Models.kt` |
| Room | `TrackermasterDatabase.kt`, `MoneyMigrations.kt`, `entity/Entities.kt`, `entity/MoneyEntities.kt`, `dao/MoneyDao.kt`, esquemas 4/5 y configuración del módulo |
| Datos | `money/MoneyRepository.kt`, `money/MoneyMappings.kt`, integración Money/Dashboard en `repository/Repositories.kt`, `mapper/Mappers.kt`, `di/DataModule.kt` y recursos en tres idiomas |
| UI existente | `feature/expense/.../ExpenseScreens.kt`, `app/.../ui/DashboardScreen.kt`, `core/widgets/.../TrackermasterWidgets.kt` y sus recursos Money |
| Pruebas | `MoneyTest.kt`, `MoneyLedgerTest.kt`, `MoneyMigrationTest.kt`, `MoneyRepositoryTest.kt`; configuración de dependencias de prueba y ajuste de recolección de StateFlow en `HabitsViewModelTest.kt` |

Los cambios locales de producción en hábitos, ajustes y pantalla de bloqueo se conservan idénticos, comprobados con SHA-256 frente a `phase2-local-before.json`. En los dos archivos compartidos previamente modificados, `Models.kt` y `Repositories.kt`, se añadieron únicamente los campos e integración de Money/Dashboard; se preservaron los cambios existentes de hábitos/ajustes. No se hicieron commits.

## Validación

El comando completo de construcción y validación terminó con `BUILD SUCCESSFUL` (registro `phase2-final-validation.log`):

```powershell
.\gradlew.bat :app:assembleDebug :core:domain:testDebugUnitTest :core:database:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest :feature:expense:lintDebug --continue --console=plain --warning-mode=all
```

| Pruebas | Resultado |
| --- | --- |
| Precisión, moneda, formato y límites | 10 pasan |
| Saldos, espacios, bolsillos y flujos | 7 pasan |
| Migración 4→5 | 5 pasan |
| Repositorio sobre Room real en memoria | 13 pasan |
| Regresión existente de hábitos | 6 pasan |
| Total | **41 pasan; 0 fallos, 0 errores, 0 omitidas** |

Las pruebas de migración verifican base vacía, base poblada con datos de todas las funciones, COP/USD, decimales y valores históricos negativos, la variante de adjuntos con contador previo y rollback ante moneda inválida. Las de repositorio verifican transferencias, repetición de solicitud, cambio de moneda, reservas, archivado, categoría incompatible, preservación del valor original y rollback ante desbordamiento tras escritura.

Lint de `feature:expense`: **0 errores, 1 advertencia previa** por `compileSdk = 35`; ese archivo no fue modificado. La compilación mantuvo cuatro advertencias de iconos deprecados en la pantalla de bloqueo existente, que tampoco se modificó. No se añadieron advertencias de código Money.

La configuración de Mockito utiliza agente explícito al arrancar la JVM de pruebas, compatible con Java 21; no añade un agente a la app Android. Las seis pruebas de la app se repitieron con esta configuración y pasaron, sin las advertencias de carga dinámica del agente ni de CDS. Registro de cierre: `phase2-close-validation.log`. La memoria adicional de metaspace se aplica solo por argumento de esa ejecución, sin editar `gradle.properties`.

La ejecución de cierre también terminó con **BUILD SUCCESSFUL**, 41 pruebas pasadas y el mismo resultado de lint (0 errores, 1 advertencia previa). Duración de la reconstrucción: 31 min 59 s. Comando para este equipo Windows, que evita el fallo de conexión con el daemon Kotlin:

```powershell
.\gradlew.bat :app:assembleDebug :core:domain:testDebugUnitTest :core:database:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest :feature:expense:lintDebug '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=1 --no-parallel --console=plain --warning-mode=all
```

## Pendiente y comprobación manual

- Fase 3: nuevo resumen, navegación configurable, formularios de cuentas/bolsillos/transferencias, editor rápido, historial y estados UDF por pantalla. Las categorías antiguas todavía conservan su nombre escrito originalmente; falta identificación por código estable para traducir categorías predeterminadas al cambiar idioma.
- Fase 4: compartir comprobantes, copia local de adjuntos, OCR, detección de duplicados y confirmación editable. Todavía no hay importación desde Nequi.
- Fases posteriores: presupuestos/recurrentes, deudas/metas, inversiones/reportes/exportación y respaldo completo. Cualquier ampliación del esquema ya distribuido requiere nueva migración; no reescribir 4→5 después de publicarlo.
- La pantalla de bloqueo conserva su interfaz local existente y consume únicamente el saldo COP mediante el adaptador compatible. Su presentación multi moneda queda pendiente de integrar sin interferir con esos cambios locales.
- Los modelos antiguos que consumen pantallas existentes siguen usando Double como adaptador de presentación; la aritmética y persistencia nueva utilizan unidades menores. No se considera terminado el reemplazo completo del modelo de UI.
- No se ejecutaron pruebas instrumentadas en dispositivo ni se midieron los diez segundos de registro. Antes de distribuir: actualizar una copia real de datos, comparar saldos y adjuntos, comprobar tres temas × tres idiomas, fuente ampliada/TalkBack, y widgets tras reiniciar. La prueba JVM de migración no sustituye esa revisión en dispositivo.

Referencias técnicas: [migraciones Room](https://developer.android.com/training/data-storage/room/migrating-db-versions) y [Mockito: agente de pruebas en Java 21](https://javadoc.io/static/org.mockito/mockito-core/5.14.2/org/mockito/Mockito.html#0.3).
