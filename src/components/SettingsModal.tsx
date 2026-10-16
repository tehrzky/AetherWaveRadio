import React, { useState } from 'react';
import {
  X,
  Settings,
  Palette,
  Bluetooth,
  Cloud,
  Smartphone,
  Check,
  RotateCcw,
  Sparkles,
  RefreshCw,
  Speaker,
  Headphones,
  Copy,
  Download,
  Upload,
  FileJson,
  FileText,
  AlertCircle,
  Share2,
  FolderArchive,
  Github,
} from 'lucide-react';
import { Station, CloudSyncState, AudioDevice } from '../types';
import { THEME_PRESETS, applyTheme, hexToRgb } from '../utils/theme';
import { audioEngine } from '../utils/audioEngine';
import { usePWAInstall } from '../hooks/usePWAInstall';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  // Theme props
  themeColor: string;
  onThemeColorChange: (color: string) => void;
  // Bluetooth props
  activeAudioDeviceId?: string;
  onSelectAudioDevice: (id: string) => void;
  // Cloud Sync props
  syncState: CloudSyncState;
  stations: Station[];
  customGroups: string[];
  onUpdateSyncState: (state: Partial<CloudSyncState>) => void;
  onPushToCloud: () => Promise<boolean>;
  onPullFromCloud: (code: string) => Promise<boolean>;
  onImportStations: (imported: Station[], mode: 'merge' | 'replace') => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  themeColor,
  onThemeColorChange,
  activeAudioDeviceId,
  onSelectAudioDevice,
  syncState,
  stations,
  customGroups,
  onUpdateSyncState,
  onPushToCloud,
  onPullFromCloud,
  onImportStations,
}) => {
  const [activeTab, setActiveTab] = useState<'theme' | 'bluetooth' | 'cloud' | 'apk'>('theme');
  const { isInstallable, isInstalled, install } = usePWAInstall();

  // Theme custom hex input
  const [hexInput, setHexInput] = useState(themeColor);

  // Bluetooth state
  const [devices, setDevices] = useState<AudioDevice[]>([]);
  const [bluetoothDeviceName, setBluetoothDeviceName] = useState<string | null>(null);
  const [isScanningBt, setIsScanningBt] = useState(false);
  const [btStatusMessage, setBtStatusMessage] = useState<string | null>(null);

  // Cloud Sync state
  const [syncInputCode, setSyncInputCode] = useState('');
  const [copiedSyncKey, setCopiedSyncKey] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncFeedback, setSyncFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [importedPreview, setImportedPreview] = useState<Station[] | null>(null);
  const [importMode, setImportMode] = useState<'merge' | 'replace'>('merge');

  // GitHub Actions copy state
  const [copiedWorkflow, setCopiedWorkflow] = useState(false);

  if (!isOpen) return null;

  // Theme handlers
  const handleSelectPreset = (color: string) => {
    setHexInput(color);
    onThemeColorChange(color);
    applyTheme(color);
  };

  const handleCustomColor = (color: string) => {
    setHexInput(color);
    if (/^#[0-9A-Fa-f]{6}$/.test(color)) {
      onThemeColorChange(color);
      applyTheme(color);
    }
  };

  const handleResetTheme = () => {
    const defaultColor = '#f59e0b';
    setHexInput(defaultColor);
    onThemeColorChange(defaultColor);
    applyTheme(defaultColor);
  };

  // Bluetooth handlers
  const loadAudioDevices = async () => {
    try {
      if (!navigator.mediaDevices?.enumerateDevices) return;
      const all = await navigator.mediaDevices.enumerateDevices();
      const outputs = all
        .filter((d) => d.kind === 'audiooutput')
        .map((d, index) => ({
          deviceId: d.deviceId,
          label: d.label || `Audio Output ${index + 1}`,
        }));
      setDevices(outputs);
    } catch (e) {
      console.warn('Audio devices error:', e);
    }
  };

  const handlePairBluetooth = async () => {
    if (!('bluetooth' in navigator)) {
      setBtStatusMessage('Web Bluetooth is not supported on this browser. Use system Bluetooth settings.');
      return;
    }
    setIsScanningBt(true);
    setBtStatusMessage(null);
    try {
      const device = await (navigator as any).bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: ['generic_access', 'battery_service'],
      });
      setBluetoothDeviceName(device.name || 'Bluetooth Audio Controller');
      setBtStatusMessage(`Connected to ${device.name || 'Bluetooth Device'}`);
      setTimeout(loadAudioDevices, 1000);
    } catch (err: any) {
      if (err.name !== 'NotFoundError') {
        setBtStatusMessage(err.message || 'Bluetooth connection was cancelled.');
      }
    } finally {
      setIsScanningBt(false);
    }
  };

  const handleSelectOutput = async (deviceId: string) => {
    onSelectAudioDevice(deviceId);
    const ok = await audioEngine.setAudioOutputDevice(deviceId);
    if (ok) {
      setBtStatusMessage('Audio routed successfully to selected device.');
    } else {
      setBtStatusMessage('Audio routed via system default output.');
    }
  };

  // Cloud Sync handlers
  const handleCopySyncKey = () => {
    navigator.clipboard.writeText(syncState.roomCode);
    setCopiedSyncKey(true);
    setTimeout(() => setCopiedSyncKey(false), 2000);
  };

  const handleGenerateNewSyncKey = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = 'RADIO-';
    for (let i = 0; i < 5; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    onUpdateSyncState({ roomCode: code, lastSyncedAt: null });
    setSyncFeedback({ type: 'success', message: `Generated new sync key: ${code}` });
  };

  const handleConnectSyncRoom = async () => {
    const trimmed = syncInputCode.trim().toUpperCase();
    if (!trimmed || trimmed.length < 3) {
      setSyncFeedback({ type: 'error', message: 'Please enter a valid sync key.' });
      return;
    }
    setIsSyncing(true);
    setSyncFeedback(null);
    try {
      const ok = await onPullFromCloud(trimmed);
      if (ok) {
        onUpdateSyncState({ roomCode: trimmed });
        setSyncInputCode('');
        setSyncFeedback({ type: 'success', message: `Successfully connected & synced with room ${trimmed}` });
      } else {
        setSyncFeedback({ type: 'error', message: `Cloud room ${trimmed} not found.` });
      }
    } catch (e) {
      setSyncFeedback({ type: 'error', message: 'Failed to connect to cloud room.' });
    } finally {
      setIsSyncing(false);
    }
  };

  const handlePushCloud = async () => {
    setIsSyncing(true);
    setSyncFeedback(null);
    try {
      const ok = await onPushToCloud();
      if (ok) {
        setSyncFeedback({ type: 'success', message: 'All stations successfully pushed to the cloud!' });
      } else {
        setSyncFeedback({ type: 'error', message: 'Failed to push to cloud server.' });
      }
    } catch (e) {
      setSyncFeedback({ type: 'error', message: 'Network error while pushing.' });
    } finally {
      setIsSyncing(false);
    }
  };

  const handlePullCloud = async () => {
    setIsSyncing(true);
    setSyncFeedback(null);
    try {
      const ok = await onPullFromCloud(syncState.roomCode);
      if (ok) {
        setSyncFeedback({ type: 'success', message: 'Loaded latest stations from cloud.' });
      } else {
        setSyncFeedback({ type: 'error', message: 'Cloud room not found or empty.' });
      }
    } catch (e) {
      setSyncFeedback({ type: 'error', message: 'Error pulling from cloud.' });
    } finally {
      setIsSyncing(false);
    }
  };

  const handleExportJson = () => {
    const exportData = {
      app: 'AetherWave Studio Radio',
      version: '1.0',
      exportedAt: new Date().toISOString(),
      customGroups,
      themeColor,
      stations,
    };
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `aetherwave-stations-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

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

  const handleFileImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const text = event.target?.result as string;
      if (!text) return;
      try {
        if (file.name.endsWith('.m3u') || file.name.endsWith('.m3u8') || text.startsWith('#EXTM3U')) {
          const lines = text.split('\n');
          const parsed: Station[] = [];
          let curName = 'Radio Station';
          let curGroup = 'Imported';
          let curLogo: string | undefined;
          for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();
            if (line.startsWith('#EXTINF:')) {
              const commaIdx = line.lastIndexOf(',');
              if (commaIdx !== -1) curName = line.substring(commaIdx + 1).trim();
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
          if (parsed.length > 0) setImportedPreview(parsed);
        } else {
          const data = JSON.parse(text);
          const raw = Array.isArray(data) ? data : data.stations;
          if (Array.isArray(raw) && raw.length > 0) {
            const valid = raw.map((s: any, idx: number) => ({
              id: s.id || `import-${Date.now()}-${idx}`,
              name: s.name || `Station ${idx + 1}`,
              streamUrl: s.streamUrl || s.url || '',
              logoUrl: s.logoUrl || s.logo,
              group: s.group || 'Imported',
              codec: s.codec || 'MP3',
              bitrate: s.bitrate || '128 kbps',
              country: s.country,
              description: s.description,
              isFavorite: Boolean(s.isFavorite),
              customOrder: idx,
            }));
            setImportedPreview(valid);
          }
        }
      } catch (err: any) {
        setSyncFeedback({ type: 'error', message: `Parse error: ${err.message}` });
      }
    };
    reader.readAsText(file);
  };

  const handleApplyImport = () => {
    if (!importedPreview) return;
    onImportStations(importedPreview, importMode);
    setSyncFeedback({
      type: 'success',
      message: `Imported ${importedPreview.length} stations!`,
    });
    setImportedPreview(null);
  };

  // GitHub Actions snippet
  const githubWorkflowYaml = `name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
    paths:
      - "android/**"
      - ".github/workflows/build-android-apk.yml"
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    name: Build Native Android APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Make Gradle Wrapper Executable
        run: chmod +x ./android/gradlew

      - name: Build Debug APK with Gradle
        run: |
          cd android
          ./gradlew assembleDebug --stacktrace

      - name: Upload Debug APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: aetherwave-radio-debug-apk
          path: android/app/build/outputs/apk/debug/*.apk
          retention-days: 14`;

  const handleCopyWorkflow = () => {
    navigator.clipboard.writeText(githubWorkflowYaml);
    setCopiedWorkflow(true);
    setTimeout(() => setCopiedWorkflow(false), 2000);
  };

  const rgb = hexToRgb(themeColor) || { r: 245, g: 158, b: 11 };

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-center justify-center p-3 sm:p-6 overflow-y-auto">
      <div className="bg-[#111622] border border-neutral-800 rounded-3xl w-full max-w-2xl overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150 my-6 flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/5 bg-neutral-950/60 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[var(--accent)] text-neutral-950 flex items-center justify-center font-bold shadow-md shadow-[var(--accent-glow)]">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h2 className="font-bold text-lg text-white">Studio Settings</h2>
              <p className="text-xs text-neutral-400">Themes, Bluetooth audio, cloud presets & GitHub APK build</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-full hover:bg-neutral-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Material 3 Segmented Nav Tabs */}
        <div className="flex items-center gap-1.5 px-6 py-3 border-b border-white/5 bg-neutral-950/40 overflow-x-auto scrollbar-none shrink-0">
          <button
            type="button"
            onClick={() => setActiveTab('theme')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-all ${
              activeTab === 'theme'
                ? 'bg-[var(--accent)] text-neutral-950 shadow-md shadow-[var(--accent-glow)]'
                : 'text-neutral-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <Palette className="w-3.5 h-3.5" />
            <span>Theme & Colors</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setActiveTab('bluetooth');
              loadAudioDevices();
            }}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-all ${
              activeTab === 'bluetooth'
                ? 'bg-[var(--accent)] text-neutral-950 shadow-md shadow-[var(--accent-glow)]'
                : 'text-neutral-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <Bluetooth className="w-3.5 h-3.5" />
            <span>Bluetooth & Audio</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('cloud')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-all ${
              activeTab === 'cloud'
                ? 'bg-[var(--accent)] text-neutral-950 shadow-md shadow-[var(--accent-glow)]'
                : 'text-neutral-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <Cloud className="w-3.5 h-3.5" />
            <span>Cloud & Backup</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('apk')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-all ${
              activeTab === 'apk'
                ? 'bg-[var(--accent)] text-neutral-950 shadow-md shadow-[var(--accent-glow)]'
                : 'text-neutral-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Android APK & GitHub</span>
          </button>
        </div>

        {/* Tab Body */}
        <div className="p-6 space-y-6 overflow-y-auto flex-1">
          {/* TAB 1: THEME & COLOR PICKER */}
          {activeTab === 'theme' && (
            <div className="space-y-6">
              <div>
                <div className="flex items-center justify-between mb-3">
                  <label className="text-xs font-bold text-neutral-200">
                    Curated Material 3 Color Presets
                  </label>
                  <button
                    type="button"
                    onClick={handleResetTheme}
                    className="text-[11px] text-neutral-400 hover:text-white flex items-center gap-1"
                  >
                    <RotateCcw className="w-3 h-3" /> Reset default
                  </button>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
                  {THEME_PRESETS.map((preset) => {
                    const isSelected = themeColor.toLowerCase() === preset.primaryColor.toLowerCase();
                    return (
                      <button
                        key={preset.id}
                        type="button"
                        onClick={() => handleSelectPreset(preset.primaryColor)}
                        className={`flex flex-col items-center gap-2 p-2.5 rounded-2xl border text-center transition-all ${
                          isSelected
                            ? 'bg-neutral-800 border-white/40 ring-2 ring-[var(--accent)] shadow-md'
                            : 'bg-neutral-950/70 border-white/5 hover:bg-neutral-800/60'
                        }`}
                      >
                        <div
                          className="w-8 h-8 rounded-full shadow-inner flex items-center justify-center border border-white/20"
                          style={{ backgroundColor: preset.primaryColor }}
                        >
                          {isSelected && <Check className="w-4 h-4 text-neutral-950 stroke-[3]" />}
                        </div>
                        <span className="text-[11px] font-medium text-neutral-300 truncate w-full">
                          {preset.name}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Custom Color Fine-Tuner */}
              <div className="p-4 rounded-2xl bg-neutral-950/80 border border-white/5 space-y-4">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold text-neutral-200 flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-[var(--accent)]" />
                    <span>Custom Color Picker & Fine-Tuner</span>
                  </label>
                  <span className="text-[11px] font-mono text-neutral-400">
                    RGB({rgb.r}, {rgb.g}, {rgb.b})
                  </span>
                </div>

                <div className="flex items-center gap-3">
                  <div className="relative w-12 h-12 rounded-2xl overflow-hidden border border-white/10 shrink-0 cursor-pointer shadow-md">
                    <input
                      type="color"
                      value={themeColor}
                      onChange={(e) => handleCustomColor(e.target.value)}
                      className="absolute -inset-4 w-20 h-20 cursor-pointer"
                    />
                  </div>

                  <div className="flex-1">
                    <label className="block text-[11px] text-neutral-400 mb-1">
                      Hex Color Code
                    </label>
                    <input
                      type="text"
                      maxLength={7}
                      value={hexInput}
                      onChange={(e) => handleCustomColor(e.target.value)}
                      placeholder="#f59e0b"
                      className="w-full px-3.5 py-2 rounded-xl bg-neutral-900 border border-neutral-700 text-white font-mono text-xs focus:outline-none focus:border-[var(--accent)] uppercase"
                    />
                  </div>
                </div>

                {/* Live Deck Preview Card */}
                <div className="pt-2">
                  <div className="p-3.5 rounded-xl bg-neutral-900 border border-white/10 flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-10 h-10 rounded-xl flex items-center justify-center font-bold text-neutral-950 text-xs shadow-md"
                        style={{ backgroundColor: themeColor }}
                      >
                        M3
                      </div>
                      <div>
                        <p className="text-xs font-semibold text-white">Active Tuner Deck</p>
                        <span
                          className="px-2 py-0.5 rounded text-[10px] font-bold inline-block mt-0.5"
                          style={{ backgroundColor: `${themeColor}25`, color: themeColor }}
                        >
                          DYNAMIC COLOR APPLIED
                        </span>
                      </div>
                    </div>

                    <div className="flex items-end gap-1 px-2.5 py-1.5 rounded-lg bg-black/60">
                      <span className="w-1 h-4 rounded-full animate-eq-1" style={{ backgroundColor: themeColor }} />
                      <span className="w-1 h-6 rounded-full animate-eq-2" style={{ backgroundColor: themeColor }} />
                      <span className="w-1 h-3 rounded-full animate-eq-3" style={{ backgroundColor: themeColor }} />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: BLUETOOTH & AUDIO */}
          {activeTab === 'bluetooth' && (
            <div className="space-y-5">
              <div className="p-4 rounded-2xl bg-neutral-950 border border-white/5 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-neutral-200">
                    Bluetooth Device Controller
                  </span>
                  {bluetoothDeviceName && (
                    <span className="text-[11px] text-emerald-400 font-medium flex items-center gap-1">
                      <Check className="w-3.5 h-3.5" /> Paired: {bluetoothDeviceName}
                    </span>
                  )}
                </div>

                <p className="text-xs text-neutral-400 leading-relaxed">
                  Connect wireless speakers, headphones, or external Bluetooth remotes to control live streaming.
                </p>

                <button
                  type="button"
                  onClick={handlePairBluetooth}
                  disabled={isScanningBt}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-white font-semibold text-xs transition-colors border border-white/10"
                >
                  {isScanningBt ? (
                    <>
                      <RefreshCw className="w-4 h-4 animate-spin text-blue-400" />
                      <span>Scanning Bluetooth devices...</span>
                    </>
                  ) : (
                    <>
                      <Bluetooth className="w-4 h-4 text-blue-400" />
                      <span>Scan & Pair Bluetooth Hardware</span>
                    </>
                  )}
                </button>
              </div>

              {/* Audio Output Destinations */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold text-neutral-200">
                    Audio Output Destination
                  </label>
                  <button
                    type="button"
                    onClick={loadAudioDevices}
                    className="text-[11px] text-[var(--accent)] hover:underline flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" /> Refresh
                  </button>
                </div>

                <div className="space-y-1.5 max-h-48 overflow-y-auto">
                  <button
                    type="button"
                    onClick={() => handleSelectOutput('')}
                    className={`w-full flex items-center justify-between p-3 rounded-xl border text-left transition-colors ${
                      !activeAudioDeviceId
                        ? 'bg-neutral-800/80 border-[var(--accent)] text-white'
                        : 'bg-neutral-950/60 border-white/5 hover:bg-neutral-900 text-neutral-300'
                    }`}
                  >
                    <div className="flex items-center gap-2.5">
                      <Speaker className="w-4 h-4 text-neutral-400 shrink-0" />
                      <div>
                        <p className="text-xs font-medium">Default Audio Output</p>
                        <p className="text-[11px] text-neutral-500">System default speakers or connected earphones</p>
                      </div>
                    </div>
                    {!activeAudioDeviceId && <Check className="w-4 h-4 text-[var(--accent)] shrink-0" />}
                  </button>

                  {devices.map((device) => {
                    const isSelected = activeAudioDeviceId === device.deviceId;
                    return (
                      <button
                        key={device.deviceId}
                        type="button"
                        onClick={() => handleSelectOutput(device.deviceId)}
                        className={`w-full flex items-center justify-between p-3 rounded-xl border text-left transition-colors ${
                          isSelected
                            ? 'bg-neutral-800/80 border-[var(--accent)] text-white'
                            : 'bg-neutral-950/60 border-white/5 hover:bg-neutral-900 text-neutral-300'
                        }`}
                      >
                        <div className="flex items-center gap-2.5 truncate">
                          <Headphones className="w-4 h-4 text-blue-400 shrink-0" />
                          <div className="truncate">
                            <p className="text-xs font-medium truncate">{device.label}</p>
                            <p className="text-[11px] text-neutral-500 font-mono text-[10px]">
                              {device.deviceId.slice(0, 14)}...
                            </p>
                          </div>
                        </div>
                        {isSelected && <Check className="w-4 h-4 text-[var(--accent)] shrink-0" />}
                      </button>
                    );
                  })}
                </div>
              </div>

              {btStatusMessage && (
                <div className="flex items-center gap-2 text-xs text-neutral-300 bg-neutral-800/80 p-3 rounded-xl border border-white/10">
                  <AlertCircle className="w-4 h-4 text-[var(--accent)] shrink-0" />
                  <span>{btStatusMessage}</span>
                </div>
              )}
            </div>
          )}

          {/* TAB 3: CLOUD SYNC & BACKUP */}
          {activeTab === 'cloud' && (
            <div className="space-y-6">
              {/* Cloud Sync Box */}
              <div className="p-4 rounded-2xl bg-neutral-950 border border-white/5 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-neutral-300">
                    Cloud Sync Room Key
                  </span>
                  <button
                    type="button"
                    onClick={handleGenerateNewSyncKey}
                    className="text-[11px] text-[var(--accent)] hover:underline flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" /> Generate key
                  </button>
                </div>

                <div className="flex items-center gap-2">
                  <div className="flex-1 px-3.5 py-2.5 rounded-xl bg-neutral-900 border border-neutral-700 font-mono text-sm tracking-wider text-white font-bold select-all flex items-center justify-between">
                    <span>{syncState.roomCode}</span>
                    <span className="text-[11px] font-normal text-neutral-500">{stations.length} stations</span>
                  </div>
                  <button
                    type="button"
                    onClick={handleCopySyncKey}
                    className="px-3.5 py-2.5 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-white text-xs font-medium transition-colors border border-white/5 flex items-center gap-1.5 shrink-0"
                  >
                    {copiedSyncKey ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span>Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy</span>
                      </>
                    )}
                  </button>
                </div>

                <div className="grid grid-cols-2 gap-3 pt-1">
                  <button
                    type="button"
                    onClick={handlePushCloud}
                    disabled={isSyncing}
                    className="flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-[var(--accent)] text-neutral-950 font-bold text-xs shadow-md shadow-[var(--accent-glow)]"
                  >
                    <Cloud className="w-4 h-4" />
                    <span>Push to Cloud</span>
                  </button>
                  <button
                    type="button"
                    onClick={handlePullCloud}
                    disabled={isSyncing}
                    className="flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-white text-xs font-semibold border border-white/10"
                  >
                    <RefreshCw className={`w-4 h-4 ${isSyncing ? 'animate-spin' : ''}`} />
                    <span>Pull from Cloud</span>
                  </button>
                </div>

                {/* Auto-Sync Toggle */}
                <label className="flex items-center justify-between p-3 rounded-xl bg-neutral-900/60 border border-white/5 cursor-pointer mt-2">
                  <div>
                    <p className="text-xs font-medium text-neutral-200">Continuous Auto-Sync</p>
                    <p className="text-[11px] text-neutral-400">Pushes station edits to this room automatically</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={syncState.isAutoSync}
                    onChange={(e) => onUpdateSyncState({ isAutoSync: e.target.checked })}
                    className="w-4 h-4 rounded text-[var(--accent)] bg-neutral-800 border-neutral-700"
                  />
                </label>

                {/* Connect code from another device */}
                <div className="pt-2 border-t border-white/5 flex gap-2">
                  <input
                    type="text"
                    value={syncInputCode}
                    onChange={(e) => setSyncInputCode(e.target.value.toUpperCase())}
                    placeholder="Enter code from other device..."
                    className="flex-1 px-3 py-2 rounded-xl bg-neutral-900 border border-neutral-700 font-mono text-xs text-white uppercase focus:outline-none focus:border-[var(--accent)]"
                  />
                  <button
                    type="button"
                    onClick={handleConnectSyncRoom}
                    disabled={isSyncing || !syncInputCode.trim()}
                    className="px-4 py-2 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-white text-xs font-medium border border-white/10"
                  >
                    Connect
                  </button>
                </div>
              </div>

              {/* Export & Import */}
              <div className="space-y-3">
                <label className="block text-xs font-bold text-neutral-200">
                  Export & Import Playlists
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={handleExportJson}
                    className="flex flex-col items-center gap-1.5 p-3 rounded-2xl bg-neutral-950 border border-white/5 hover:border-white/15 hover:bg-neutral-900 transition-all text-center"
                  >
                    <FileJson className="w-5 h-5 text-amber-400" />
                    <span className="text-xs font-semibold text-neutral-200">Export JSON Backup</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleExportM3u}
                    className="flex flex-col items-center gap-1.5 p-3 rounded-2xl bg-neutral-950 border border-white/5 hover:border-white/15 hover:bg-neutral-900 transition-all text-center"
                  >
                    <FileText className="w-5 h-5 text-sky-400" />
                    <span className="text-xs font-semibold text-neutral-200">Export M3U Playlist</span>
                  </button>
                </div>

                <label className="flex flex-col items-center justify-center p-4 rounded-2xl border-2 border-dashed border-neutral-700 hover:border-[var(--accent)] bg-neutral-950/60 cursor-pointer transition-colors group">
                  <Upload className="w-5 h-5 text-neutral-400 group-hover:text-[var(--accent)] mb-1" />
                  <span className="text-xs font-semibold text-neutral-200">Upload JSON or M3U Playlist file</span>
                  <input type="file" accept=".json,.m3u,.m3u8" onChange={handleFileImport} className="hidden" />
                </label>

                {importedPreview && (
                  <div className="p-3.5 rounded-2xl bg-neutral-950 border border-[var(--accent)]/40 space-y-3">
                    <span className="text-xs font-bold text-white">Found {importedPreview.length} stations to import</span>
                    <div className="flex gap-4 text-xs text-neutral-300">
                      <label className="flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="radio"
                          name="importMode"
                          checked={importMode === 'merge'}
                          onChange={() => setImportMode('merge')}
                        />
                        <span>Merge</span>
                      </label>
                      <label className="flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="radio"
                          name="importMode"
                          checked={importMode === 'replace'}
                          onChange={() => setImportMode('replace')}
                        />
                        <span>Replace all</span>
                      </label>
                    </div>
                    <button
                      type="button"
                      onClick={handleApplyImport}
                      className="w-full py-2 rounded-xl bg-[var(--accent)] text-neutral-950 font-bold text-xs"
                    >
                      Confirm Import
                    </button>
                  </div>
                )}
              </div>

              {syncFeedback && (
                <div
                  className={`flex items-center gap-2 text-xs p-3 rounded-xl border ${
                    syncFeedback.type === 'success'
                      ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                      : 'bg-red-500/10 border-red-500/20 text-red-400'
                  }`}
                >
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{syncFeedback.message}</span>
                </div>
              )}
            </div>
          )}

          {/* TAB 4: ANDROID APK & GITHUB ACTIONS WORKFLOW */}
          {activeTab === 'apk' && (
            <div className="space-y-6">
              {/* GitHub Actions CI/CD Box */}
              <div className="p-4 rounded-2xl bg-neutral-950 border border-emerald-500/30 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Github className="w-5 h-5 text-white" />
                    <h3 className="font-bold text-sm text-white">GitHub Actions Automated APK Build</h3>
                  </div>
                  <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    CI/CD Ready
                  </span>
                </div>

                <p className="text-xs text-neutral-300 leading-relaxed">
                  We configured the workflow file <code className="text-emerald-400 font-mono">.github/workflows/build-android-apk.yml</code> and Gradle wrapper in this repository! When you push this project to GitHub, GitHub Actions will automatically compile the native Kotlin code and generate your <code className="text-[var(--accent)] font-mono">app-debug.apk</code> as a downloadable artifact!
                </p>

                <div className="flex items-center justify-between text-xs text-neutral-400 pt-1">
                  <span>Workflow File: <code className="text-white">.github/workflows/build-android-apk.yml</code></span>
                  <button
                    type="button"
                    onClick={handleCopyWorkflow}
                    className="text-[var(--accent)] hover:underline flex items-center gap-1 font-semibold"
                  >
                    {copiedWorkflow ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span>Copied Workflow YAML!</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy YAML</span>
                      </>
                    )}
                  </button>
                </div>

                <pre className="p-3 rounded-xl bg-black/80 border border-white/5 font-mono text-[11px] text-neutral-300 max-h-40 overflow-y-auto leading-relaxed select-all">
                  {githubWorkflowYaml}
                </pre>
              </div>

              {/* 1-Click Project Download */}
              <div className="p-4 rounded-2xl bg-neutral-950 border border-white/5 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-neutral-200">
                    Download Native Kotlin Android Studio Project
                  </span>
                  <span className="text-[11px] text-neutral-500 font-mono">.ZIP</span>
                </div>

                <p className="text-xs text-neutral-400 leading-relaxed">
                  Includes complete Jetpack Compose Material 3 UI, AndroidX Media3 ExoPlayer foreground service, AndroidManifest.xml, and Gradle build configuration.
                </p>

                <a
                  href="/aetherwave-android-kotlin.zip"
                  download="aetherwave-android-kotlin.zip"
                  className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-neutral-950 font-bold text-xs shadow-md transition-all"
                >
                  <Download className="w-4 h-4 stroke-[2.5]" />
                  <span>Download aetherwave-android-kotlin.zip</span>
                </a>
              </div>

              {/* Instant WebAPK on Android */}
              <div className="p-4 rounded-2xl bg-neutral-950 border border-white/5 space-y-2">
                <span className="text-xs font-bold text-neutral-200">Instant Android WebAPK (Direct to Home Screen)</span>
                <p className="text-xs text-neutral-400">
                  Installs as a standalone Android app directly via Chrome/Edge without needing Android Studio.
                </p>
                {isInstalled ? (
                  <div className="flex items-center gap-2 text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-3 py-2 rounded-xl">
                    <Check className="w-4 h-4 shrink-0" />
                    <span>Application is already installed in Standalone Mode!</span>
                  </div>
                ) : isInstallable ? (
                  <button
                    type="button"
                    onClick={async () => {
                      await install();
                    }}
                    className="w-full flex items-center justify-center gap-2 py-2 px-4 rounded-xl bg-[var(--accent)] text-neutral-950 font-bold text-xs shadow-md"
                  >
                    <Download className="w-4 h-4" />
                    <span>Install WebAPK on Android</span>
                  </button>
                ) : (
                  <p className="text-[11px] text-neutral-500">
                    Open on Android Chrome &gt; Tap menu (⋮) &gt; &quot;Install App&quot;
                  </p>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-3 border-t border-white/5 bg-neutral-950/40 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 text-xs font-bold text-neutral-950 bg-[var(--accent)] rounded-full shadow-md shadow-[var(--accent-glow)] hover:brightness-110 transition-all"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
