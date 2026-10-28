# Asistente TrackMaster

## Qué está implementado

Ejecutor Windows, motor de reglas, reportes diarios/semanales/mensuales, resumen Gemini opcional, revisión React y comandos aprobados hacia Android. Room migra de 9 a 10 sin borrar registros. Los datos históricos sin metas verificables no se inventan. Ninguna credencial se incluye en React.

## Configuración de servicios ($0)

1. Completa primero `docs/cloud-sync/SETUP.md` para la sincronización Android y el dashboard. Mantén Firebase en Spark, sin cuenta de facturación. Usa Firebase Hosting, no App Hosting. No se requiere Functions ni Storage.
2. En Firebase Authentication habilita Email/Password y crea una cuenta **diferente** para el ejecutor. La cuenta Google propietaria sigue siendo quien aprueba.
3. En la consola Firestore crea `privateCloud/agent`: `{ "uid": "UID_DEL_AGENTE", "ownerUid": "UID_PROPIETARIO" }`. Es configuración administrativa; las reglas impiden consultarla desde clientes.
4. Publica reglas e índices desde la raíz: `web/node_modules/.bin/firebase deploy --only firestore:rules,firestore:indexes --project TU_PROYECTO`. No se despliegan Functions.
5. Desde `web`, ejecuta `npm ci`, `npm run build`, `npm test` y `npm run test:rules`. Para publicar el dashboard usa el CLI Firebase con `--only hosting` desde la raíz.
6. Compila e instala Android actualizado. Abre TrackMaster y sincroniza. La migración vuelve a publicar hábitos y métricas del diario sin texto para inicializar el seguimiento del agente.

## Ejecutar en Windows

Requiere Node.js 24 o posterior. Todos los comandos siguientes se ejecutan desde `web`.

1. Copia `agent/config.example.json` a `agent/config.json`; completa Firebase, ownerUid, agentUid y una ruta absoluta a secrets.dpapi bajo tu perfil de Windows. La configuración Firebase es pública; contraseñas y claves de IA quedan fuera de ese archivo.
2. Ejecuta `powershell -NoProfile -File agent/setup-windows.ps1`. Introduce la cuenta del ejecutor y opcionalmente una clave de Gemini de un proyecto **sin facturación habilitada**. Enter genera únicamente resúmenes locales.
3. Ejecuta `npm run agent -- health`. La salida confirma conexión y frescura; no imprime credenciales.
4. Abre Asistente personal en React y activa los hábitos de tiempo que deseas adaptar. Comienza a registrar metas verificables desde esa fecha.
5. Ejecuta `npm run agent -- run-due`. También puedes regenerar: `npm run agent -- generate --cadence weekly --period 2026-10-05`.
6. Ejecuta `powershell -NoProfile -File agent/register-windows.ps1`. Programa 08:00 diarias, domingo 20:00 e inicio de sesión. Configura Windows en zona Bogotá: los disparadores son del sistema, y el cálculo de períodos usa America/Bogota.

La tarea se ejecuta bajo tu usuario de Windows; DPAPI impide trasladar los secretos a otro usuario. No se instala antes de configurar credenciales. PC apagado o sesión cerrada: recupera reportes al iniciar sesión. El proceso termina en 15 minutos y evita ejecuciones duplicadas.

## Decisiones y consistencia

- Domingo: propuestas basadas en lunes–sábado; domingo continúa abierto. Lunes: reporte final añade los registros dominicales, manteniendo las propuestas originales.
- Un día se considera cubierto solo si terminó antes de la última sincronización completa. Tener una sincronización de menos de 24 horas no basta para declarar fallado un día que aún no se subió completo.
- Reducir 5 minutos si cumplimiento <60%; aumentar 5 después de dos semanas >=90% con misma meta/revisión. Rango de nuevas metas: 5–60 minutos. Un hábito con pocos días o cobertura desconocida no recibe ajustes.
- React escribe propuesta aprobada, decisión inmutable y comando en una transacción. Las reglas comprueban revisión y rango. Android comprueba además su cola local y duración; una modificación concurrente produce conflicto.
- La aprobación aparece como pendiente hasta que Android la aplica. Las revisiones de comandos ocurren cada cinco minutos con la app abierta. Llegada tardía: siguiente día completo. Android conserva recibos para no repetir cambios.
- Después de cambiar o aplicar una meta, Android actualiza la base de seguimiento al sincronizar. No se usa una meta nueva como si hubiera regido días anteriores.
- Las tareas sin plannedDate nunca se consideran falladas. En Android, expande una tarea para guardar o quitar su fecha objetivo.

## Privacidad y costos

El ejecutor no puede leer journal_entries, attachments, money_receipts ni mood_entries. Solo cuenta journal_metrics. La IA recibe aliases, cumplimiento, tiempo y totales por moneda. No recibe nombres, títulos, notas, movimientos detallados ni textos. Gemini gratuito tiene condiciones de tratamiento de datos propias: https://ai.google.dev/gemini-api/terms.

Hasta seis solicitudes/día, dos intentos/reporte; fallback local ante errores. Tope local de unas 1.800 lecturas por ejecución; consultas limitadas que fallan en vez de producir reportes truncados. Es un control operativo, no una medición completa de facturación Firestore. Los límites del proyecto se supervisan también en Firebase. No habilites Blaze ni proveedor de pago para aumentar disponibilidad.

## Verificación y operación

`npm run build`; `npm test`; `npm run test:rules`; `npx tsc -p tsconfig.agent.json`; Gradle `:core:database:testDebugUnitTest :core:data:testDebugUnitTest :app:assembleDebug`.

Si el puerto 8085 está ocupado, usa el emulador aislado desde PowerShell en `web`:

```powershell
$env:TRACKMASTER_TEST_PORT = '8285'
.\node_modules\.bin\firebase.cmd emulators:exec --config ../firebase.assistant-test.json --project demo-trackmaster-assistant --only firestore "npm.cmd exec -- vitest run --config vitest.rules.config.ts"
```

`agentState/current` contiene estado, bloqueo, última ejecución y contadores. `agent-state/budget.json` cuenta las solicitudes diarias incluso si fallan. No se guardan prompts privados en logs. La exportación de desarrollo omite identidad, secretos y datos privados.

El piloto de dos semanas y la comprobación real de cobros se hacen tras conectar tus cuentas. Ollama, Groq y GitHub Actions se mantienen como ampliaciones; el sistema funciona sin ellos.
