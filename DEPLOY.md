# ARK Dinosauria: despliegue sin PostgreSQL

Esta versión puede guardar tickets, mensajes, adjuntos, valoraciones y sanciones en SQLite, un archivo local. No hay que instalar PostgreSQL ni contratar una base de datos.

Para una opción sin tarjeta, consulta primero [HOSTING_GRATIS.md](HOSTING_GRATIS.md). La opción Docker siguiente es independiente del proveedor.

## Opción preparada: una máquina Linux con Docker

Sirve para un VPS, una máquina virtual gratuita compatible o un equipo propio que permanezca encendido. Docker Compose conserva los datos en un volumen y reinicia el bot si el proceso falla o se reinicia la máquina. No depende de mantener abierta una ventana ni una sesión SSH.

### 1. Consigue el equipo donde se ejecutará

Una opción es [Oracle Cloud Always Free](https://www.oracle.com/cloud/free/). Verifica las condiciones actuales al crear la cuenta y selecciona únicamente recursos marcados como Always Free-eligible en tu región principal. La capacidad disponible puede variar.

Oracle solicita una tarjeta para verificar la identidad. Los recursos Always Free son distintos del crédito de prueba de 30 días: el bot debe quedar en recursos elegibles gratuitos. El servicio gratuito no tiene SLA y Oracle puede recuperar instancias consideradas inactivas; no se garantiza disponibilidad permanente.

Referencias oficiales:
- https://www.oracle.com/cloud/free/faq/
- https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm

Utiliza una imagen Ubuntu de 64 bits compatible. Este proyecto usa Java 21 y las imágenes Docker elegidas admiten x86-64 y ARM64. Una máquina pequeña con aproximadamente 1 GB de RAM puede ejecutar el bot de una comunidad pequeña, pero compilarlo necesita memoria adicional y puede tardar. Ajusta recursos después de observar su uso real.

### 2. Instala Docker y Git

Sigue la [instalación oficial de Docker Engine y Compose para Ubuntu](https://docs.docker.com/engine/install/ubuntu/). Instala Git con el gestor de paquetes del sistema. Comprueba:

```bash
docker --version
docker compose version
git --version
```

Usa `sudo docker` si tu usuario todavía no tiene acceso a Docker. Activa el servicio de Docker al iniciar el equipo según la documentación de tu distribución.

### 3. Descarga y configura el bot

```bash
git clone https://github.com/DanielftD3/isla-spider-ticket-bot.git
cd isla-spider-ticket-bot
cp .env.example .env
```

Edita `.env` con tu token de Discord y los IDs de tu servidor. El archivo está excluido de Git y del contexto de construcción Docker. No compartas su contenido.

La configuración Discord sigue siendo la misma: permisos, categorías, roles, canales de logs y de valoraciones. Activa los intents que ya utiliza el bot (Server Members y Message Content) en Discord Developer Portal.

Compose utiliza SQLite en `/app/data/ark-dinosauria.db` y transcripciones en `/app/data/transcripts`. No se necesitan `DB_USER` ni `DB_PASSWORD`.

### 4. Arranca

```bash
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 -f bot
```

Cuando el registro confirme la conexión, usa `/setup` en Discord y publica el panel. Puedes cerrar SSH; el contenedor continúa ejecutándose.

El bot inicia conexiones salientes hacia Discord. No requiere abrir un puerto web público. Mantén disponible DNS y salida HTTPS/WebSocket hacia Discord y sus servicios de archivos.

### 5. Actualiza

```bash
git pull --ff-only
docker compose up -d --build
```

El volumen se conserva al reconstruir o recrear el contenedor. Ejecuta una sola instancia del bot con este archivo SQLite. No abras el mismo archivo desde varios hosts o réplicas.

## Datos, copias y PostgreSQL anterior

- El volumen `bot-data` contiene SQLite (incluidos sus archivos WAL/SHM mientras está activo) y las transcripciones.
- Eliminar el contenedor conserva el volumen. Borrar el volumen, por ejemplo con `docker compose down --volumes`, elimina esos datos.
- Cambiar una instalación de PostgreSQL a SQLite NO importa automáticamente el historial. La configuración anterior sigue funcionando si se conserva su URL PostgreSQL y se ejecuta el JAR fuera de este Compose. Este Compose fuerza SQLite.
- Conserva una copia de PostgreSQL antes de cambiar una instalación existente y decide cómo trasladar su historial. No se ejecutan migraciones entre motores ni se borra PostgreSQL.

Para obtener una copia consistente y completa, detén brevemente el bot, copia el directorio y vuelve a iniciarlo:

```bash
docker compose stop bot
docker compose cp bot:/app/data ./copia-bot
docker compose start bot
```

Usa una carpeta de destino nueva para cada copia y guarda también las copias fuera de esa máquina. Si la copia falla, vuelve a iniciar el bot con el último comando. Para restaurar, detén el bot y copia el contenido respaldado a `/app/data`, manteniendo los permisos de escritura del usuario 10001 del contenedor.

## ¿Se puede desplegar directamente en Vercel?

El bot actual usa Java/JDA y mantiene una conexión continua con Discord para recibir mensajes e interacciones. Las Functions de Vercel tienen límites de duración; el plan Hobby tiene un máximo de 300 segundos para sus runtimes documentados. Aunque Vercel ya admite WebSockets, esto no convierte una Function en un proceso Java permanente.

Se puede crear una versión distinta basada en interacciones HTTP de Discord. Eso requiere adaptar la arquitectura y un almacenamiento remoto persistente; la recepción y el archivo de mensajes del bot actual no se trasladan simplemente cambiando una configuración. Por eso esta versión mantiene sus funciones y se prepara para un host continuo.

Referencias oficiales:
- https://vercel.com/docs/functions/limitations
- https://vercel.com/changelog/websocket-support-is-now-generally-available
- https://docs.discord.com/developers/interactions/overview

## Otros planes gratuitos

Render Free puede suspender servicios por inactividad y su disco es efímero. No uses un archivo SQLite allí sin almacenamiento persistente compatible: los datos locales pueden desaparecer en un reinicio o despliegue. No se incluyen pings artificiales ni trucos para evitar los límites del alojamiento.

Referencia: https://render.com/docs/free

Gratis no significa disponibilidad garantizada. Para este diseño se necesita un host continuo con disco persistente; la cuenta y la máquina se crean aparte, y este repositorio no las contrata ni las despliega automáticamente.
