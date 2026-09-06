# Isla Spider Ticket Bot

Bot de Discord en Java para tickets privados, apelaciones por mensaje directo y moderación basada en strikes. Usa JDA 6, PostgreSQL y un archivo `.env`; no necesita una página web para funcionar.

## Funciones incluidas

- Panel con menú para `Bug`, `Ayuda`, `Reporte` y `Apelación`.
- Formularios distintos según el tipo de caso.
- Identificación mediante el nombre dentro del juego.
- Canal privado para el autor y el equipo responsable: Staff en bugs/ayuda y Moderación en reportes.
- Un único botón dentro del ticket: **Enviar encuesta**.
- Registro de mensajes, imágenes, archivos y enlaces en PostgreSQL.
- Log visual de imágenes y archivos en un canal independiente.
- Transcripción HTML al cerrar y copia archivada en su propio canal.
- Valoraciones con administrador, atención, solución y comentario.
- Cierre seguro: el canal solo se elimina después de confirmar logs, valoración, transcripción y PostgreSQL.
- Apelaciones enviadas a un canal privado de administración.
- Resultado de la apelación por mensaje directo.
- Panel de Moderación para añadir, quitar y revisar warns, además de mute, kick y ban.
- Un único comando: `/setup`; el resto funciona con botones, menús y formularios.
- Historial de strikes activos y retiro auditable de strikes.
- Instalación como tarea programada de Windows Server.

## Warn y strike no son lo mismo

| Acción | Suma puntos | Queda registrada | Puede activar sanción |
|---|---:|---:|---:|
| **Advertir sin puntos** | No | Sí | No |
| **Añadir Warn** | Sí | Sí | Sí, si se habilita |
| **Quitar Warn** | Resta el warn activo | Sí, sin borrar el historial | Recalcula el total para el futuro |

Las sanciones automáticas están desactivadas inicialmente con `AUTO_APPLY_STRIKE_SANCTIONS=false`. Cuando la política esté probada, se puede activar. Por defecto, 3 puntos generan mute de 24 horas y 5 puntos generan ban.

## Requisitos

- Windows Server 2019, 2022 o 2025.
- JDK 21.
- Apache Maven 3.9 o superior.
- PostgreSQL 14 o superior.
- Un bot creado en Discord Developer Portal.

## 1. Preparar Discord

1. En Discord Developer Portal crea una aplicación y luego un bot.
2. En **Bot > Privileged Gateway Intents**, activa:
   - `Server Members Intent`.
   - `Message Content Intent`.
3. Invita el bot con los scopes `bot` y `applications.commands`.
4. Asígnale estos permisos:
   - View Channels.
   - Send Messages.
   - Embed Links.
   - Attach Files.
   - Read Message History.
   - Manage Channels.
   - Moderate Members.
   - Kick Members.
   - Ban Members.
5. Coloca el rol del bot por encima de los roles que debe moderar.
6. Activa **Modo desarrollador** en Discord para copiar IDs.

## 2. Preparar PostgreSQL

Abre `database/create_database.sql`, cambia la contraseña y ejecútalo como administrador de PostgreSQL. El bot crea sus tablas automáticamente la primera vez que inicia.

Si el usuario y la base ya existen, no ejecutes ese archivo; solo configura su conexión en `.env`.

## 3. Configurar el bot

Desde PowerShell, dentro de la carpeta del proyecto:

```powershell
Copy-Item .env.example .env
notepad .env
```

Completa todos los IDs:

| Variable | Contenido |
|---|---|
| `GUILD_ID` | ID del servidor de Discord |
| `TICKET_CATEGORY_ID` | Categoría donde se crearán los canales privados |
| `TICKET_LOG_CHANNEL_ID` | Canal que recibe el resumen de los tickets cerrados |
| `TICKET_IMAGE_CHANNEL_ID` | Canal que muestra las imágenes y archivos enviados |
| `TICKET_TRANSCRIPT_CHANNEL_ID` | Canal que conserva las copias HTML de transcripciones |
| `TICKET_RATING_CHANNEL_ID` | Canal que recibe las valoraciones de los tickets |
| `APPEAL_LOG_CHANNEL_ID` | Canal privado donde administración revisa apelaciones |
| `STAFF_ROLE_ID` | Rol que atiende bugs y ayuda |
| `MOD_ROLE_ID` | Rol autorizado para strikes y sanciones |

Nunca publiques ni subas el archivo `.env`. El token permite controlar el bot.

## 4. Compilar y arrancar

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build.ps1
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

Al iniciar, debe aparecer algo similar a:

```text
Bot conectado como ISLA SPIDER BOT en Isla Spider
```

Después ejecuta `/setup`. El bot mostrará un asistente privado con dos botones:

- **Publicar tickets**: publica el centro de soporte y su menú de categorías.
- **Publicar moderación**: publica el panel privado de warns y sanciones.

El canal donde publiques Moderación debe ser visible únicamente para el equipo autorizado.

## 5. Dejarlo funcionando al reiniciar Windows

Abre PowerShell como administrador y ejecuta:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-service.ps1
```

Esto crea la tarea programada `IslaSpiderTicketBot` bajo la cuenta `SYSTEM`.
El instalador fija la ruta exacta de Java, reinicia el proceso si falla y escribe la salida en `logs\bot.log`.

## Paneles y botones

`/setup` es el único comando y solo puede usarlo un administrador. Todas las operaciones diarias se realizan desde componentes de Discord:

| Lugar | Acciones |
|---|---|
| Panel de tickets | Bug, ayuda, reporte y apelación |
| Dentro de un ticket | Enviar encuesta |
| Panel de Moderación | Añadir, quitar o revisar warns; advertir sin puntos; mute, kick y ban |
| Encuesta | Selección del administrador, atención y solución de 1 a 10, comentario y cierre automático |

## Política sugerida para empezar

| Total activo | Acción sugerida |
|---:|---|
| 1 | Advertencia formal |
| 2 | Último aviso y seguimiento |
| 3–4 | Mute de 24 horas |
| 5 o más | Revisión para ban |

Conviene mantener `AUTO_APPLY_STRIKE_SANCTIONS=false` durante las primeras pruebas. Así el bot informa el total, pero un moderador confirma la sanción.

## Estructura principal

```text
src/main/java/com/islaspider/bot/
├── BotApplication.java
├── CommandRegistry.java
├── config/
├── db/
├── listener/
├── model/
└── service/
```

## Solución de problemas

- **No aparecen los comandos:** confirma que se invitó con `applications.commands` y reinicia Discord.
- **No se archivan mensajes:** activa `Message Content Intent` en el portal.
- **No puede crear canales:** dale `Manage Channels` y acceso a la categoría.
- **No puede mutear o banear:** sube el rol del bot por encima del usuario objetivo.
- **Error de PostgreSQL:** verifica `DB_URL`, usuario, contraseña y que el servicio escuche en `localhost:5432`.
- **No llegan mensajes privados:** el usuario puede tener bloqueados los DMs del servidor; el caso queda registrado igualmente.
- **La tarea de Windows se detiene:** revisa `logs\bot.log` y el historial de `IslaSpiderTicketBot` en el Programador de tareas.
