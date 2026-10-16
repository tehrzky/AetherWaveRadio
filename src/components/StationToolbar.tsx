import React from 'react';
import {
  Search,
  LayoutGrid,
  List,
  ArrowUpDown,
  Settings,
  X,
  Star,
} from 'lucide-react';
import { ViewMode, SortOption } from '../types';

interface StationToolbarProps {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  viewMode: ViewMode;
  onViewModeChange: (m: ViewMode) => void;
  sortOption: SortOption;
  onSortOptionChange: (s: SortOption) => void;
  selectedGroup: string;
  onSelectGroup: (g: string) => void;
  allGroups: string[];
  totalStationsCount: number;
  filteredCount: number;
  onOpenSettingsModal: () => void;
}

export const StationToolbar: React.FC<StationToolbarProps> = ({
  searchQuery,
  onSearchChange,
  viewMode,
  onViewModeChange,
  sortOption,
  onSortOptionChange,
  selectedGroup,
  onSelectGroup,
  allGroups,
  totalStationsCount,
  filteredCount,
  onOpenSettingsModal,
}) => {
  return (
    <div className="space-y-3.5">
      {/* Single Clean Row: Search + View Toggle + Sort + Dedicated Settings */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        {/* Search Input (Material 3 Expressive Pill) */}
        <div className="relative flex-1 max-w-lg">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search stations, genres, stream links..."
            className="w-full pl-11 pr-9 py-2.5 rounded-full bg-[#111726] border border-white/10 text-white placeholder-neutral-400 text-xs focus:outline-none focus:border-[var(--accent)] transition-all shadow-inner"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => onSearchChange('')}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-white"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* View Toggle + Sort Dropdown + Settings Button */}
        <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
          {/* M3 Segmented Control for Grid vs List */}
          <div className="flex items-center p-1 rounded-full bg-[#111726] border border-white/10 shadow-inner">
            <button
              type="button"
              onClick={() => onViewModeChange('grid')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all ${
                viewMode === 'grid'
                  ? 'bg-[var(--accent)] text-neutral-950 shadow-md shadow-[var(--accent-glow)]'
                  : 'text-neutral-400 hover:text-white'
              }`}
              title="Grid View (Prominent logos)"
            >
              <LayoutGrid className="w-3.5 h-3.5" />
              <span>Grid</span>
            </button>

            <button
              type="button"
              onClick={() => onViewModeChange('list')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all ${
                viewMode === 'list'
                  ? 'bg-[var(--accent)] text-neutral-950 shadow-md shadow-[var(--accent-glow)]'
                  : 'text-neutral-400 hover:text-white'
              }`}
              title="List View (Stream details & bitrate)"
            >
              <List className="w-3.5 h-3.5" />
              <span>List</span>
            </button>
          </div>

          {/* Sort Selector Dropdown */}
          <div className="flex items-center gap-1.5 px-3 py-2 rounded-full bg-[#111726] border border-white/10 text-neutral-300 text-xs">
            <ArrowUpDown className="w-3.5 h-3.5 text-[var(--accent)] shrink-0" />
            <select
              value={sortOption}
              onChange={(e) => onSortOptionChange(e.target.value as SortOption)}
              className="bg-transparent text-white font-medium focus:outline-none cursor-pointer text-xs"
            >
              <option value="custom" className="bg-neutral-900 text-white">Custom Order</option>
              <option value="name-asc" className="bg-neutral-900 text-white">Name (A → Z)</option>
              <option value="name-desc" className="bg-neutral-900 text-white">Name (Z → A)</option>
              <option value="recently-played" className="bg-neutral-900 text-white">Recently Played</option>
              <option value="favorites-first" className="bg-neutral-900 text-white">Favorites First</option>
            </select>
          </div>

          {/* Dedicated Settings Button */}
          <button
            type="button"
            onClick={onOpenSettingsModal}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-[#111726] hover:bg-neutral-800 border border-white/10 text-neutral-200 hover:text-white text-xs font-semibold transition-colors shadow-inner"
            title="Studio Settings (Themes, Bluetooth, Cloud Sync, GitHub APK Build)"
          >
            <Settings className="w-4 h-4 text-[var(--accent)]" />
            <span className="hidden sm:inline">Settings</span>
          </button>
        </div>
      </div>

      {/* Horizontal Scrollable Category Filter Chips */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
        {allGroups.map((grp) => {
          const isSelected = selectedGroup === grp;
          const isFav = grp === 'Favorites';

          return (
            <button
              key={grp}
              type="button"
              onClick={() => onSelectGroup(grp)}
              className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-all border ${
                isSelected
                  ? 'bg-[var(--accent)] text-neutral-950 border-[var(--accent)] font-semibold shadow-sm'
                  : 'bg-[#111726]/80 text-neutral-300 border-white/5 hover:bg-[#1a2336] hover:text-white'
              }`}
            >
              {isFav && <Star className={`w-3.5 h-3.5 ${isSelected ? 'fill-current' : 'text-amber-400'}`} />}
              <span>{grp}</span>
            </button>
          );
        })}

        <span className="ml-auto text-[11px] text-neutral-500 whitespace-nowrap pl-2 font-mono-numbers">
          {filteredCount} of {totalStationsCount} stations
        </span>
      </div>
    </div>
  );
};
