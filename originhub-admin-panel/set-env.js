const dotenv = require('dotenv');
const fs = require('fs');

dotenv.config();

const env = process.env;
const apiUrl = env.ORIGINHUB_API_URL || 'http://localhost:8080';

const targetPath = './src/environments/environment.ts';
const content = `export const environment = {
  apiUrl: '${apiUrl}',
};
`;

fs.writeFileSync(targetPath, content);
console.log('✅ environment.ts created successfully.');
