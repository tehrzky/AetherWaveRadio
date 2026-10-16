/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import {
  Station,
  ViewMode,
  SortOption,
  SleepTimerConfig,
  CloudSyncState,
} from './types';
import { DEFAULT_STATIONS, DEFAULT_GROUPS } from './data/defaultStations';
import { audioEngine } from './utils/audioEngine';
import { applyTheme } from './utils/theme';
import { StationToolbar } from './components/StationToolbar';
import { StationGridItem } from './components/StationGridItem';
import { StationListItem } from './components/StationListItem';
import { MiniPlayer } from './components/MiniPlayer';
import { ExpandedDeckModal } from './components/ExpandedDeckModal';
import { StationModal } from './components/StationModal';
import { SleepTimerModal } from './components/SleepTimerModal';
import { SettingsModal } from './components/SettingsModal';
import { Radio, Settings } from 'lucide-react';

const STORAGE_KEY_STATIONS = 'aetherwave_stations_v1';
const STORAGE_KEY_THEME = 'aetherwave_theme_color';
const STORAGE_KEY_VIEWMODE = 'aetherwave_view_mode';
const STORAGE_KEY_SORT = 'aetherwave_sort_option';
const STORAGE_KEY_VOLUME = 'aetherwave_volume';
const STORAGE_KEY_GROUPS = 'aetherwave_custom_groups';
const STORAGE_KEY_SYNC = 'aetherwave_sync_config';

