import dotenv from 'dotenv';
import fs from 'fs';

dotenv.config();

const env = process.env;

const targetPath = './src/environments/environment.ts';
const content = `export const environment = {
  apiUrl: '${env.VERCEL_API_BASE_URL || 'http://localhost:8080'}',
  gitUrl: '${env.VERCEL_GIT_SSH_URL || 'git@originhub-local'}',
};
`;

fs.writeFileSync(targetPath, content);
console.log('✅ environment.ts created successfully.');
