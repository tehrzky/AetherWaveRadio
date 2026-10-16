import React, { useState } from 'react';
import {
  X,
  Cloud,
  RefreshCw,
  Copy,
  Check,
  Download,
  Upload,
  FileJson,
  FileText,
  AlertCircle,
  Share2,
} from 'lucide-react';
import { Station, CloudSyncState } from '../types';

interface CloudSyncModalProps {
  isOpen: boolean;
  onClose: () => void;
  syncState: CloudSyncState;
  stations: Station[];
  customGroups: string[];
  themeColor: string;
  onUpdateSyncState: (state: Partial<CloudSyncState>) => void;
  onPushToCloud: () => Promise<boolean>;
  onPullFromCloud: (code: string) => Promise<boolean>;
  onImportStations: (importedStations: Station[], mode: 'merge' | 'replace') => void;
}

export const CloudSyncModal: React.FC<CloudSyncModalProps> = ({
  isOpen,
  onClose,
  syncState,
  stations,
  customGroups,
  themeColor,
  onUpdateSyncState,
  onPushToCloud,
  onPullFromCloud,
  onImportStations,
}) => {
  const [activeTab, setActiveTab] = useState<'sync' | 'backup'>('sync');
  const [inputCode, setInputCode] = useState('');
  const [copiedKey, setCopiedKey] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(
    null
  );

  // Import state
  const [importedPreview, setImportedPreview] = useState<Station[] | null>(null);
  const [importMode, setImportMode] = useState<'merge' | 'replace'>('merge');

  if (!isOpen) return null;

  const handleCopyKey = () => {
    navigator.clipboard.writeText(syncState.roomCode);
    setCopiedKey(true);
    setTimeout(() => setCopiedKey(false), 2000);
  };

  const handleGenerateNewKey = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = 'RADIO-';
    for (let i = 0; i < 5; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    onUpdateSyncState({ roomCode: code, lastSyncedAt: null });
    setFeedback({ type: 'success', message: `Generated new sync key: ${code}` });
  };

  const handleConnectExisting = async () => {
    const trimmed = inputCode.trim().toUpperCase();
    if (!trimmed || trimmed.length < 3) {
      setFeedback({ type: 'error', message: 'Please enter a valid sync key.' });
      return;
    }

    setIsSyncing(true);
    setFeedback(null);
    try {
      const ok = await onPullFromCloud(trimmed);
      if (ok) {
        onUpdateSyncState({ roomCode: trimmed });
        setInputCode('');
        setFeedback({ type: 'success', message: `Successfully connected & synced with room ${trimmed}` });
      } else {
        setFeedback({ type: 'error', message: `Cloud room ${trimmed} not found. Check the code and try again.` });
      }
    } catch (e: any) {
      setFeedback({ type: 'error', message: 'Failed to connect to cloud room.' });
    } finally {
      setIsSyncing(false);
    }
  };

  const handleManualPush = async () => {
    setIsSyncing(true);
    setFeedback(null);
    try {
      const ok = await onPushToCloud();
      if (ok) {
        setFeedback({ type: 'success', message: 'All stations successfully pushed to the cloud!' });
      } else {
        setFeedback({ type: 'error', message: 'Failed to push stations to cloud server.' });
      }
    } catch (e) {
      setFeedback({ type: 'error', message: 'Network error while pushing to cloud.' });
    } finally {
      setIsSyncing(false);
    }
  };

  const handleManualPull = async () => {
    setIsSyncing(true);
    setFeedback(null);
    try {
      const ok = await onPullFromCloud(syncState.roomCode);
      if (ok) {
        setFeedback({ type: 'success', message: 'Loaded latest stations from cloud.' });
      } else {
        setFeedback({ type: 'error', message: 'Cloud room not found or empty.' });
      }
    } catch (e) {
      setFeedback({ type: 'error', message: 'Error pulling from cloud.' });
    } finally {
      setIsSyncing(false);
    }
  };

  // Export JSON
  const handleExportJson = () => {
    const exportData = {
      app: 'AetherWave Studio Radio',
      version: '1.0',
      exportedAt: new Date().toISOString(),
      customGroups,
      themeColor,
      stations,
    };

    const blob = new Blob([JSON.stringify(exportData, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `aetherwave-stations-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Export M3U Playlist
  const handleExportM3u = () => {
    let m3u = '#EXTM3U\n';
    stations.forEach((st) => {
      m3u += `#EXTINF:-1 tvg-logo="${st.logoUrl || ''}" group-title="${st.group}",${st.name}\n`;
      m3u += `${st.streamUrl}\n`;
    });

    const blob = new Blob([m3u], { type: 'audio/x-mpegurl' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `aetherwave-playlist-${new Date().toISOString().slice(0, 10)}.m3u`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // File import handler
  const handleFileImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const text = event.target?.result as string;
      if (!text) return;

      try {
        if (file.name.endsWith('.m3u') || file.name.endsWith('.m3u8') || text.startsWith('#EXTM3U')) {
          // Parse M3U
          const lines = text.split('\n');
          const parsed: Station[] = [];
          let curName = 'Radio Station';
          let curGroup = 'Imported';
          let curLogo: string | undefined = undefined;

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();
            if (line.startsWith('#EXTINF:')) {
              // Extract name after last comma
              const commaIdx = line.lastIndexOf(',');
              if (commaIdx !== -1) {
                curName = line.substring(commaIdx + 1).trim();
              }
              const groupMatch = line.match(/group-title="([^"]+)"/i);
              if (groupMatch) curGroup = groupMatch[1];
              const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
              if (logoMatch) curLogo = logoMatch[1];
            } else if (line.startsWith('http://') || line.startsWith('https://')) {
              parsed.push({
                id: `import-${Date.now()}-${parsed.length}`,
                name: curName,
                streamUrl: line,
                logoUrl: curLogo,
                group: curGroup,
                codec: line.includes('.m3u8') ? 'HLS' : 'MP3',
                isFavorite: false,
                customOrder: parsed.length,
              });
              curName = 'Radio Station';
              curGroup = 'Imported';
              curLogo = undefined;
            }
          }
          if (parsed.length > 0) {
            setImportedPreview(parsed);
          } else {
            setFeedback({ type: 'error', message: 'No valid audio streams found in M3U file.' });
          }
        } else {
          // Parse JSON
          const data = JSON.parse(text);
          const rawStations = Array.isArray(data) ? data : data.stations;
          if (Array.isArray(rawStations) && rawStations.length > 0) {
            const valid = rawStations.map((s: any, idx: number) => ({
              id: s.id || `import-${Date.now()}-${idx}`,
              name: s.name || `Station ${idx + 1}`,
              streamUrl: s.streamUrl || s.url || '',
              logoUrl: s.logoUrl || s.logo || undefined,
              group: s.group || 'Imported',
              codec: s.codec || 'MP3',
              bitrate: s.bitrate || '128 kbps',
              country: s.country,
              description: s.description,
              isFavorite: Boolean(s.isFavorite),
              customOrder: idx,
            }));
            setImportedPreview(valid);
          } else {
            setFeedback({ type: 'error', message: 'Invalid JSON station backup format.' });
          }
        }
      } catch (err: any) {
        setFeedback({ type: 'error', message: `Parse error: ${err.message}` });
      }
    };
    reader.readAsText(file);
  };

  const handleApplyImport = () => {
    if (!importedPreview || importedPreview.length === 0) return;
    onImportStations(importedPreview, importMode);
    setFeedback({
      type: 'success',
      message: `Successfully imported ${importedPreview.length} stations (${importMode === 'merge' ? 'merged' : 'replaced'})!`,
    });
    setImportedPreview(null);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150 my-6">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-800 bg-neutral-950/60">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400">
              <Cloud className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-semibold text-lg text-white">Cloud Sync & Data Backup</h2>
              <p className="text-xs text-neutral-400">Synchronize presets across devices or export/import station lists</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab buttons */}
        <div className="flex border-b border-neutral-800 px-6 bg-neutral-950/30">
          <button
            type="button"
            onClick={() => setActiveTab('sync')}
            className={`py-3 px-4 text-xs font-semibold border-b-2 transition-colors flex items-center gap-2 ${
              activeTab === 'sync'
                ? 'border-[var(--accent)] text-white'
                : 'border-transparent text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Share2 className="w-3.5 h-3.5" />
            <span>Cloud Multi-Device Sync</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('backup')}
            className={`py-3 px-4 text-xs font-semibold border-b-2 transition-colors flex items-center gap-2 ${
              activeTab === 'backup'
                ? 'border-[var(--accent)] text-white'
                : 'border-transparent text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Download className="w-3.5 h-3.5" />
            <span>Export & Import Lists</span>
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5">
          {activeTab === 'sync' ? (
            <>
              {/* Sync Key Box */}
              <div className="p-4 rounded-xl bg-neutral-950 border border-white/5 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-neutral-300">
                    Your Current Sync Room Key
                  </span>
                  <button
                    type="button"
                    onClick={handleGenerateNewKey}
                    className="text-[11px] text-[var(--accent)] hover:underline flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" /> Generate new key
                  </button>
                </div>

                <div className="flex items-center gap-2">
                  <div className="flex-1 px-3.5 py-2.5 rounded-lg bg-neutral-900 border border-neutral-700 font-mono text-sm tracking-wider text-white font-bold select-all flex items-center justify-between">
                    <span>{syncState.roomCode}</span>
                    <span className="text-[11px] font-normal text-neutral-500">
                      {stations.length} stations
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={handleCopyKey}
                    className="px-3.5 py-2.5 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-white text-xs font-medium transition-colors border border-white/5 flex items-center gap-1.5 shrink-0"
                  >
                    {copiedKey ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span>Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy Key</span>
                      </>
                    )}
                  </button>
                </div>

                <p className="text-[11px] text-neutral-400 leading-relaxed">
                  Enter this key on any other device (phone, tablet, computer) to instantly synchronize your stations, custom groups, and favorites.
                </p>
              </div>

              {/* Push & Pull Actions */}
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={handleManualPush}
                  disabled={isSyncing}
                  className="flex items-center justify-center gap-2 py-2.5 px-3 rounded-lg bg-[var(--accent)] text-neutral-950 font-semibold text-xs shadow-md hover:brightness-110 transition-all shadow-[var(--accent-glow)] disabled:opacity-50"
                >
                  <Cloud className="w-4 h-4" />
                  <span>Push to Cloud</span>
                </button>

                <button
                  type="button"
                  onClick={handleManualPull}
                  disabled={isSyncing}
                  className="flex items-center justify-center gap-2 py-2.5 px-3 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-white font-medium text-xs transition-colors border border-white/10 disabled:opacity-50"
                >
                  <RefreshCw className={`w-4 h-4 ${isSyncing ? 'animate-spin' : ''}`} />
                  <span>Pull from Cloud</span>
                </button>
              </div>

              {/* Auto Sync Toggle */}
              <label className="flex items-center justify-between p-3.5 rounded-xl bg-neutral-950 border border-white/5 cursor-pointer">
                <div>
                  <p className="text-xs font-medium text-neutral-200">Continuous Auto-Sync</p>
                  <p className="text-[11px] text-neutral-400">
                    Automatically syncs station edits and favorites to this room key
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={syncState.isAutoSync}
                  onChange={(e) => onUpdateSyncState({ isAutoSync: e.target.checked })}
                  className="w-4 h-4 rounded text-[var(--accent)] bg-neutral-800 border-neutral-700 focus:ring-0"
                />
              </label>

              {/* Connect to another device */}
              <div className="pt-2 border-t border-neutral-800">
                <label className="block text-xs font-semibold text-neutral-300 mb-1.5">
                  Connect to Another Device Key
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={inputCode}
                    onChange={(e) => setInputCode(e.target.value.toUpperCase())}
                    placeholder="e.g. RADIO-8X92K"
                    className="flex-1 px-3.5 py-2 rounded-lg bg-neutral-950 border border-neutral-700 font-mono text-xs text-white uppercase focus:outline-none focus:border-[var(--accent)]"
                  />
                  <button
                    type="button"
                    onClick={handleConnectExisting}
                    disabled={isSyncing || !inputCode.trim()}
                    className="px-4 py-2 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-white text-xs font-medium transition-colors border border-white/10 disabled:opacity-50"
                  >
                    Connect
                  </button>
                </div>
              </div>
            </>
          ) : (
            <>
              {/* Export Section */}
              <div>
                <label className="block text-xs font-semibold text-neutral-200 mb-2">
                  Export Station Lists
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={handleExportJson}
                    className="flex flex-col items-center gap-1.5 p-3 rounded-xl bg-neutral-950 border border-white/5 hover:border-white/15 hover:bg-neutral-800/50 transition-all text-center"
                  >
                    <FileJson className="w-5 h-5 text-amber-400" />
                    <span className="text-xs font-medium text-neutral-200">Export JSON</span>
                    <span className="text-[10px] text-neutral-500">Full backup with logos & groups</span>
                  </button>

                  <button
                    type="button"
                    onClick={handleExportM3u}
                    className="flex flex-col items-center gap-1.5 p-3 rounded-xl bg-neutral-950 border border-white/5 hover:border-white/15 hover:bg-neutral-800/50 transition-all text-center"
                  >
                    <FileText className="w-5 h-5 text-sky-400" />
                    <span className="text-xs font-medium text-neutral-200">Export M3U Playlist</span>
                    <span className="text-[10px] text-neutral-500">Compatible with VLC & media players</span>
                  </button>
                </div>
              </div>

              {/* Import Section */}
              <div className="space-y-3 pt-2 border-t border-neutral-800">
                <label className="block text-xs font-semibold text-neutral-200">
                  Import Station Lists (.json or .m3u)
                </label>

                <label className="flex flex-col items-center justify-center p-5 rounded-xl border-2 border-dashed border-neutral-700 hover:border-[var(--accent)] bg-neutral-950/60 cursor-pointer transition-colors group">
                  <Upload className="w-6 h-6 text-neutral-400 group-hover:text-[var(--accent)] mb-1.5 transition-colors" />
                  <span className="text-xs font-medium text-neutral-200">Click to choose backup file</span>
                  <span className="text-[11px] text-neutral-500 mt-0.5">Supports .json backup and .m3u playlist files</span>
                  <input
                    type="file"
                    accept=".json,.m3u,.m3u8"
                    onChange={handleFileImport}
                    className="hidden"
                  />
                </label>

                {/* Import Preview */}
                {importedPreview && (
                  <div className="p-3.5 rounded-xl bg-neutral-950 border border-[var(--accent)]/40 space-y-3 animate-in fade-in">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-white">
                        Found {importedPreview.length} stations to import
                      </span>
                      <button
                        type="button"
                        onClick={() => setImportedPreview(null)}
                        className="text-[11px] text-neutral-400 hover:text-white"
                      >
                        Cancel
                      </button>
                    </div>

                    <div className="flex gap-4 text-xs text-neutral-300">
                      <label className="flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="radio"
                          name="importMode"
                          checked={importMode === 'merge'}
                          onChange={() => setImportMode('merge')}
                          className="text-[var(--accent)]"
                        />
                        <span>Merge with current ({stations.length})</span>
                      </label>
                      <label className="flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="radio"
                          name="importMode"
                          checked={importMode === 'replace'}
                          onChange={() => setImportMode('replace')}
                          className="text-[var(--accent)]"
                        />
                        <span>Replace all</span>
                      </label>
                    </div>

                    <button
                      type="button"
                      onClick={handleApplyImport}
                      className="w-full py-2 rounded-lg bg-[var(--accent)] text-neutral-950 font-semibold text-xs shadow-md hover:brightness-110 transition-all shadow-[var(--accent-glow)]"
                    >
                      Confirm Import ({importedPreview.length} Stations)
                    </button>
                  </div>
                )}
              </div>
            </>
          )}

          {feedback && (
            <div
              className={`flex items-center gap-2 text-xs p-3 rounded-lg border ${
                feedback.type === 'success'
                  ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                  : 'bg-red-500/10 border-red-500/20 text-red-400'
              }`}
            >
              {feedback.type === 'success' ? (
                <Check className="w-4 h-4 shrink-0" />
              ) : (
                <AlertCircle className="w-4 h-4 shrink-0" />
              )}
              <span>{feedback.message}</span>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-3 border-t border-neutral-800 bg-neutral-950/40">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-neutral-300 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
