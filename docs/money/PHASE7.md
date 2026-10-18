# Money — Fase 7: inversiones, reportes y exportación

Estado al 18 de octubre de 2026: implementación, validación integrada y exportación en Android aprobadas. Esta fase se guarda en un commit local a petición del usuario; todavía no se publica en el remoto. La mejora completa sigue pendiente de la fase 8 y de las comprobaciones manuales indicadas abajo.

## Inversiones locales

Posiciones independientes por moneda COP/USD y espacio personal, negocio, ahorros o Work & Travel. Una inversión existente se registra mediante costo original, valor estimado y fecha, sin crear dinero en cuentas. No debe incluirse el mismo activo también como saldo inicial de una cuenta. Una inversión nueva empieza en cero y recibe aportes desde una cuenta o bolsillo real.

Los aportes reducen el efectivo e incrementan el costo y valor de la posición. Los retiros reciben efectivo y registran por separado el costo original de la parte vendida: la diferencia constituye ganancia o pérdida realizada. Un rendimiento cobrado aumenta el efectivo como ingreso, sin incrementar de nuevo el activo. Las valoraciones fechadas modifican únicamente la estimación del activo. El capital de inversión se excluye de presupuestos y del ingreso/gasto cotidiano.

Se muestran costo pendiente, valor estimado, rendimiento realizado, ganancia/pérdida no realizada y fecha de la última valoración manual. Los aportes y retiros posteriores ajustan aquella estimación; no son cotizaciones de mercado. Las operaciones se ordenan por fecha y secuencia persistida. Se rechazan retiros que recuperen más costo del existente, incluso al introducir operaciones anteriores.

Los datos iniciales pueden corregirse: costo, valoración y fecha se revalidan contra todo el historial. La corrección no crea ni elimina efectivo y conserva las valoraciones posteriores. Se rechazan cambios que dejen sin respaldo un costo ya recuperado, fechas posteriores a una operación existente o una moneda diferente.

Los movimientos ya registrados, incluidos comprobantes importados, pueden vincularse explícitamente sin duplicar efectivo. Deben corresponder a moneda, cuenta/bolsillo, espacio, monto y dirección; no pueden pertenecer a una deuda, plan u otra inversión. Deshacer una operación nueva revierte el movimiento; deshacer una vinculación conserva el original y su adjunto. La reversión revalida operaciones posteriores y fondos reservados. El historial general dirige estos movimientos a Inversiones para mantener coherentes capital y efectivo.

La actualización de posición, evento, clasificación y efectivo es atómica en Room. Las solicitudes estables impiden duplicados; la versión evita sobrescribir una posición modificada. Archivar exige costo y valor en cero. No hay precios en línea, conversión automática de monedas ni recomendaciones de inversión.

## Reportes

Los totales cotidianos, categorías y barras/línea del periodo se recalculan con los filtros de cuenta, categoría, tipo, búsqueda, moneda, espacio y fechas. Las transferencias y el capital de préstamos/inversiones se distinguen del flujo cotidiano. La serie mensual usa únicamente los días incluidos en el rango seleccionado, incluidos meses parciales.

Los gráficos siguen usando Canvas de Compose, colores del tema y tablas/leyendas accesibles. El modo privacidad oculta importes y gráficos y deshabilita exportación. La escritura de un movimiento no recalcula reportes; los cambios de filtros y datos sí actualizan sus proyecciones.

Sin filtros adicionales, el reporte muestra una situación actual de efectivo más inversiones estimadas, con fecha y una aclaración: los bolsillos ya forman parte del efectivo y ese subtotal no incluye deudas ni cuentas por cobrar. No se presenta como patrimonio neto ni se suman COP y USD.

## Exportación

CSV UTF-8 con esquema estable: fecha ISO, moneda explícita, unidades menores exactas, importe decimal sin separadores regionales, dirección, clasificación, cuenta, bolsillo, categoría, espacio y nota. Comillas, comas y saltos de línea se conservan; los campos de texto que podrían interpretarse como fórmulas se escapan. Las transferencias se exportan por sus movimientos reales dentro del ámbito elegido, incluida la pierna correcta al filtrar otra moneda/cuenta.

PDF con texto seleccionable, formato regional de moneda/fecha, resumen cotidiano, categorías, serie mensual y detalle de los movimientos seleccionados. Usa PdfDocument nativo, fuentes de Android y paginación, sin dependencias nuevas. El límite por PDF es de 10.000 movimientos; la interfaz explica que puede reducirse el rango o utilizarse CSV.

Se exporta una captura inmutable de los filtros al pulsar la acción; cambiar de moneda durante la generación no cambia silenciosamente su contenido. La generación ocurre fuera del hilo principal. El archivo temporal se publica solo al terminar, se guarda en caché privada y se comparte mediante FileProvider con permiso temporal de lectura. Los archivos de exportación antiguos se limpian después de 24 horas al generar uno nuevo. La exportación no incluye archivos de comprobantes; su respaldo integral corresponde a la fase 8.

## Arquitectura y datos

UDF con StateFlow, UiState inmutable, acciones explícitas, reglas de dominio, gateway de datos/exportación, borradores en SavedStateHandle y previews. Actividad interna protegida por configuración cargada y autenticación compartida con Money. Se reutilizan Material 3, los componentes de Money y los tres temas. Recursos nuevos en inglés, español y chino.

