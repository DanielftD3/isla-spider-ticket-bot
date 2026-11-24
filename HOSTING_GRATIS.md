# Alojamiento gratuito sin tarjeta

Una alternativa para probar este bot es [Bot-Hosting.net](https://bot-hosting.net/discord-bot-hosting). Su documentación anuncia Java/JDA y reinicio tras fallos. A 23 de noviembre de 2026, su plan Free indica:

- $0 al mes, sin tarjeta.
- 256 MB de RAM y 512 MB de disco.
- Un despliegue.
- Renovación manual cada 4 días.

Fuentes oficiales:
- https://bot-hosting.net/pricing
- https://bot-hosting.net/docs/guides/create-a-deployment

No es un servicio con disponibilidad garantizada. Si no renuevas o superas los recursos, el bot puede quedar detenido. 256 MB son ajustados para Java: el perfil incluido reduce el heap, pero el consumo total también incluye memoria nativa, hilos y bibliotecas. Hay que observar el bot conectado y con la carga real del servidor.

## Pasos

1. Crea una cuenta y selecciona el plan Free; no hace falta elegir un plan de pago.
2. Crea un despliegue de tipo Application y elige Java 21. Si el panel no ofrece Java 21, este JAR no puede ejecutarse con versiones anteriores.
3. Descarga el artefacto **ark-dinosauria-hosting** de un Build aprobado en [GitHub Actions](https://github.com/DanielftD3/isla-spider-ticket-bot/actions). Extrae el ZIP y sube su contenido desde el gestor de archivos del alojamiento. No intentes compilar Maven dentro de los 256 MB.
4. Configura las variables de Discord del archivo `.env.example` en Environment; marca `DISCORD_TOKEN` como secreto. También puedes crear un `.env` privado allí. No lo subas a GitHub ni lo compartas.
   Para las categorías, canales y casos privados de apelación, sigue [CONFIGURACION_DISCORD.md](CONFIGURACION_DISCORD.md). `APPEAL_CATEGORY_ID` permite separar las apelaciones de los tickets normales.
5. Para almacenamiento utiliza:
   - `DB_URL=jdbc:sqlite:data/ark-dinosauria.db`
   - `TRANSCRIPT_DIR=data/transcripts`
   No necesitas usuario ni contraseña de base de datos.
6. Si el panel permite definir un comando de inicio, utiliza:

```sh
sh scripts/start-low-memory.sh
```

Si el panel pide directamente el JAR y los argumentos de Java, selecciona `target/isla-spider-ticket-bot.jar` y usa:

```text
-Xms32m -Xmx128m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -Xss512k
```

7. Inicia y revisa la consola. Cuando confirme la conexión, utiliza `/setup` en Discord.
8. Revisa el consumo de RAM y de disco. Renueva desde el panel cada 4 días. No automatices la renovación ni intentes eludir las condiciones del plan.

Los nombres y opciones exactos del panel pueden cambiar: sigue el apartado Startup/Runtime que esté disponible. Este repositorio no crea cuentas ni contrata alojamiento.

## Conserva tus datos

Mantén la carpeta `data` entre despliegues. Al actualizar, reemplaza el JAR y los scripts, no esa carpeta. Evita tener dos instancias del mismo bot ejecutándose al mismo tiempo.

Antes de mover o reinstalar el bot, detenlo y descarga una copia completa de `data` y de tu configuración privada. Las transcripciones van ocupando espacio y el plan gratuito tiene un límite de disco.

Si antes usabas PostgreSQL, su historial no se importa automáticamente al archivo SQLite nuevo. Conserva el PostgreSQL anterior y sus copias hasta decidir cómo trasladarlo.

## Alternativa con más control

El mismo proyecto incluye Docker Compose para una máquina Linux con disco persistente: consulta [DEPLOY.md](DEPLOY.md). Oracle Always Free es una posibilidad, pero pide tarjeta para verificar la cuenta y está sujeto a capacidad y políticas de recuperación de recursos.
