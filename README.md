# Isla Spider Ticket Bot

A Java Discord bot for private support tickets, ticket ratings, HTML transcripts, appeals by direct message, and strike-based moderation. It uses JDA 6 and PostgreSQL and is designed to run on Windows Server.

The only slash command is `/setup`. All daily actions use buttons, select menus, and forms.

## Features

- Ticket panel for bugs, general help, player reports, and appeals.
- A category-specific form asks for the in-game name and case description.
- Private ticket channels for the requester and the responsible team.
- A single **Send survey** button inside each ticket.
- Ticket ratings for the staff member, attention, solution, and an optional comment.
- Automatic HTML transcript when a ticket closes.
- Uses the existing channel layout: **Log-Ticket** receives closures, ratings, and appeals; **Images-tickets** receives attachments; **copias-transcrip** receives transcripts.
- PostgreSQL storage for tickets, messages, attachments, ratings, strikes, and moderation actions.
- Moderation panel for warnings, strikes, timeouts, kicks, and bans.
- Windows scheduled-task installation with automatic restart.

## Requirements

- Windows Server 2019, 2022, or 2025
- JDK 21
- Apache Maven 3.9 or newer
- PostgreSQL 14 or newer
- A Discord application and bot

## 1. Configure Discord

In the [Discord Developer Portal](https://discord.com/developers/applications):

1. Enable **Server Members Intent** and **Message Content Intent**.
2. Install the bot with the `bot` and `applications.commands` scopes.
3. Grant these permissions:
   - View Channels
   - Manage Channels
   - Manage Roles
   - Send Messages
   - Manage Messages
   - Embed Links
   - Attach Files
   - Read Message History
   - Use Application Commands
   - Moderate Members
   - Kick Members
   - Ban Members
4. Place the bot role above every role it must moderate.
5. Enable Developer Mode in Discord so you can copy server, channel, category, and role IDs.

## 2. Prepare the Discord server

The bot is configured for this existing layout inside the **TICKET** category:

| Existing channel | Purpose |
|---|---|
| `Ticket` | Ticket panel published with `/setup` |
| `Log-Ticket` | Closed-ticket logs, ticket ratings, and appeals |
| `Images-tickets` | Images, files, and links sent in tickets |
| `copias-transcrip` | Archived HTML transcript copies |

You only need a Staff role and a Moderator role in addition to these channels. Run `/setup` inside `Ticket` to publish the support panel. The moderation panel can be published in a separate staff-only channel if needed.

## 3. Prepare PostgreSQL

Open `database/create_database.sql`, replace the example password, and run it with a PostgreSQL administrator account.

Do not run the script if the database and user already exist. The bot creates and updates its own tables when it starts.

## 4. Configure the environment

From PowerShell in the project directory:

```powershell
Copy-Item .env.example .env
notepad .env
```

Fill in only these values:

| Variable | Value |
|---|---|
| `DISCORD_TOKEN` | Secret bot token from the Developer Portal |
| `GUILD_ID` | Discord server ID |
| `TICKET_CATEGORY_ID` | Category used for private ticket channels |
| `TICKET_LOG_CHANNEL_ID` | `Log-Ticket`; also receives ratings and appeals |
| `TICKET_IMAGE_CHANNEL_ID` | `Images-tickets` channel |
| `TICKET_TRANSCRIPT_CHANNEL_ID` | `copias-transcrip` channel |
| `STAFF_ROLE_ID` | Staff role ID |
| `MOD_ROLE_ID` | Moderator role ID |
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USER` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |

Never commit or share the real `.env` file. It is already excluded by `.gitignore`.

## 5. Build and start

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build.ps1
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

A successful startup prints a message similar to:

```text
Bot conectado como ISLA SPIDER BOT en Isla Spider
```

The bot validates the configured Discord objects and permissions during startup. If an ID or permission is incorrect, it stops and reports the exact configuration problem.

## 6. Publish the panels

Run `/setup` as a server administrator. The private setup assistant provides two buttons:

- **Publish tickets**: posts the support center and ticket category menu.
- **Publish moderation**: posts the warnings and sanctions panel.

Publish the moderation panel only in a channel visible to authorized staff.

## 7. Start automatically with Windows

Open PowerShell as Administrator and run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-service.ps1
```

This creates the `IslaSpiderTicketBot` scheduled task under the `SYSTEM` account. Runtime output is written to `logs\bot.log`.

## Troubleshooting

- **The bot is offline:** run `scripts\start.ps1` and check `logs\bot.log`.
- **The command is missing:** confirm that the bot was installed with `applications.commands`.
- **Messages are not archived:** enable Message Content Intent.
- **Tickets cannot be created:** verify Manage Channels and access to the ticket category.
- **Timeout, kick, or ban fails:** move the bot role above the target member's highest role.
- **PostgreSQL fails:** verify `DB_URL`, `DB_USER`, `DB_PASSWORD`, and that PostgreSQL is listening on `localhost:5432`.
- **Appeal DMs fail:** the user may have disabled direct messages; the appeal remains stored and logged.
