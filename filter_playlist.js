const fs = require('fs');
const readline = require('readline');
const { Readable } = require('stream');

const url = 'http://64.225.82.239/get.php?username=SCSF41T2BO&password=HW36KVFYUU&type=m3u_plus&output=mpegts';
const outputFile = 'filtered_playlist.m3u';

async function filterPlaylist() {
    console.log(`Starting to download and filter playlist...`);
    const writeStream = fs.createWriteStream(outputFile);
    writeStream.write('#EXTM3U\n');

    try {
        const response = await fetch(url, { redirect: 'follow' });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const rl = readline.createInterface({
            input: Readable.fromWeb(response.body),
            crlfDelay: Infinity
        });

        let currentExtInf = null;
        let shouldKeep = false;
        let modifiedExtInf = null;
        let channelsKept = 0;

        for await (const line of rl) {
            if (line.startsWith('#EXTINF')) {
                currentExtInf = line;
                shouldKeep = false;
                
                const lowerLine = line.toLowerCase();
                
                // Check for Kurdish
                if (lowerLine.includes('kurd')) {
                    shouldKeep = true;
                    // Replace whatever group-title with Kurdistan
                    if (currentExtInf.includes('group-title="')) {
                        modifiedExtInf = currentExtInf.replace(/group-title="[^"]*"/, 'group-title="Kurdistan"');
                    } else {
                        modifiedExtInf = currentExtInf.replace(',', ' group-title="Kurdistan",');
                    }
                } 
                // Check for beIN
                else if (lowerLine.includes('bein')) {
                    shouldKeep = true;
                    modifiedExtInf = currentExtInf;
                }
            } 
            else if (line.startsWith('http')) {
                if (shouldKeep && currentExtInf) {
                    writeStream.write(modifiedExtInf + '\n');
                    writeStream.write(line + '\n');
                    channelsKept++;
                    if (channelsKept % 50 === 0) {
                        console.log(`Kept ${channelsKept} channels so far...`);
                    }
                }
                currentExtInf = null;
                shouldKeep = false;
                modifiedExtInf = null;
            }
        }

        writeStream.end();
        console.log(`Finished filtering! Kept ${channelsKept} channels total.`);
        console.log(`Saved as ${outputFile}`);
        
    } catch (err) {
        console.error(`Error: ${err.message}`);
    }
}

filterPlaylist();
