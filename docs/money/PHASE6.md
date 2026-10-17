# Money — Fase 6: deudas, abonos y metas de ahorro

15 de octubre de 2026. **Fase 6 implementada y validada automáticamente**; la mejora completa conserva pendientes las fases 7 y 8.

- Compilación debug completa; **256 pruebas aprobadas**, cero fallos: dominio 53, base de datos 19, datos 88, tema 3, Money/Compose 87 y app 6. Son 50 pruebas nuevas respecto a la fase 5.
- Lint de datos y Money: cero errores, sin advertencias nuevas de código Money. Se conservan los 13 avisos anteriores de configuración/dependencias de datos, uno de dependencia de Money y cuatro avisos de iconos anteriores del bloqueo.
- 22 capturas nativas: los tres temas y los tres idiomas, fuentes al 150 %, tarjetas de metas, deudas, privacidad y editores. El encabezado adapta su disposición con fuente ampliada para conservar los controles.
- [APK debug de fase 6](artifacts/trackermaster-money-phase6-debug.apk), firma v2, misma identidad de firma que la fase 5 e integridad CRC verificadas. [Resultados y huellas](phase6-test-results.json).

## Comportamiento

- Accesos a **Deudas** y **Metas de ahorro** desde el resumen, compatibles con pestañas y secciones. Actividad interna con el tema compartido y autenticación biométrica cuando está habilitada; espera la configuración antes de mostrar datos.
- Deudas por pagar y por cobrar, persona o entidad, propósito, COP/USD separados, capital pendiente, fecha opcional, nota e historial. Las deudas existentes registran el saldo pendiente sin inventar efectivo. Un préstamo nuevo permite registrar también el desembolso o recepción real en una cuenta o bolsillo.
- Abonos de capital con monto y fecha reales, cuenta y bolsillo compatibles con moneda y propósito. Se rechazan los sobrepagos, cambios concurrentes y vínculos repetidos. El movimiento y el abono se guardan en una sola transacción Room, con una solicitud estable para recuperar reintentos.
- Vinculación explícita de un movimiento ya registrado o comprobante importado, sin una segunda entrada/salida de efectivo. Las sugerencias comparan cuenta, bolsillo, moneda, propósito, monto, dirección y siete días alrededor de la fecha. Movimientos ya usados por planes u otras deudas quedan excluidos.
- El capital modifica efectivo y deuda; se excluye de ingresos/gastos ordinarios, presupuestos y gráficos operativos. Intereses y comisiones se registran como movimientos ordinarios separados.
- Se puede corregir el capital inicial respetando los abonos existentes. Si hubo desembolso registrado, su monto y el efectivo se corrigen atómicamente, comprobando las reservas; en una deuda histórica sin desembolso el efectivo permanece igual. Moneda, tipo y propósito se pueden corregir antes de registrar movimientos vinculados.
- Los movimientos de capital se identifican en el historial y se administran desde Deudas. Revertir un abono reabre el capital pendiente: elimina el movimiento creado para ese abono o conserva y desvincula el movimiento importado. No se permite dejar bolsillos sin financiación al revertir.
- Las deudas se pueden archivar una vez saldadas. Se conserva su historial y se pueden reactivar. Las metas archivadas conservan el dinero real reservado.
- Metas con nombre, moneda, monto objetivo, fecha opcional y bolsillo exclusivo. Se puede crear un bolsillo vacío dentro de una cuenta o asociar un bolsillo existente. Una misma reserva no financia dos metas.
- Aportar y retirar usa transferencias reales con dos movimientos atómicos. El total de las cuentas no aumenta al reservar; se reduce o aumenta el saldo sin asignar. Se comprueba el dinero disponible y se impiden aportes/retiros sin fondos. El progreso refleja el saldo actual del bolsillo, incluidos gastos o transferencias realizados desde otras pantallas.
- Privacidad compartida: importes y porcentaje de la meta se ocultan también en las descripciones accesibles. Formularios conservados en SavedStateHandle, guardado protegido contra dobles pulsaciones, estados de carga, error y vacío con acciones.
- Textos en inglés, español y chino; fechas y monedas con formato regional; sin dependencias nuevas para gráficos ni servicios financieros.

