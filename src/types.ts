export interface Station {
  id: string;
  name: string;
  streamUrl: string;
  logoUrl?: string;
  group: string;
  description?: string;
  bitrate?: string;
  codec?: 'MP3' | 'AAC' | 'HLS' | 'OGG' | 'AUTO';
  country?: string;
  isFavorite: boolean;
  lastPlayedAt?: number;
  customOrder: number;
}

export type ViewMode = 'grid' | 'list';

export type SortOption =
  | 'custom'
  | 'name-asc'
  | 'name-desc'
  | 'recently-played'
  | 'favorites-first';

export interface ThemePreset {
  id: string;
  name: string;
  primaryColor: string;
  glowColor: string;
  subtleColor: string;
}

export interface SleepTimerConfig {
  active: boolean;
  remainingSeconds: number;
  initialSeconds: number;
  fadeOut: boolean;
}

export interface CloudSyncState {
  roomCode: string;
  isAutoSync: boolean;
  lastSyncedAt: number | null;
  status: 'idle' | 'syncing' | 'synced' | 'error';
  errorMessage?: string;
}

export interface AudioDevice {
  deviceId: string;
  label: string;
}
