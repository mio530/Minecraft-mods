import type {
  ChatInputCommandInteraction,
  RESTPostAPIChatInputApplicationCommandsJSONBody,
  SlashCommandBuilder,
} from 'discord.js';
import type { BotContext } from '../context.js';
import * as suggestion from './suggestion.js';

export interface Command {
  data: SlashCommandBuilder;
  execute: (interaction: ChatInputCommandInteraction, ctx: BotContext) => Promise<void>;
}

export const commands: Command[] = [
  { data: suggestion.data as SlashCommandBuilder, execute: suggestion.execute },
];

export function commandJson(): RESTPostAPIChatInputApplicationCommandsJSONBody[] {
  return commands.map((c) => c.data.toJSON());
}
