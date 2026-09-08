# Isla Spider Ticket Bot

A Java Discord bot for private support tickets, ticket ratings, HTML transcripts, appeals, and strike-based moderation.

> This README contains developer documentation only. Bot configuration and deployment are managed separately.

## Technology

- Java 21
- JDA 6.5
- PostgreSQL
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
- Ticket, feedback, attachment, transcript, strike, and moderation data are persisted in PostgreSQL.
- Repository methods own persistence and transactional consistency.
- Discord listeners coordinate interactions and delegate reusable work to services.
- Configuration is validated before the bot starts accepting events.

## CI

GitHub Actions runs on every pull request and every push to `main`:

- Windows build and unit tests on Java 21.
- PostgreSQL integration tests on Linux with PostgreSQL 16.
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
