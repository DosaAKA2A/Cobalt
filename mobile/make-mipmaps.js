// Genera los iconos launcher (mipmaps) desde build/icon.png del proyecto de escritorio.
const { app, nativeImage } = require('electron');
const fs = require('fs');
const path = require('path');

app.disableHardwareAcceleration();
app.whenReady().then(() => {
  const src = nativeImage.createFromPath(path.join(__dirname, '..', 'build', 'icon.png'));
  const dens = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
  const base = path.join(__dirname, 'app', 'src', 'main', 'res');
  for (const [d, s] of Object.entries(dens)) {
    const dir = path.join(base, 'mipmap-' + d);
    fs.mkdirSync(dir, { recursive: true });
    const png = src.resize({ width: s, height: s, quality: 'best' }).toPNG();
    fs.writeFileSync(path.join(dir, 'ic_launcher.png'), png);
    fs.writeFileSync(path.join(dir, 'ic_launcher_round.png'), png);
  }
  console.log('mipmaps generados');
  app.exit(0);
});
