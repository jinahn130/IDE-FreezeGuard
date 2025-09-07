// Simple test to verify extension functionality
const http = require('http');

// Simulate an action event
const testEvent = {
  action: 'TestAction',
  duration_ms: 123.45,
  thread: 'MAIN',
  heap_delta_bytes: 1024,
  edt_stalls: 2,
  edt_longest_stall_ms: 250.5,
  ts: new Date().toISOString()
};

console.log('Testing VSCode FreezeGuard Extension...');
console.log('Sending test event:', JSON.stringify(testEvent, null, 2));

// Test sending event to collector
const payload = JSON.stringify(testEvent);

const options = {
  hostname: '127.0.0.1',
  port: 8000,
  path: '/ingest',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json; charset=UTF-8',
    'Content-Length': Buffer.byteLength(payload)
  }
};

const req = http.request(options, (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    console.log(`Response status: ${res.statusCode}`);
    console.log('Response body:', data);
    
    // Check the report endpoint
    setTimeout(() => {
      const reportReq = http.request({
        hostname: '127.0.0.1',
        port: 8000,
        path: '/report',
        method: 'GET'
      }, (reportRes) => {
        let reportData = '';
        reportRes.on('data', (chunk) => reportData += chunk);
        reportRes.on('end', () => {
          console.log('\nLatest events from collector:');
          console.log(reportData);
        });
      });
      reportReq.end();
    }, 500);
  });
});

req.on('error', (err) => {
  console.error('Request error:', err);
});

req.write(payload);
req.end();