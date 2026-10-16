import React from 'react';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeX,
  Star,
  Moon,
  Bluetooth,
  Maximize2,
  Radio,
} from 'lucide-react';
import { Station, SleepTimerConfig } from '../types';

interface MiniPlayerProps {
  station: Station | null;
  isPlaying: boolean;
  isLoading: boolean;
  isMuted: boolean;
  volume: number;
  timerConfig: SleepTimerConfig;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onToggleFavorite: (id: string, e: React.MouseEvent) => void;
  onVolumeChange: (vol: number) => void;
  onToggleMute: () => void;
  onOpenExpandDeck: () => void;
  onOpenSleepTimer: () => void;
  onOpenBluetooth: () => void;
}

export const MiniPlayer: React.FC<MiniPlayerProps> = ({
  station,
  isPlaying,
  isLoading,
  isMuted,
  volume,
  timerConfig,
  onTogglePlay,
  onNext,
  onPrevious,
  onToggleFavorite,
  onVolumeChange,
  onToggleMute,
  onOpenExpandDeck,
  onOpenSleepTimer,
  onOpenBluetooth,
}) => {
  if (!station) {
    return (
      <div className="fixed bottom-3 left-3 right-3 sm:left-6 sm:right-6 max-w-5xl mx-auto z-40">
        <div className="bg-[#111622]/90 backdrop-blur-xl border border-white/10 rounded-[28px] px-5 py-3.5 shadow-2xl flex items-center justify-between text-neutral-400 text-xs">
          <div className="flex items-center gap-2.5">
            <Radio className="w-4 h-4 text-[var(--accent)]" />
            <span>Select a radio station to begin streaming</span>
          </div>
          <span className="hidden sm:inline text-neutral-500 font-mono text-[11px]">
            Ready to broadcast
          </span>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed bottom-3 left-3 right-3 sm:left-6 sm:right-6 max-w-5xl mx-auto z-40">
      <div className="bg-[#101522]/95 backdrop-blur-2xl border border-white/10 rounded-[26px] p-2.5 sm:px-4 sm:py-3 shadow-2xl shadow-black/80 flex items-center justify-between gap-3 sm:gap-4 transition-all">
        {/* Left: Station Artwork Squircle + Title + Specs (Clickable to Expand) */}
        <div
          onClick={onOpenExpandDeck}
          className="flex items-center gap-3 min-w-0 cursor-pointer group shrink"
        >
          {/* Artwork Squircle */}
          <div className="relative w-12 h-12 rounded-[18px] overflow-hidden bg-neutral-950 border border-white/10 shrink-0 flex items-center justify-center shadow-md">
            {station.logoUrl ? (
              <img
                src={station.logoUrl}
                alt={station.name}
                referrerPolicy="no-referrer"
                className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                onError={(e) => {
                  (e.currentTarget as HTMLElement).style.display = 'none';
                }}
              />
            ) : null}

            {/* EQ Animation overlay when playing */}
            {isPlaying && (
              <div className="absolute inset-0 bg-black/40 backdrop-blur-[1px] flex items-center justify-center gap-0.5">
                <span className="w-1 bg-[var(--accent)] rounded-full animate-eq-1"></span>
                <span className="w-1 bg-[var(--accent)] rounded-full animate-eq-2"></span>
                <span className="w-1 bg-[var(--accent)] rounded-full animate-eq-3"></span>
              </div>
            )}
          </div>

          {/* Details */}
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h4 className="text-xs sm:text-sm font-bold text-white truncate group-hover:text-[var(--accent)] transition-colors">
                {station.name}
              </h4>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleFavorite(station.id, e);
                }}
                className={`p-1 rounded-full hover:bg-white/10 transition-colors ${
                  station.isFavorite ? 'text-amber-400' : 'text-neutral-500'
                }`}
              >
                <Star
                  className={`w-3.5 h-3.5 ${
                    station.isFavorite ? 'fill-amber-400 text-amber-400' : ''
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center gap-2 text-[11px] text-neutral-400">
              <span className="truncate">{station.group}</span>
              <span aria-hidden="true" className="hidden sm:inline">·</span>
              <span className="hidden sm:inline uppercase font-mono-numbers px-1 py-0.2 rounded bg-white/5 text-[10px] text-neutral-300">
                {station.codec || 'MP3'}
              </span>
              {station.bitrate && (
                <span className="hidden md:inline font-mono-numbers text-[10px]">
                  {station.bitrate}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Center: Playback Controls (Material 3 Expressive) */}
        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          <button
            type="button"
            onClick={onPrevious}
            className="p-2 text-neutral-400 hover:text-white rounded-full hover:bg-white/10 transition-colors"
            title="Previous station"
          >
            <SkipBack className="w-4 h-4 fill-current" />
          </button>

          {/* Play/Pause Button */}
          <button
            type="button"
            onClick={onTogglePlay}
            disabled={isLoading}
            className="w-11 h-11 rounded-full bg-[var(--accent)] text-neutral-950 flex items-center justify-center shadow-lg hover:scale-105 active:scale-95 transition-all shadow-[var(--accent-glow)] font-bold shrink-0 disabled:opacity-50"
            title={isPlaying ? 'Pause' : 'Play'}
          >
            {isPlaying ? (
              <Pause className="w-5 h-5 fill-current" />
            ) : (
              <Play className="w-5 h-5 fill-current ml-0.5" />
            )}
          </button>

          <button
            type="button"
            onClick={onNext}
            className="p-2 text-neutral-400 hover:text-white rounded-full hover:bg-white/10 transition-colors"
            title="Next station"
          >
            <SkipForward className="w-4 h-4 fill-current" />
          </button>
        </div>

        {/* Right: Quick Features (Sleep Timer, Bluetooth, Volume, Expand Deck) */}
        <div className="flex items-center gap-1.5 sm:gap-2.5 shrink-0">
          {/* Sleep Timer badge button */}
          <button
            type="button"
            onClick={onOpenSleepTimer}
            className={`p-2 rounded-full transition-colors flex items-center gap-1 text-xs ${
              timerConfig.active
                ? 'bg-[var(--accent)] text-neutral-950 font-bold px-2.5 shadow-sm'
                : 'text-neutral-400 hover:text-white hover:bg-white/10'
            }`}
            title="Sleep timer"
          >
            <Moon className="w-4 h-4" />
            {timerConfig.active && (
              <span className="font-mono text-[10px]">
                {Math.ceil(timerConfig.remainingSeconds / 60)}m
              </span>
            )}
          </button>

          {/* Bluetooth button */}
          <button
            type="button"
            onClick={onOpenBluetooth}
            className="p-2 text-neutral-400 hover:text-blue-400 rounded-full hover:bg-white/10 transition-colors hidden xs:flex"
            title="Bluetooth / External Audio"
          >
            <Bluetooth className="w-4 h-4" />
          </button>

          {/* Volume Control */}
          <div className="hidden lg:flex items-center gap-2 pl-1 border-l border-white/10">
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
              className="w-18 xl:w-24 h-1.5 bg-neutral-800 rounded-lg appearance-none cursor-pointer accent-[var(--accent)]"
            />
          </div>

          {/* Expand Deck Button */}
          <button
            type="button"
            onClick={onOpenExpandDeck}
            className="p-2 text-neutral-400 hover:text-white rounded-full hover:bg-white/10 transition-colors ml-0.5"
            title="Expand Full Studio Deck"
          >
            <Maximize2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
