# ARK Dinosauria: tickets y casos de apelación

## 1. Crea estas categorías y canales en orden

| Categoría | Canal de texto | Función |
|---|---|---|
| 🎫 SOPORTE | `crear-ticket` | Panel público. Los jugadores pueden verlo; solo el bot y el equipo publican mensajes. |
| 📂 TICKETS ABIERTOS | Ninguno manualmente | El bot crea `bug-0001`, `ayuda-0002` o `reporte-0003`. |
| ⚖️ APELACIONES | Ninguno manualmente | El bot crea `apelacion-0004`. |
| 🔒 REGISTROS | `logs-tickets` | Aperturas y cierres de casos. |
| 🔒 REGISTROS | `pruebas-tickets` | Copias de archivos y enlaces aportados en los tickets. |
| 🔒 REGISTROS | `transcripciones` | Archivos HTML con los datos y la conversación de cada caso. |
| 🔒 REGISTROS | `logs-apelaciones` | Aperturas y resoluciones de apelaciones con número de caso. |
| 🔒 REGISTROS | `valoraciones` | Encuestas de atención y solución de tickets de soporte. |
| 🔒 REGISTROS | `panel-moderacion` | Panel privado para gestionar sanciones. |

Oculta REGISTROS para `@everyone` y permite el acceso solamente a Moderador, administradores y al bot: sus archivos pueden incluir reportes y apelaciones privados. Oculta también TICKETS ABIERTOS y APELACIONES para `@everyone`. El bot concederá acceso al solicitante y al equipo responsable en cada canal, además del acceso de los administradores de Discord. No sincronices manualmente todos los tickets con los permisos de la categoría: cada caso tiene sus propios participantes.

Crea o reutiliza `Staff` y `Moderador`. Staff atiende bugs y ayuda. Moderador atiende reportes y apelaciones. Los rangos superiores deben tener también el rol configurado o el permiso Administrador; el nombre o la posición del rango por sí solos no autorizan los botones. El rol del bot debe estar por encima de los usuarios a los que vaya a moderar.

## 2. Configura los identificadores

En Discord activa Ajustes de usuario → Avanzado → Modo desarrollador. Haz clic derecho sobre cada elemento para copiar su ID. Configura estas variables en Entorno del alojamiento o en un `.env` privado en la raíz. No compartas DISCORD_TOKEN ni lo subas a GitHub.

| Variable | Valor |
|---|---|
| `DISCORD_TOKEN` | Token de Developer Portal → Bot. |
| `GUILD_ID` | ID del servidor ARK DINOSAURIA; no es el ID de la aplicación. |
| `TICKET_CATEGORY_ID` | ID de TICKETS ABIERTOS. |
| `APPEAL_CATEGORY_ID` | ID de APELACIONES. |
| `TICKET_LOG_CHANNEL_ID` | ID de `logs-tickets`. |
| `TICKET_IMAGE_CHANNEL_ID` | ID de `pruebas-tickets`. |
| `TICKET_TRANSCRIPT_CHANNEL_ID` | ID de `transcripciones`. |
| `APPEAL_LOG_CHANNEL_ID` | ID de `logs-apelaciones`. |
| `TICKET_RATING_CHANNEL_ID` | ID de `valoraciones`. |
| `STAFF_ROLE_ID` | ID de Staff. |
| `MOD_ROLE_ID` | ID de Moderador. |

`crear-ticket` y `panel-moderacion` no tienen variables de canal: el panel se publica donde ejecutes `/setup` y pulses su botón.

```dotenv
DB_URL=jdbc:sqlite:data/ark-dinosauria.db
TRANSCRIPT_DIR=data/transcripts
AUTO_APPLY_STRIKE_SANCTIONS=false
```

Si omites APPEAL_CATEGORY_ID se utiliza la categoría normal de tickets. Si omites APPEAL_LOG_CHANNEL_ID se utiliza logs-tickets. Para esta organización configura ambos explícitamente; si copias sus líneas comentadas de `.env.example`, quita el `#` y reemplaza el valor.

## 3. Publica los paneles

1. En Developer Portal → Bot activa Server Members Intent y Message Content Intent. Presence Intent puede quedar desactivado.
2. Instala el bot en el servidor con los ámbitos `bot` y `applications.commands`. Necesita gestionar canales y permisos, enviar mensajes, insertar enlaces, adjuntar archivos y leer historial; la moderación además utiliza Moderar, Expulsar y Banear miembros.
3. Sustituye el JAR antiguo por la versión con casos de apelación. Conserva tu `.env` privado y toda la carpeta `data`. La compilación del repositorio se llama `target/isla-spider-ticket-bot.jar`; el ZIP preparado para el alojamiento puede incluir el mismo JAR con el nombre `ark-dinosauria.jar`. El archivo de entrada debe coincidir con el nombre real subido.
4. Inicia el bot y espera `Bot conectado como ...` en la consola.
5. Como administrador, ejecuta `/setup` en `crear-ticket` y pulsa **Publicar tickets**.
6. Ejecuta `/setup` en `panel-moderacion` y pulsa **Publicar moderación**.

Si otro bot protege contra la creación o eliminación de canales, configura una excepción para esta cuenta del bot antes de probarlo.

## 4. Flujo de cada caso

- El ID permanente es el mismo en el canal, los registros y la transcripción. Reiniciar conserva el historial mientras se mantenga `data`.
- Cada apelación abre un ticket privado para reunir pruebas y conversar con el solicitante. No depende de que tenga abiertos los mensajes directos.
- Moderación pulsa **Aprobar apelación** o **Rechazar apelación** e introduce un motivo. Nadie puede resolver su propia apelación.
- Se guardan transcripción y registros antes de confirmar la decisión. Si falla el archivo, el caso permanece abierto para reintentarlo. Una encuesta o el botón de eliminación de soporte no pueden cerrar una apelación.
- Se conservan decisión, motivo, moderador y fecha. La resolución aparece en el ticket; el solicitante mantiene la lectura y recibe además un mensaje directo si Discord lo permite.
- El canal permanece disponible tras la decisión. Moderación puede pulsar **Archivar canal del caso** y confirmar para eliminar el canal. El expediente y la transcripción se conservan. Este archivo es manual, independientemente del cierre automático configurado para los tickets de soporte.
- Aprobar registra una decisión. No revoca automáticamente un warn de Vortex ni un ban del juego o de Discord: Moderación debe aplicar ese cambio en el sistema correspondiente.

Quien ya está baneado de este servidor de Discord no puede abrir un ticket dentro de él. Para ese ban hace falta un medio externo de contacto o un servidor de apelaciones separado. Un ban del juego no impide usar el ticket si la persona sigue en Discord.

## 5. Prueba antes de anunciarlo

Con una cuenta de jugador distinta de la del moderador, abre una apelación. Comprueba que otro jugador no ve su canal, que Staff sin Moderador no accede, que se pueden adjuntar pruebas, que la resolución aparece aunque el solicitante tenga los mensajes privados cerrados y que el HTML contiene motivo y conversación. Resuelve también una apelación rechazada y comprueba que solo se elimina el canal tras confirmar su archivo. Estas comprobaciones necesitan el bot conectado al servidor real.