## Persistencia y compatibilidad

La versión actual del repositorio era 10 por las fases anteriores y los cambios independientes de sincronización/métricas. Esta fase añade **10→11**, sin modificar las migraciones 4→10 ni usar restauración destructiva:

- `transactions.principal`, inicialmente falso en todos los movimientos anteriores.
- `money_debts`, `money_debt_payments` y `money_savings_goals`, con identificadores estables e índices para propósito y vínculos únicos.
- Conservación de cuentas, importes originales, unidades menores, comprobantes, planes y tablas externas. Las reglas de dominio usan Long exactos y comprobación de desbordamiento.

Los avisos de vencimiento reutilizan WorkManager, el canal y el permiso de notificaciones de planificación. No contienen nombres, notas ni importes. Abren Deudas con su moneda y propósito. Las deudas saldadas o archivadas dejan de producir avisos. La campana permite abrir los ajustes de notificación de la app; la entrega depende del permiso y de las restricciones de Android.

El respaldo integral y su restauración siguen en fase 8. La sincronización externa presente en el repositorio no se amplía en esta fase: las nuevas tablas de compromisos permanecen locales.

## Validación

Compilación, pruebas de dominio, migración, repositorios, ViewModel, interfaz y lint completados. Las pruebas de Compose se ejecutan con Robolectric y gráficos nativos. La compilación final terminó correctamente con 528 tareas: 106 ejecutadas y 422 sin cambios.

Se comprobaron 216 referencias del estado anterior y las huellas de las 241 fuentes utilizadas para compilar. Los archivos protegidos mantienen su contenido; los esquemas históricos 4–10 permanecen intactos. La migración prueba tanto 10→11 con datos poblados —incluidos planes y confirmaciones— como la cadena completa 4→11.

El APK entregado tiene 72.181.344 bytes y SHA-256 `adf0415478674ed11b2054319413c4a0c9d00384c63d1662dabf6e6f1b2e1730`. El verificador reproducible es `python docs/money/qa_phase6.py`; también compara traducciones, firmas, capturas, fuentes y advertencias anteriores.

Pruebas nuevas: cuatro direcciones de efectivo de préstamos; capital separado de gasto; cantidades y monedas; sobrepagos; idempotencia; vinculación de comprobantes; correcciones; protección del historial; fechas de avisos; creación y uso de bolsillos; transferencias de aporte/retiro y fondos insuficientes; borradores, errores, privacidad y flujos Compose.

Pendiente en teléfono: TalkBack, teclado, biometría, entrega de avisos con permiso denegado/concedido, Doze y reinicio; actualización con una copia real de datos y comprobantes reales. No hay teléfono ni emulador conectado. Las pruebas de interfaz locales no sustituyen estas comprobaciones. Sigue vigente el fallo previo de MainActivity documentado en PHASE4.md.

## Archivos

Son 43 archivos de código, esquema, recursos y pruebas añadidos o modificados. [Inventario completo](phase6-files.json).

- Dominio: `MoneyCommitments.kt`, clasificación de movimientos y filtros en `MoneyLedger`/`MoneyPlanning`.
- Base de datos: `MoneyCommitmentEntities`, `MoneyCommitmentDao`, `CommitmentMigration`, entidades, registro del esquema y migración en `DataModule`.
- Datos: `MoneyCommitmentRepository`, mapeo y protección de movimientos en `MoneyRepository`, planificación y avisos.
- Interfaz: `MoneyCommitmentState`, `Drafts`, `Gateway`, `ViewModel`, `Screen`, `Activity`; accesos, presentación e historial de Money; recursos EN/ES/ZH y previews.
- Pruebas nuevas en dominio, base de datos, repositorio, ViewModel y Compose. Verificación reproducible con `qa_phase6.py` y referencias SHA-256 del código anterior y de las fuentes compiladas.

No se han editado hábitos, ajustes, bloqueo ni MainActivity. No hay commit ni push de esta fase.
