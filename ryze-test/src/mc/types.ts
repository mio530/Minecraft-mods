/** Shared types for the observation/action loop. */

export interface Vec3Lite {
  x: number;
  y: number;
  z: number;
}

export interface ItemLite {
  slot: number;
  name: string;
  displayName: string;
  count: number;
}

export interface EntityLite {
  id: number;
  name: string;
  type: string;
  username: string | null;
  distance: number;
  position: Vec3Lite;
}

export interface Observation {
  connected: boolean;
  sessionId: string;
  serverHost: string;
  username: string | null;
  position: Vec3Lite | null;
  yaw: number | null;
  pitch: number | null;
  health: number | null;
  food: number | null;
  saturation: number | null;
  experience: number | null;
  dimension: string | null;
  gameMode: string | null;
  timeOfDay: number | null;
  isRaining: boolean;
  onGround: boolean;
  heldItem: ItemLite | null;
  nearbyEntities: EntityLite[];
  nearbyPlayers: string[];
  recentChat: ChatLine[];
  tookAt: number;
}

export interface ChatLine {
  ts: number;
  username: string | null;
  text: string;
}

export type ActionType =
  | 'move' // forward/back/left/right/jump/sprint/sneak for durationMs
  | 'look' // set yaw/pitch (radians) or yawDeg/pitchDeg
  | 'lookAtNearest' // face nearest player/entity
  | 'attackNearest'
  | 'useItem' // right-click / activate held item
  | 'stopAll' // clear all control states
  | 'jumpOnce';

export interface ActionRequest {
  type: ActionType;
  // move
  direction?: 'forward' | 'back' | 'left' | 'right';
  sprint?: boolean;
  sneak?: boolean;
  jump?: boolean;
  durationMs?: number;
  // look
  yaw?: number;
  pitch?: number;
  yawDeg?: number;
  pitchDeg?: number;
  // lookAtNearest / attackNearest
  entityType?: 'player' | 'mob' | 'any';
}

export interface ActionResult {
  ok: boolean;
  action: ActionType;
  message: string;
  observationAfter?: Observation;
}