export default function App() {
  // Theme state
  const [themeColor, setThemeColor] = useState<string>(() => {
    return localStorage.getItem(STORAGE_KEY_THEME) || '#f59e0b';
  });

  // Stations state
  const [stations, setStations] = useState<Station[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_STATIONS);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch (e) {
      console.warn('Failed to parse saved stations', e);
    }
    return DEFAULT_STATIONS;
  });

  // Custom groups state
  const [customGroups, setCustomGroups] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_GROUPS);
      if (saved) return JSON.parse(saved);
    } catch {}
    return [];
  });

  // View and sort state
  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    return (localStorage.getItem(STORAGE_KEY_VIEWMODE) as ViewMode) || 'grid';
  });
  const [sortOption, setSortOption] = useState<SortOption>(() => {
    return (localStorage.getItem(STORAGE_KEY_SORT) as SortOption) || 'custom';
  });
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedGroup, setSelectedGroup] = useState('All');

  // Audio Playback state
  const [currentStation, setCurrentStation] = useState<Station | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [volume, setVolume] = useState<number>(() => {
    const saved = localStorage.getItem(STORAGE_KEY_VOLUME);
    return saved ? parseFloat(saved) : 0.85;
  });
  const [activeAudioDeviceId, setActiveAudioDeviceId] = useState<string>('');

  // Sleep Timer state
  const [timerConfig, setTimerConfig] = useState<SleepTimerConfig>({
    active: false,
    remainingSeconds: 0,
    initialSeconds: 0,
    fadeOut: true,
  });
  const originalVolumeBeforeFade = useRef<number>(volume);

  // Cloud Sync state
  const [syncState, setSyncState] = useState<CloudSyncState>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_SYNC);
      if (saved) return JSON.parse(saved);
    } catch {}
    const rand = Math.random().toString(36).substring(2, 7).toUpperCase();
    return {
      roomCode: `RADIO-${rand}`,
      isAutoSync: false,
      lastSyncedAt: null,
      status: 'idle',
    };
  });

  // Modals state
  const [isStationModalOpen, setIsStationModalOpen] = useState(false);
  const [stationToEdit, setStationToEdit] = useState<Station | null>(null);
  const [isDeckModalOpen, setIsDeckModalOpen] = useState(false);
  const [isSleepModalOpen, setIsSleepModalOpen] = useState(false);
  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);

  // Apply theme on mount and when changed
  useEffect(() => {
    applyTheme(themeColor);
    localStorage.setItem(STORAGE_KEY_THEME, themeColor);
  }, [themeColor]);

  // Persist view and sort settings
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY_VIEWMODE, viewMode);
  }, [viewMode]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY_SORT, sortOption);
  }, [sortOption]);

  // Persist stations to localStorage and trigger auto-sync
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY_STATIONS, JSON.stringify(stations));
    localStorage.setItem(STORAGE_KEY_GROUPS, JSON.stringify(customGroups));

    if (syncState.isAutoSync && syncState.roomCode) {
      pushStationsToCloud(syncState.roomCode, stations, customGroups, themeColor);
    }
  }, [stations, customGroups]);

  // Sync state persistence
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY_SYNC, JSON.stringify(syncState));
  }, [syncState]);

  // Initialize AudioEngine events
  useEffect(() => {
    const audio = audioEngine.getAudioElement();
    audioEngine.setVolume(volume);

    const onPlay = () => {
      setIsPlaying(true);
      setIsLoading(false);
    };
    const onPause = () => {
      setIsPlaying(false);
    };
    const onWaiting = () => {
      setIsLoading(true);
    };
    const onPlaying = () => {
      setIsPlaying(true);
      setIsLoading(false);
    };
    const onError = () => {
      setIsLoading(false);
      setIsPlaying(false);
    };

    audio.addEventListener('play', onPlay);
    audio.addEventListener('pause', onPause);
    audio.addEventListener('waiting', onWaiting);
    audio.addEventListener('playing', onPlaying);
    audio.addEventListener('error', onError);

    return () => {
      audio.removeEventListener('play', onPlay);
      audio.removeEventListener('pause', onPause);
      audio.removeEventListener('waiting', onWaiting);
      audio.removeEventListener('playing', onPlaying);
      audio.removeEventListener('error', onError);
    };
  }, []);

  // Compute all available groups
  const allGroups = useMemo(() => {
    const set = new Set<string>(DEFAULT_GROUPS);
    stations.forEach((s) => {
      if (s.group) set.add(s.group);
    });
    customGroups.forEach((g) => set.add(g));
    return Array.from(set);
  }, [stations, customGroups]);

  // Filtered and Sorted Stations
  const filteredAndSortedStations = useMemo(() => {
    let result = [...stations];

    // Search query filter
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter(
        (s) =>
          s.name.toLowerCase().includes(q) ||
          s.group.toLowerCase().includes(q) ||
          s.streamUrl.toLowerCase().includes(q) ||
          (s.country && s.country.toLowerCase().includes(q))
      );
    }

    // Group filter
    if (selectedGroup === 'Favorites') {
      result = result.filter((s) => s.isFavorite);
    } else if (selectedGroup !== 'All') {
      result = result.filter((s) => s.group === selectedGroup);
    }

    // Sorting
    switch (sortOption) {
      case 'name-asc':
        result.sort((a, b) => a.name.localeCompare(b.name));
        break;
      case 'name-desc':
        result.sort((a, b) => b.name.localeCompare(a.name));
        break;
      case 'recently-played':
        result.sort((a, b) => (b.lastPlayedAt || 0) - (a.lastPlayedAt || 0));
        break;
      case 'favorites-first':
        result.sort((a, b) => {
          if (a.isFavorite === b.isFavorite) {
            return a.customOrder - b.customOrder;
          }
          return a.isFavorite ? -1 : 1;
        });
        break;
      case 'custom':
      default:
        result.sort((a, b) => a.customOrder - b.customOrder);
        break;
    }

    return result;
  }, [stations, searchQuery, selectedGroup, sortOption]);

  // Handle Play Station
  const handlePlayStation = useCallback(
    async (station: Station) => {
      try {
        if (currentStation?.id === station.id) {
          if (isPlaying) {
            audioEngine.pause();
          } else {
            await audioEngine.resume();
          }
          return;
        }

        setIsLoading(true);
        setCurrentStation(station);

        // Update last played timestamp
        setStations((prev) =>
          prev.map((s) =>
            s.id === station.id ? { ...s, lastPlayedAt: Date.now() } : s
          )
        );

        await audioEngine.playStation(station);
      } catch (err) {
        console.warn('Playback error:', err);
      } finally {
        setIsLoading(false);
      }
    },
    [currentStation, isPlaying]
  );

  // Play Next / Previous
  const handleNextStation = useCallback(() => {
    if (filteredAndSortedStations.length === 0) return;
    const currentIndex = filteredAndSortedStations.findIndex(
      (s) => s.id === currentStation?.id
    );
    const nextIndex =
      currentIndex === -1 || currentIndex >= filteredAndSortedStations.length - 1
        ? 0
        : currentIndex + 1;
    handlePlayStation(filteredAndSortedStations[nextIndex]);
  }, [filteredAndSortedStations, currentStation, handlePlayStation]);

  const handlePreviousStation = useCallback(() => {
    if (filteredAndSortedStations.length === 0) return;
    const currentIndex = filteredAndSortedStations.findIndex(
      (s) => s.id === currentStation?.id
    );
    const prevIndex =
      currentIndex <= 0
        ? filteredAndSortedStations.length - 1
        : currentIndex - 1;
    handlePlayStation(filteredAndSortedStations[prevIndex]);
  }, [filteredAndSortedStations, currentStation, handlePlayStation]);

  // Bind MediaSession actions for Bluetooth external keys
  useEffect(() => {
    audioEngine.bindMediaSessionActions({
      onPlay: () => {
        if (currentStation) audioEngine.resume();
      },
      onPause: () => {
        audioEngine.pause();
      },
      onPrevious: handlePreviousStation,
      onNext: handleNextStation,
    });
  }, [currentStation, handleNextStation, handlePreviousStation]);

  // Volume & Mute
  const handleVolumeChange = (newVol: number) => {
    setVolume(newVol);
    setIsMuted(false);
    audioEngine.setVolume(newVol);
    audioEngine.setMuted(false);
    localStorage.setItem(STORAGE_KEY_VOLUME, newVol.toString());
  };

  const handleToggleMute = () => {
    const nextMuted = !isMuted;
    setIsMuted(nextMuted);
    audioEngine.setMuted(nextMuted);
  };

  // Toggle Favorite
  const handleToggleFavorite = (stationId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setStations((prev) =>
      prev.map((s) =>
        s.id === stationId ? { ...s, isFavorite: !s.isFavorite } : s
      )
    );
  };

  // Move Station Up / Down for Custom Order
  const handleMoveStation = (stationId: string, direction: 'up' | 'down') => {
    setStations((prev) => {
      const copy = [...prev].sort((a, b) => a.customOrder - b.customOrder);
      const index = copy.findIndex((s) => s.id === stationId);
      if (index === -1) return prev;

      const targetIndex = direction === 'up' ? index - 1 : index + 1;
      if (targetIndex < 0 || targetIndex >= copy.length) return prev;

      // Swap custom orders
      const tempOrder = copy[index].customOrder;
      copy[index].customOrder = copy[targetIndex].customOrder;
      copy[targetIndex].customOrder = tempOrder;

      return [...copy];
    });
  };

  // Edit / Add Station
  const handleOpenAddModal = () => {
    setStationToEdit(null);
    setIsStationModalOpen(true);
  };

  const handleOpenEditModal = (station: Station, e: React.MouseEvent) => {
    e.stopPropagation();
    setStationToEdit(station);
    setIsStationModalOpen(true);
  };

  const handleSaveStation = (station: Station) => {
    setStations((prev) => {
      const exists = prev.some((s) => s.id === station.id);
      if (exists) {
        return prev.map((s) => (s.id === station.id ? station : s));
      }
      return [station, ...prev];
    });

    if (station.group && !allGroups.includes(station.group)) {
      setCustomGroups((prev) => [...prev, station.group]);
    }

    if (currentStation?.id === station.id) {
      setCurrentStation(station);
      audioEngine.updateMediaSession(station);
    }
  };

  const handleDeleteStation = (stationId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (confirm('Delete this radio station from your list?')) {
      if (currentStation?.id === stationId) {
        audioEngine.pause();
        setCurrentStation(null);
      }
      setStations((prev) => prev.filter((s) => s.id !== stationId));
    }
  };

  // Sleep Timer countdown interval
  useEffect(() => {
    if (!timerConfig.active) return;

    const interval = setInterval(() => {
      setTimerConfig((prev) => {
        if (!prev.active) return prev;

        if (prev.remainingSeconds <= 1) {
          // Timer finished: stop audio
          audioEngine.pause();
          audioEngine.setVolume(originalVolumeBeforeFade.current);
          return {
            ...prev,
            active: false,
            remainingSeconds: 0,
          };
        }

        const newRemaining = prev.remainingSeconds - 1;

        // Smooth fade out logic during last 60 seconds
        if (prev.fadeOut && newRemaining <= 60 && newRemaining > 0) {
          const fadeRatio = newRemaining / 60;
          const fadedVolume = originalVolumeBeforeFade.current * fadeRatio;
          audioEngine.setVolume(Math.max(0, fadedVolume));
        }

        return {
          ...prev,
          remainingSeconds: newRemaining,
        };
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [timerConfig.active]);

  const handleStartSleepTimer = (minutes: number, fadeOut: boolean) => {
    const totalSeconds = minutes * 60;
    originalVolumeBeforeFade.current = volume;
    setTimerConfig({
      active: true,
      remainingSeconds: totalSeconds,
      initialSeconds: totalSeconds,
      fadeOut,
    });
  };

  const handleCancelSleepTimer = () => {
    audioEngine.setVolume(originalVolumeBeforeFade.current);
    setTimerConfig((prev) => ({
      ...prev,
      active: false,
      remainingSeconds: 0,
    }));
  };

  // Cloud Sync Functions
  const pushStationsToCloud = async (
    code: string,
    stList: Station[],
    groups: string[],
    color: string
  ): Promise<boolean> => {
    try {
      const res = await fetch(`/api/sync/${encodeURIComponent(code)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          stations: stList,
          customGroups: groups,
          themeColor: color,
        }),
      });
      const data = await res.json();
      if (data.success) {
        setSyncState((prev) => ({
          ...prev,
          lastSyncedAt: Date.now(),
          status: 'synced',
        }));
        return true;
      }
      return false;
    } catch (e) {
      console.warn('Push sync error:', e);
      return false;
    }
  };

  const pullStationsFromCloud = async (code: string): Promise<boolean> => {
    try {
      const res = await fetch(`/api/sync/${encodeURIComponent(code)}`);
      if (!res.ok) return false;
      const json = await res.json();
      if (json.success && json.data) {
        const cloudData = json.data;
        if (Array.isArray(cloudData.stations) && cloudData.stations.length > 0) {
          setStations(cloudData.stations);
        }
        if (Array.isArray(cloudData.customGroups)) {
          setCustomGroups(cloudData.customGroups);
        }
        if (cloudData.themeColor) {
          setThemeColor(cloudData.themeColor);
          applyTheme(cloudData.themeColor);
        }
        setSyncState((prev) => ({
          ...prev,
          roomCode: code,
          lastSyncedAt: Date.now(),
          status: 'synced',
        }));
        return true;
      }
      return false;
    } catch (e) {
      console.warn('Pull sync error:', e);
      return false;
    }
  };

  const handleImportStations = (
    importedStations: Station[],
    mode: 'merge' | 'replace'
  ) => {
    if (mode === 'replace') {
      setStations(importedStations);
    } else {
      setStations((prev) => {
        const existingIds = new Set(prev.map((s) => s.id));
        const newStations = importedStations.filter((s) => !existingIds.has(s.id));
        return [...prev, ...newStations];
      });
    }
  };

  return (
    <div className="min-h-screen bg-[#090d16] text-neutral-100 flex flex-col pb-28 selection:bg-[var(--accent)] selection:text-neutral-950">
      {/* Top Bar Contract: 3 Zones (Brand Wordmark - 4-5 Navigation Links - Primary Action) */}
      <header className="sticky top-0 z-30 bg-[#090d16]/90 backdrop-blur-xl border-b border-white/5 px-4 sm:px-8 py-3.5 flex items-center justify-between gap-8">
        {/* Zone 1: Single text element wordmark in display face */}
        <div className="flex items-center gap-2.5 shrink-0">
          <div className="w-9 h-9 rounded-xl bg-[var(--accent)] text-neutral-950 flex items-center justify-center font-extrabold shadow-md shadow-[var(--accent-glow)]">
            <Radio className="w-5 h-5 fill-current" />
          </div>
          <span className="text-base sm:text-lg font-bold tracking-tight text-white whitespace-nowrap">
            AetherWave Studio Radio
          </span>
        </div>

        {/* Zone 2: Concise navigation filter links */}
        <nav className="hidden md:flex items-center gap-6 text-xs font-semibold text-neutral-400">
          <button
            type="button"
            onClick={() => {
              setSelectedGroup('All');
              setSortOption('custom');
            }}
            className={`hover:text-white transition-colors whitespace-nowrap ${
              selectedGroup === 'All' && sortOption !== 'recently-played' ? 'text-white' : ''
            }`}
          >
            All Stations
          </button>
          <button
            type="button"
            onClick={() => setSelectedGroup('Favorites')}
            className={`hover:text-white transition-colors whitespace-nowrap ${
              selectedGroup === 'Favorites' ? 'text-amber-400 font-bold' : ''
            }`}
          >
            Favorites
          </button>
          <button
            type="button"
            onClick={() => {
              setSelectedGroup('All');
              setSortOption('recently-played');
            }}
            className={`hover:text-white transition-colors whitespace-nowrap ${
              sortOption === 'recently-played' ? 'text-[var(--accent)] font-bold' : ''
            }`}
          >
            Recently Played
          </button>
        </nav>

        {/* Zone 3: Single primary Add Station action + Dedicated Settings */}
        <div className="flex items-center gap-2.5 shrink-0">
          <button
            type="button"
            onClick={() => setIsSettingsModalOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-full text-xs font-semibold bg-neutral-800/80 hover:bg-neutral-700 text-neutral-200 hover:text-white border border-white/10 transition-colors"
            title="Studio Settings"
          >
            <Settings className="w-4 h-4 text-[var(--accent)]" />
            <span className="hidden sm:inline">Settings</span>
          </button>

          <button
            type="button"
            onClick={handleOpenAddModal}
            className="px-4 py-2 rounded-full text-xs font-bold bg-[var(--accent)] text-neutral-950 hover:brightness-110 active:scale-95 shadow-md shadow-[var(--accent-glow)] transition-all whitespace-nowrap"
          >
            + Add Station
          </button>
        </div>
      </header>

      {/* Main Content Viewport */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
        {/* Material 3 Expressive Toolbar: Search, Filters, View Mode, Sorting */}
        <StationToolbar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          sortOption={sortOption}
          onSortOptionChange={setSortOption}
          selectedGroup={selectedGroup}
          onSelectGroup={setSelectedGroup}
          allGroups={allGroups}
          totalStationsCount={stations.length}
          filteredCount={filteredAndSortedStations.length}
          onOpenSettingsModal={() => setIsSettingsModalOpen(true)}
        />

        {/* Stations View: Grid (Prominent Logos) vs List (Technical stream details) */}
        {filteredAndSortedStations.length > 0 ? (
          viewMode === 'grid' ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3.5 sm:gap-4">
              {filteredAndSortedStations.map((station, index) => {
                const isCurrent = currentStation?.id === station.id;
                return (
                  <StationGridItem
                    key={station.id}
                    station={station}
                    isCurrent={isCurrent}
                    isPlaying={isCurrent && isPlaying}
                    isCustomSort={sortOption === 'custom'}
                    onPlay={handlePlayStation}
                    onToggleFavorite={handleToggleFavorite}
                    onEdit={handleOpenEditModal}
                    onMoveUp={() => handleMoveStation(station.id, 'up')}
                    onMoveDown={() => handleMoveStation(station.id, 'down')}
                    canMoveUp={index > 0}
                    canMoveDown={index < filteredAndSortedStations.length - 1}
                  />
                );
              })}
            </div>
          ) : (
            <div className="space-y-2">
              {filteredAndSortedStations.map((station, index) => {
                const isCurrent = currentStation?.id === station.id;
                return (
                  <StationListItem
                    key={station.id}
                    station={station}
                    isCurrent={isCurrent}
                    isPlaying={isCurrent && isPlaying}
                    isCustomSort={sortOption === 'custom'}
                    onPlay={handlePlayStation}
                    onToggleFavorite={handleToggleFavorite}
                    onEdit={handleOpenEditModal}
                    onDelete={handleDeleteStation}
                    onMoveUp={() => handleMoveStation(station.id, 'up')}
                    onMoveDown={() => handleMoveStation(station.id, 'down')}
                    canMoveUp={index > 0}
                    canMoveDown={index < filteredAndSortedStations.length - 1}
                  />
                );
              })}
            </div>
          )
        ) : (
          /* Empty State */
          <div className="p-12 text-center rounded-3xl bg-[#111726]/40 border border-white/5 space-y-4 max-w-lg mx-auto my-12">
            <div className="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center mx-auto text-neutral-500">
              <Radio className="w-8 h-8" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">No stations found</h3>
              <p className="text-xs text-neutral-400 mt-1">
                {searchQuery
                  ? `No radio stations matched "${searchQuery}". Try a different keyword.`
                  : 'There are no stations in this category yet.'}
              </p>
            </div>
            <div className="flex items-center justify-center gap-3 pt-2">
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  className="px-4 py-2 rounded-full text-xs font-medium bg-neutral-800 hover:bg-neutral-700 text-white"
                >
                  Clear Search
                </button>
              )}
              <button
                type="button"
                onClick={handleOpenAddModal}
                className="px-4 py-2 rounded-full text-xs font-bold bg-[var(--accent)] text-neutral-950 shadow-md"
              >
                + Add Station
              </button>
            </div>
          </div>
        )}
      </main>

      {/* Material 3 Expressive Floating Mini Player Dock */}
      <MiniPlayer
        station={currentStation}
        isPlaying={isPlaying}
        isLoading={isLoading}
        isMuted={isMuted}
        volume={volume}
        timerConfig={timerConfig}
        onTogglePlay={() => {
          if (currentStation) {
            if (isPlaying) audioEngine.pause();
            else audioEngine.resume();
          }
        }}
        onNext={handleNextStation}
        onPrevious={handlePreviousStation}
        onToggleFavorite={handleToggleFavorite}
        onVolumeChange={handleVolumeChange}
        onToggleMute={handleToggleMute}
        onOpenExpandDeck={() => setIsDeckModalOpen(true)}
        onOpenSleepTimer={() => setIsSleepModalOpen(true)}
        onOpenBluetooth={() => setIsSettingsModalOpen(true)}
      />

      {/* Expanded Studio Master Deck Modal */}
      <ExpandedDeckModal
        isOpen={isDeckModalOpen}
        onClose={() => setIsDeckModalOpen(false)}
        station={currentStation}
        isPlaying={isPlaying}
        isMuted={isMuted}
        volume={volume}
        timerConfig={timerConfig}
        accentColor={themeColor}
        onTogglePlay={() => {
          if (currentStation) {
            if (isPlaying) audioEngine.pause();
            else audioEngine.resume();
          }
        }}
        onNext={handleNextStation}
        onPrevious={handlePreviousStation}
        onToggleFavorite={handleToggleFavorite}
        onVolumeChange={handleVolumeChange}
        onToggleMute={handleToggleMute}
        onOpenSleepTimer={() => setIsSleepModalOpen(true)}
        onOpenBluetooth={() => setIsSettingsModalOpen(true)}
      />

      {/* Add / Edit Station Modal */}
      <StationModal
        isOpen={isStationModalOpen}
        onClose={() => setIsStationModalOpen(false)}
        onSave={handleSaveStation}
        stationToEdit={stationToEdit}
        existingGroups={allGroups}
      />

      {/* Sleep Timer Modal */}
      <SleepTimerModal
        isOpen={isSleepModalOpen}
        onClose={() => setIsSleepModalOpen(false)}
        timerConfig={timerConfig}
        onStartTimer={handleStartSleepTimer}
        onCancelTimer={handleCancelSleepTimer}
      />

      {/* Dedicated Studio Settings Modal (Themes, Bluetooth, Cloud Sync, GitHub APK Build) */}
      <SettingsModal
        isOpen={isSettingsModalOpen}
        onClose={() => setIsSettingsModalOpen(false)}
        themeColor={themeColor}
        onThemeColorChange={setThemeColor}
        activeAudioDeviceId={activeAudioDeviceId}
        onSelectAudioDevice={setActiveAudioDeviceId}
        syncState={syncState}
        stations={stations}
        customGroups={customGroups}
        onUpdateSyncState={(upd) => setSyncState((prev) => ({ ...prev, ...upd }))}
        onPushToCloud={() =>
          pushStationsToCloud(syncState.roomCode, stations, customGroups, themeColor)
        }
        onPullFromCloud={pullStationsFromCloud}
        onImportStations={handleImportStations}
      />
    </div>
  );
}
