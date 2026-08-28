const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');

const { extractAndInspectProject } = require('./projectInspector');
const { createBuildRecord, getBuildRecord, startBuildProcess } = require('./buildManager');

const UPLOAD_DIR = process.env.UPLOAD_DIRECTORY || path.join(__dirname, '../uploads');
const MAX_UPLOAD_SIZE = parseInt(process.env.MAX_UPLOAD_SIZE || '524288000', 10); // 500MB default

if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOAD_DIR),
  filename: (req, file, cb) => cb(null, `${Date.now()}-${uuidv4()}-${file.originalname}`)
});

const upload = multer({
  storage,
  limits: { fileSize: MAX_UPLOAD_SIZE },
  fileFilter: (req, file, cb) => {
    if (file.mimetype === 'application/zip' || file.originalname.endsWith('.zip')) {
      cb(null, true);
    } else {
      cb(new Error('Only .zip files containing Android Gradle projects are allowed.'));
    }
  }
});

// GET /api/health
router.get('/health', (req, res) => {
  const sdkPath = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || '/opt/android-sdk';
  const sdkConfigured = fs.existsSync(sdkPath);

  res.json({
    status: 'online',
    workerAvailable: true,
    sdkConfigured,
    message: sdkConfigured ? 'Android Build Worker is online and ready.' : 'Android SDK path not found. Please set ANDROID_SDK_ROOT.'
  });
});

// POST /api/upload
router.post('/upload', upload.single('zipFile'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ success: false, message: 'No file uploaded.' });
    }

    const buildId = uuidv4();
    const workspaceDir = path.join(process.env.BUILD_WORK_DIRECTORY || path.join(__dirname, '../temp_builds'), buildId);

    // Extract & Inspect
    const projectInfo = await extractAndInspectProject(req.file.path, workspaceDir);

    const record = createBuildRecord(buildId, req.file.originalname, req.file.path, projectInfo);

    res.json({
      success: true,
      buildId,
      fileName: req.file.originalname,
      projectRoot: projectInfo.projectRoot,
      gradleFound: projectInfo.hasGradlew,
      message: 'Project uploaded and inspected successfully.'
    });

  } catch (err) {
    console.error('Upload Error:', err);
    if (req.file && fs.existsSync(req.file.path)) {
      fs.unlinkSync(req.file.path);
    }
    res.status(400).json({
      success: false,
      message: err.message || 'Failed to process uploaded ZIP file.'
    });
  }
});

// POST /api/build
router.post('/build', async (req, res) => {
  const { buildId, task = 'assembleDebug' } = req.body;
  if (!buildId) {
    return res.status(400).json({ success: false, message: 'buildId parameter is required.' });
  }

  const record = getBuildRecord(buildId);
  if (!record) {
    return res.status(440).json({ success: false, message: 'Build ID expired or not found.' });
  }

  // Trigger background build
  startBuildProcess(buildId, task).catch(err => {
    console.error(`Build background error for ${buildId}:`, err);
  });

  res.json({
    success: true,
    message: `Build task '${task}' initiated for buildId: ${buildId}`
  });
});

// GET /api/build-status/:buildId
router.get('/build-status/:buildId', (req, res) => {
  const record = getBuildRecord(req.params.buildId);
  if (!record) {
    return res.status(404).json({
      status: 'FAILED',
      isFinished: true,
      isSuccess: false,
      errorMessage: 'Build ID not found or expired.'
    });
  }

  const host = req.get('host');
  const protocol = req.protocol;
  const downloadUrl = record.isSuccess && record.apkName ? `${protocol}://${host}/api/download/${record.buildId}` : null;

  res.json({
    status: record.status,
    step: record.step,
    progressPercent: record.progressPercent,
    isFinished: record.isFinished,
    isSuccess: record.isSuccess,
    apkName: record.apkName,
    apkSize: record.apkSize,
    downloadUrl,
    errorMessage: record.errorMessage
  });
});

// GET /api/build-logs/:buildId
router.get('/build-logs/:buildId', (req, res) => {
  const record = getBuildRecord(req.params.buildId);
  if (!record) {
    return res.status(404).json({ logs: ['[ERROR] Build session expired or not found.'] });
  }
  res.json({ logs: record.logs });
});

// GET /api/download/:buildId
router.get('/download/:buildId', (req, res) => {
  const record = getBuildRecord(req.params.buildId);
  if (!record || !record.apkPath || !fs.existsSync(record.apkPath)) {
    return res.status(404).send('APK file not found or expired.');
  }

  res.download(record.apkPath, record.apkName || 'app-debug.apk', (err) => {
    if (err) {
      console.error('Error downloading APK:', err);
    }
  });
});

module.exports = router;
