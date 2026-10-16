import Hls from 'hls.js';
import { Station } from '../types';

class AudioEngine {
  private audio: HTMLAudioElement;
  private hls: Hls | null = null;
  private audioCtx: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private sourceNode: MediaElementAudioSourceNode | null = null;
  private isSourceConnected = false;
  private currentStation: Station | null = null;
  private isAudioUnlocked = false;

  constructor() {
    this.audio = new Audio();
    this.audio.preload = 'none';
    // Do not set crossOrigin initially to allow all live streams (even without CORS headers) to play!
    // We will attempt crossOrigin cautiously for Web Audio API.
  }

  public getAudioElement(): HTMLAudioElement {
    return this.audio;
  }

  public getCurrentStation(): Station | null {
    return this.currentStation;
  }

  public async setAudioOutputDevice(deviceId: string): Promise<boolean> {
    try {
      if ('setSinkId' in this.audio && typeof (this.audio as any).setSinkId === 'function') {
        await (this.audio as any).setSinkId(deviceId);
        return true;
      }
      return false;
    } catch (err) {
      console.warn('Failed to set sink ID:', err);
      return false;
    }
  }

  public async initAudioContext(): Promise<AnalyserNode | null> {
    if (this.analyser) return this.analyser;

    try {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtxClass) return null;

      if (!this.audioCtx) {
        this.audioCtx = new AudioCtxClass();
      }

      if (this.audioCtx.state === 'suspended') {
        await this.audioCtx.resume();
      }

      if (!this.isSourceConnected) {
        try {
          this.analyser = this.audioCtx.createAnalyser();
          this.analyser.fftSize = 64;
          this.analyser.smoothingTimeConstant = 0.8;

          this.sourceNode = this.audioCtx.createMediaElementSource(this.audio);
          this.sourceNode.connect(this.analyser);
          this.analyser.connect(this.audioCtx.destination);
          this.isSourceConnected = true;
        } catch (corsErr) {
          // If stream has CORS restrictions, createMediaElementSource may fail or mute output
          console.info('MediaElementSource not bound due to CORS stream policy, using fallback visualizer mode.');
        }
      }
      this.isAudioUnlocked = true;
      return this.analyser;
    } catch (e) {
      console.warn('AudioContext init error:', e);
      return null;
    }
  }

  public getAnalyser(): AnalyserNode | null {
    return this.analyser;
  }

  public async playStation(
    station: Station,
    options?: { onMetadataUpdate?: (title: string) => void }
  ): Promise<void> {
    this.currentStation = station;

    // Destroy existing HLS session if any
    if (this.hls) {
      this.hls.destroy();
      this.hls = null;
    }

    const streamUrl = station.streamUrl.trim();
    const isHls =
      station.codec === 'HLS' ||
      streamUrl.toLowerCase().includes('.m3u8') ||
      streamUrl.toLowerCase().includes('application/x-mpegurl');

    if (isHls && Hls.isSupported()) {
      this.hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
      });
      this.hls.loadSource(streamUrl);
      this.hls.attachMedia(this.audio);
    } else {
      this.audio.src = streamUrl;
    }

    // MediaSession updates for Bluetooth devices, car infotainment, and lockscreens
    this.updateMediaSession(station);

    // Attempt Web Audio context unlock in user gesture
    this.initAudioContext().catch(() => {});

    await this.audio.play();
  }

  public pause(): void {
    this.audio.pause();
    if ('mediaSession' in navigator) {
      navigator.mediaSession.playbackState = 'paused';
    }
  }

  public resume(): Promise<void> {
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
    if ('mediaSession' in navigator) {
      navigator.mediaSession.playbackState = 'playing';
    }
    return this.audio.play();
  }

  public setVolume(volume: number): void {
    const clamped = Math.max(0, Math.min(1, volume));
    this.audio.volume = clamped;
  }

  public setMuted(muted: boolean): void {
    this.audio.muted = muted;
  }

  public updateMediaSession(station: Station): void {
    if (!('mediaSession' in navigator)) return;

    try {
      const artwork = station.logoUrl
        ? [
            { src: station.logoUrl, sizes: '96x96', type: 'image/jpeg' },
            { src: station.logoUrl, sizes: '192x192', type: 'image/jpeg' },
            { src: station.logoUrl, sizes: '512x512', type: 'image/jpeg' },
          ]
        : [];

      navigator.mediaSession.metadata = new MediaMetadata({
        title: station.name,
        artist: station.group || 'Live Radio',
        album: 'AetherWave Studio Radio',
        artwork: artwork.length > 0 ? artwork : undefined,
      });

      navigator.mediaSession.playbackState = 'playing';
    } catch (e) {
      console.warn('Failed to update MediaSession metadata', e);
    }
  }

  public bindMediaSessionActions(handlers: {
    onPlay: () => void;
    onPause: () => void;
    onPrevious: () => void;
    onNext: () => void;
  }): void {
    if (!('mediaSession' in navigator)) return;

    try {
      navigator.mediaSession.setActionHandler('play', handlers.onPlay);
      navigator.mediaSession.setActionHandler('pause', handlers.onPause);
      navigator.mediaSession.setActionHandler('previoustrack', handlers.onPrevious);
      navigator.mediaSession.setActionHandler('nexttrack', handlers.onNext);
      navigator.mediaSession.setActionHandler('stop', handlers.onPause);
    } catch (e) {
      console.warn('MediaSession action handler error:', e);
    }
  }
}

export const audioEngine = new AudioEngine();
