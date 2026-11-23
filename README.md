# ARK Dinosauria Ticket Bot

A Java Discord bot for private support tickets, ticket ratings, HTML transcripts, appeals, and strike-based moderation.

> SQLite is the default: no external database server or database credentials are required. Existing PostgreSQL configurations remain supported. See [DEPLOY.md](DEPLOY.md) for deployment with persistent storage.

## Technology

- Java 21
- JDA 6.5
- SQLite (embedded, default) or PostgreSQL (optional)
- HikariCP
- Maven
- JUnit 5

## Project structure

| Path | Responsibility |
|---|---|
| `src/main/java/com/islaspider/bot/BotApplication.java` | Application bootstrap and JDA lifecycle |
| `src/main/java/com/islaspider/bot/CommandRegistry.java` | Registration of the single `/setup` command |
| `src/main/java/com/islaspider/bot/config` | Environment loading and configuration validation |
| `src/main/java/com/islaspider/bot/db` | Database connection, schema initialization, and repositories |
| `src/main/java/com/islaspider/bot/listener` | Discord commands, buttons, menus, modals, and message events |
| `src/main/java/com/islaspider/bot/model` | Ticket and moderation domain records |
| `src/main/java/com/islaspider/bot/service` | Panels, permissions, and transcript generation |
| `src/main/resources/schema.sql` | Application database schema |
| `src/test/java` | Unit and PostgreSQL integration tests |
| `.github/workflows/build.yml` | Windows build and PostgreSQL integration CI |

For a free host without a card, see [HOSTING_GRATIS.md](HOSTING_GRATIS.md). The linked free tier requires manual renewal and its memory limit must be tested with your bot.

## Run without PostgreSQL

1. Copy `.env.example` to `.env` and fill in the Discord token, channel IDs, and role IDs.
2. Leave `DB_URL=jdbc:sqlite:data/ark-dinosauria.db`; `DB_USER` and `DB_PASSWORD` are not needed.
3. Build and run the JAR using the existing PowerShell scripts, or deploy with Docker:

```bash
docker compose up -d --build
docker compose logs --tail=100 -f bot
```

The Compose setup stores SQLite and transcripts in the `bot-data` volume and restarts the bot after a crash or host reboot, provided Docker starts with the host. A continuously running host with persistent disk is required. This repository alone does not provide hosting.

## Development requirements

- JDK 21
- Apache Maven 3.9 or newer
- PostgreSQL 14 or newer only when running integration tests locally

Check the toolchain:

```bash
java -version
mvn -version
```

## Build and test

Run the complete verification suite:

```bash
mvn clean verify
```

Create the runnable shaded JAR:

```bash
mvn clean package
```

The resulting artifact is:

```text
target/isla-spider-ticket-bot.jar
```

## PostgreSQL integration tests

Integration tests are skipped when `TEST_DB_URL` is not defined. To run them locally, provide a disposable PostgreSQL database through these test-only variables:

```text
TEST_DB_URL
TEST_DB_USER
TEST_DB_PASSWORD
```

Then run:

```bash
mvn verify
```

The integration suite initializes the schema and truncates its tables between tests. Never point it at production data.

## Architecture notes

- `/setup` is the only slash command.
- Regular workflows use Discord buttons, select menus, and modal forms.
- Ticket, feedback, attachment, strike, and moderation data are persisted in SQLite or PostgreSQL. HTML transcripts are stored on disk.
- SQLite uses a single connection with foreign keys enabled, WAL journaling, and a busy timeout; run only one bot instance against a SQLite file.
- Changing from PostgreSQL to SQLite creates a separate store. Existing PostgreSQL history is not automatically copied.
- Repository methods own persistence and transactional consistency.
- Discord listeners coordinate interactions and delegate reusable work to services.
- Configuration is validated before the bot starts accepting events.

## CI

GitHub Actions runs on every pull request and every push to `main`:

- Windows build, unit tests, and SQLite persistence tests on Java 21.
- PostgreSQL and SQLite integration tests on Linux with PostgreSQL 16.
- Build of the Docker deployment image.
- Upload of the runnable JAR after a successful Windows build.

## Contribution workflow

1. Create a branch from `main`.
2. Keep changes focused and avoid committing secrets or generated files.
3. Add or update tests for behavior changes.
4. Run `mvn clean verify`.
5. Open a pull request and wait for all CI jobs to pass.

## Security

- Never commit Discord tokens, database passwords, or real environment files.
- Keep permission checks on every staff or moderation interaction.
- Use parameterized SQL for all user-controlled values.
- Treat transcript and attachment data as private support information.
