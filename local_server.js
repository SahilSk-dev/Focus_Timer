const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

// Security & Content-Security-Policy headers
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader(
    'Content-Security-Policy',
    "default-src 'self'; " +
    "script-src 'self' 'unsafe-inline' https://www.gstatic.com https://cdnjs.cloudflare.com; " +
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
    "img-src 'self' data: blob: https:; " +
    "connect-src 'self' https://firestore.googleapis.com https://*.firebaseio.com https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://www.googleapis.com https://accounts.google.com https://translate.googleapis.com https://cdnjs.cloudflare.com https://www.gstatic.com https://fonts.googleapis.com https://fonts.gstatic.com wss://*.firebaseio.com; " +
    "font-src 'self' data: https://fonts.gstatic.com https://fonts.googleapis.com; " +
    "frame-ancestors 'self'; " +
    "base-uri 'self'; form-action 'self'"
  );
  next();
});

// Serve static assets from public/ directory
const publicDir = path.join(__dirname, 'public');
app.use(express.static(publicDir));

// SPA fallback: Route all other requests to index.html
app.get('*', (req, res) => {
  res.sendFile(path.join(publicDir, 'index.html'));
});

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`Focus Timer server running at http://localhost:${PORT}`);
  });
}

module.exports = app;
