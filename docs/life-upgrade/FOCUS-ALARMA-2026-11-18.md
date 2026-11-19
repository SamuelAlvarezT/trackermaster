# Verificación de alarma Focus · 18 de noviembre de 2026

Dispositivo Samsung SM-A336M, paquete aislado `com.trackermaster.validation.unrecorded20261116`; no se modificaron las sesiones ni los datos de la aplicación principal. La compilación aislada y la prueba de persistencia de `FocusAlarmStore` finalizaron correctamente.

- Se abrió la configuración, se seleccionó un sonido del sistema y el usuario confirmó que escuchó su muestra.
- Se seleccionó un WAV sintético mediante el selector de documentos. Tras guardar, cerrar y reabrir la app aislada, la referencia al archivo seguía guardada.
- Una sesión sintética de un minuto se inició con la pantalla apagada. Al completarse, Android informó una notificación «Session completed» con la acción «Turn off alarm» y un único reproductor activo con uso de alarma. El usuario confirmó que escuchó la alarma. Se detuvo mediante el comando del servicio; el reproductor y la notificación dejaron de estar activos.
- Tras eliminar el archivo sintético se ejecutó otra sesión. El usuario confirmó que escuchó el sonido alternativo y vio el aviso de archivo no disponible.
- Se volvió a crear el archivo sintético y se ejecutó otra sesión. El botón «Turn off alarm» dentro de la app detuvo la reproducción y retiró la notificación. Al cerrar y volver a abrir la app, la alarma no volvió a activarse.
- `uiautomator` no pudo obtener una jerarquía estable del panel mientras sonaba. En una sesión posterior, el usuario pulsó «Turn off alarm» directamente en la notificación y confirmó que lo hizo; después Android informó cero reproductores activos y la alarma quedó registrada como apagada.
- El archivo WAV sintético se retiró de Descargas al finalizar. La copia de prueba queda instalada por separado.

La prueba no demuestra funcionamiento continuo con el proceso detenido a la fuerza ni en todos los modos de ahorro de batería. No se comprobó la selección del sonido con la pantalla bloqueada ni el toque de apagado específicamente desde la pantalla bloqueada. La duración dinámica y otras partes del encargo siguen pendientes.