Room 11→12 añade `money_investments`, `money_investment_events` y dos campos de clasificación en transacciones. Conserva datos y esquemas anteriores. La versión 12 se genera con KSP; las pruebas validan 11→12 con deudas/metas y la cadena completa 4→12 con importes originales y adjuntos.

## Validación

Compilación integrada aprobada: 323 pruebas JVM sin fallos (dominio 73, base de datos 21, datos 107, componentes 3, Money 113 y app 6). Lint de datos y Money sin errores y sin advertencias nuevas; se conservan las 13 y 1 advertencias previas respectivamente. Se generaron 53 capturas de validación: 26 de interfaz y 27 páginas del diseño del reporte, incluyendo los tres idiomas, los tres temas y casos con fuente al 150 %.

Robolectric 4.14.1 no implementa la serialización de PdfDocument en este proyecto. El dibujo paginado se valida sobre Canvas/Bitmap nativo con los mismos textos, medidas y posiciones. La serialización real se verificó además con una prueba instrumentada en un teléfono Android 14 (API 34): tres PDF de nueve páginas, lectura mediante URI privada, renderizado nativo de las 27 páginas y CSV legible. La prueba utiliza datos sintéticos en el paquete independiente `com.trackermaster.money.validation.phase7`, deshabilita sus pantallas financieras y elimina ese paquete al terminar. La instalación de Trackermaster permanece intacta y no se accede a su base de datos.

FileProvider se verificó en Android: apertura de PDF, CSV y comprobante sintético, y rechazo de archivos ajenos a las rutas autorizadas. Las pruebas JVM verifican además el Intent de un único archivo con permiso de lectura, sin escritura ni URI `file://`. El test nativo cubre la diferencia entre rutas de Android y del host Windows. Se reutilizan la autoridad y las rutas de comprobantes con una subclase propia, como recomienda la [implementación de AndroidX](https://raw.githubusercontent.com/androidx/androidx/androidx-main/core/core/src/main/java/androidx/core/content/FileProvider.java). El selector de compartir y la recepción por otra app siguen pendientes de revisión manual.

Se revisaron visualmente páginas renderizadas por Android en los tres idiomas y se extrajo el texto de las 27 páginas: aparecen los 41 movimientos sintéticos y los totales, con texto seleccionable y contenido dentro de los márgenes. Las fuentes chinas de Android pueden mapear glifos compartidos a radicales Unicode compatibles; la comparación textual aplica normalización NFKC. El CSV conserva el texto UTF-8 original. Evidencia: `phase7-native-results.json` y `phase7-native-pdf-review.json`.

Las fases 5 y 6 se publicaron en `970573b`, con corrección documental en `5636684`. Esta fase 7 se guarda en un commit local; su publicación remota queda pendiente. Continúan pendientes la fase 8 y las verificaciones manuales de comprobantes reales de Nequi, biometría, TalkBack, teclado, compartir entre apps, avisos bajo Doze/reinicio, actualización con datos existentes y registro menor de diez segundos. El fallo previo de biometría en MainActivity permanece documentado en PHASE4.md; esa pantalla contiene cambios independientes del usuario y no se modificó aquí.

Se conservan los cambios independientes de hábitos, tareas, ajustes, bloqueo, sincronización, modelos compartidos y respaldo. La validación utiliza la copia aislada existente para reutilizar cachés; sus fuentes permanecen congeladas durante cada ejecución.

El alcance es de 55 archivos fuente: 27 modificados y 28 nuevos. Las 264 fuentes compiladas se comparan por SHA-256 con el workspace; 209 fuentes existentes ajenas a esta fase permanecen idénticas. En los archivos compartidos de entidades, base de datos, inyección y catálogo se verifica además que, al retirar las adiciones de esta fase, su contenido coincide con el original.

- `core/domain/.../money`: modelos y reglas de inversión, clasificación del capital y proyecciones/exportación de reportes.
- `core/database`: dos tablas, DAO transaccional, campos de transacciones, migración 11→12, esquema generado y pruebas de conservación.
- `core/data/.../money`: repositorio de inversiones, efectivo atómico, vinculación/reversión, proyecciones y protección de movimientos asociados.
- `feature/expense`: actividad, UiState, eventos, ViewModel, pantallas, previews, enlaces desde resumen/historial, exportador nativo y proveedor privado compartido con comprobantes.
- `feature/expense/src/main/res`: 66 recursos nuevos por idioma y ruta privada de exportación.
- Pruebas de dominio, datos, base de datos, ViewModel, UI y exportación; APK instrumental separado mediante `src/androidTest`, configuración del módulo y dependencia de test en el catálogo. No añade dependencias de exportación al producto.

El inventario final de fuentes se registra en `phase7-files.json`; la evidencia de pruebas y preservación, en `phase7-test-results.json`. Las capturas y los registros de ejecución son datos de validación locales.

APK debug: [descargar](artifacts/trackermaster-money-phase7-debug.apk). Firma v2 verificada y certificado igual al de fase 6. SHA-256: `27370a7e2eeb617c65bfc99f2c4d0934c53b5361ec427c3d9e48bd01f84f2012`.
