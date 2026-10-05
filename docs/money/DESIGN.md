# Money — diseño de producto y reglas financieras

Fase 1 · 4 de octubre de 2026 · diseño aprobado para desarrollar por fases.

Este documento define el comportamiento objetivo. El prototipo usa datos ficticios en memoria y no modifica la app Android, no mueve dinero y no contiene comprobantes reales. No representa funcionalidades ya implementadas.

## Prioridad y límites

Primero separar correctamente el dinero y registrar gastos rápidamente; después importar comprobantes compartidos desde Nequi, con revisión antes de guardar. Sin bancos/Open Banking, Google Drive, multiusuario, cotizaciones en línea ni operaciones bancarias. El OCR y la interpretación se ejecutan en el dispositivo. CSV/PDF son reportes; el ZIP es el respaldo restaurable.

## Estructura de información

Espacios iniciales configurables: Personal (COP), Negocio (COP), Ahorros (COP) y Work and Travel (USD). Una cuenta representa un depósito real: Nequi, banco, tarjeta, efectivo. Cada cuenta tiene una moneda inmutable mientras existan movimientos. Un bolsillo reserva una parte de una cuenta para un espacio o propósito; una misma cuenta no se duplica por pertenecer a varios espacios. Los bolsillos no pueden asignar más dinero del disponible sin una decisión explícita y visible.

La UI debe distinguir «cuenta: Nequi» de «destino: Negocio». La vista global agrega las cuentas reales una sola vez, por moneda. La vista del espacio muestra sus asignaciones. Para migrar se preservan cuenta, moneda y saldo existentes: una cuenta antigua en USD no se cambia a COP por el idioma ni porque sea la cuenta predeterminada. Las cuentas sin clasificación aparecen en «Sin organizar», con asistente opcional.

## Reglas financieras

1. Dinero persistido como unidades menores en Long; operaciones en decimal preciso, nunca aritmética monetaria en Double. COP y USD tendrán escala explícita de dos decimales. Presentación con código de moneda y formato regional; no usar un símbolo `$` aislado. Redondeo de importación documentado (HALF_EVEN) y comprobación de desbordamiento. Al migrar conservar los campos originales o una tabla de trazabilidad y comparar saldos anteriores/nuevos antes de liberar.
2. Importe positivo y finito; el tipo determina la dirección. Saldo de cuenta = saldo inicial + entradas confirmadas − salidas confirmadas. Las correcciones de saldo se identifican como ajustes; no se disfrazan de ingresos ordinarios.
3. COP y USD nunca se suman directamente. El global muestra ambos totales; una conversión futura requiere tasa explícita y no altera el saldo original. Una transferencia entre monedas registra ambos montos y la tasa efectiva indicada por el usuario; las comisiones son gastos separados.
4. Una transferencia crea sus dos movimientos en una transacción Room atómica y comparten identificador. Editar/borrar/deshacer afecta al par completo. Una transferencia interna no altera el ingreso/gasto operativo; si cruza espacios se presenta como transferencia entrante/saliente por separado. Reservar dinero en un bolsillo no crea dinero ni ingreso.
5. Saldo actual y flujo del periodo son métricas distintas. El selector de fechas cambia ingresos, gastos y gráficos; no cambia silenciosamente el saldo actual. Semana según región; mes/año naturales. Rango inclusivo para el usuario y exclusivo en el extremo superior de la consulta.
6. Fechas financieras como LocalDate/epochDay; instante de creación y hora del comprobante por separado cuando estén disponibles. No desplazar el día de una operación al viajar o cambiar zona horaria.
7. Presupuesto ligado a espacio/moneda, categoría y límites de fecha; consumo de gastos confirmados del periodo. Estados: normal, cerca (umbral configurable, inicial 80 %), excedido (>100 %); alcanzar exactamente 100 % es «Límite alcanzado». Mostrar monto restante y texto además de color. Política de reembolsos explícita: un reembolso ligado reduce el gasto neto original en el periodo del reembolso y se identifica en reportes.
8. Deudas separadas en «Debo» y «Me deben». Desembolsar/recibir un préstamo y amortizar principal modifica efectivo y deuda, sin inflar ingreso/gasto operativo. Intereses/comisiones sí cuentan como gastos/ingresos. Cada abono se enlaza al movimiento financiero; impedir doble abono accidental y sobrepago silencioso. Saldo disponible, deuda pendiente y patrimonio se etiquetan por separado.
9. Meta = objetivo + asignaciones/aportes − retiros; no se suma al patrimonio si ya está dentro de una cuenta. Inversión distingue aporte, retiro, rendimiento realizado y valoración estimada fechada. La valoración no incrementa el efectivo disponible; el patrimonio cuenta cada activo una vez.
10. Recurrentes y estimaciones generan previsiones/recordatorios por defecto. Confirmar el pago crea el movimiento. Si se habilita registro automático local, usar clave única por ocurrencia y permitir reconciliar con comprobantes sin duplicar. Recordatorio de compra (jabón), cobro fijo (datos) y estimación mensual (transporte) son tipos distintos. Reprogramar tras reinicio y explicar permisos de notificación denegados.
11. Comprobante importado genera borrador: monto, moneda, fecha, referencia, origen/destino y tipo sugerido. Tipo «por confirmar» si no hay evidencia suficiente, y posible transferencia entre cuentas propias. OCR no valida autenticidad ni confirma el éxito bancario. Estado pendiente/fallido de un comprobante no altera saldos como operación confirmada.
12. Detectar duplicados mediante referencia, huella del archivo y coincidencia cuenta/monto/fecha, con advertencia revisable. Copiar adjuntos al almacenamiento interno mientras la URI sea legible; rutas relativas portables. No guardar únicamente URI externas temporales. Conservar borradores al recrear la pantalla y no registrar dos veces al repetir Guardar.

