# TrackMaster en PC — configuración sin conectar el teléfono

El código incluye sincronización Android → Firestore y un dashboard React de consulta. Necesitas crear tu propio proyecto Firebase para activarlo. Ningún dato local se sube hasta configurar e iniciar sesión. No se ha creado ni publicado ningún proyecto externo automáticamente.

## 1. Crear Firebase sin facturación

1. Abre https://console.firebase.google.com/ y crea un proyecto. Conserva **Spark**, sin vincular una cuenta de facturación. Analytics no es necesario.
2. En Authentication → Sign-in method, habilita **Google** y elige tu correo de soporte.
3. Crea una base **Cloud Firestore Standard**, con modo de producción y el ID `(default)`, y elige la región antes de crearla. No habilites Cloud Storage, Functions, App Hosting ni SQL Connect.
4. Registra una app Android con paquete `com.trackermaster.app` y una app web.
5. En PowerShell, desde la raíz del repositorio, ejecuta `./gradlew.bat :app:signingReport`. Registra el SHA-1 y SHA-256 de tu certificado en la app Android de Firebase. Si usas una firma de release, registra también sus huellas.
6. Descarga el `google-services.json` actualizado después de habilitar Google. En esta implementación se inicializa Firebase explícitamente: el archivo no se copia al código ni requiere el plugin Google Services.

