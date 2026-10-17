# Money — Fase 5: presupuestos, recurrentes y recordatorios

Estado al 9 de octubre de 2026: **fase 5 implementada y validada automáticamente**. La mejora completa de Money conserva pendientes las fases 6–8.

- Compilación debug completa y firmada; 206 pruebas aprobadas, cero fallos.
- Dominio: 43; base de datos: 17; datos: 67; tema compartido: 3; Money/Compose: 70; app: 6. Incluyen pruebas de los cambios externos presentes en el repositorio.
- Lint de datos y Money: cero errores y ninguna advertencia nueva de código Money. Permanecen avisos de SDK/dependencias/catálogo en la configuración externa de sincronización y cuatro avisos previos de iconos en la pantalla de bloqueo.
- 17 capturas nuevas de planificación: claro/oscuro/OLED, inglés/español/chino, fuentes al 150 %, privacidad y formularios. Revisión visual de contraste y contenido; la captura OLED mide 4,51:1 entre acento y tarjeta.
- [APK debug de fase 5](artifacts/trackermaster-money-phase5-debug.apk), firma v2 y CRC verificados. [Resultados completos](phase5-test-results.json).

La validación usó una copia aislada para evitar archivos de resultados bloqueados por otras compilaciones. La huella final de todas las fuentes coincide con el repositorio actual. Se conservaron las referencias originales y los puntos de observación de las ediciones externas de hábitos, tareas, ajustes, sincronización y modelos compartidos; esos archivos no fueron sobrescritos por esta fase. Money incorpora 7→8, y las modificaciones externas incorporan 8→9→10. El esquema histórico 8 se regeneró con KSP y se verificó dentro del APK.

## Alcance

- Presupuestos por categoría o todas las categorías, moneda COP/USD y espacio; periodos semanal según región, mensual, anual o rango inclusivo. Crear, editar, configurar presupuestos anteriores y eliminar con confirmación.
- Consumo calculado sobre gastos confirmados; las transferencias y los ingresos no incrementan el consumo. Importe restante, barra y estados: dentro del presupuesto, cerca del límite, límite alcanzado y excedido. Umbral configurable de 1 a 100 %. El estado no depende solo del color. Privacidad oculta importes y barras.
- Pagos fijos o ingresos recurrentes, recordatorios de compra sin monto obligatorio y estimaciones de gasto. Frecuencias: una vez, diaria, semanal, mensual y anual. Conservación del día de origen al pasar por meses cortos y años no bisiestos.
- Cuenta, bolsillo y categoría opcionales al planear; obligatorios al confirmar. Moneda y propósito del dinero se conservan por separado. La cuenta/bolsillo determina el espacio efectivo al asignar financiación.
- Previsión mensual separada del saldo, sin contabilizar compras con importe desconocido. Los pendientes nunca generan movimientos automáticamente.
- Confirmación editable del monto real, fecha, cuenta, bolsillo, categoría y nota. Guardado atómico del movimiento, ocurrencia y avance a la siguiente fecha. Solicitud estable por plan/fecha para impedir duplicados al pulsar varias veces o recuperar el proceso.
- Vinculación explícita de movimientos existentes compatibles —incluidos comprobantes importados— para no descontar el pago dos veces. Coincidencias en los siete días alrededor de la fecha seleccionada, con cuenta/bolsillo, monto, moneda, dirección y categoría compatibles. Un movimiento solo puede confirmar una ocurrencia.
- Aplazar el aviso a mañana conserva el vencimiento. Omitir una ocurrencia avanza la planificación sin crear un pago y exige confirmación. Pausar/reanudar y archivar conservan el historial financiero.
- Accesos desde el resumen, la pestaña de presupuestos y las vistas por secciones. Resumen detallado con próximos pagos. Editor y listas con UDF/StateFlow, borradores en SavedStateHandle, recursos EN/ES/ZH y temas existentes.
- Colores de acento legibles en el tema compartido para los 20 presets y los tres temas; se conserva el fondo negro OLED y se reutiliza el sistema de colores de toda la app.
- Nueva actividad interna protegida por configuración cargada y biometría fuerte cuando está habilitada. Comparte el componente de autenticación con comprobantes; no modifica MainActivity ni los cambios locales de bloqueo.

## Datos y reglas

Room pasa de 7 a 8: fechas opcionales en presupuestos y tablas `money_plans`, `money_plan_occurrences` y `money_planning_alerts`. El esquema 8 se genera con KSP. Se conservan los esquemas y migraciones anteriores; no hay restauración destructiva. Los presupuestos anteriores siguen sin moneda inferida hasta que el usuario los configure.

La previsión usa unidades menores exactas; no mezcla monedas ni se suma al patrimonio. El registro de un gasto puede conservar un saldo negativo, siguiendo el comportamiento existente de Money. Planear, omitir, pausar y aplazar no alteran saldos. La confirmación comprueba versión, vencimiento, cuenta activa, espacio, categoría, moneda y correspondencia del movimiento vinculado dentro de Room.

