import { PermissionFlagsBits, type GuildMember, type Interaction } from 'discord.js';
import type { BotConfig } from '../config/index.js';

/**
 * A user is staff if they have a configured staff role, or (as a safe default
 * when no roles are configured) the Manage Guild permission.
 */
export function isStaff(cfg: BotConfig, member: GuildMember | null): boolean {
  if (!member) return false;
  if (cfg.staffRoleIds.length > 0) {
    if (cfg.staffRoleIds.some((r) => member.roles.cache.has(r))) return true;
  }
  return member.permissions.has(PermissionFlagsBits.ManageGuild);
}

export function memberFrom(interaction: Interaction): GuildMember | null {
  const m = interaction.member;
  if (m && 'permissions' in m && typeof m.permissions !== 'string') {
    return m as GuildMember;
  }
  return null;
}
