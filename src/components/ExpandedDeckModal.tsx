import React, { useState } from 'react';
import {
  X,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeX,
  Star,
  Moon,
  Bluetooth,
  Radio,
  Sliders,
  ExternalLink,
  ShieldCheck,
  Zap,
} from 'lucide-react';
import { Station, SleepTimerConfig } from '../types';
import { AudioVisualizer } from './AudioVisualizer';

interface ExpandedDeckModalProps {
  isOpen: boolean;
  onClose: () => void;
  station: Station | null;
  isPlaying: boolean;
  isMuted: boolean;
  volume: number;
  timerConfig: SleepTimerConfig;
  accentColor: string;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onToggleFavorite: (id: string, e: React.MouseEvent) => void;
  onVolumeChange: (vol: number) => void;
  onToggleMute: () => void;
  onOpenSleepTimer: () => void;
  onOpenBluetooth: () => void;
}

export const ExpandedDeckModal: React.FC<ExpandedDeckModalProps> = ({
  isOpen,
  onClose,
  station,
  isPlaying,
  isMuted,
  volume,
  timerConfig,
  accentColor,
  onTogglePlay,
  onNext,
  onPrevious,
  onToggleFavorite,
  onVolumeChange,
  onToggleMute,
  onOpenSleepTimer,
  onOpenBluetooth,
}) => {
  const [eqPreset, setEqPreset] = useState<'flat' | 'bass' | 'vocal' | 'club' | 'treble'>('bass');

  if (!isOpen || !station) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-3 sm:p-6 overflow-y-auto">
      <div className="relative w-full max-w-2xl bg-[#111622] border border-white/10 rounded-[32px] overflow-hidden shadow-2xl flex flex-col my-auto">
        {/* Dynamic ambient backlight */}
        <div
          className="absolute -top-24 left-1/2 -translate-x-1/2 w-96 h-96 rounded-full blur-[110px] pointer-events-none opacity-25"
          style={{ backgroundColor: accentColor }}
        />

        {/* Top Bar - Material 3 Expressive Header */}
        <div className="relative z-10 flex items-center justify-between px-6 py-4 border-b border-white/5">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-neutral-400">
            <Radio className="w-4 h-4 text-[var(--accent)]" />
            <span>Studio Master Deck</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onOpenBluetooth}
              className="p-2 rounded-full bg-white/5 hover:bg-white/10 text-neutral-300 hover:text-white transition-colors"
              title="Bluetooth Output"
            >
              <Bluetooth className="w-4 h-4 text-blue-400" />
            </button>

            <button
              type="button"
              onClick={onOpenSleepTimer}
              className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors flex items-center gap-1.5 ${
                timerConfig.active
                  ? 'bg-[var(--accent)] text-neutral-950 font-semibold'
                  : 'bg-white/5 hover:bg-white/10 text-neutral-300'
              }`}
            >
              <Moon className="w-3.5 h-3.5" />
              <span>
                {timerConfig.active
                  ? `${Math.ceil(timerConfig.remainingSeconds / 60)}m`
                  : 'Sleep'}
              </span>
            </button>

            <button
              type="button"
              onClick={onClose}
              className="p-2 rounded-full bg-white/5 hover:bg-white/10 text-neutral-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Modal Body */}
        <div className="relative z-10 p-6 sm:p-8 space-y-6">
          {/* Main Hero Card */}
          <div className="flex flex-col sm:flex-row items-center gap-6">
            {/* Artwork Squircle */}
            <div className="relative w-44 h-44 sm:w-52 sm:h-52 rounded-[28px] overflow-hidden bg-neutral-950 border border-white/10 shadow-2xl shrink-0 flex items-center justify-center">
              {station.logoUrl ? (
                <img
                  src={station.logoUrl}
                  alt={station.name}
                  referrerPolicy="no-referrer"
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="flex flex-col items-center justify-center text-neutral-500">
                  <Radio className="w-16 h-16 mb-2 text-neutral-600" />
                  <span className="font-bold text-sm tracking-wider uppercase text-neutral-400">
                    {station.name.slice(0, 3)}
                  </span>
                </div>
              )}

              {/* Status pill on artwork */}
              <div className="absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-black/75 backdrop-blur-md border border-white/10 text-[10px] font-mono tracking-wider">
                <span
                  className={`w-2 h-2 rounded-full ${
                    isPlaying ? 'bg-emerald-400 animate-pulse' : 'bg-neutral-500'
                  }`}
                />
                <span className="text-white uppercase font-bold">
                  {isPlaying ? 'ON AIR' : 'PAUSED'}
                </span>
              </div>
            </div>

            {/* Station details & stream stats */}
            <div className="flex-1 text-center sm:text-left min-w-0">
              <div className="flex items-center justify-center sm:justify-start gap-2 mb-2">
                <span className="px-3 py-1 rounded-full bg-white/5 text-[11px] font-semibold text-neutral-300 border border-white/5">
                  {station.group}
                </span>
                <button
                  type="button"
                  onClick={(e) => onToggleFavorite(station.id, e)}
                  className={`p-1.5 rounded-full hover:bg-white/10 transition-colors ${
                    station.isFavorite ? 'text-amber-400' : 'text-neutral-500'
                  }`}
                >
                  <Star
                    className={`w-4 h-4 ${
                      station.isFavorite ? 'fill-amber-400 text-amber-400' : ''
                    }`}
                  />
                </button>
              </div>

              <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight line-clamp-2">
                {station.name}
              </h2>

              {station.description && (
                <p className="text-xs text-neutral-400 mt-2 line-clamp-2 leading-relaxed">
                  {station.description}
                </p>
              )}

              {/* Technical specs */}
              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2 mt-4 font-mono-numbers text-xs text-neutral-400">
                <span className="px-2 py-0.5 rounded-md bg-white/5 border border-white/5 text-neutral-300 font-semibold uppercase">
                  {station.codec || 'MP3'}
                </span>
                <span>{station.bitrate || '128 kbps'}</span>
                {station.country && (
                  <>
                    <span aria-hidden="true">·</span>
                    <span>{station.country}</span>
                  </>
                )}
              </div>

              {/* Stream link copy */}
              <div className="mt-3 flex items-center justify-center sm:justify-start gap-1.5 text-[11px] font-mono text-neutral-500 truncate">
                <span className="truncate max-w-[260px]">{station.streamUrl}</span>
                <a
                  href={station.streamUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="text-neutral-400 hover:text-white"
                  title="Open direct stream URL in new tab"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>
          </div>

          {/* Real-Time Audio Visualizer Canvas */}
          <div className="p-4 rounded-2xl bg-neutral-950/80 border border-white/5 space-y-2">
            <div className="flex items-center justify-between text-xs text-neutral-400">
              <span className="flex items-center gap-1.5 font-medium">
                <Zap className="w-3.5 h-3.5 text-[var(--accent)]" />
                <span>Live Audio Spectrum</span>
              </span>
              <span className="font-mono text-[11px] text-neutral-500">64-BAND FFT ANALYSER</span>
            </div>
            <div className="h-16 w-full flex items-center justify-center">
              <AudioVisualizer
                isPlaying={isPlaying}
                barCount={36}
                height={64}
                accentColor={accentColor}
              />
            </div>
          </div>

          {/* Equalizer Profile Presets (Material 3 Expressive Tonal Pills) */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs text-neutral-400">
              <span className="flex items-center gap-1.5 font-medium">
                <Sliders className="w-3.5 h-3.5 text-[var(--accent)]" />
                <span>Audio Tone Preset</span>
              </span>
              <span className="text-[11px] text-neutral-500 uppercase">{eqPreset} mode</span>
            </div>

            <div className="grid grid-cols-5 gap-2">
              {(['bass', 'vocal', 'club', 'treble', 'flat'] as const).map((mode) => {
                const isSel = eqPreset === mode;
                return (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => setEqPreset(mode)}
                    className={`py-2 rounded-xl text-xs font-semibold capitalize transition-all border ${
                      isSel
                        ? 'bg-[var(--accent)] text-neutral-950 border-[var(--accent)] shadow-md shadow-[var(--accent-glow)]'
                        : 'bg-neutral-950/70 border-white/5 text-neutral-300 hover:bg-white/5 hover:text-white'
                    }`}
                  >
                    {mode === 'flat' ? 'Balanced' : mode}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Material 3 Expressive Playback Controls */}
          <div className="pt-2 flex flex-col items-center gap-4">
            {/* Core Buttons */}
            <div className="flex items-center justify-center gap-6">
              <button
                type="button"
                onClick={onPrevious}
                className="w-12 h-12 rounded-full bg-white/5 hover:bg-white/10 active:scale-95 text-neutral-200 hover:text-white flex items-center justify-center transition-all border border-white/5"
                title="Previous Station"
              >
                <SkipBack className="w-5 h-5 fill-current" />
              </button>

              {/* Large M3 Expressive Play/Pause FAB */}
              <button
                type="button"
                onClick={onTogglePlay}
                className="w-20 h-20 rounded-full bg-[var(--accent)] text-neutral-950 flex items-center justify-center shadow-xl hover:scale-105 active:scale-95 transition-all shadow-[var(--accent-glow)] font-bold"
                title={isPlaying ? 'Pause' : 'Play'}
              >
                {isPlaying ? (
                  <Pause className="w-8 h-8 fill-current" />
                ) : (
                  <Play className="w-8 h-8 fill-current ml-1" />
                )}
              </button>

              <button
                type="button"
                onClick={onNext}
                className="w-12 h-12 rounded-full bg-white/5 hover:bg-white/10 active:scale-95 text-neutral-200 hover:text-white flex items-center justify-center transition-all border border-white/5"
                title="Next Station"
              >
                <SkipForward className="w-5 h-5 fill-current" />
              </button>
            </div>

            {/* Volume Control Bar */}
            <div className="w-full max-w-md flex items-center gap-3 px-4 py-2.5 rounded-full bg-neutral-950/70 border border-white/5">
              <button
                type="button"
                onClick={onToggleMute}
                className="text-neutral-400 hover:text-white transition-colors"
              >
                {isMuted || volume === 0 ? (
                  <VolumeX className="w-4 h-4 text-red-400" />
                ) : (
                  <Volume2 className="w-4 h-4" />
                )}
              </button>

              <input
                type="range"
                min="0"
                max="1"
                step="0.01"
                value={isMuted ? 0 : volume}
                onChange={(e) => onVolumeChange(parseFloat(e.target.value))}
                className="flex-1 h-1.5 bg-neutral-800 rounded-lg appearance-none cursor-pointer accent-[var(--accent)]"
              />

              <span className="text-xs font-mono text-neutral-400 font-mono-numbers w-9 text-right">
                {Math.round((isMuted ? 0 : volume) * 100)}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
