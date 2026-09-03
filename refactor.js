const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, 'Frontend', 'src');
const configPath = path.join(srcDir, 'config.ts');

function processDir(dir) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
        const fullPath = path.join(dir, file);
        if (fs.statSync(fullPath).isDirectory()) {
            processDir(fullPath);
        } else if (fullPath.endsWith('.tsx') || fullPath.endsWith('.ts')) {
            if (fullPath === configPath) continue;
            
            let content = fs.readFileSync(fullPath, 'utf8');
            let modified = false;

            if (content.includes('http://localhost:8080') || content.includes('http://localhost:3000')) {
                // Determine relative path to config.ts
                const relativePath = path.relative(path.dirname(fullPath), configPath).replace(/\\\\/g, '/').replace('.ts', '');
                const importPath = relativePath.startsWith('.') ? relativePath : './' + relativePath;
                
                if (!content.includes('import { API_URL')) {
                    content = import { API_URL, WS_URL } from '';\n + content;
                }
                
                // Replace 'http://localhost:8080/...' with \\/...\
                content = content.replace(/'http:\/\/localhost:8080([^']*)'/g, '\');
                content = content.replace(/"http:\/\/localhost:8080([^"]*)"/g, '\');
                
                // Replace 'http://localhost:3000/...' with \\/...\
                content = content.replace(/'http:\/\/localhost:3000([^']*)'/g, '\');
                content = content.replace(/"http:\/\/localhost:3000([^"]*)"/g, '\');

                fs.writeFileSync(fullPath, content);
                console.log('Updated:', fullPath);
            }
        }
    }
}

processDir(srcDir);
