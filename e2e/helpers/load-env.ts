import fs from 'node:fs';
import path from 'node:path';

import dotenv from 'dotenv';

const envPath = path.join(__dirname, '..', '.env');

if (fs.existsSync(envPath)) {
  dotenv.config({ path: envPath });
}