Una ocurrencia confirmada conserva su auditoría cuando se elimina el movimiento desde el historial. Eliminarlo no reabre automáticamente una obligación ni crea un pago nuevo; deshacer recupera su vínculo. La tarjeta advierte si falta el movimiento de la confirmación más reciente.

Los ingresos ordinarios no se tratan como reembolsos de un presupuesto. Los reembolsos vinculados siguen requiriendo un modelo explícito futuro para no deducir de un gasto cualquier ingreso que comparta categoría.

## Avisos locales

WorkManager verifica avisos aproximadamente cada hora, y se solicita una revisión tras guardar movimientos o planes. Los recordatorios son elegibles desde las 09:00 del día seleccionado o si están atrasados. No se solicitan alarmas exactas nuevas. Las tareas persisten y se reprograman tras reiniciar, sujetas a restricciones de Android.

Los identificadores visibles para Android se derivan de tipo, ID y fecha, sin importes ni nombres; las claves financieras de deduplicación permanecen en Room.

Los avisos de presupuesto se identifican por presupuesto/periodo/límite/umbral/estado; los de plan, por ocurrencia/fecha de aviso. Una reserva temporal permite recuperar un envío interrumpido. Las notificaciones no incluyen nombres, montos ni notas. Abren la moneda y el espacio correspondientes, y los avisos que dejan de ser pertinentes se cancelan al revisar.

Con permiso o canal desactivado se muestran los pendientes dentro de Money y acciones para pedir permiso o abrir ajustes. No se marcan avisos como enviados mientras el permiso esté denegado. La inicialización de WorkManager usa el Configuration.Provider/HiltWorkerFactory que la app ya ofrece.

## Validación y revisión manual

Las pruebas cubren límites monetarios, monedas/espacios/categorías, periodos regionales, fechas extremas, mes corto/año bisiesto, previsiones atrasadas, presupuestos históricos, CRUD, idempotencia, rechazo con rollback, vinculación sin doble gasto, cambio concurrente, eliminación/deshacer y recuperación de avisos.

ViewModels y Compose: montos vacíos/inválidos sin interrumpir la observación, vinculación desde bolsillos con propósito distinto al de su cuenta, campos vinculados protegidos y edición tras desvincular; formularios incompletos, guardado repetido, borradores recreados, errores/reintento, privacidad entre pantallas, permiso denegado, creación de presupuesto/plan y confirmación o vínculo de movimiento. Previews de presupuestos, planes y editores; capturas nativas en los tres temas/idiomas y fuentes al 150 %.

Pendiente en teléfono: entrega real de avisos con pantalla apagada, Doze y reinicio; permiso denegado y canal bloqueado; enlace desde notificación; biometría; TalkBack/teclado; actualización con una copia real de datos. No hay dispositivo o emulador conectado, y las pruebas Robolectric no sustituyen esas comprobaciones. La medición de registro en menos de diez segundos continúa pendiente.

El fallo previo de biometría en MainActivity continúa documentado en PHASE4.md. El respaldo/restauración integral, deudas/metas e inversiones/exportación conservan las fases 6–8 pendientes.

## Archivos principales

- Dominio: `MoneyPlanning.kt`, ampliación de `MoneyModels.kt` y pruebas de reglas.
- Base de datos: `MoneyPlanningEntities.kt`, `MoneyPlanningDao.kt`, `PlanningMigration.kt`, `TrackermasterDatabase.kt`, presupuestos, esquema 8 y pruebas de migración.
- Datos: `MoneyPlanningRepository.kt`, `MoneyPlanningMapping.kt`, `MoneyPlanningScheduler.kt`, `MoneyPlanningWorker.kt`, integración en `MoneyRepository`/`DataModule`, textos de notificación y pruebas.
- Interfaz: `MoneyPlanningState`, `MoneyPlanningDrafts`, `MoneyPlanningGateway`, `MoneyPlanningViewModel`, `MoneyPlanningScreen`, `MoneyPlanningActivity`, previews y pruebas; integración en presupuesto/resumen/presentación/eventos y autenticación compartida.
- Tema compartido: `core/ui/Theme.kt`, cálculo de contraste recordado y pruebas para todos los acentos.
- Datos/integración: manifest de la biblioteca con el permiso normal ACCESS_NETWORK_STATE, que la app ya declaraba para su sincronización; Money no añade operaciones de red.
- App: inicialización en `TrackermasterApp` y ajuste de inicializador WorkManager en manifest. Sin editar fuentes protegidas de hábitos, ajustes o bloqueo.

Fuentes: [contraste mínimo de texto](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html), [WorkManager y trabajo persistente](https://developer.android.com/develop/background-work/background-tasks/persistent), [periodicidad y retrasos](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [permiso de notificaciones](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
