import { loadConfig } from './config/index.js';
import { createStore } from './database/index.js';
import { createClient } from './bot/client.js';
import { logger } from './utils/logger.js';

async function main(): Promise<void> {
  let config;
  try {
    config = loadConfig();
  } catch (err) {
    logger.error('Configuration error:', String(err));
    logger.error('Copy .env.example to .env and fill in the required values.');
    process.exit(1);
    return;
  }
  logger.setLevel(config.logLevel);

  if (config.staffRoleIds.length === 0) {
    logger.warn('No STAFF_ROLE_IDS configured — moderation falls back to Manage Guild permission.');
  }

  const store = createStore(config.databasePath);
  const bot = createClient(config, store);

  const shutdown = async (signal: string) => {
    logger.info(`Received ${signal}, shutting down...`);
    try {
      await bot.stop();
    } finally {
      process.exit(0);
    }
  };
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('unhandledRejection', (reason) => logger.error('Unhandled rejection:', String(reason)));
  process.on('uncaughtException', (err) => logger.error('Uncaught exception:', String(err)));

  try {
    await bot.client.login(config.discord.token);
  } catch (err) {
    logger.error('Failed to log in to Discord:', String(err));
    store.close();
    process.exit(1);
  }
}

void main();
