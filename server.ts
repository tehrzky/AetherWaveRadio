import express, { Request, Response } from 'express';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = parseInt(process.env.PORT || '3000', 10);

app.use(express.json({ limit: '10mb' }));

// In-memory + file-backed sync store
const DATA_DIR = path.join(__dirname, '.data');
const SYNC_FILE = path.join(DATA_DIR, 'sync-store.json');

interface SyncPayload {
  stations: any[];
  customGroups: string[];
  themeColor: string;
  themePresetId: string;
  updatedAt: number;
  deviceLabel?: string;
}

const syncStore: Record<string, SyncPayload> = {};

// Load existing synced presets if available
try {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
  if (fs.existsSync(SYNC_FILE)) {
    const raw = fs.readFileSync(SYNC_FILE, 'utf-8');
    Object.assign(syncStore, JSON.parse(raw));
  }
} catch (e) {
  console.warn('Failed to load initial sync store:', e);
}

function persistStore() {
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    fs.writeFileSync(SYNC_FILE, JSON.stringify(syncStore, null, 2), 'utf-8');
  } catch (e) {
    console.error('Failed to write sync store:', e);
  }
}

// Cloud Sync endpoints
app.get('/api/sync/:code', (req: Request, res: Response): void => {
  const code = (req.params.code || '').trim().toUpperCase();
  if (!code || code.length < 3) {
    res.status(400).json({ error: 'Invalid sync code format.' });
    return;
  }

  const room = syncStore[code];
  if (!room) {
    res.status(404).json({ error: 'Sync room not found or expired.' });
    return;
  }

  res.json({ success: true, roomCode: code, data: room });
});

app.post('/api/sync/:code', (req: Request, res: Response): void => {
  const code = (req.params.code || '').trim().toUpperCase();
  if (!code || code.length < 3) {
    res.status(400).json({ error: 'Invalid sync code format.' });
    return;
  }

  const { stations, customGroups, themeColor, themePresetId, deviceLabel } = req.body;
  if (!Array.isArray(stations)) {
    res.status(400).json({ error: 'Invalid stations payload.' });
    return;
  }

  const payload: SyncPayload = {
    stations,
    customGroups: Array.isArray(customGroups) ? customGroups : [],
    themeColor: typeof themeColor === 'string' ? themeColor : '#f59e0b',
    themePresetId: typeof themePresetId === 'string' ? themePresetId : 'studio_amber',
    updatedAt: Date.now(),
    deviceLabel: typeof deviceLabel === 'string' ? deviceLabel : 'Web Client',
  };

  syncStore[code] = payload;
  persistStore();

  res.json({
    success: true,
    roomCode: code,
    updatedAt: payload.updatedAt,
    stationCount: stations.length,
    message: 'Preset synchronized successfully.',
  });
});

// Stream health checker
app.post('/api/test-stream', async (req: Request, res: Response): Promise<void> => {
  const { url } = req.body;
  if (!url || typeof url !== 'string' || !url.startsWith('http')) {
    res.status(400).json({ valid: false, message: 'Invalid URL provided' });
    return;
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4500);

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Icy-MetaData': '1',
        'User-Agent': 'AetherWave/1.0',
        Range: 'bytes=0-1024',
      },
      signal: controller.signal,
    });
    clearTimeout(timeout);

    const contentType = response.headers.get('content-type') || '';
    const isAudio =
      contentType.includes('audio') ||
      contentType.includes('mpeg') ||
      contentType.includes('ogg') ||
      contentType.includes('aac') ||
      contentType.includes('application/vnd.apple.mpegurl') ||
      contentType.includes('application/x-mpegURL') ||
      url.endsWith('.m3u8') ||
      url.endsWith('.mp3') ||
      url.endsWith('.aac') ||
      response.status === 200 ||
      response.status === 206;

    res.json({
      valid: isAudio,
      status: response.status,
      contentType,
      icyName: response.headers.get('icy-name') || undefined,
      icyBr: response.headers.get('icy-br') || undefined,
    });
  } catch (err: any) {
    // Some streams block range requests or CORS HEAD, but still play in <audio> tag
    res.json({
      valid: true,
      notice: 'Audio test timeout or blocked headers, but stream may still load in browser audio player.',
    });
  }
});

// Download complete Native Kotlin Android Project
app.get('/api/download-android-project', async (_req: Request, res: Response): Promise<void> => {
  try {
    const JSZip = (await import('jszip')).default;
    const zip = new JSZip();
    const androidDir = path.join(__dirname, 'android');

    function addDirectoryToZip(currentDir: string, zipFolder: any) {
      if (!fs.existsSync(currentDir)) return;
      const entries = fs.readdirSync(currentDir, { withFileTypes: true });
      for (const entry of entries) {
        const fullPath = path.join(currentDir, entry.name);
        if (entry.isDirectory()) {
          const subFolder = zipFolder.folder(entry.name);
          addDirectoryToZip(fullPath, subFolder);
        } else {
          const content = fs.readFileSync(fullPath);
          zipFolder.file(entry.name, content);
        }
      }
    }

    addDirectoryToZip(androidDir, zip);

    const buffer = await zip.generateAsync({
      type: 'nodebuffer',
      compression: 'DEFLATE',
      compressionOptions: { level: 9 },
    });

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="aetherwave-android-kotlin.zip"');
    res.send(buffer);
  } catch (err: any) {
    console.error('Failed to create Android project zip:', err);
    res.status(500).json({ error: 'Failed to generate Android project ZIP' });
  }
});

async function startServer() {
  const isProd = process.env.NODE_ENV === 'production';

  if (!isProd) {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(path.join(__dirname, 'dist')));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(__dirname, 'dist', 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Radio server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