## Navegación y pantallas

Money mantiene la entrada existente de la navegación principal. Dentro, modos independientes:

- Densidad: Resumen simple / Panel detallado.
- Organización: Pestañas / Secciones desplazables.
- Privacidad: ocultar importes con un toque; preferencia persistente, también aplicada a widgets e inicio. TalkBack no debe anunciar importes ocultos.

Resumen, Movimientos, Presupuestos y Reportes son destinos principales. Cuentas, Deudas, Metas e Inversiones se abren desde accesos visibles; no crear ocho pestañas estrechas. Al cambiar el modo se conserva espacio, periodo y filtros. Ajustes propios de Money; integración pequeña en ajustes globales sin modificar preferencias de otras funciones.

| Pantalla | Contenido y acción primaria |
| --- | --- |
| Resumen | Selector de espacio y periodo, saldo actual, ingresos/gastos, neto, cuentas/bolsillos. Registrar gasto e importar comprobante. En detallado: gráfico, próximos pagos y alertas. |
| Editor | Hoja expandible: monto + teclado numérico, ingreso/gasto/transferencia, categorías con iconos y cuenta. Fecha, nota, etiquetas, adjuntos y recurrencia en «Más detalles». |
| Movimientos | Agrupación diaria con subtotal, cuenta, categoría, importe y nota breve. Búsqueda y filtros combinables, estados claros de «sin resultados». Deslizar con alternativa visible en menú y deshacer por Snackbar. |
| Revisión de comprobante | Miniatura, campos detectados editables, campos pendientes destacados, cuenta/espacio, tipo por confirmar cuando aplique y posible duplicado. Guardar una sola vez. |
| Presupuestos | Tarjetas por categoría y periodo, barra, consumo/restante, umbral y alertas. Crear/editar, escoger alcance. |
| Reportes | Dona por categoría, barras mensuales, tendencia y flujo neto. Leyendas, tabla/resumen accesible y exportación. Gráficos con Canvas de Compose. |
| Cuentas | Tarjetas con tipo, moneda, saldo y bolsillos; crear/editar/archivar y transferir. Historial conserva cuentas archivadas. |
| Deudas | Debo/Me deben, persona, moneda, vencimiento, abonos y saldo. Registrar abono vinculado a cuenta. |
| Metas | Monto objetivo, disponible reservado, progreso y fecha opcional. Aportar/retirar sin doble contabilización. |
| Inversiones | Valoración manual fechada, aportes/retiros y resultado separado del efectivo. |
| Widget | Espacio/cuenta y moneda elegibles, saldo ocultable, acceso a Money y registrar gasto; refresco tras escritura y restauración. |

Objetivo de rapidez: abrir Money → monto → categoría → guardar, con cuenta/fecha recordadas y detalles opcionales. «Tres toques» describe decisiones principales y excluye dígitos del monto; medir gasto habitual en menos de 10 segundos en dispositivo, sin prometerlo para un formulario completo ni OCR ambiguo. Si hay varios bolsillos, recordar el último utilizado de forma visible y corregible.

## Estados y accesibilidad

Cada pantalla especificará Initial/Loading/Content/Empty/Error; editor añade Saving/Saved y Validación; importación añade Reading/NeedsReview/Unreadable/Duplicate. Carga inicial muestra esqueletos; refresco conserva datos visibles. Error incluye acción de reintentar; error al guardar conserva el borrador. Vacío inicial tiene icono/ilustración vectorial y CTA; sin resultados permite limpiar filtros. Snackbar accesible y borrado deshacible, con alternativa persistente si el tiempo de interacción requerido lo aconseja.

