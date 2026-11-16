import { Client, Events, GatewayIntentBits, Partials } from 'discord.js';
import type { BotConfig } from '../config/index.js';
import type { SuggestionStore } from '../database/index.js';
import { logger } from '../utils/logger.js';
import { BotContext } from './context.js';
import { EvaluationScheduler } from './scheduler.js';
import { onInteraction } from './events/interactionCreate.js';
import { onReady } from './events/ready.js';
import { onThreadCreate } from './events/threadCreate.js';

export interface RunningBot {
  client: Client;
  ctx: BotContext;
  scheduler: EvaluationScheduler;
  stop: () => Promise<void>;
}

export function createClient(config: BotConfig, store: SuggestionStore): RunningBot {
  const client = new Client({
    intents: [
      GatewayIntentBits.Guilds,
      GatewayIntentBits.GuildMessages,
      GatewayIntentBits.MessageContent,
    ],
    partials: [Partials.Channel, Partials.Message],
  });

  const ctx = new BotContext(client, config, store);
  const scheduler = new EvaluationScheduler(ctx);

  client.once(Events.ClientReady, (c) => {
    void onReady(c, ctx).then(() => scheduler.start());
  });
  client.on(Events.ThreadCreate, (thread) => void onThreadCreate(thread, ctx));
  client.on(Events.InteractionCreate, (interaction) => void onInteraction(interaction, ctx));
  client.on(Events.Error, (err) => logger.error('Discord client error:', String(err)));
  client.on(Events.Warn, (msg) => logger.warn('Discord warning:', msg));

  const stop = async () => {
    scheduler.stop();
    await client.destroy();
    store.close();
  };

  return { client, ctx, scheduler, stop };
}
