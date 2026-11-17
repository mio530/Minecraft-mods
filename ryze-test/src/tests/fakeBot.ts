import { EventEmitter } from 'node:events';
import { Vec3 } from 'vec3';

/**
 * A simulated mineflayer bot for integration testing WITHOUT a network
 * connection. It implements just the surface McBot uses and models enough
 * behaviour (movement while a control is held, chat echo, nearby entities)
 * for the observe/act loop and the predefined tests to run meaningfully.
 */
export class FakeBot extends EventEmitter {
  username = 'RyzeTestBot';
  health = 20;
  food = 18;
  foodSaturation = 5;
  experience = { level: 7 };
  game = { dimension: 'minecraft:overworld', gameMode: 'survival' };
  time = { timeOfDay: 1000 };
  isRaining = false;
  heldItem: any = { slot: 36, name: 'stone_sword', displayName: 'Stone Sword', count: 1 };

  entity = { id: 1, position: new Vec3(0, 64, 0), yaw: 0, pitch: 0, onGround: true };

  private controls: Record<string, boolean> = {};
  private mover: NodeJS.Timeout;

  inventory = {
    items: () => [
      { slot: 36, name: 'stone_sword', displayName: 'Stone Sword', count: 1 },
      { slot: 37, name: 'bread', displayName: 'Bread', count: 5 },
      { slot: 38, name: 'oak_log', displayName: 'Oak Log', count: 12 },
    ],
  };

  entities: Record<number, any> = {
    1: this.entity,
    2: { id: 2, name: 'Mio', type: 'player', username: 'Mio', position: new Vec3(6, 64, 3), height: 1.8 },
    3: { id: 3, name: 'zombie', type: 'mob', username: null, position: new Vec3(10, 64, -4), height: 1.95 },
    4: { id: 4, name: 'cow', type: 'animal', username: null, position: new Vec3(-3, 64, 5), height: 1.4 },
  };
  players: Record<string, any> = { RyzeTestBot: {}, Mio: {} };

  constructor() {
    super();
    // simulate walking physics: advance position while a direction is held
    this.mover = setInterval(() => {
      const speed = this.controls.sprint ? 0.28 : 0.2;
      const f = this.entity.yaw;
      // minecraft yaw: 0 → +z, rotate movement vector
      if (this.controls.forward) this.step(-Math.sin(f) * speed, Math.cos(f) * speed);
      if (this.controls.back) this.step(Math.sin(f) * speed, -Math.cos(f) * speed);
      if (this.controls.left) this.step(-Math.cos(f) * speed, -Math.sin(f) * speed);
      if (this.controls.right) this.step(Math.cos(f) * speed, Math.sin(f) * speed);
    }, 50);
    this.mover.unref?.();
  }

  private step(dx: number, dz: number): void {
    this.entity.position = this.entity.position.offset(dx, 0, dz);
    this.emit('move');
  }

  setControlState(key: string, state: boolean): void {
    this.controls[key] = state;
  }

  async look(yaw: number, pitch: number, _force?: boolean): Promise<void> {
    this.entity.yaw = yaw;
    this.entity.pitch = pitch;
  }

  async lookAt(point: Vec3, _force?: boolean): Promise<void> {
    const dx = point.x - this.entity.position.x;
    const dz = point.z - this.entity.position.z;
    this.entity.yaw = Math.atan2(-dx, dz);
  }

  attack(_entity: any): void {
    /* no-op */
  }
  activateItem(): void {
    /* no-op */
  }
  deactivateItem(): void {
    /* no-op */
  }

  nearestEntity(filter: (e: any) => boolean): any {
    const me = this.entity.position;
    return Object.values(this.entities)
      .filter((e) => e.id !== this.entity.id && e.position && filter(e))
      .sort((a, b) => a.position.distanceTo(me) - b.position.distanceTo(me))[0];
  }

  chat(message: string): void {
    // server echoes the message back to the player (as RyzeSMP would)
    setTimeout(() => this.emit('messagestr', `<${this.username}> ${message}`, 'chat', { username: this.username }), 40);
  }

  quit(_reason?: string): void {
    clearInterval(this.mover);
    this.emit('end', 'quit');
  }
}