Objetivos táctiles ≥48dp; iconos de acción con etiqueta, decorativos sin anuncio; agrupación semántica de movimiento; ingresos/gastos identificados por texto/signo además de color. Fuente ampliada y traducciones extensas no truncarán montos ni botones; permitir filas apiladas. Gráficos con resumen numérico y lista/table accesible. Animación breve y opcional respetando configuración del sistema; importes animados no se anuncian en cada fotograma. Teclado decimal accesible con separador regional y soporte para teclado físico.

Tres temas reutilizan MaterialTheme existente; no añadir un tema paralelo a la app. Componentes financieros en feature/expense; solo componentes genéricos compartibles en core/ui. Verificar acentos claros y oscuros contra onPrimary/surface. Todos los textos de Android y categorías predeterminadas por código estable con recursos EN/ES/ZH; nombres personalizados permanecen como los escribió el usuario. Idioma de UI y moneda son independientes.

## Contrato técnico y evolución

Mantener el nombre físico feature/expense para evitar un renombrado ajeno al objetivo. Separar ViewModels, composables y reglas de dominio. Por pantalla UiState inmutable en StateFlow y eventos explícitos; flujos lifecycle-aware. Reglas y agregaciones fuera de Compose. Consultas filtradas por periodo/cuenta/espacio, índices apropiados, LazyColumn con claves estables y mapeo de categorías por id; paginación cuando lo justifique el volumen.

SavedStateHandle para datos simples del editor y referencias a borradores persistidos; adjuntos y texto OCR en almacenamiento local. Efectos de navegación/Snackbar separados de datos, con guardado idempotente. Operaciones relacionadas mediante transacciones Room, restricciones e integridad referencial con política explícita de archivado/borrado.

Antes del esquema 5: exportar esquema 4 reproducible y probar la ruta 3→4 (la definición de attachments del migrador declara una FK distinta de la entidad actual). Diseñar esquema 5 base extensible para espacios, unidades menores, transferencias, adjuntos y entidades previstas; fases posteriores usarán nuevas migraciones si cambia el esquema, nunca modificar una migración ya distribuida. Sin fallback destructivo. Pruebas de MigrationTestHelper con casos vacíos y poblados, valores decimales, todas las funciones y cuentas COP/USD; comprobar integridad y recuentos tras 4→5 y restauración.

Respaldo completo: snapshot consistente de SQLite, manifest versionado, adjuntos internos con rutas relativas y preferencias leídas mediante sus APIs (no copiar un DataStore activo como garantía de consistencia). Validar ZIP, rutas, manifest y esquema antes de sustituir datos; cerrar y reabrir Room, restauración por etapas y recuperación ante fallo. Incluir hábitos, tareas, ánimo, foco, diario, Money y ajustes. Probar en instalación limpia y no exigir al usuario perder datos para recuperar.

## Entregas posteriores

2: base monetaria/migraciones/cuentas. 3: interfaz y CRUD rápido. 4: comprobantes. 5: presupuestos/recurrentes. 6: deudas/metas. 7: inversiones/reportes/CSV/PDF. 8: widget y respaldo integral.

Cada fase: compilar assembleDebug; pruebas unitarias afectadas y suite de regresión disponible; lint del alcance y comparación de warnings; UI instrumentada para flujos clave si hay dispositivo/emulador. Registrar verificaciones manuales en claro/oscuro/OLED × EN/ES/ZH, TalkBack, fuentes ampliadas, permisos denegados, proceso recreado y sin conexión. No afirmar pruebas pasadas si no se pudieron ejecutar.

## Preservación de trabajo local

Los 11 archivos modificados previamente permanecen intactos en esta fase. No checkout/reset/stash de cambios ajenos, ni commits que los incluyan. Nuevas reglas/modelos/repositorios/ajustes en archivos específicos de Money. Cuando MainActivity o archivos compartidos necesiten integración, aplicar únicamente cambios mínimos y revisar las diferencias preexistentes. Hábitos y pantalla de bloqueo quedan fuera del rediseño.

## Fuentes y validación

- Código auditado: ExpenseScreens.kt, ExpenseDao, entidades/modelos, ExpenseRepository, MainActivity/ShareIntentHandler, temas/componentes, widgets y BackupManager.
- OCR propuesto: https://developers.google.com/ml-kit/vision/text-recognition/v2/android (modelo incluido, reconocimiento local; interpretación propia).
- Resultado de compilación/pruebas y límites del entorno: BASELINE.md.
- Prototipo visual: wireframes.html (ES, tres temas, dos densidades y dos organizaciones; traducciones Android se implementan en sus fases).