Referencias: [configuración Android](https://firebase.google.com/docs/android/setup), [autenticación Google](https://firebase.google.com/docs/auth/android/google-signin), [precios y cuotas](https://firebase.google.com/pricing).

## 2. Configurar la web y obtener tu UID

Desde la raíz, copia `web/.env.example` a `web/.env.local`. Rellena los cuatro valores de configuración de la app web que muestra Firebase:

```dotenv
VITE_FIREBASE_API_KEY=tu-api-key-web
VITE_FIREBASE_AUTH_DOMAIN=tu-proyecto.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=tu-proyecto
VITE_FIREBASE_APP_ID=tu-id-app-web
VITE_OWNER_UID=
```

La configuración web identifica el proyecto; no contiene la clave de escritura Android ni credenciales de administrador.

En Authentication → Settings → Authorized domains, añade `localhost` si no aparece. Luego:

```powershell
cd web
npm.cmd ci
npm.cmd run dev -- --host localhost
```

Abre la dirección que muestra Vite. En desarrollo local, con los cuatro valores configurados y el UID todavía vacío, aparece **Identificar mi cuenta de Google**. Inicia sesión con tu cuenta personal, copia el UID mostrado a `VITE_OWNER_UID` y guarda el archivo. Reinicia Vite si fuera necesario. Esta pantalla no lee ni escribe Firestore y no se ofrece en el build publicado.

## 3. Configurar Android

Copia `sync.local.properties.example` a `sync.local.properties` en la raíz. Los valores del JSON descargado corresponden a:

| Propiedad | Campo de `google-services.json` |
|---|---|
| `api_key` | `client` del paquete correcto → `api_key[0].current_key` |
| `application_id` | `client_info.mobilesdk_app_id` |
| `project_id` | `project_info.project_id` |
| `web_client_id` | `oauth_client` con `client_type: 3` (cliente OAuth **web**, no Android) |
| `owner_uid` | UID obtenido en el paso anterior |
| `writer_key` | Clave aleatoria de 64 caracteres hexadecimales del siguiente paso |

Genera la clave local sin imprimirla en la terminal. Desde la raíz:

```powershell
node -e "require('fs').writeFileSync('writer-key.local.txt', require('crypto').randomBytes(32).toString('hex'))"
```

Abre ese archivo localmente y copia su contenido a `writer_key`. No copies esta clave a variables `VITE_*`, a la web, a capturas ni al repositorio. El APK contiene esta capacidad de escritura: usa este build para tu teléfono personal; no lo distribuyas. La protección distingue al poseedor de la clave, no constituye una certificación del hardware.

## 4. Autorizar tu cuenta y el móvil en Firestore

En la consola Firestore, crea el documento **`privateCloud/owner`** con dos campos de tipo string:

```text
uid        = el mismo UID de owner_uid / VITE_OWNER_UID
writerKey  = la misma clave de writer_key
```

Solo la consola/IAM puede acceder a este documento. Las reglas deniegan su lectura y escritura a todos los clientes, incluso a tu usuario. La web puede leer tus registros, pero necesita la clave exclusiva de Android para escribir. Cada lote Android aporta una prueba nueva de escritura; las pruebas nunca son legibles ni reutilizables por la web.

## 5. Publicar reglas, índices y dashboard

Firebase CLI ya es una dependencia de desarrollo de `web`; no hace falta instalarla globalmente. Desde `web`:

```powershell
npx.cmd firebase login
npm.cmd run build
npx.cmd firebase deploy --config ../firebase.json --project TU_ID_PROYECTO --only "firestore:rules,firestore:indexes,hosting"
```

Espera a que los índices se activen. El comando utiliza **Hosting clásico**, con los archivos de `web/dist`, y el dominio gratuito del proyecto. No selecciones App Hosting ni SSR. Comprueba que el dominio `TU_ID_PROYECTO.web.app`/`firebaseapp.com` está autorizado para Authentication. No necesitas comprar un dominio.

El build web carga Firebase 12.19.0 mediante módulos del CDN oficial de Google; React y la interfaz se alojan en Hosting. Usa un navegador moderno con soporte de módulos e import maps. Tanto Firebase como la web necesitan internet para consultar la nube. La versión está fijada en `web/index.html` y `web/package.json`; si la actualizas, mantén ambos valores iguales.

## 6. Instalar Android y comprobar el flujo

Desde la raíz:

```powershell
./gradlew.bat :app:assembleDebug
```

Instala el APK conservando la misma firma de la instalación anterior. En TrackMaster → Ajustes → **TrackMaster en PC**, pulsa **Conectar con Google** e inicia sesión con tu cuenta personal.

- La migración 8 → 9 conserva los registros y añade una cola de sincronización. El primer envío publica todos los módulos registrados, incluidos planes financieros, sin subir archivos.
- Mientras MainActivity está visible, la app detecta pendientes y los envía al abrir, editar o recuperar conexión. Los widgets y otros escritores locales también dejan sus cambios en la cola; con la app cerrada se enviarán en la siguiente apertura.
- Sin conexión o con cuota agotada, los pendientes permanecen en SQLite. Hay reintentos con espera creciente; **Reintentar** permite solicitar otro intento. No se programa sincronización periódica en segundo plano.
- Abre la web y compara datos. Apaga el móvil después de completar el envío: la web debe seguir consultando la nube.
- Las eliminaciones se guardan como marcas versionadas y se excluyen de las consultas. Así un envío antiguo no puede resucitar un registro eliminado.
- La web muestra 25 registros por página, filtros de fechas y detalles. Las colecciones auxiliares de nombres se limitan a 200 registros; los detalles conservan los IDs si un nombre no está cargado.
- La web sanea el HTML del diario y no carga imágenes ni archivos externos incluidos en el texto. Fotos y recibos binarios permanecen solo en Android; se publican sus metadatos sin rutas locales.

## 7. Validación local

```powershell
# Raíz:
./gradlew.bat :core:database:testDebugUnitTest :core:data:testDebugUnitTest :app:assembleDebug
# web:
npm.cmd test
npm.cmd run build
npm.cmd run test:rules
```

Las pruebas de reglas usan exclusivamente el proyecto ficticio `demo-trackmaster` y el emulador en `127.0.0.1:8085`; no escriben en tu proyecto real. El emulador requiere Java y descarga su runtime la primera vez.

Prueba manualmente también: cierre sin conexión, nueva apertura, cambio de registro durante envío, cuenta equivocada, error de permisos y recuperación después de agotar cuota. La autenticación Google real y el flujo entre tu teléfono y Hosting requieren tu configuración; no pueden certificarse antes de crear el proyecto.

## Límites y mantenimiento

Mantén Spark para impedir facturación por uso. Al superar cuotas, Firebase puede bloquear temporalmente el servicio. Los listeners y reintentos generan operaciones; revisa Usage tras las primeras sesiones. Firestore Spark ofrece actualmente 1 GiB, 50.000 lecturas/día y 20.000 escrituras/día; Hosting 10 GB y 360 MB/día. [Fuente oficial](https://firebase.google.com/pricing).

La instalación está diseñada para un usuario y un teléfono. No borres los datos Android ni conectes un segundo teléfono con la misma clave esperando una sincronización bidireccional. La web es de consulta.

Después de importar un respaldo, **cierra completamente y vuelve a abrir la app**, como indica el importador. Se publicará un dataset nuevo, por lo que registros ausentes del respaldo no seguirán apareciendo. La web conserva el dataset anterior hasta que termine el envío nuevo. Una reinstalación también crea un dataset nuevo. Los datasets anteriores, marcas de eliminación y pruebas privadas ocupan almacenamiento; si se acumulan, elimínalos desde la consola cuando no los necesites, conservando el dataset indicado en `users/TU_UID/meta/sync`.

Para revocar una clave filtrada, cambia `writerKey` en la consola y `writer_key` local, recompila y reinstala Android. Ninguna credencial administrativa debe entrar en la web.
