import { REST, Routes } from 'discord.js';
import { loadConfig } from '../config/index.js';
import { logger } from '../utils/logger.js';
import { commandJson } from './commands/index.js';

/**
 * Registers the guild slash commands. Run once after changing commands:
 *   npm run deploy-commands            (ts, via tsx)
 *   npm run deploy-commands:prod       (compiled)
 */
async function main(): Promise<void> {
  const cfg = loadConfig();
  logger.setLevel(cfg.logLevel);
  const rest = new REST({ version: '10' }).setToken(cfg.discord.token);
  const body = commandJson();
  logger.info(`Registering ${body.length} command(s) to guild ${cfg.discord.guildId}...`);
  await rest.put(
    Routes.applicationGuildCommands(cfg.discord.clientId, cfg.discord.guildId),
    { body },
  );
  logger.info('Slash commands registered.');
}

main().catch((err) => {
  logger.error('Command registration failed:', String(err));
  process.exit(1);
});
