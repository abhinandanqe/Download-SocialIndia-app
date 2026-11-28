const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const DIRECTORY = path.join(__dirname, 'web_server');

const server = http.createServer((req, res) => {
    console.log(`${new Date().toISOString()} - ${req.method} ${req.url}`);
    
    let filePath = path.join(DIRECTORY, req.url === '/' ? 'index.html' : req.url);
    
    // Safety check: ensure file is within web_server directory
    if (!filePath.startsWith(DIRECTORY)) {
        res.statusCode = 403;
        res.end('Forbidden');
        return;
    }

    fs.access(filePath, fs.constants.F_OK, (err) => {
        if (err) {
            console.error(`File not found: ${filePath}`);
            res.statusCode = 404;
            res.end('Not Found');
            return;
        }

        const extname = path.extname(filePath);
        let contentType = 'text/html';
        if (extname === '.apk') contentType = 'application/vnd.android.package-archive';

        res.setHeader('Content-Type', contentType);
        
        // For APK files, force download
        if (extname === '.apk') {
            res.setHeader('Content-Disposition', 'attachment; filename="SocialIndia-debug.apk"');
        }

        const stream = fs.createReadStream(filePath);
        stream.pipe(res);
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running at http://0.0.0.0:${PORT}/`);
});
