const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const builds = new Map();

const BUILD_WORK_DIR = process.env.BUILD_WORK_DIRECTORY || path.join(__dirname, '../temp_builds');
const RETENTION_MS = parseInt(process.env.APK_RETENTION_TIME || '3600000', 10); // 1 hour
const BUILD_TIMEOUT_MS = parseInt(process.env.BUILD_TIMEOUT || '600000', 10); // 10 mins

if (!fs.existsSync(BUILD_WORK_DIR)) {
  fs.mkdirSync(BUILD_WORK_DIR, { recursive: true });
}

function createBuildRecord(buildId, fileName, uploadPath, projectInfo) {
  const record = {
    buildId,
    fileName,
    uploadPath,
    workspaceDir: path.join(BUILD_WORK_DIR, buildId),
    projectRoot: projectInfo.projectRoot,
    hasGradlew: projectInfo.hasGradlew,
    status: 'QUEUED', // QUEUED, BUILDING, SUCCESS, FAILED
    step: 'Uploaded. Ready to compile.',
    progressPercent: 10,
    isFinished: false,
    isSuccess: false,
    logs: [
      `[INFO] Build initialized for ${fileName}`,
      `[INFO] Workspace created at ${path.join(BUILD_WORK_DIR, buildId)}`,
      `[INFO] Target project root: ${projectInfo.projectRoot}`,
      `[INFO] Gradle wrapper status: ${projectInfo.hasGradlew ? 'Present' : 'Not found (Using system gradle)'}`
    ],
    apkName: null,
    apkSize: null,
    apkPath: null,
    errorMessage: null,
    createdAt: Date.now()
  };

  builds.set(buildId, record);
  return record;
}

function getBuildRecord(buildId) {
  return builds.get(buildId) || null;
}

function appendLog(buildId, line) {
  const record = builds.get(buildId);
  if (record) {
    const time = new Date().toISOString().substring(11, 19);
    record.logs.push(`[${time}] ${line}`);
  }
}

async function startBuildProcess(buildId, task = 'assembleDebug') {
  const record = builds.get(buildId);
  if (!record) throw new Error('Build ID not found');

  record.status = 'BUILDING';
  record.step = 'Preparing Gradle environment...';
  record.progressPercent = 20;
  appendLog(buildId, `Starting build process with task: ${task}`);

  return new Promise((resolve) => {
    let cmd = 'gradle';
    let args = [task, '--stacktrace'];

    if (record.hasGradlew) {
      cmd = path.join(record.projectRoot, 'gradlew');
    }

    appendLog(buildId, `Executing command: ${cmd} ${args.join(' ')}`);
    record.step = 'Resolving dependencies & executing Gradle...';
    record.progressPercent = 40;

    const child = spawn(cmd, args, {
      cwd: record.projectRoot,
      env: { ...process.env, JAVA_HOME: process.env.JAVA_HOME || undefined }
    });

    let timeoutTimer = setTimeout(() => {
      child.kill('SIGKILL');
      record.status = 'FAILED';
      record.isFinished = true;
      record.errorMessage = `Build timed out after ${BUILD_TIMEOUT_MS / 1000} seconds.`;
      appendLog(buildId, `[ERROR] Build timed out after ${BUILD_TIMEOUT_MS / 1000}s`);
      resolve(false);
    }, BUILD_TIMEOUT_MS);

    child.stdout.on('data', (data) => {
      const lines = data.toString().split('\n').filter(l => l.trim().length > 0);
      for (const line of lines) {
        appendLog(buildId, line);

        if (line.includes('> Task :') && line.includes('Compile')) {
          record.step = 'Compiling source code...';
          record.progressPercent = 60;
        } else if (line.includes('> Task :') && line.includes('package')) {
          record.step = 'Packaging APK...';
          record.progressPercent = 80;
        }
      }
    });

    child.stderr.on('data', (data) => {
      const lines = data.toString().split('\n').filter(l => l.trim().length > 0);
      for (const line of lines) {
        appendLog(buildId, `[STDERR] ${line}`);
      }
    });

    child.on('error', (err) => {
      clearTimeout(timeoutTimer);
      record.status = 'FAILED';
      record.isFinished = true;
      record.errorMessage = `Failed to launch Gradle process: ${err.message}`;
      appendLog(buildId, `[ERROR] Failed to start process: ${err.message}`);
      resolve(false);
    });

    child.on('close', (code) => {
      clearTimeout(timeoutTimer);
      appendLog(buildId, `Gradle process exited with code ${code}`);

      if (code === 0) {
        record.step = 'Locating generated APK...';
        record.progressPercent = 90;

        const apkInfo = findGeneratedApk(record.projectRoot);
        if (apkInfo) {
          record.status = 'SUCCESS';
          record.isFinished = true;
          record.isSuccess = true;
          record.step = 'APK Build Successful';
          record.progressPercent = 100;
          record.apkName = apkInfo.name;
          record.apkSize = apkInfo.size;
          record.apkPath = apkInfo.path;
          appendLog(buildId, `[SUCCESS] Located APK: ${apkInfo.name} (${(apkInfo.size / 1024 / 1024).toFixed(2)} MB)`);
          scheduleCleanup(buildId);
          resolve(true);
        } else {
          record.status = 'FAILED';
          record.isFinished = true;
          record.errorMessage = 'Build completed successfully according to Gradle, but APK file was not found in output directories.';
          appendLog(buildId, '[ERROR] Build completed but APK file was not found.');
          resolve(false);
        }
      } else {
        record.status = 'FAILED';
        record.isFinished = true;
        record.errorMessage = `Gradle build failed with exit code ${code}. Inspect logs above for specific compilation errors.`;
        appendLog(buildId, `[ERROR] Build compilation failed (exit code ${code}).`);
        resolve(false);
      }
    });
  });
}

function findGeneratedApk(dir) {
  const searchDirs = [
    path.join(dir, 'app/build/outputs/apk/debug'),
    path.join(dir, 'app/build/outputs/apk/release'),
    path.join(dir, 'build/outputs/apk/debug'),
    path.join(dir, 'build/outputs/apk'),
    dir
  ];

  for (const searchDir of searchDirs) {
    if (fs.existsSync(searchDir)) {
      const files = fs.readdirSync(searchDir);
      for (const file of files) {
        if (file.endsWith('.apk')) {
          const fullPath = path.join(searchDir, file);
          const stat = fs.statSync(fullPath);
          return {
            name: file,
            path: fullPath,
            size: stat.size
          };
        }
      }
    }
  }

  return null;
}

function scheduleCleanup(buildId) {
  setTimeout(() => {
    const record = builds.get(buildId);
    if (record) {
      try {
        if (fs.existsSync(record.workspaceDir)) {
          fs.rmSync(record.workspaceDir, { recursive: true, force: true });
        }
        if (fs.existsSync(record.uploadPath)) {
          fs.unlinkSync(record.uploadPath);
        }
      } catch (err) {
        console.error(`Error cleaning up build ${buildId}:`, err);
      }
      builds.delete(buildId);
    }
  }, RETENTION_MS);
}

module.exports = {
  createBuildRecord,
  getBuildRecord,
  startBuildProcess
};
