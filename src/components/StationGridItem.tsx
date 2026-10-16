import React from 'react';
import { Station } from '../types';
import { Play, Pause, Star, Edit3, Radio, ArrowUp, ArrowDown } from 'lucide-react';

interface StationGridItemProps {
  station: Station;
  isCurrent: boolean;
  isPlaying: boolean;
  isCustomSort: boolean;
  onPlay: (station: Station) => void;
  onToggleFavorite: (id: string, e: React.MouseEvent) => void;
  onEdit: (station: Station, e: React.MouseEvent) => void;
  onMoveUp?: (id: string) => void;
  onMoveDown?: (id: string) => void;
  canMoveUp?: boolean;
  canMoveDown?: boolean;
}

export const StationGridItem: React.FC<StationGridItemProps> = ({
  station,
  isCurrent,
  isPlaying,
  isCustomSort,
  onPlay,
  onToggleFavorite,
  onEdit,
  onMoveUp,
  onMoveDown,
  canMoveUp,
  canMoveDown,
}) => {
  return (
    <div
      onClick={() => onPlay(station)}
      className={`group relative rounded-xl p-4 transition-all duration-200 cursor-pointer flex flex-col justify-between ${
        isCurrent
          ? 'bg-neutral-800/90 ring-1 ring-[var(--accent)] shadow-lg shadow-[var(--accent-glow)]'
          : 'bg-neutral-900/70 hover:bg-neutral-800/80 border border-white/5 hover:border-white/15'
      }`}
    >
      {/* Top action row */}
      <div className="flex items-center justify-between mb-3 z-10">
        <span className="text-xs font-medium text-neutral-400 group-hover:text-neutral-300 truncate max-w-[140px]">
          {station.group}
        </span>

        <div className="flex items-center gap-1">
          {isCustomSort && (
            <div className="flex items-center gap-0.5 mr-1" onClick={(e) => e.stopPropagation()}>
              <button
                type="button"
                disabled={!canMoveUp}
                onClick={() => onMoveUp?.(station.id)}
                title="Move up"
                className="p-1 text-neutral-400 hover:text-white disabled:opacity-20 disabled:hover:text-neutral-400"
              >
                <ArrowUp className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                disabled={!canMoveDown}
                onClick={() => onMoveDown?.(station.id)}
                title="Move down"
                className="p-1 text-neutral-400 hover:text-white disabled:opacity-20 disabled:hover:text-neutral-400"
              >
                <ArrowDown className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          <button
            type="button"
            onClick={(e) => onEdit(station, e)}
            className="p-1.5 text-neutral-400 hover:text-white rounded-md hover:bg-neutral-700/60 transition-colors"
            title="Edit station name & link"
          >
            <Edit3 className="w-3.5 h-3.5" />
          </button>

          <button
            type="button"
            onClick={(e) => onToggleFavorite(station.id, e)}
            className={`p-1.5 rounded-md hover:bg-neutral-700/60 transition-colors ${
              station.isFavorite
                ? 'text-amber-400 fill-amber-400'
                : 'text-neutral-500 hover:text-neutral-300'
            }`}
            title={station.isFavorite ? 'Remove from favorites' : 'Add to favorites'}
          >
            <Star
              className={`w-4 h-4 ${station.isFavorite ? 'fill-amber-400 text-amber-400' : ''}`}
            />
          </button>
        </div>
      </div>

      {/* Prominent Logo Canvas */}
      <div className="relative aspect-square w-full rounded-lg overflow-hidden bg-neutral-950 flex items-center justify-center mb-3.5 border border-white/5 group-hover:border-white/10">
        {station.logoUrl ? (
          <img
            src={station.logoUrl}
            alt={station.name}
            referrerPolicy="no-referrer"
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
            onError={(e) => {
              // Graceful fallback to monogram icon if image fails
              (e.currentTarget as HTMLElement).style.display = 'none';
              const parent = (e.currentTarget as HTMLElement).parentElement;
              if (parent) {
                const fallback = parent.querySelector('.logo-fallback') as HTMLElement;
                if (fallback) fallback.style.display = 'flex';
              }
            }}
          />
        ) : null}

        <div
          className={`logo-fallback absolute inset-0 items-center justify-center bg-gradient-to-br from-neutral-800 to-neutral-950 text-neutral-400 ${
            station.logoUrl ? 'hidden' : 'flex'
          }`}
        >
          <div className="flex flex-col items-center">
            <Radio className="w-10 h-10 mb-1 opacity-70" />
            <span className="text-xs font-semibold uppercase tracking-wider text-neutral-400">
              {station.name.slice(0, 3)}
            </span>
          </div>
        </div>

        {/* Hover / Active Play Button Overlay */}
        <div
          className={`absolute inset-0 bg-black/45 backdrop-blur-[2px] flex items-center justify-center transition-opacity duration-200 ${
            isCurrent ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
          }`}
        >
          <div
            className={`w-12 h-12 rounded-full flex items-center justify-center text-white shadow-xl transform transition-transform group-hover:scale-105 ${
              isCurrent && isPlaying
                ? 'bg-[var(--accent)] text-neutral-950 shadow-[var(--accent-glow)] scale-105'
                : 'bg-white/20 hover:bg-white/30 text-white backdrop-blur-md'
            }`}
          >
            {isCurrent && isPlaying ? (
              <Pause className="w-5 h-5 fill-current" />
            ) : (
              <Play className="w-5 h-5 fill-current ml-0.5" />
            )}
          </div>
        </div>

        {/* Live Audio Waves badge if playing */}
        {isCurrent && isPlaying && (
          <div className="absolute bottom-2 left-2 flex items-end gap-1 px-2 py-1 rounded bg-black/70 backdrop-blur-md border border-white/10">
            <span className="w-1 h-3.5 bg-[var(--accent)] rounded-full animate-eq-1"></span>
            <span className="w-1 h-2 bg-[var(--accent)] rounded-full animate-eq-2"></span>
            <span className="w-1 h-4 bg-[var(--accent)] rounded-full animate-eq-3"></span>
            <span className="w-1 h-2.5 bg-[var(--accent)] rounded-full animate-eq-4"></span>
          </div>
        )}
      </div>

      {/* Station Details */}
      <div>
        <h3
          className={`font-semibold text-sm line-clamp-1 transition-colors ${
            isCurrent ? 'text-[var(--accent)]' : 'text-neutral-100 group-hover:text-white'
          }`}
          title={station.name}
        >
          {station.name}
        </h3>

        <div className="flex items-center gap-2 mt-1 text-xs text-neutral-400 font-mono-numbers">
          <span className="uppercase text-[11px] px-1.5 py-0.5 rounded bg-white/5 border border-white/5 text-neutral-300">
            {station.codec || 'MP3'}
          </span>
          {station.bitrate && <span>{station.bitrate}</span>}
          {station.country && (
            <>
              <span aria-hidden="true">·</span>
              <span className="truncate">{station.country}</span>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
