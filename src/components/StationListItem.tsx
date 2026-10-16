import React from 'react';
import { Station } from '../types';
import { Play, Pause, Star, Edit3, Trash2, ArrowUp, ArrowDown, ExternalLink, Radio } from 'lucide-react';

interface StationListItemProps {
  station: Station;
  isCurrent: boolean;
  isPlaying: boolean;
  isCustomSort: boolean;
  onPlay: (station: Station) => void;
  onToggleFavorite: (id: string, e: React.MouseEvent) => void;
  onEdit: (station: Station, e: React.MouseEvent) => void;
  onDelete: (id: string, e: React.MouseEvent) => void;
  onMoveUp?: (id: string) => void;
  onMoveDown?: (id: string) => void;
  canMoveUp?: boolean;
  canMoveDown?: boolean;
}

export const StationListItem: React.FC<StationListItemProps> = ({
  station,
  isCurrent,
  isPlaying,
  isCustomSort,
  onPlay,
  onToggleFavorite,
  onEdit,
  onDelete,
  onMoveUp,
  onMoveDown,
  canMoveUp,
  canMoveDown,
}) => {
  return (
    <div
      onClick={() => onPlay(station)}
      className={`group px-4 py-3 rounded-xl transition-all duration-150 cursor-pointer flex items-center justify-between gap-4 border ${
        isCurrent
          ? 'bg-neutral-800/90 border-[var(--accent)] shadow-md shadow-[var(--accent-glow)]'
          : 'bg-neutral-900/50 hover:bg-neutral-800/60 border-white/5 hover:border-white/10'
      }`}
    >
      {/* Left: Reorder controls + Play/Pause button + Station Logo */}
      <div className="flex items-center gap-3 shrink-0">
        {isCustomSort && (
          <div className="flex flex-col gap-0.5" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              disabled={!canMoveUp}
              onClick={() => onMoveUp?.(station.id)}
              title="Move up"
              className="p-1 text-neutral-500 hover:text-white disabled:opacity-20"
            >
              <ArrowUp className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              disabled={!canMoveDown}
              onClick={() => onMoveDown?.(station.id)}
              title="Move down"
              className="p-1 text-neutral-500 hover:text-white disabled:opacity-20"
            >
              <ArrowDown className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onPlay(station);
          }}
          className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 transition-colors ${
            isCurrent && isPlaying
              ? 'bg-[var(--accent)] text-neutral-950 shadow-md shadow-[var(--accent-glow)]'
              : 'bg-neutral-800 text-neutral-200 group-hover:bg-neutral-700 hover:text-white'
          }`}
        >
          {isCurrent && isPlaying ? (
            <Pause className="w-4 h-4 fill-current" />
          ) : (
            <Play className="w-4 h-4 fill-current ml-0.5" />
          )}
        </button>

        {/* Thumbnail logo */}
        <div className="w-11 h-11 rounded-lg overflow-hidden bg-neutral-950 border border-white/10 shrink-0 relative flex items-center justify-center">
          {station.logoUrl ? (
            <img
              src={station.logoUrl}
              alt={station.name}
              referrerPolicy="no-referrer"
              className="w-full h-full object-cover"
              onError={(e) => {
                (e.currentTarget as HTMLElement).style.display = 'none';
                const parent = (e.currentTarget as HTMLElement).parentElement;
                if (parent) {
                  const fallback = parent.querySelector('.list-fallback') as HTMLElement;
                  if (fallback) fallback.style.display = 'flex';
                }
              }}
            />
          ) : null}

          <div
            className={`list-fallback absolute inset-0 items-center justify-center bg-neutral-800 text-neutral-400 ${
              station.logoUrl ? 'hidden' : 'flex'
            }`}
          >
            <Radio className="w-5 h-5 opacity-70" />
          </div>
        </div>
      </div>

      {/* Middle: Detailed info - Station Name, Group, Stream URL & Details */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <h3
            className={`font-semibold text-sm truncate ${
              isCurrent ? 'text-[var(--accent)]' : 'text-neutral-100 group-hover:text-white'
            }`}
          >
            {station.name}
          </h3>
          <span className="text-xs px-2 py-0.5 rounded-md bg-neutral-800 text-neutral-400 shrink-0 border border-white/5">
            {station.group}
          </span>
        </div>

        {/* Stream link & technical specs */}
        <div className="flex items-center gap-2 mt-1 text-xs text-neutral-400">
          <span className="font-mono text-[11px] text-neutral-500 truncate max-w-[260px] md:max-w-md hover:text-neutral-300">
            {station.streamUrl}
          </span>
          <span aria-hidden="true">·</span>
          <span className="uppercase font-mono-numbers px-1.5 py-0.2 rounded bg-white/5 text-[11px] text-neutral-300">
            {station.codec || 'MP3'}
          </span>
          {station.bitrate && (
            <>
              <span aria-hidden="true">·</span>
              <span className="font-mono-numbers text-[11px]">{station.bitrate}</span>
            </>
          )}
          {station.lastPlayedAt && (
            <>
              <span aria-hidden="true" className="hidden sm:inline">·</span>
              <span className="hidden sm:inline text-neutral-500 text-[11px]">
                Played {new Date(station.lastPlayedAt).toLocaleDateString()}
              </span>
            </>
          )}
        </div>
      </div>

      {/* Right: Actions (Favorite, Edit Name/Link, Delete) */}
      <div className="flex items-center gap-1 shrink-0" onClick={(e) => e.stopPropagation()}>
        <button
          type="button"
          onClick={(e) => onToggleFavorite(station.id, e)}
          className={`p-2 rounded-lg hover:bg-neutral-800 transition-colors ${
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

        <button
          type="button"
          onClick={(e) => onEdit(station, e)}
          className="p-2 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          title="Edit station name & stream URL"
        >
          <Edit3 className="w-4 h-4" />
        </button>

        <button
          type="button"
          onClick={(e) => onDelete(station.id, e)}
          className="p-2 text-neutral-500 hover:text-red-400 rounded-lg hover:bg-neutral-800 transition-colors"
          title="Delete station"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
