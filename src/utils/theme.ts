import { ThemePreset } from '../types';

export const THEME_PRESETS: ThemePreset[] = [
  {
    id: 'studio-amber',
    name: 'Studio Amber',
    primaryColor: '#f59e0b',
    glowColor: 'rgba(245, 158, 11, 0.35)',
    subtleColor: 'rgba(245, 158, 11, 0.12)',
  },
  {
    id: 'cyberpunk-cyan',
    name: 'Cyber Cyan',
    primaryColor: '#06b6d4',
    glowColor: 'rgba(6, 182, 212, 0.35)',
    subtleColor: 'rgba(6, 182, 212, 0.12)',
  },
  {
    id: 'emerald-deck',
    name: 'Emerald Phosphor',
    primaryColor: '#10b981',
    glowColor: 'rgba(16, 185, 129, 0.35)',
    subtleColor: 'rgba(16, 185, 129, 0.12)',
  },
  {
    id: 'vaporwave-magenta',
    name: 'Vapor Magenta',
    primaryColor: '#ec4899',
    glowColor: 'rgba(236, 72, 153, 0.35)',
    subtleColor: 'rgba(236, 72, 153, 0.12)',
  },
  {
    id: 'electric-violet',
    name: 'Electric Violet',
    primaryColor: '#8b5cf6',
    glowColor: 'rgba(139, 92, 246, 0.35)',
    subtleColor: 'rgba(139, 92, 246, 0.12)',
  },
  {
    id: 'solar-orange',
    name: 'Solar Orange',
    primaryColor: '#f97316',
    glowColor: 'rgba(249, 115, 22, 0.35)',
    subtleColor: 'rgba(249, 115, 22, 0.12)',
  },
  {
    id: 'crimson-pulse',
    name: 'Crimson Broadcast',
    primaryColor: '#ef4444',
    glowColor: 'rgba(239, 68, 68, 0.35)',
    subtleColor: 'rgba(239, 68, 68, 0.12)',
  },
  {
    id: 'matrix-lime',
    name: 'Matrix Lime',
    primaryColor: '#84cc16',
    glowColor: 'rgba(132, 204, 22, 0.35)',
    subtleColor: 'rgba(132, 204, 22, 0.12)',
  },
  {
    id: 'arctic-blue',
    name: 'Arctic Blue',
    primaryColor: '#38bdf8',
    glowColor: 'rgba(56, 189, 248, 0.35)',
    subtleColor: 'rgba(56, 189, 248, 0.12)',
  },
  {
    id: 'pure-platinum',
    name: 'Pure Platinum',
    primaryColor: '#e2e8f0',
    glowColor: 'rgba(226, 232, 240, 0.35)',
    subtleColor: 'rgba(226, 232, 240, 0.12)',
  },
];

export function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const cleanHex = hex.replace('#', '').trim();
  if (cleanHex.length === 3) {
    const r = parseInt(cleanHex[0] + cleanHex[0], 16);
    const g = parseInt(cleanHex[1] + cleanHex[1], 16);
    const b = parseInt(cleanHex[2] + cleanHex[2], 16);
    return { r, g, b };
  }
  if (cleanHex.length === 6) {
    const r = parseInt(cleanHex.substring(0, 2), 16);
    const g = parseInt(cleanHex.substring(2, 4), 16);
    const b = parseInt(cleanHex.substring(4, 6), 16);
    return { r, g, b };
  }
  return null;
}

export function applyTheme(primaryColor: string) {
  const rgb = hexToRgb(primaryColor) || { r: 245, g: 158, b: 11 };
  const root = document.documentElement;

  root.style.setProperty('--accent', primaryColor);
  root.style.setProperty('--accent-rgb', `${rgb.r}, ${rgb.g}, ${rgb.b}`);
  root.style.setProperty('--accent-glow', `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.35)`);
  root.style.setProperty('--accent-subtle', `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.12)`);
}
