# DroidSCP — WinSCP para Android

Cliente de archivos remoto: **SFTP, SCP, FTP y FTPS**, con cola de transferencias,
terminal SSH, editor remoto, favoritos y gestor de conexiones.

## Cómo compilar (gratis, sin instalar nada)

1. Crea un repositorio nuevo en GitHub (puede ser privado).
2. Sube **todo el contenido de esta carpeta** (incluida la carpeta oculta `.github`).
3. Pestaña **Actions** → espera a que termine "Build APK" (4-6 min). Si no arranca solo,
   pulsa "Run workflow".
4. Descarga el artefacto **DroidSCP-debug-apk**, descomprime e instala el `.apk`.

## Funciones

**Conexiones**
- SFTP, SCP, FTP y FTPS · contraseña o clave privada con passphrase
- Ruta inicial, modo pasivo FTP, duplicar y editar conexiones

**Explorador**
- Pestañas Remoto / Local, navegación, "Ir a ruta…"
- Búsqueda dentro de la carpeta
- Orden por nombre, tamaño, fecha o tipo (asc/desc)
- Mostrar u ocultar archivos ocultos
- Seleccionar todo / ninguno · Propiedades (tamaño, permisos, UID/GID, fecha)
- Favoritos de rutas remotas por conexión

**Operaciones**
- Subir y descargar con **cola y barra de progreso por archivo** (carpetas recursivas)
- Reintentar los fallidos y limpiar los terminados
- Copiar, cortar y pegar (remoto y local)
- Crear carpeta, crear archivo vacío, renombrar, eliminar recursivo
- chmod con permisos en octal

**Novedades v3**
- Notificación de progreso mientras se transfiere, con aviso al terminar
- **Sincronizar carpetas** local ↔ remoto (solo nuevos/modificados, o modo espejo)
- **Túneles SSH** (port forwarding local: 127.0.0.1:puerto → host:puerto del servidor)
- **Comprimir y descomprimir** en el servidor (tar.gz, tar, zip, bz2, xz)
- Verificación de **huella del servidor** guardada por conexión, con aviso si cambia
- Abrir archivos locales con otra app del móvil

**Novedades v4**
- Transferencias **en paralelo** (1 a 4 simultáneas, configurable) en SFTP
- **Reanudar descargas** interrumpidas en vez de empezar de cero
- Editor con **buscar y reemplazar** y contador de líneas/caracteres
- **Bloqueo con huella o PIN** al abrir la app
- Pantalla de **Ajustes** y de **Acerca de** con las licencias de terceros

**Extras**
- Editor de texto remoto (abrir, editar, guardar en el servidor)
- Terminal SSH con historial de comandos y limpiar consola
- Tema claro/oscuro coral y crema

## Notas

- La primera vez pide permiso de acceso a todos los archivos (pestaña "Local").
- Clave privada: copia el archivo al móvil e indica la ruta completa,
  ej. `/storage/emulated/0/Download/id_rsa`.
- Copiar en FTP se hace descargando y volviendo a subir (el protocolo no tiene copia nativa).
- La huella del servidor se guarda en la primera conexión. Si cambia, la app avisa y no conecta; usa "Olvidar huella del servidor" si el cambio es legítimo.
- Comprimir/descomprimir y los túneles solo funcionan por SSH/SFTP/SCP.


## Licencia

DroidSCP se publica bajo **licencia MIT** — ver el archivo [`LICENSE`](LICENSE).
Las librerías de terceros y sus licencias están listadas en [`NOTICE.md`](NOTICE.md)
y también dentro de la app, en *Acerca de*.

© 2026 kVe — Xito Development
