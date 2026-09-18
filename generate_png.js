const sharp = require('sharp');
sharp('icon.svg')
  .resize(512, 512)
  .png()
  .toFile('icon.png')
  .then(info => console.log('Successfully generated icon.png'))
  .catch(err => console.error('Error:', err));
